package com.accountvault.app

import android.content.Context
import android.graphics.BitmapFactory
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

// MARK: - colour & theme

fun hex(s: String): Color {
  var h = s.trim().removePrefix("#")
  if (h.length == 3) h = h.map { "$it$it" }.joinToString("")
  val v = h.toLongOrNull(16) ?: 0x3a3a3c
  return Color(0xff000000 or v)
}

data class Palette(
  val dark: Boolean,
  val bg: Color, val glass: Color, val glassStrong: Color, val stroke: Color,
  val primary: Color, val secondary: Color, val tertiary: Color,
  val accent: Color, val red: Color, val field: Color,
)
val DarkPal = Palette(true, Color.Black, Color(0x1FFFFFFF), Color(0xFF1C1C1E), Color(0x1AFFFFFF),
  Color.White, Color(0x99EBEBF5), Color(0x4DEBEBF5), hex("#0a84ff"), hex("#ff453a"), Color(0xFF1C1C1E))
val LightPal = Palette(false, hex("#f2f2f7"), Color(0xB3FFFFFF), Color.White, Color(0x14000000),
  Color.Black, Color(0x993C3C43), Color(0x4D3C3C43), hex("#007aff"), hex("#ff3b30"), Color.White)
val LocalPal = staticCompositionLocalOf { DarkPal }
val pal: Palette @Composable get() = LocalPal.current

/** Apple-like motion: critically damped, no overshoot. */
fun <T> calm(stiffness: Float = Spring.StiffnessMediumLow) = spring<T>(dampingRatio = 1f, stiffness = stiffness)

data class Skin(val name: String, val stops: List<Pair<String, Float>>)
val SKINS: List<Pair<String, Skin>> = listOf(
  "#0a84ff" to Skin("深海", listOf("#56c6ff" to 0f, "#0a84ff" to 0.42f, "#3b2fd0" to 1f)),
  "#30d158" to Skin("青柠", listOf("#d2f56a" to 0f, "#30d158" to 0.48f, "#0b7f6a" to 1f)),
  "#ff9f0a" to Skin("落日", listOf("#ffdf5c" to 0f, "#ff9f0a" to 0.42f, "#ff3d5a" to 1f)),
  "#ff375f" to Skin("莓果", listOf("#ff9bb8" to 0f, "#ff375f" to 0.46f, "#9e1470" to 1f)),
  "#bf5af2" to Skin("极光", listOf("#ff86dc" to 0f, "#bf5af2" to 0.44f, "#4636ee" to 1f)),
  "#64d2ff" to Skin("冰川", listOf("#9df0ff" to 0f, "#4cc3fb" to 0.40f, "#2a6cf0" to 1f)),
  "#ff453a" to Skin("珊瑚", listOf("#ffa36b" to 0f, "#ff453a" to 0.46f, "#c1185f" to 1f)),
  "#5e5ce6" to Skin("靛蓝", listOf("#9a98ff" to 0f, "#5e5ce6" to 0.46f, "#1f1b78" to 1f)),
  "#00c7be" to Skin("海湾", listOf("#8af5d6" to 0f, "#00c7be" to 0.44f, "#0a62b0" to 1f)),
  "#3a3a3c" to Skin("曜石", listOf("#76767c" to 0f, "#3a3a3c" to 0.46f, "#0d0d10" to 1f)),
)
val COLORS = SKINS.map { it.first }
/** Default skins of 微信 / QQ / Apple cards (not in the general swatch list). */
val BRAND_SKINS: List<Pair<String, Skin>> = listOf(
  "#07c160" to Skin("微信绿", listOf("#6fe39b" to 0f, "#07c160" to 0.46f, "#03733a" to 1f)),
  "#12b7f5" to Skin("QQ 蓝", listOf("#8fe3ff" to 0f, "#12b7f5" to 0.46f, "#0a6fb8" to 1f)),
  "#1d1d1f" to Skin("石墨", listOf("#b9bcc2" to 0f, "#4a4b50" to 0.42f, "#121214" to 1f)),
)

/** 145deg CSS gradient over a box of the given size. */
fun skinBrush(c: String, size: Size): Brush {
  val s = SKINS.firstOrNull { it.first == c }?.second ?: BRAND_SKINS.firstOrNull { it.first == c }?.second
  val stops = s?.stops?.map { it.second to hex(it.first) }?.toTypedArray()
    ?: hex(c).let { k -> arrayOf(0f to k.copy(alpha = 0.7f), 0.5f to k, 1f to k) }
  return Brush.linearGradient(*stops, start = Offset(size.width * 0.21f, 0f), end = Offset(size.width * 0.79f, size.height))
}
fun Modifier.skin(c: String, shape: Shape) = this.clip(shape).drawBehindCompat { drawRect(skinBrush(c, size)) }

