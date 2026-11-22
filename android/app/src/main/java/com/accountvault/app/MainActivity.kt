package com.accountvault.app

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import kotlinx.coroutines.launch
import androidx.compose.runtime.withFrameNanos
import kotlin.math.hypot
import kotlin.math.max

class MainActivity : FragmentActivity() {
  private var _unlocked = androidx.compose.runtime.mutableStateOf(false)
  val unlocked: androidx.compose.runtime.State<Boolean> get() = _unlocked

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    // No forced refresh-rate mode: the system's adaptive rate (60/90/120 Hz) is left alone, which saves
    // battery and avoids mode-switch hitches on some ROMs. Smoothness comes from the animations instead.
    Store.init(applicationContext)
    IconCache.prewarm(applicationContext)
    Sync.init()
    Store.onSaved = { Sync.schedule() }
    // launch extras used by the CI emulator screenshots
    intent?.getStringExtra("atlasTheme")?.let { Store.putTheme(it) }
    val startTab = intent?.getIntExtra("atlasTab", 0) ?: 0
    when (intent?.getStringExtra("atlasRoute")) {
      "picker" -> Store.push(Route.Picker(Store.active?.id))
      "identity" -> Store.active?.let { Store.push(Route.IdentityR(it.id)) }
      "settings" -> Store.push(Route.Settings)
      "new" -> Store.push(Route.NewIdentity("email"))
    }
    Store.onLocked = { lockNow() }
    enableEdgeToEdge(SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT))
    setContent {
      if (unlocked.value) AtlasRoot(startTab)
      else UnlockGate { firstSetup ->
        Store.loadFromVault(firstSetup)
        _unlocked.value = true
      }
    }
  }

  private fun lockNow() {
    if (!Store.ready) return
    Store.clearOnLock()
    Vault.lock()
    _unlocked.value = false
  }

  override fun onStop() { super.onStop(); lockNow() }
  override fun onResume() {
    super.onResume()
    if (Store.ready) Sync.runNow()
  }
}

object ThemeFX { var origin = Offset.Zero; var request: ((String) -> Unit)? = null }

@Composable
fun AtlasRoot(startTab: Int) {
  val sysDark = isSystemInDarkTheme()
  val dark = when (Store.theme) { "light" -> false; "system" -> sysDark; else -> true }
  val p = if (dark) DarkPal else LightPal
  val view = androidx.compose.ui.platform.LocalView.current
  LaunchedEffect(dark) {
    val w = (view.context as? android.app.Activity)?.window ?: return@LaunchedEffect
    WindowCompat.getInsetsController(w, view).apply { isAppearanceLightStatusBars = !dark; isAppearanceLightNavigationBars = !dark }
  }
  val scheme = if (dark) darkColorScheme(primary = p.accent, background = p.bg, surface = p.glassStrong, surfaceContainerHigh = hex("#2c2c2e"), error = p.red)
               else lightColorScheme(primary = p.accent, background = p.bg, surface = Color.White, surfaceContainerHigh = Color.White, error = p.red)

  // Theme switch like the iOS app: freeze a snapshot, flip the theme underneath, then open a
  // circular hole in the snapshot from the button.
  val layer = rememberGraphicsLayer()
  var snap by remember { mutableStateOf<ImageBitmap?>(null) }
  val reveal = remember { Animatable(0f) }
  val scope = rememberCoroutineScope()
  ThemeFX.request = { t ->
    if (snap == null) scope.launch {
      snap = runCatching { layer.toImageBitmap() }.getOrNull()
      Store.putTheme(t)
      reveal.snapTo(0f)
      reveal.animateTo(1f, tween(550, easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)))
      snap = null
    }
  }

  MaterialTheme(colorScheme = scheme) {
    CompositionLocalProvider(LocalPal provides p, androidx.compose.material3.LocalContentColor provides p.primary) {
      Box(Modifier.fillMaxSize().background(p.bg)) {
        Box(Modifier.fillMaxSize().drawWithContent { layer.record { this@drawWithContent.drawContent() }; drawLayer(layer) }) {
          Shell(startTab)
        }
        snap?.let { img ->
          Canvas(Modifier.fillMaxSize().clickable(remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, null) {}) {
            val o = ThemeFX.origin.takeIf { it != Offset.Zero } ?: Offset(size.width / 2, 60f)
            val maxR = hypot(max(o.x, size.width - o.x), max(o.y, size.height - o.y)) + 4
            val r = 0.5f + (maxR - 0.5f) * reveal.value
            clipPath(Path().apply { addOval(Rect(o, r)) }, ClipOp.Difference) { drawImage(img) }
          }
        }
      }
    }
  }
}

