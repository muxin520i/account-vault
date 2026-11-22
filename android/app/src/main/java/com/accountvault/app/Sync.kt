package com.accountvault.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import javax.crypto.AEADBadTagException

// Same encrypted file as the web app and iOS: PBKDF2-SHA256 (310k) -> AES-GCM, stored in a private gist (Gitee or GitHub).
@Serializable
data class SyncConf(
  val provider: String? = null,
  val token: String,
  val salt: String,
  val key: String,
  val gistId: String? = null,
  val at: Double? = null,
)

class SyncError(msg: String) : Exception(msg)

object Sync {
  const val FILE = "atlas.enc.json"
  const val DESC = "账号仓库 · 加密数据（只有同步密码能解开）"
  var state by mutableStateOf("off")     // off | idle | syncing | error
  var err by mutableStateOf("")
  var at by mutableDoubleStateOf(0.0)
  private var conf: SyncConf? = null
  private var key: ByteArray? = null
  private var busy = false
  private var again = false
  private var timer: Job? = null
  private val http = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build()

  val on get() = conf != null
  val provider get() = conf?.provider ?: "github"

  fun init() {
    val s = Store.prefs().getString("atlas.sync", null) ?: return
    val c = runCatching { JSON.decodeFromString(SyncConf.serializer(), s) }.getOrNull() ?: return
    conf = c; at = c.at ?: 0.0; state = "idle"
    key = runCatching { AtlasCrypto.unb64(c.key) }.getOrNull()
    if (key == null) { state = "error"; err = "本机的同步信息损坏，请重新开启" }
  }
  private fun persistConf() {
    val c = conf
    if (c != null) Store.prefs().edit().putString("atlas.sync", JSON.encodeToString(SyncConf.serializer(), c)).apply()
    else Store.prefs().edit().remove("atlas.sync").apply()
  }

  // MARK: api (blocking, call on IO)
  private fun api(path: String, method: String = "GET", body: JsonObject? = null, token: String, prov: String): JsonElement {
    val gitee = prov == "gitee"
    var url = (if (gitee) "https://gitee.com/api/v5" else "https://api.github.com") + path
    if (gitee && method == "GET") url += (if ("?" in url) "&" else "?") + "access_token=" + URLEncoder.encode(token, "UTF-8")
    val rb = Request.Builder().url(url).header("Cache-Control", "no-cache")
    val json = "application/json".toMediaType()
    if (gitee) {
      if (method != "GET") {
        val b = JsonObject((body ?: JsonObject(emptyMap())) + ("access_token" to JsonPrimitive(token)))
        rb.method(method, b.toString().toRequestBody(json))
      }
    } else {
      rb.header("Authorization", "Bearer $token").header("Accept", "application/vnd.github+json")
      if (body != null) rb.method(method, body.toString().toRequestBody(json))
    }
    val name = if (gitee) "Gitee" else "GitHub"
    val resp = try { http.newCall(rb.build()).execute() } catch (e: Exception) {
      throw SyncError("连不上 $name" + if (gitee) "" else "，国内网络建议改用 Gitee")
    }
    resp.use {
      val code = it.code
      if (code == 401) throw SyncError("令牌无效或已过期")
      if (code == 403 || code == 404) throw SyncError(if (gitee) "令牌没有 gists 权限" else "令牌没有 gist 权限")
      if (code !in 200..299) throw SyncError("$name 返回 $code")
      val t = it.body?.string() ?: ""
      return if (t.isBlank()) JsonNull else JSON.parseToJsonElement(t)
    }
  }
  private fun idOf(e: JsonElement?): String? = (e as? JsonPrimitive)?.contentOrNull
  private fun readGist(id: String, token: String, prov: String): String? {
    val g = api("/gists/$id", token = token, prov = prov) as? JsonObject ?: return null
    val f = (g["files"] as? JsonObject)?.get(FILE) as? JsonObject ?: return null
    val content = (f["content"] as? JsonPrimitive)?.contentOrNull
    val truncated = (f["truncated"] as? JsonPrimitive)?.booleanOrNull == true
    if (content != null && !truncated) return content
    val raw = (f["raw_url"] as? JsonPrimitive)?.contentOrNull ?: return null
    return http.newCall(Request.Builder().url(raw).build()).execute().use { it.body?.string() }
  }