fun Modifier.drawBehindCompat(f: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit) = this.drawBehind(f)

fun brandColor(name: String, domain: String): Color {
  presetFor(name, domain)?.let { return hex(it.color) }
  var h = 0
  val key = cleanDomain(domain).ifEmpty { name.lowercase() }
  for (u in key.codePoints()) h = h * 31 + u
  val hue = (h.toLong() and 0xffffffffL) % 360
  return Color.hsv(hue.toFloat(), 0.62f, 0.74f)
}

// MARK: - shapes

/** Phone numbers get a SIM-card silhouette: top-right corner cut off. */
class CardShape(private val sim: Boolean) : Shape {
  override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
    val w = size.width; val h = size.height
    val r = w * 0.065f
    if (!sim) return Outline.Rounded(androidx.compose.ui.geometry.RoundRect(0f, 0f, w, h, androidx.compose.ui.geometry.CornerRadius(r, r)))
    val cut = w * 0.085f
    val p = Path().apply {
      moveTo(r, 0f); lineTo(w - cut, 0f); lineTo(w, cut); lineTo(w, h - r)
      quadraticTo(w, h, w - r, h); lineTo(r, h); quadraticTo(0f, h, 0f, h - r); lineTo(0f, r); quadraticTo(0f, 0f, r, 0f); close()
    }
    return Outline.Generic(p)
  }
}
fun squircle(size: Dp) = RoundedCornerShape(size * 0.235f)

// MARK: - interaction

/** Press feedback that feels like a card being pushed. */
@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
fun Modifier.press(scale: Float = 0.96f, haptic: Boolean = false, enabled: Boolean = true, onLongClick: (() -> Unit)? = null, onClick: () -> Unit): Modifier {
  val src = remember { MutableInteractionSource() }
  val pressed by src.collectIsPressedAsState()
  val s by animateFloatAsState(if (pressed) scale else 1f, calm(Spring.StiffnessMedium), label = "press")
  val view = LocalView.current
  val m = this.graphicsLayer { scaleX = s; scaleY = s }
  return if (onLongClick == null) m.clickable(src, null, enabled = enabled) { if (haptic) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY); onClick() }
  else m.combinedClickable(src, null, enabled = enabled, onLongClick = onLongClick) {
    if (haptic) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY); onClick()
  }
}

@Composable fun haptics(): (Int) -> Unit { val v = LocalView.current; return { v.performHapticFeedback(it) } }
const val TICK = HapticFeedbackConstants.CLOCK_TICK
val CONFIRM = if (android.os.Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY

@Composable
fun Modifier.glass(shape: Shape, strong: Boolean = false) =
  this.clip(shape).background(if (strong) pal.glassStrong else pal.glass).border(0.5.dp, pal.stroke, shape)

@Composable
fun T(text: String, size: Float, weight: FontWeight = FontWeight.Normal, color: Color = pal.primary, modifier: Modifier = Modifier, lines: Int = Int.MAX_VALUE, align: androidx.compose.ui.text.style.TextAlign? = null) {
  Text(text, modifier, color = color, fontSize = size.sp, fontWeight = weight, maxLines = lines, overflow = TextOverflow.Ellipsis,
    textAlign = align, style = TextStyle(lineHeight = (size * 1.25f).sp))
}

// MARK: - app icon

object IconCache {
  /** Decoded preset icons. Filled off the main thread (prewarm at launch, or on first use) so a sheet
   *  full of icons never decodes PNGs inside the frame that opens it. */
  val bitmaps = java.util.concurrent.ConcurrentHashMap<String, ImageBitmap>()
  val remote = mutableStateMapOf<String, ImageBitmap?>()
  private val http = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS).build()
  fun preset(name: String, domain: String): Preset? {
    val d = cleanDomain(domain)
    return presetMatch(name, d, byName = d.isEmpty())
  }
  private fun decode(ctx: Context, p: Preset): ImageBitmap? = bitmaps[p.domain] ?: runCatching {
    ctx.assets.open("icons/${p.domain}.png").use { BitmapFactory.decodeStream(it) }?.asImageBitmap()?.also { it.prepareToDraw(); bitmaps[p.domain] = it }
  }.getOrNull()
  suspend fun load(ctx: Context, p: Preset): ImageBitmap? = bitmaps[p.domain] ?: withContext(Dispatchers.IO) { decode(ctx, p) }
  /** Decode every preset icon in the background right after launch. */
  fun prewarm(ctx: Context) {
    val app = ctx.applicationContext
    kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch { Presets.all.forEach { decode(app, it) } }
  }
  suspend fun fetch(domain: String) {
    if (remote.containsKey(domain)) return
    remote[domain] = null
    val bmp = withContext(Dispatchers.IO) {
      runCatching {
        http.newCall(Request.Builder().url("https://favicon.im/$domain?larger=true").build()).execute().use { r ->
          if (!r.isSuccessful) null else r.body?.bytes()?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
        }
      }.getOrNull()
    }
    remote[domain] = bmp
  }
}

