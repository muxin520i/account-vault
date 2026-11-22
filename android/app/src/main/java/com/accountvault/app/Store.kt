package com.accountvault.app

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.time.Instant
import kotlin.random.Random

sealed class Route {
  data class IdentityR(val id: String) : Route()
  data class AccountR(val id: String) : Route()
  data class NewIdentity(val type: String) : Route()
  data class EditIdentity(val id: String) : Route()
  data class EditAccount(val id: String) : Route()
  data class Picker(val preset: String?) : Route()
  data object Settings : Route()
}

data class Entry(val key: Long, val route: Route)

data class Toast(val text: String, val action: String? = null, val id: Long = System.nanoTime())

/** App state. One instance, Compose reads the mutableState fields directly. */
object Store {
  private lateinit var prefs: SharedPreferences
  private lateinit var file: File
  val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

  var data by mutableStateOf(AtlasData())
  var privacy by mutableStateOf(false)
  var kind by mutableStateOf("email")
  var theme by mutableStateOf("dark")
  var activeID by mutableStateOf<String?>(null)
  /** sheet stack: the last one is on top (iOS presents a sheet over a sheet) */
  var routes by mutableStateOf(listOf<Entry>())
  private var nextKey = 0L
  var toast by mutableStateOf<Toast?>(null)
  var demoFocus by mutableStateOf<String?>(null)
  var toastAction: (() -> Unit)? = null
  var onSaved: (() -> Unit)? = null
  var onLocked: (() -> Unit)? = null
  var ready = false; private set

  private var seen = HashMap<String, Any>()
  private var undoSnap: AtlasData? = null
  private var toastJob: Job? = null

  fun init(ctx: Context) {
    if (ready) return
    prefs = ctx.getSharedPreferences("atlas", Context.MODE_PRIVATE)
    file = File(ctx.filesDir, "atlas.json")
    Vault.init(ctx)
    privacy = prefs.getBoolean("atlas.privacy", false)
    kind = prefs.getString("atlas.kind", "email") ?: "email"
    theme = prefs.getString("atlas.theme", "dark") ?: "dark"
  }

  /** 解锁成功后从保险层加载明文数据；首次设置密码时载入示例。 */
  fun loadFromVault(firstSetup: Boolean = false) {
    val plain = Vault.plaintext
    data = if (plain != null) runCatching { decodeAtlas(plain) }.getOrElse { demo() }
           else if (firstSetup) demo()
           else AtlasData()
    remember()
    activeID = deck.firstOrNull()?.id
    ready = true
  }

  /** 锁定：清空内存中的数据，回到未解锁状态 */
  fun clearOnLock() {
    data = AtlasData()
    remember()
    activeID = null
    routes = emptyList()
    ready = false
  }
  fun prefs() = prefs

  fun putPrivacy(v: Boolean) { privacy = v; prefs.edit().putBoolean("atlas.privacy", v).apply() }
  fun putTheme(v: String) { theme = v; prefs.edit().putString("atlas.theme", v).apply() }
  private fun setKindPref(v: String) { kind = v; prefs.edit().putString("atlas.kind", v).apply() }

  fun push(r: Route) { routes = routes + Entry(nextKey++, r) }
  fun pop() { routes = routes.dropLast(1) }
  fun popAll() { routes = emptyList() }

  val deck: List<Identity> get() = data.identities.filter { it.type == kind }
  val active: Identity? get() = deck.firstOrNull { it.id == activeID } ?: if (activeID == "add") null else deck.firstOrNull()
  fun identity(id: String) = data.identities.firstOrNull { it.id == id }
  fun account(id: String) = data.accounts.firstOrNull { it.id == id }
  fun accountsOf(id: String) = data.accounts.filter { id in it.identityIds }.sortedWith(compareBy(nameOrder) { it.name })

