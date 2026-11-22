package com.accountvault.app

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 * 账号仓库 · 本地加密保险层（真原生版）
 *
 * 数据文件 vault.dat 是一个加密信封：
 *   key  = PBKDF2-HMAC-SHA256(主密码 UTF-8, salt[16], 310000, 32)
 *   file = {"v":1,"salt":b64,"iv":b64,"ct":b64(ciphertext||tag)}
 * 明文只在解锁期间存在于内存；锁定即清空。
 *
 * 首次启动若检测到旧版明文 atlas.json，会在设置主密码后自动迁移并删除明文。
 */
object Vault {
  const val PROBE = "accountvault-probe-v1"
  const val BACKUP_APP = "accountvault-backup"

  enum class State { NEED_SETUP, LOCKED, UNLOCKED }

  private lateinit var prefs: SharedPreferences
  private lateinit var dir: File
  private var dataFile: File? = null
  private var legacyFile: File? = null
  private var key: ByteArray? = null

  @Volatile var state = State.NEED_SETUP
    private set

  /** 明文缓存，仅解锁期间非空 */
  var plaintext: String? = null
    private set

  fun init(ctx: Context) {
    prefs = ctx.getSharedPreferences("accountvault.vault", Context.MODE_PRIVATE)
    dir = ctx.filesDir
    dataFile = File(dir, "vault.dat")
    legacyFile = File(dir, "atlas.json")
    state = when {
      prefs.getString("salt", null) == null -> State.NEED_SETUP
      else -> State.LOCKED
    }
  }

  fun isSetup(): Boolean = prefs.getString("salt", null) != null

  /** 设置主密码；若有旧明文则迁移加密 */
  fun create(password: String): Boolean {
    if (password.length < 4) return false
    val salt = AtlasCrypto.randomBytes(16)
    val k = AtlasCrypto.derive(password, salt)
    val saltB64 = AtlasCrypto.b64(salt)
    // 写验证头
    val probeIv = AtlasCrypto.randomBytes(12)
    val probeCt = aesEncrypt(PROBE.toByteArray(Charsets.UTF_8), k, probeIv)
    prefs.edit()
      .putString("salt", saltB64)
      .putString("probeIv", AtlasCrypto.b64(probeIv))
      .putString("probeCt", AtlasCrypto.b64(probeCt))
      .apply()
    key = k
    // 迁移旧明文
    val legacy = legacyFile
    val plain = if (legacy != null && legacy.exists()) {
      val t = runCatching { legacy.readText() }.getOrNull()
      if (t != null && t.isNotBlank()) t else "{}"
    } else "{}"
    plaintext = plain
    persist(plain)
    legacy?.takeIf { it.exists() }?.delete()
    state = State.UNLOCKED
    return true
  }

  /** 解锁；返回 true 表示密码正确 */
  fun unlock(password: String): Boolean {
    val saltB64 = prefs.getString("salt", null) ?: return false
    val probeIv = prefs.getString("probeIv", null) ?: return false
    val probeCt = prefs.getString("probeCt", null) ?: return false
    val k = try {
      AtlasCrypto.derive(password, AtlasCrypto.unb64(saltB64))
    } catch (e: Exception) { return false }
    val ok = try {
      val p = aesDecrypt(AtlasCrypto.unb64(probeCt), k, AtlasCrypto.unb64(probeIv))
      String(p, Charsets.UTF_8) == PROBE
    } catch (e: Exception) { false }
    if (!ok) return false
    key = k
    // 读取并解密数据
    val f = dataFile
    plaintext = if (f != null && f.exists()) {
      val text = f.readText()
      try { openEnvelope(text, k) } catch (e: Exception) { "{}" }
    } else "{}"
    state = State.UNLOCKED
    return true
  }

  /** 校验当前密码是否正确（用于改密码前确认） */
  fun verify(password: String): Boolean {
    val saltB64 = prefs.getString("salt", null) ?: return false
    val probeIv = prefs.getString("probeIv", null) ?: return false
    val probeCt = prefs.getString("probeCt", null) ?: return false
    return try {
      val k = AtlasCrypto.derive(password, AtlasCrypto.unb64(saltB64))
      String(aesDecrypt(AtlasCrypto.unb64(probeCt), k, AtlasCrypto.unb64(probeIv)), Charsets.UTF_8) == PROBE
    } catch (e: Exception) { false }
  }