/** Account icon: its own custom / App Store icon first, then the preset, favicon, letter. */
@Composable
fun AppIcon(a: Account, size: Dp = 52.dp, modifier: Modifier = Modifier) = AppIcon(a.name, a.domain, size, modifier, a.icon, a.id)

@Composable
fun AppIcon(name: String, domain: String, size: Dp = 52.dp, modifier: Modifier = Modifier, icon: String? = null, iconKey: String = "") {
  val shape = squircle(size)
  val ck = remember(iconKey, icon) { icon?.takeIf { it.startsWith("data:image") }?.let { CustomIcon.key(iconKey, it) } }
  var custom by remember(ck) { androidx.compose.runtime.mutableStateOf(ck?.let { CustomIcon.cached(it) }) }
  if (ck != null && custom == null) androidx.compose.runtime.LaunchedEffect(ck) { custom = CustomIcon.decode(ck, icon!!) }
  val c = custom
  if (c != null) {
    Box(modifier.size(size).clip(shape).border(0.5.dp, Color.White.copy(alpha = 0.12f), shape)) {
      Image(c, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
  } else PresetIcon(name, domain, size, modifier)
}

@Composable
private fun PresetIcon(name: String, domain: String, size: Dp, modifier: Modifier) {
  val ctx = LocalContext.current
  val shape = squircle(size)
  val preset = remember(name, domain) { IconCache.preset(name, domain) }
  val local by androidx.compose.runtime.produceState(preset?.let { IconCache.bitmaps[it.domain] }, preset) {
    if (value == null && preset != null) value = IconCache.load(ctx, preset)
  }
  val d = if (preset != null) "" else cleanDomain(domain)
  Box(modifier.size(size).clip(shape).border(0.5.dp, Color.White.copy(alpha = 0.12f), shape), contentAlignment = Alignment.Center) {
    if (local != null) {
      Image(local!!, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    } else {
      if (d.isNotEmpty()) androidx.compose.runtime.LaunchedEffect(d) { IconCache.fetch(d) }
      val r = if (d.isNotEmpty()) IconCache.remote[d] else null
      if (r != null) {
        Box(Modifier.fillMaxSize().background(Color.White).padding(size * 0.14f)) { Image(r, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
      } else {
        val b = brandColor(name, domain)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(b.copy(alpha = 0.78f), b))), contentAlignment = Alignment.Center) {
          Text(name.trim().take(1).uppercase(), color = Color.White, fontSize = (size.value * 0.44f).sp, fontWeight = FontWeight.SemiBold)
        }
      }
    }
  }
}

// MARK: - wallet card

@Composable
fun Chip() {
  Canvas(Modifier.size(32.dp, 22.dp)) {
    val r = androidx.compose.ui.geometry.CornerRadius(5.dp.toPx())
    drawRoundRect(Brush.linearGradient(listOf(hex("#f7e3a1"), hex("#d4a84b"), hex("#f1d78a")), Offset.Zero, Offset(size.width, size.height)), cornerRadius = r)
    val k = size.width / 32f
    val c = hex("#8a6a24").copy(alpha = 0.55f); val sw = 0.8f * k
    for (y in listOf(8f, 14f)) {
      drawLine(c, Offset(0f, y * k), Offset(9 * k, y * k), sw); drawLine(c, Offset(23 * k, y * k), Offset(32 * k, y * k), sw)
    }
    drawRoundRect(c, Offset(9 * k, 4 * k), Size(14 * k, 14 * k), androidx.compose.ui.geometry.CornerRadius(3 * k), style = Stroke(sw))
  }
}

/** Stands in for the gold chip on an eSIM: there is no physical card. */
@Composable
fun ESimBadge() {
  Box(Modifier.size(32.dp, 22.dp).clip(RoundedCornerShape(5.dp)).background(Color.White.copy(alpha = 0.18f))
    .border(0.8.dp, Color.White.copy(alpha = 0.55f), RoundedCornerShape(5.dp)), contentAlignment = Alignment.Center) {
    T("eSIM", 10f, FontWeight.Bold, Color.White, lines = 1)
  }
}

fun footNote(i: Identity): String {
  if (i.isLogin) {
    val v = Store.viaOf(i)
    if (v != null) return "绑定 " + Store.title(v)
    return i.notes.ifEmpty { if (i.isApple) "Apple ID" else if (i.isQQ) "QQ 号" else "微信号" }
  }
  if (i.isPhone) {
    val c = carrier(i.value)
    val parts = listOf(if (c.isNotEmpty() && !i.notes.contains(c.drop(2))) c else "", i.notes).filter { it.isNotEmpty() }
    return if (parts.isEmpty()) "手机号" else parts.joinToString(" · ")
  }
  return i.notes.ifEmpty { "邮箱" }
}

@Composable
fun WalletCard(i: Identity, modifier: Modifier = Modifier, shadow: Boolean = true) {
  val accs = Store.accountsOf(i.id)
  val shape = CardShape(i.isPhone)
  val glow = hex(i.color)
  val poster = remember(accs) { accs.sortedByDescending { it.createdAt }.map { brandColor(it.name, it.domain) } }
  Box(
    modifier.fillMaxWidth().aspectRatio(1.586f)
      .then(if (shadow) Modifier.shadow(22.dp, shape, ambientColor = glow, spotColor = glow) else Modifier)
      .clip(shape)
      .drawBehindCompat {
        drawRect(skinBrush(i.color, size))
        // soft mosaic of the brand colours of what this identity unlocks
        if (poster.isNotEmpty()) {
          val cw = size.width / 3; val ch = size.height / 3
          for (k in 0 until 9) {
            val c = poster[k % poster.size]
            val cx = cw * (k % 3 + 0.5f); val cy = ch * (k / 3 + 0.5f)
            drawCircle(Brush.radialGradient(listOf(c.copy(alpha = 0.30f), Color.Transparent), Offset(cx, cy), cw * 0.9f), cw * 0.9f, Offset(cx, cy))
          }
        }
        // soft light from the top-left, like a printed card
        drawRect(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.22f), Color.Transparent), Offset(size.width * 0.12f, 0f), size.width * 0.75f))
      }
      .border(0.6.dp, Color.White.copy(alpha = 0.22f), shape)
  ) {
    CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides Color.White) {
      Column(Modifier.fillMaxSize().padding(18.dp)) {
        Row(verticalAlignment = Alignment.Top) {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (i.isESim) ESimBadge() else if (i.isPhone) Chip() else if (i.isLogin) LoginGlyph(i, 22.dp) else Icon(Icons.Outlined.Email, null, Modifier.size(20.dp), tint = Color.White)
            if (i.isLogin) {
              T(providerName(i.provider), 16f, FontWeight.SemiBold, Color.White, lines = 1)
              if (i.label.isNotEmpty()) T(i.label, 13f, FontWeight.Medium, Color.White.copy(alpha = 0.75f), lines = 1)
            } else T(i.label.ifEmpty { if (i.isPhone) "手机号" else "邮箱" }, 16f, FontWeight.SemiBold, Color.White, lines = 1)
          }
          Spacer(Modifier.weight(1f))
          Column(horizontalAlignment = Alignment.End) {
            androidx.compose.animation.AnimatedContent(accs.size, label = "count") { n -> T("$n", 32f, FontWeight.Bold, Color.White) }
            T("个网站与 App", 11f, FontWeight.Medium, Color.White.copy(alpha = 0.75f))
          }
        }
        Spacer(Modifier.weight(1f))
        AutoSizeText(Store.display(i), 21f, FontWeight.SemiBold, Color.White)
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.Bottom) {
          T(footNote(i), 12.5f, FontWeight.Medium, Color.White.copy(alpha = 0.75f), Modifier.weight(1f), lines = 1)
          IconStack(accs)
        }
      }
    }
  }
}