  fun display(i: Identity): String {
    if (!privacy) return i.value
    if (i.type == "email") {
      val parts = i.value.split("@", limit = 2)
      return parts[0].take(2) + "•••" + (if (parts.size > 1) "@" + parts[1] else "")
    }
    val digits = i.value.filter { it.isDigit() }
    return if (digits.length > 7) "••• •••• " + digits.takeLast(4) else "••••"
  }
  fun handleText(h: String) = if (privacy) h.take(1) + "•••" else h
  fun title(i: Identity) = i.label.ifEmpty { display(i) }

  // every change goes through here: snapshot for undo, stamp, persist, sync
  fun mutate(message: String? = null, undo: Boolean = true, f: (AtlasData) -> AtlasData) {
    undoSnap = data
    data = f(data).copy(demo = null)
    save()
    if (message != null) show(message, if (undo) "撤销" else null) { undo() }
  }
  fun undo() { undoSnap?.let { data = it; save(); fixActive() } }

  private fun remember() {
    seen = HashMap()
    for (x in data.identities) seen[x.id] = x.copy(updatedAt = null)
    for (x in data.accounts) seen[x.id] = x.copy(updatedAt = null)
  }
  fun save() {
    val now = nowMS()
    val del = HashMap(data.deleted)
    val live = HashSet<String>()
    val ids = data.identities.map { x -> live += x.id; del.remove(x.id); if (seen[x.id] != x.copy(updatedAt = null) || x.updatedAt == null) x.copy(updatedAt = now) else x }
    val accs = data.accounts.map { x -> live += x.id; del.remove(x.id); if (seen[x.id] != x.copy(updatedAt = null) || x.updatedAt == null) x.copy(updatedAt = now) else x }
    for (id in seen.keys) if (id !in live) del[id] = now
    data = data.copy(identities = ids, accounts = accs, deleted = del)
    remember(); persist(); onSaved?.invoke()
  }
  fun persist() {
    if (Vault.state != Vault.State.UNLOCKED) return
    Vault.savePlain(encodeAtlas(data))
  }
  /** replace everything (sync merge), without new stamps */
  fun adopt(d: AtlasData) { data = d; remember(); persist(); fixActive() }
  private fun fixActive() { if (active == null || deck.none { it.id == activeID }) activeID = deck.firstOrNull()?.id ?: "add" }

  fun show(text: String, action: String? = null, fn: (() -> Unit)? = null) {
    toastAction = fn
    val t = Toast(text, action)
    toast = t
    toastJob?.cancel()
    toastJob = scope.launch { delay(if (action == null) 2200 else 4500); if (toast?.id == t.id) toast = null }
  }

  fun setKind(k: String, focus: String? = null) {
    setKindPref(k)
    activeID = focus ?: deck.firstOrNull()?.id ?: "add"
  }

  // apps the user typed in by hand come back in the picker
  private fun savedCustom(): List<List<String>> = runCatching {
    JSON.decodeFromString<List<List<String>>>(prefs.getString("atlas.custom", "[]") ?: "[]")
  }.getOrDefault(emptyList())
  val customApps: List<Preset> get() {
    val out = ArrayList<Preset>(); val seenN = HashSet<String>()
    val all = savedCustom() + data.accounts.reversed().map { listOf(it.name, it.domain, it.category) }
    for (t in all) {
      if (t.isEmpty()) continue
      val k = t[0].lowercase()
      if (k.isEmpty() || k in seenN || Presets.all.any { it.name.lowercase() == k }) continue
      seenN += k
      out += Preset(t[0], t.getOrElse(1) { "" }, t.getOrElse(2) { "其他" }, "")
    }
    return out
  }
  fun rememberApp(p: Preset) {
    if (Presets.all.any { it.name.lowercase() == p.name.lowercase() }) return
    val saved = savedCustom().filter { it.firstOrNull()?.lowercase() != p.name.lowercase() }.toMutableList()
    saved.add(0, listOf(p.name, p.domain, p.category))
    prefs.edit().putString("atlas.custom", JSON.encodeToString(saved.take(80))).apply()
  }