@Composable
fun Shell(startTab: Int) {
  var tab by remember { mutableIntStateOf(startTab.coerceIn(0, 2)) }
  var dir by remember { mutableIntStateOf(1) }
  val hap = haptics()
  val sheetUp = Store.routes.isNotEmpty()
  val backScale by animateFloatAsState(if (sheetUp) 0.94f else 1f, calm(), label = "back")
  BackHandler(enabled = sheetUp) { Store.pop() }

  Box(Modifier.fillMaxSize()) {
    Box(Modifier.fillMaxSize().graphicsLayer { scaleX = backScale; scaleY = backScale }.clip(RoundedCornerShape(if (sheetUp) 28.dp else 0.dp))) {
      Glow()
      // the page you switch to slides in a little from the side you came from
      for (k in 0..2) {
        if (k == tab) key(k) {
          val enter = remember { Animatable(0f) }
          LaunchedEffect(Unit) { enter.animateTo(1f, calm(380f)) }
          val d = LocalDensity.current
          Box(Modifier.fillMaxSize().graphicsLayer {
            alpha = 0.35f + 0.65f * enter.value
            translationX = (1 - enter.value) * dir * with(d) { 28.dp.toPx() }
          }) {
            when (k) { 0 -> WalletScreen(); 1 -> SitesScreen(); else -> MapScreen() }
          }
        }
      }
      TabBar(tab, Modifier.align(Alignment.BottomCenter)) { t -> if (t != tab) { dir = if (t >= tab) 1 else -1; tab = t; hap(TICK) } }
    }
    Sheets()
    ToastView(Modifier.align(Alignment.TopCenter))
  }
}

@Composable
fun Glow() {
  val c = androidx.compose.animation.animateColorAsState(hex(Store.active?.color ?: "#3a3a3c"), tween(600), label = "glow").value
  val dark = pal.dark
  Canvas(Modifier.fillMaxSize()) {
    drawRect(if (dark) Color.Black else hex("#f2f2f7"))
    val center = Offset(size.width / 2, size.height * 0.18f)
    drawRect(androidx.compose.ui.graphics.Brush.radialGradient(listOf(c.copy(alpha = if (dark) 0.55f else 0.32f), Color.Transparent), center, 420.dp.toPx()))
  }
}

@Composable
fun TabBar(tab: Int, modifier: Modifier, onTab: (Int) -> Unit) {
  val items = listOf(Triple("卡包", Icons.Filled.ViewCarousel, 0), Triple("全部 App", Icons.Filled.Apps, 1), Triple("关系图", Icons.Filled.AccountTree, 2))
  Box(modifier.fillMaxWidth().background(if (pal.dark) Color(0xE6121212) else Color(0xE6F9F9F9))) {
    Box(Modifier.fillMaxWidth().height(0.5.dp).background(pal.stroke))
    Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).height(56.dp)) {
      items.forEach { (name, icon, k) ->
        val on = tab == k
        Column(Modifier.weight(1f).fillMaxSize().clickable(remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, null) { onTab(k) },
          horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
          Icon(icon, name, Modifier.size(24.dp), tint = if (on) pal.accent else pal.secondary)
          T(name, 10.5f, FontWeight.Medium, if (on) pal.accent else pal.secondary)
        }
      }
    }
  }
}

// MARK: - sheets (a stack, like a sheet presented over a sheet)

@Composable
fun Sheets() {
  val items = remember { mutableStateListOf<Entry>() }
  val leaving = remember { mutableStateMapOf<Long, Boolean>() }
  LaunchedEffect(Store.routes) {
    for (e in Store.routes) if (items.none { it.key == e.key }) items.add(e)
    for (e in items) leaving[e.key] = Store.routes.none { it.key == e.key }
  }
  val top = items.lastOrNull { leaving[it.key] != true }?.key
  items.toList().forEach { e ->
    key(e.key) {
      SheetFrame(leaving[e.key] == true, covered = top != null && e.key != top && leaving[e.key] != true, onGone = { items.remove(e); leaving.remove(e.key) }) {
        SheetHost(e.route)
      }
    }
  }
}

private val SheetShape = RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp)

