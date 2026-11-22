package com.accountvault.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * web-blob.enc.json was produced exactly like app.js / Sync.swift do it (Python `cryptography`:
 * PBKDF2-SHA256 310k over the UTF-8 password, AES-256-GCM, 12-byte iv, standard base64,
 * ct = ciphertext || tag). The Android code must open it and produce files they can open.
 */
class InteropTest {
  private val pass = "同步密码-Test 123"
  private fun res(n: String) = File(javaClass.classLoader!!.getResource(n)!!.toURI()).readText().trim()

  @Test fun derivesSameKeyAsWebAndIos() {
    val blob = res("web-blob.enc.json")
    val salt = AtlasCrypto.unb64(AtlasCrypto.saltOf(blob)!!)
    val key = AtlasCrypto.derive(pass, salt)
    assertEquals(res("web-blob.key.b64"), AtlasCrypto.b64(key))
  }

  @Test fun opensWebBlob() {
    val blob = res("web-blob.enc.json")
    val key = AtlasCrypto.unb64(res("web-blob.key.b64"))
    val d = AtlasCrypto.openData(blob, key)
    assertEquals(2, d.identities.size)
    assertEquals("主力邮箱", d.identities[0].label)
    assertEquals(0.0, d.identities[0].order!!, 0.0)
    assertNull(d.identities[1].order)
    assertEquals("octo", d.accounts[0].handle)
    assertNull("empty handle becomes null", d.accounts[1].handle)
    assertEquals(1763500000000.5, d.accounts[0].createdAt, 0.0)
    assertEquals(1763600000004.0, d.deleted["gone0001"]!!, 0.0)
  }

  @Test fun wrongPasswordFails() {
    val blob = res("web-blob.enc.json")
    val key = AtlasCrypto.derive("wrong", AtlasCrypto.unb64(AtlasCrypto.saltOf(blob)!!))
    try { AtlasCrypto.open(blob, key); fail("should not open") } catch (e: javax.crypto.AEADBadTagException) { }
  }

  @Test fun roundTripKeepsShape() {
    val key = AtlasCrypto.unb64(res("web-blob.key.b64"))
    val d = AtlasCrypto.openData(res("web-blob.enc.json"), key)
    val sealed = AtlasCrypto.sealData(d, key, "XSWGpiiSmpMPkLClFjq0sA==")
    val back = AtlasCrypto.openData(sealed, key)
    assertEquals(Merge.sig(d), Merge.sig(back))
    val plain = String(AtlasCrypto.open(sealed, key))
    assertTrue("integers stay integers: $plain", plain.contains("\"updatedAt\":1763600000000,"))
    assertTrue(!plain.contains("E12"))
    assertTrue("nulls are omitted", !plain.contains("null"))
    System.getenv("ATLAS_INTEROP_OUT")?.let { File(it).writeText(sealed) }
  }

  @Test fun mergeNewestWinsAndTombstones() {
    val a = AtlasData(identities = listOf(Identity("x", value = "a", updatedAt = 10.0), Identity("y", value = "y", updatedAt = 5.0)))
    val b = AtlasData(identities = listOf(Identity("x", value = "b", updatedAt = 20.0)), deleted = mapOf("y" to 6.0))
    val m = Merge.merge(a, b, now = 100.0)
    assertEquals(listOf("x"), m.identities.map { it.id })
    assertEquals("b", m.identities[0].value)
    assertEquals(6.0, m.deleted["y"]!!, 0.0)
  }
}