  // MARK: run
  fun schedule() {
    if (conf == null) return
    timer?.cancel()
    timer = Store.scope.launch { delay(1200); run() }
  }

  fun runNow() { Store.scope.launch { run() } }

  suspend fun run() {
    val c = conf ?: return; val k = key ?: return; val gid = c.gistId ?: return
    if (busy) { again = true; return }
    busy = true; state = "syncing"
    try {
      val prov = c.provider ?: "github"
      val text = withContext(Dispatchers.IO) { readGist(gid, c.token, prov) }
      val remote = withContext(Dispatchers.Default) { text?.let { AtlasCrypto.openData(it, k) } ?: AtlasData() }
      val local = Store.data
      val merged = Merge.merge(local, remote)
      if (Merge.sig(merged) != Merge.sig(local)) Store.adopt(merged)
      if (text == null || Merge.sig(merged) != Merge.sig(remote)) {
        val content = AtlasCrypto.sealData(merged, k, c.salt)
        withContext(Dispatchers.IO) { api("/gists/$gid", "PATCH", filesBody(content), c.token, prov) }
      }
      state = "idle"; err = ""; at = nowMS(); conf = conf?.copy(at = at); persistConf()
    } catch (e: Exception) {
      state = "error"
      err = when (e) { is SyncError -> e.message ?: "同步失败"; is AEADBadTagException -> "同步密码不对"; is AtlasCrypto.BadFormat -> e.message!!; else -> "同步失败" }
    }
    busy = false
    if (again) { again = false; run() }
  }

  private fun filesBody(content: String, public: Boolean? = null) = buildJsonObject {
    put("description", DESC)
    if (public != null) put("public", public)
    put("files", buildJsonObject { put(FILE, buildJsonObject { put("content", content) }) })
  }

  /** returns true when existing data was found in the cloud */
  suspend fun enable(raw: String, pass: String, prov: String): Boolean {
    val token = raw.trim()
    val list = withContext(Dispatchers.IO) { api("/gists?per_page=100", token = token, prov = prov) } as? JsonArray ?: JsonArray(emptyList())
    val g = list.firstOrNull { ((it as? JsonObject)?.get("files") as? JsonObject)?.containsKey(FILE) == true } as? JsonObject
    val salt: String; val k: ByteArray; var gid: String? = null; var found = false
    if (g != null && idOf(g["id"]) != null) {
      gid = idOf(g["id"])
      val text = withContext(Dispatchers.IO) { readGist(gid!!, token, prov) } ?: throw SyncError("云端的同步文件损坏")
      val s = AtlasCrypto.saltOf(text) ?: throw SyncError("云端的同步文件损坏")
      val sd = runCatching { AtlasCrypto.unb64(s) }.getOrNull() ?: throw SyncError("云端的同步文件损坏")
      salt = s
      k = withContext(Dispatchers.Default) { AtlasCrypto.derive(pass, sd) }
      try { AtlasCrypto.open(text, k) } catch (e: Exception) { throw SyncError("同步密码不对（和第一次设置的不一样）") }
      found = true
    } else {
      val b = AtlasCrypto.randomBytes(16)
      salt = AtlasCrypto.b64(b)
      k = withContext(Dispatchers.Default) { AtlasCrypto.derive(pass, b) }
    }
    if (Store.data.demo == true) Store.adopt(AtlasData())
    if (gid == null) {
      val content = AtlasCrypto.sealData(Store.data, k, salt)
      val made = withContext(Dispatchers.IO) { api("/gists", "POST", filesBody(content, public = false), token, prov) } as? JsonObject
      gid = idOf(made?.get("id"))
    }
    conf = SyncConf(prov, token, salt, AtlasCrypto.b64(k), gid, 0.0)
    key = k; persistConf(); state = "idle"
    run()
    return found
  }

  fun disable() { conf = null; key = null; state = "off"; err = ""; persistConf() }
}