@Composable
fun AutoSizeText(text: String, max: Float, weight: FontWeight, color: Color) {
  BoxWithConstraints(Modifier.fillMaxWidth()) {
    // shrink long addresses down to 60% like minimumScaleFactor
    val approx = text.sumOf { if (it.code > 0x2e80) 2.0 else 1.12 } * 0.5
    val fit = (maxWidth.value / approx).toFloat()
    T(text, fit.coerceIn(max * 0.6f, max), weight, color, lines = 1)
  }
}

@Composable
fun IconStack(accounts: List<Account>) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    accounts.take(4).forEachIndexed { k, a ->
      AppIcon(a, 28.dp, Modifier.offset(x = (-9 * k).dp).border(1.5.dp, Color.White.copy(alpha = 0.55f), squircle(28.dp)))
    }
    if (accounts.size > 4) {
      val shift = (-9 * (minOf(accounts.size, 4) - 1)).dp
      Box(Modifier.offset(x = shift + 4.dp).height(28.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.22f)).padding(horizontal = 7.dp), contentAlignment = Alignment.Center) {
        T("+${accounts.size - 4}", 11f, FontWeight.Bold, Color.White)
      }
    }
  }
}

// MARK: - rows

@Composable
fun IdentityCaps(ids: List<String>) {
  val list = ids.mapNotNull { Store.identity(it) }
  Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
    if (list.isEmpty()) T("未关联", 12f, color = pal.secondary)
    list.forEach { i ->
      val c = hex(i.color)
      Row(Modifier.clip(CircleShape).background(c.copy(alpha = 0.16f)).padding(horizontal = 7.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(c)); Spacer(Modifier.width(4.dp))
        T(Store.title(i), 12f, FontWeight.Medium, lines = 1)
      }
    }
  }
}