  fun backupJSON(): String {
    val o = kotlinx.serialization.json.buildJsonObject {
      put("app", kotlinx.serialization.json.JsonPrimitive("atlas"))
      put("version", kotlinx.serialization.json.JsonPrimitive(1))
      put("exportedAt", kotlinx.serialization.json.JsonPrimitive(Instant.now().toString()))
      put("identities", JSON.encodeToJsonElement(kotlinx.serialization.builtins.ListSerializer(Identity.serializer()), data.identities))
      put("accounts", JSON.encodeToJsonElement(kotlinx.serialization.builtins.ListSerializer(Account.serializer()), data.accounts))
    }
    return kotlinx.serialization.json.Json { prettyPrint = true }.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), o)
  }
  fun importBackup(text: String): Boolean {
    val raw = runCatching { JSON.parseToJsonElement(text) as? kotlinx.serialization.json.JsonObject }.getOrNull() ?: return false
    if (raw["identities"] !is kotlinx.serialization.json.JsonArray || raw["accounts"] !is kotlinx.serialization.json.JsonArray) return false
    val b = runCatching { decodeAtlas(text) }.getOrNull() ?: return false
    mutate("已导入 ${b.identities.size} 个身份和 ${b.accounts.size} 个网站") { it.copy(identities = b.identities, accounts = b.accounts) }
    setKind(kind)
    return true
  }

  fun loadDemo() {
    mutate("已载入示例") { demo() }
    data = data.copy(demo = true); persist(); setKind("email")
  }

  fun demo(): AtlasData {
    val ids = listOf(
      Identity(newID(), "email", "me@gmail.com", "主力邮箱", "#0a84ff"),
      Identity(newID(), "phone", "+86 138 0000 1234", "主力手机号", "#30d158", "移动，已实名"),
      Identity(newID(), "email", "shopping.fun@qq.com", "购物小号", "#ff375f"),
      Identity(newID(), "email", "dev@outlook.com", "开发专用", "#bf5af2"),
      Identity(newID(), "phone", "+86 170 0000 5678", "副卡", "#ff9f0a"),
    )
    val g = ids[0].id; val p1 = ids[1].id; val q = ids[2].id; val o = ids[3].id; val p2 = ids[4].id
    val day = 86_400_000.0; val now = nowMS()
    fun mk(n: String, d: String, c: String, i: List<String>, ago: Double, notes: String = "") =
      Account(newID(), n, d, c, i, notes, null, (now - ago * day - Random.nextDouble(0.0, 3e6)).let { Math.round(it).toDouble() })
    return AtlasData(ids, listOf(
      mk("GitHub", "github.com", "开发", listOf(o, p1), 0.0, "已开两步验证"), mk("Claude", "claude.ai", "AI", listOf(g), 0.0),
      mk("Cloudflare", "cloudflare.com", "开发", listOf(o), 1.0), mk("ChatGPT", "chatgpt.com", "AI", listOf(g), 1.0),
      mk("小红书", "xiaohongshu.com", "社交", listOf(p2), 3.0), mk("Binance", "binance.com", "加密", listOf(o, p1), 3.0, "已完成 KYC"),
      mk("淘宝", "taobao.com", "购物", listOf(q, p1), 9.0), mk("京东", "jd.com", "购物", listOf(q, p2), 12.0),
      mk("微信", "weixin.qq.com", "社交", listOf(p1), 30.0), mk("支付宝", "alipay.com", "支付金融", listOf(p1), 30.0),
      mk("哔哩哔哩", "bilibili.com", "娱乐", listOf(p2), 40.0), mk("Steam", "steampowered.com", "游戏", listOf(q), 60.0, "令牌在主力手机上"),
      mk("Apple ID", "apple.com", "工具", listOf(g, p1), 90.0), mk("Notion", "notion.so", "工具", listOf(g), 120.0), mk("网易云音乐", "music.163.com", "娱乐", listOf(p2), 150.0),
    ), demo = true)
  }
}
