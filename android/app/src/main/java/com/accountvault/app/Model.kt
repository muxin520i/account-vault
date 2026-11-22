package com.accountvault.app

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonUnquotedLiteral
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.roundToLong

// Same JSON shape as the web app and the iOS app, so backups and sync interoperate.
// Pure Kotlin (no Android imports) so it can be unit tested on the JVM.

/** Millisecond timestamps: written as plain integers when whole (like JS / Swift do), never 1.7E12. */
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
object MsSerializer : KSerializer<Double> {
  override val descriptor = PrimitiveSerialDescriptor("atlas.ms", PrimitiveKind.DOUBLE)
  override fun serialize(encoder: Encoder, value: Double) {
    val e = encoder as? JsonEncoder
    if (e == null) { encoder.encodeDouble(value); return }
    if (value == Math.floor(value) && !value.isInfinite() && kotlin.math.abs(value) < 9e15) e.encodeJsonElement(JsonPrimitive(value.toLong()))
    else e.encodeJsonElement(JsonUnquotedLiteral(java.math.BigDecimal.valueOf(value).toPlainString()))
  }
  override fun deserialize(decoder: Decoder): Double {
    val d = decoder as? JsonDecoder ?: return decoder.decodeDouble()
    return d.decodeJsonElement().jsonPrimitive.double
  }
}

@Serializable
data class Identity(
  val id: String,
  val type: String = "email",          // "email" | "phone"
  val value: String = "",
  val label: String = "",
  val color: String = "#0a84ff",
  val notes: String = "",
  @Serializable(with = MsSerializer::class) val updatedAt: Double? = null,
  @Serializable(with = MsSerializer::class) val order: Double? = null,
) {
  val isPhone get() = type == "phone"
}

@Serializable
data class Account(
  val id: String,
  val name: String = "?",
  val domain: String = "",
  val category: String = "其他",
  val identityIds: List<String> = emptyList(),
  val notes: String = "",
  val handle: String? = null,
  @Serializable(with = MsSerializer::class) val createdAt: Double = nowMS(),
  @Serializable(with = MsSerializer::class) val updatedAt: Double? = null,
)

@Serializable
data class AtlasData(
  val identities: List<Identity> = emptyList(),
  val accounts: List<Account> = emptyList(),
  val deleted: Map<String, @Serializable(with = MsSerializer::class) Double> = emptyMap(),
  val demo: Boolean? = null,
)

val JSON = Json {
  ignoreUnknownKeys = true
  coerceInputValues = true
  explicitNulls = false
  encodeDefaults = true
  isLenient = true
}

fun decodeAtlas(text: String): AtlasData {
  val d = JSON.decodeFromString(AtlasData.serializer(), text)
  return d.copy(accounts = d.accounts.map { if (it.handle != null && it.handle.isEmpty()) it.copy(handle = null) else it })
}
fun encodeAtlas(d: AtlasData): String = JSON.encodeToString(AtlasData.serializer(), d)

fun newID(): String {
  val a = "abcdefghijklmnopqrstuvwxyz0123456789"
  val r = (0 until 8).map { a.random() }.joinToString("")
  val t = System.currentTimeMillis().toString(36)
  return r + t.takeLast(4)
}
fun nowMS(): Double = System.currentTimeMillis().toDouble()

val CATS = listOf("社交", "购物", "支付金融", "开发", "AI", "娱乐", "游戏", "出行", "工具", "加密", "其他")

data class Preset(val name: String, val domain: String, val category: String, val color: String)

fun cleanDomain(d: String): String {
  var s = d.trim().lowercase()
  for (p in listOf("https://", "http://")) if (s.startsWith(p)) s = s.removePrefix(p)
  if (s.startsWith("www.")) s = s.removePrefix("www.")
  return s.split('/', '?', '#').firstOrNull { it.isNotEmpty() } ?: ""
}

fun presetFor(name: String, domain: String): Preset? {
  val d = cleanDomain(domain); val n = name.lowercase()
  return Presets.all.firstOrNull { p -> (d.isNotEmpty() && (d == p.domain || d.endsWith("." + p.domain))) || p.name.lowercase() == n }
}

fun carrier(v: String): String {
  var d = v.filter { it.isDigit() }
  if (d.startsWith("0086")) d = d.drop(4) else if (d.startsWith("86") && d.length == 13) d = d.drop(2)
  if (d.length != 11 || !d.startsWith("1")) {
    val t = v.trim()
    if (t.startsWith("+")) { val cc = t.drop(1).takeWhile { it.isDigit() }.take(3); if (cc.isNotEmpty() && cc != "86") return "+$cc" }
    return ""
  }
  val p3 = d.take(3); val p4 = d.take(4)
  val mobile = setOf("134","135","136","137","138","139","147","148","150","151","152","157","158","159","172","178","182","183","184","187","188","195","197","198")
  val unicom = setOf("130","131","132","145","146","155","156","166","167","171","175","176","185","186","196")
  val telecom = setOf("133","149","153","173","174","177","180","181","189","190","191","193","199")
  if (p4 == "1349") return "中国电信"
  if (p3 in mobile) return "中国移动"
  if (p3 in unicom) return "中国联通"
  if (p3 in telecom) return "中国电信"
  if (p3 == "192") return "中国广电"
  return ""
}

/** Natural, locale-aware-ish name order (like localizedStandardCompare). */
val nameOrder: Comparator<String> = run {
  val c = java.text.Collator.getInstance(java.util.Locale.CHINA)
  Comparator { a, b -> c.compare(a, b) }
}

// MARK: merge (per item, newest wins, tombstones) — mirrors Sync.swift / app.js
object Merge {
  fun merge(a: AtlasData, b: AtlasData, now: Double = nowMS()): AtlasData {
    val del = HashMap(a.deleted)
    for ((id, t) in b.deleted) del[id] = maxOf(del[id] ?: 0.0, t)
    fun <T> pick(xs: List<T>, ys: List<T>, id: (T) -> String, up: (T) -> Double): List<T> {
      val order = ArrayList<String>(); val map = HashMap<String, T>()
      for (x in xs + ys) {
        val c = map[id(x)]
        if (c != null) { if (up(x) > up(c)) map[id(x)] = x } else { map[id(x)] = x; order.add(id(x)) }
      }
      return order.mapNotNull { map[it] }.filter { !((del[id(it)] ?: -1.0) >= up(it)) }
    }
    var ids = pick(a.identities, b.identities, { it.id }) { it.updatedAt ?: 0.0 }
    var accs = pick(a.accounts, b.accounts, { it.id }) { it.updatedAt ?: 0.0 }
    val live = ids.map { it.id }.toSet() + accs.map { it.id }.toSet()
    for (k in del.keys.toList()) if (k in live) del.remove(k)
    ids = ids.withIndex().sortedWith(compareBy({ it.value.order ?: 1e9 }, { it.index.toDouble() })).map { it.value }
    val idSet = ids.map { it.id }.toSet()
    accs = accs.map { it.copy(identityIds = it.identityIds.filter { x -> x in idSet }) }
    val old = now - 90 * 86_400_000.0
    return AtlasData(identities = ids, accounts = accs, deleted = del.filterValues { it >= old })
  }
  /** Signature used to decide whether anything changed (order-sensitive like the iOS one). */
  fun sig(d: AtlasData): String = encodeAtlas(AtlasData(d.identities, d.accounts, d.deleted.toSortedMap()))
}