@Composable
fun ReelRow(a: Account, meta: String? = null, onClick: () -> Unit) {
  val b = brandColor(a.name, a.domain)
  val shape = RoundedCornerShape(18.dp)
  Row(
    Modifier.fillMaxWidth().press(0.98f, onClick = onClick).clip(shape).background(pal.glass).background(b.copy(alpha = 0.14f))
      .padding(horizontal = 12.dp, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    AppIcon(a, 44.dp)
    Spacer(Modifier.width(12.dp))
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        T(a.name, 16f, FontWeight.SemiBold, lines = 1, modifier = Modifier.weight(1f, fill = false))
        a.handle?.let { Spacer(Modifier.width(6.dp)); T(Store.handleText(it), 13f, color = pal.secondary, lines = 1) }
      }
      IdentityCaps(a.identityIds)
    }
    Spacer(Modifier.width(4.dp))
    if (meta != null) T(meta, 12f, FontWeight.Medium, pal.secondary)
    else Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = pal.tertiary)
  }
}

@Composable
fun SmallCard(i: Identity, icon: ImageVector, w: Dp, h: Dp) {
  Box(Modifier.size(w, h).clip(CardShape(i.isPhone)).drawBehindCompat { drawRect(skinBrush(i.color, size)) }, contentAlignment = Alignment.Center) {
    if (i.isESim) T("eSIM", (h.value * 0.36f), FontWeight.Bold, Color.White, lines = 1)
    else if (i.isLogin) LoginGlyph(i, h * 0.6f)
    else Icon(icon, null, Modifier.size(h * 0.5f), tint = Color.White)
  }
}

/** Material icon for an identity kind (login cards draw their own glyph, see LoginGlyph). */
fun kindIcon(i: Identity): ImageVector = when {
  i.isPhone -> Icons.Filled.SimCard
  i.isLogin -> Icons.Filled.Key
  else -> Icons.Filled.Email
}

@Composable
fun LoginGlyph(i: Identity, size: Dp, color: Color = Color.White) = if (i.isApple) AppleGlyph(size, color) else if (i.isQQ) QQGlyph(size, color) else WeChatGlyph(size, color)

