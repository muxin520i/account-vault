package com.accountvault.app

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Same encrypted envelope as the web app (app.js) and iOS (Sync.swift):
 *   key  = PBKDF2-HMAC-SHA256(UTF-8 password, salt[16], 310000 iterations, 32 bytes)
 *   file = {"v":1,"salt":b64(salt),"iv":b64(iv[12]),"ct":b64(ciphertext || tag[16]),"at":ms}
 * Base64 is standard with padding (btoa / base64EncodedString). Plaintext is the AtlasData JSON.
 */
object AtlasCrypto {
  const val ITERATIONS = 310_000
  class BadFormat : Exception("同步文件格式不对")

  private val b64e = Base64.getEncoder()
  private val b64d = Base64.getDecoder()
  fun b64(b: ByteArray): String = b64e.encodeToString(b)
  fun unb64(s: String): ByteArray = b64d.decode(s.trim())

  fun randomBytes(n: Int) = ByteArray(n).also { SecureRandom().nextBytes(it) }

  /** PBKDF2 done by hand on UTF-8 bytes, so non-ASCII passwords match WebCrypto / CommonCrypto exactly. */
  fun derive(pass: String, salt: ByteArray, iterations: Int = ITERATIONS): ByteArray {
    val mac = Mac.getInstance("HmacSHA256")
    // HMAC zero-pads the key, so an empty key equals a single 0x00 byte (SecretKeySpec rejects empty)
    val k = pass.toByteArray(Charsets.UTF_8)
    mac.init(SecretKeySpec(if (k.isEmpty()) ByteArray(1) else k, "HmacSHA256"))
    val out = ByteArray(32)
    // one block is enough for 32 bytes with SHA-256
    mac.update(salt); mac.update(byteArrayOf(0, 0, 0, 1))
    var u = mac.doFinal()
    val t = u.copyOf()
    for (i in 1 until iterations) {
      u = mac.doFinal(u)
      for (j in t.indices) t[j] = (t[j].toInt() xor u[j].toInt()).toByte()
    }
    System.arraycopy(t, 0, out, 0, 32)
    return out
  }

  fun seal(plain: ByteArray, key: ByteArray, saltB64: String, at: Double = nowMS()): String {
    val iv = randomBytes(12)
    val c = Cipher.getInstance("AES/GCM/NoPadding")
    c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
    val ct = c.doFinal(plain)
    val env = JsonObject(mapOf(
      "v" to JsonPrimitive(1), "salt" to JsonPrimitive(saltB64), "iv" to JsonPrimitive(b64(iv)),
      "ct" to JsonPrimitive(b64(ct)), "at" to JsonPrimitive(at.toLong()),
    ))
    return env.toString()
  }

  fun saltOf(text: String): String? = runCatching { Json.parseToJsonElement(text).jsonObject["salt"]?.jsonPrimitive?.content }.getOrNull()

  /** Throws BadFormat for a malformed file, javax.crypto.AEADBadTagException for a wrong password. */
  fun open(text: String, key: ByteArray): ByteArray {
    val o = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull() ?: throw BadFormat()
    val iv = o["iv"]?.jsonPrimitive?.content?.let { runCatching { unb64(it) }.getOrNull() } ?: throw BadFormat()
    val ct = o["ct"]?.jsonPrimitive?.content?.let { runCatching { unb64(it) }.getOrNull() } ?: throw BadFormat()
    if (ct.size <= 16) throw BadFormat()
    val c = Cipher.getInstance("AES/GCM/NoPadding")
    c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
    return c.doFinal(ct)
  }

  fun sealData(d: AtlasData, key: ByteArray, saltB64: String) = seal(encodeAtlas(d).toByteArray(Charsets.UTF_8), key, saltB64)
  fun openData(text: String, key: ByteArray): AtlasData = decodeAtlas(String(open(text, key), Charsets.UTF_8))
}