  /** 修改主密码：用当前明文以新盐新密钥重新加密 */
  fun changePassword(old: String, new: String): Boolean {
    if (!verify(old) || new.length < 4) return false
    val plain = plaintext ?: return false
    val salt = AtlasCrypto.randomBytes(16)
    val k = AtlasCrypto.derive(new, salt)
    val saltB64 = AtlasCrypto.b64(salt)
    val probeIv = AtlasCrypto.randomBytes(12)
    val probeCt = aesEncrypt(PROBE.toByteArray(Charsets.UTF_8), k, probeIv)
    prefs.edit()
      .putString("salt", saltB64)
      .putString("probeIv", AtlasCrypto.b64(probeIv))
      .putString("probeCt", AtlasCrypto.b64(probeCt))
      .apply()
    key = k
    persist(plain)
    return true
  }

  /** 由 Store 在每次数据变更后更新明文缓存并落盘 */
  fun savePlain(text: String) { plaintext = text; persist(text) }

  /** 加密写入当前明文 */
  fun persist() { plaintext?.let { persist(it) } }

  private fun persist(plain: String) {
    val k = key ?: return
    val f = dataFile ?: return
    val text = sealEnvelope(plain.toByteArray(Charsets.UTF_8), k)
    val tmp = File(dir, "vault.dat.tmp")
    tmp.writeText(text)
    tmp.renameTo(f)
  }

  fun lock() {
    key = null
    plaintext = null
    state = State.LOCKED
  }

  /** 导出加密备份（内容仍为密文，备份密码=主密码） */
  fun exportEncrypted(): String? {
    val k = key ?: return null
    return sealEnvelope(plaintext?.toByteArray(Charsets.UTF_8) ?: ByteArray(0), k, backup = true)
  }

  /** 导入加密备份：先用主密码解开验证，成功后替换当前数据 */
  fun importEncrypted(text: String): Boolean {
    val k = key ?: return false
    val plain = try { openEnvelope(text, k) } catch (e: Exception) { return false }
    if (plain.isBlank()) return false
    plaintext = plain
    persist(plain)
    return true
  }

  // ---- 加密信封工具（与 AtlasCrypto 同格式，复用其 derive/randomBytes） ----
  private fun aesEncrypt(plain: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
    val c = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
    c.init(javax.crypto.Cipher.ENCRYPT_MODE, javax.crypto.spec.SecretKeySpec(key, "AES"), javax.crypto.spec.GCMParameterSpec(128, iv))
    return c.doFinal(plain)
  }

  private fun aesDecrypt(ct: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
    val c = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
    c.init(javax.crypto.Cipher.DECRYPT_MODE, javax.crypto.spec.SecretKeySpec(key, "AES"), javax.crypto.spec.GCMParameterSpec(128, iv))
    return c.doFinal(ct)
  }

  private fun sealEnvelope(plain: ByteArray, key: ByteArray, backup: Boolean = false): String {
    val iv = AtlasCrypto.randomBytes(12)
    val ct = aesEncrypt(plain, key, iv)
    val saltB64 = prefs.getString("salt", null) ?: AtlasCrypto.b64(AtlasCrypto.randomBytes(16))
    return JsonObject(mapOf(
      "app" to JsonPrimitive(if (backup) BACKUP_APP else "accountvault-local"),
      "v" to JsonPrimitive(1),
      "salt" to JsonPrimitive(saltB64),
      "iv" to JsonPrimitive(AtlasCrypto.b64(iv)),
      "ct" to JsonPrimitive(AtlasCrypto.b64(ct)),
    )).toString()
  }

  private fun openEnvelope(text: String, key: ByteArray): String {
    val o = Json.parseToJsonElement(text).jsonObject
    val iv = AtlasCrypto.unb64(o["iv"]?.jsonPrimitive?.content ?: throw AtlasCrypto.BadFormat())
    val ct = AtlasCrypto.unb64(o["ct"]?.jsonPrimitive?.content ?: throw AtlasCrypto.BadFormat())
    return String(aesDecrypt(ct, key, iv), Charsets.UTF_8)
  }
}