/** Outlined QQ penguin silhouette: same 24×24 path as the web icon (ICON.qq in app.js), not the official artwork. */
private val qqBody = androidx.compose.ui.graphics.vector.PathParser().parsePathString(
  "M12 2.8c-3.3 0-5.6 2.6-5.6 6 0 .8.1 1.5.3 2.1-1.1 1.4-2.2 3.3-2.2 5 0 .9.4 1.3 1 .9.5-.3 1-.9 1.3-1.5.3 1.2 1 2.3 1.8 3.1-1 .4-1.9 1-1.9 1.6 0 .8 1.6 1.2 3.4 1.2 1.1 0 2-.2 2.6-.5h.6c.6.3 1.5.5 2.6.5 1.8 0 3.4-.4 3.4-1.2 0-.6-.9-1.2-1.9-1.6.8-.8 1.5-1.9 1.8-3.1.3.6.8 1.2 1.3 1.5.6.4 1 0 1-.9 0-1.7-1.1-3.6-2.2-5 .2-.6.3-1.3.3-2.1 0-3.4-2.3-6-5.6-6Z" +
  "M8.6 11.6c2.2 1 4.6 1 6.8 0").toPath()

@Composable
fun QQGlyph(size: Dp, color: Color = Color.White) {
  Canvas(Modifier.size(size)) {
    val k = this.size.width / 24f
    scale(k, k, pivot = androidx.compose.ui.geometry.Offset.Zero) {
      drawPath(qqBody, color, style = Stroke(1.7f, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
      drawCircle(color, radius = 0.9f, center = androidx.compose.ui.geometry.Offset(10f, 8.2f))
      drawCircle(color, radius = 0.9f, center = androidx.compose.ui.geometry.Offset(14f, 8.2f))
    }
  }
}

/** Two overlapping chat bubbles, outlined (not the official artwork). */
@Composable
fun WeChatGlyph(size: Dp, color: Color = Color.White) {
  Canvas(Modifier.size(size)) {
    val w = this.size.width; val sw = w * 0.075f
    fun bubble(cx: Float, cy: Float, rx: Float, ry: Float, tailLeft: Boolean) {
      val p = Path().apply {
        addOval(androidx.compose.ui.geometry.Rect(cx - rx, cy - ry, cx + rx, cy + ry))
      }
      drawPath(p, color, style = Stroke(sw))
      val tx = if (tailLeft) cx - rx * 0.45f else cx + rx * 0.45f
      val tail = Path().apply {
        moveTo(tx - rx * 0.18f, cy + ry * 0.82f)
        lineTo(tx + (if (tailLeft) -rx * 0.22f else rx * 0.22f), cy + ry * 1.32f)
        lineTo(tx + rx * 0.22f, cy + ry * 0.9f)
      }
      drawPath(tail, color, style = Stroke(sw, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    }
    bubble(w * 0.38f, w * 0.38f, w * 0.34f, w * 0.27f, true)
    bubble(w * 0.66f, w * 0.64f, w * 0.27f, w * 0.22f, false)
  }
}

/** A simple drawn apple: body with a bite, and a leaf. */
@Composable
fun AppleGlyph(size: Dp, color: Color = Color.White) {
  Canvas(Modifier.size(size)) {
    val w = this.size.width; val h = this.size.height
    val body = Path().apply {
      moveTo(w * 0.50f, h * 0.30f)
      cubicTo(w * 0.36f, h * 0.20f, w * 0.10f, h * 0.24f, w * 0.12f, h * 0.55f)
      cubicTo(w * 0.14f, h * 0.80f, w * 0.30f, h * 0.98f, w * 0.42f, h * 0.95f)
      cubicTo(w * 0.47f, h * 0.94f, w * 0.53f, h * 0.94f, w * 0.58f, h * 0.95f)
      cubicTo(w * 0.70f, h * 0.98f, w * 0.82f, h * 0.84f, w * 0.88f, h * 0.70f)
      cubicTo(w * 0.72f, h * 0.62f, w * 0.72f, h * 0.40f, w * 0.85f, h * 0.33f)
      cubicTo(w * 0.74f, h * 0.20f, w * 0.60f, h * 0.22f, w * 0.50f, h * 0.30f)
      close()
    }
    drawPath(body, color)
    val leaf = Path().apply {
      moveTo(w * 0.50f, h * 0.24f)
      cubicTo(w * 0.50f, h * 0.12f, w * 0.58f, h * 0.04f, w * 0.68f, h * 0.03f)
      cubicTo(w * 0.68f, h * 0.15f, w * 0.60f, h * 0.23f, w * 0.50f, h * 0.24f)
      close()
    }
    drawPath(leaf, color)
  }
}

fun Path.thread(from: Offset, to: Offset): Path {
  val mx = (from.x + to.x) / 2
  moveTo(from.x, from.y); cubicTo(mx, from.y, mx, to.y, to.x, to.y)
  return this
}