@Composable
fun SheetFrame(leaving: Boolean, covered: Boolean, onGone: () -> Unit, content: @Composable () -> Unit) {
  val y = remember { Animatable(1f) }
  var h by remember { mutableStateOf(1f) }
  val scope = rememberCoroutineScope()
  LaunchedEffect(leaving) {
    if (leaving) { y.animateTo(1f, calm(420f)); onGone() }
    else {
      // Let the sheet's first (heaviest) frame compose and draw off-screen before the clock starts,
      // so the slide-up begins from its first position instead of skipping the opening frames.
      if (y.value == 1f) { withFrameNanos { }; withFrameNanos { } }
      y.animateTo(0f, calm(320f))
    }
  }
  val coverScale by animateFloatAsState(if (covered) 0.94f else 1f, calm(), label = "cover")
  Box(Modifier.fillMaxSize()) {
    Box(Modifier.fillMaxSize().graphicsLayer { alpha = (1 - y.value) }.background(Color.Black.copy(alpha = 0.4f))
      .clickable(remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, null) { Store.pop() })
    BoxWithConstraints(Modifier.fillMaxSize().statusBarsPadding().padding(top = 10.dp)) {
      h = constraints.maxHeight.toFloat()
      Box(Modifier.fillMaxSize()
        // one layer does the move, the shadow and the rounded clip (no separate shadow + clip layers)
        .graphicsLayer {
          translationY = y.value * h; scaleX = coverScale; scaleY = coverScale; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0f)
          shadowElevation = 20.dp.toPx(); shape = SheetShape; clip = true
        }
        .background(if (pal.dark) hex("#0b0b0d") else hex("#f2f2f7"))
        .clickable(remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, null) {}
      ) {
        content()
        // drag handle: pull down to dismiss
        Box(Modifier.fillMaxWidth().height(28.dp).pointerInput(Unit) {
          detectVerticalDragGestures(
            onDragEnd = { scope.launch { if (y.value > 0.22f) Store.pop() else y.animateTo(0f, calm()) } },
            onDragCancel = { scope.launch { y.animateTo(0f, calm()) } },
          ) { ch, dy -> ch.consume(); scope.launch { y.snapTo((y.value + dy / h).coerceIn(0f, 1f)) } }
        }, contentAlignment = Alignment.Center) {
          Box(Modifier.size(36.dp, 5.dp).clip(RoundedCornerShape(3.dp)).background(pal.tertiary))
        }
      }
    }
  }
}

@Composable
fun SheetHost(r: Route) {
  when (r) {
    is Route.IdentityR -> IdentityDetail(r.id)
    is Route.AccountR -> AccountDetail(r.id)
    is Route.NewIdentity -> IdentityForm(null, r.type, COLORS[Store.data.identities.size % COLORS.size])
    is Route.EditIdentity -> IdentityForm(Store.identity(r.id), "email", COLORS[0])
    is Route.EditAccount -> Store.account(r.id)?.let { AccountForm(it) }
    is Route.Picker -> PickerSheet(r.preset)
    Route.Settings -> SettingsSheet()
  }
}

@Composable
fun ToastView(modifier: Modifier) {
  val t = Store.toast
  val hap = haptics()
  LaunchedEffect(t?.id) { if (t != null) hap(CONFIRM) }
  AnimatedVisibility(t != null, modifier.windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 14.dp, vertical = 6.dp),
    enter = slideInVertically(calm()) { -it } + fadeIn(), exit = slideOutVertically(calm()) { -it } + fadeOut()) {
    val last = remember { mutableStateOf(t) }
    if (t != null) last.value = t
    val cur = last.value ?: return@AnimatedVisibility
    Row(
      Modifier.fillMaxWidth().shadow(20.dp, RoundedCornerShape(24.dp)).clip(RoundedCornerShape(24.dp))
        .background(if (pal.dark) hex("#2c2c2e") else Color.White).padding(horizontal = 14.dp, vertical = 11.dp)
        .pointerInput(Unit) { detectVerticalDragGestures { _, dy -> if (dy < -10) Store.toast = null } },
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Image(painterResource(R.mipmap.ic_launcher_flat), null, Modifier.size(38.dp).clip(RoundedCornerShape(9.dp)))
      Spacer(Modifier.width(12.dp))
      Column(Modifier.weight(1f)) {
        T("账号仓库", 13f, FontWeight.SemiBold, pal.secondary)
        T(cur.text, 15f, FontWeight.SemiBold)
      }
      cur.action?.let { a ->
        Text(a, Modifier.clickable { Store.toastAction?.invoke(); Store.toast = null }.padding(8.dp), color = pal.accent, fontWeight = FontWeight.SemiBold)
      }
    }
  }
}
