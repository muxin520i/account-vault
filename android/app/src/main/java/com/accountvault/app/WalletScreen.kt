package com.accountvault.app

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.SimCard
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

@Composable
fun TopButtons() {
  var menu by remember { mutableStateOf(false) }
  val hap = haptics()
  Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 12.dp, vertical = 4.dp),
    horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
    val light = Store.theme == "light"
    RoundButton(Modifier.onGloballyPositioned { ThemeFX.origin = it.boundsInRoot().center }, onClick = {
      hap(TICK); val t = if (Store.theme == "light") "dark" else "light"
      ThemeFX.request?.invoke(t) ?: Store.putTheme(t)
    }) {
      AnimatedContent(light, transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.6f)) togetherWith fadeOut() }, label = "sun") { l ->
        Icon(if (l) Icons.Filled.DarkMode else Icons.Filled.LightMode, "外观", Modifier.size(20.dp), tint = pal.accent)
      }
    }
    Spacer(Modifier.width(8.dp))
    RoundButton(onClick = {
      if (Sync.on) { Store.show("正在同步…"); Sync.runNow() }
      else { Store.push(Route.Settings); Store.show("请先在设置里开启加密同步") }
    }) {
      Icon(Icons.Filled.Sync, "同步", Modifier.size(20.dp), tint = pal.accent)
    }
    Spacer(Modifier.width(8.dp))
    Box {
      RoundButton(onClick = { menu = true }) { Icon(Icons.Filled.Add, "添加", Modifier.size(22.dp), tint = pal.accent) }
      DropdownMenu(menu, { menu = false }) {
        DropdownMenuItem({ androidx.compose.material3.Text("添加网站或 App") }, { menu = false; Store.push(Route.Picker(Store.active?.id)) }, leadingIcon = { Icon(Icons.Outlined.Apps, null) })
        DropdownMenuItem({ androidx.compose.material3.Text("添加邮箱") }, { menu = false; Store.push(Route.NewIdentity("email")) }, leadingIcon = { Icon(Icons.Outlined.Email, null) })
        DropdownMenuItem({ androidx.compose.material3.Text("添加手机号") }, { menu = false; Store.push(Route.NewIdentity("phone")) }, leadingIcon = { Icon(Icons.Outlined.SimCard, null) })
        DropdownMenuItem({ androidx.compose.material3.Text("添加微信或 Apple 账号") }, { menu = false; Store.push(Route.NewIdentity("login")) }, leadingIcon = { Icon(Icons.Outlined.Key, null) })
      }
    }
    Spacer(Modifier.width(8.dp))
    RoundButton(onClick = { Store.push(Route.Settings) }) { Icon(Icons.Filled.MoreHoriz, "设置", Modifier.size(22.dp), tint = pal.accent) }
  }
}

@Composable
fun RoundButton(modifier: Modifier = Modifier, onClick: () -> Unit, content: @Composable () -> Unit) {
  Box(modifier.size(38.dp).press(0.9f, onClick = onClick).glass(CircleShape), contentAlignment = Alignment.Center) { content() }
}

@Composable
fun LargeTitle(title: String, sub: String? = null) {
  Column(Modifier.padding(horizontal = 20.dp)) {
    T(title, 34f, FontWeight.Bold)
    if (sub != null) T(sub, 15f, color = pal.secondary, modifier = Modifier.padding(top = 4.dp))
  }
}

@Composable
fun WalletScreen() {
  val s = rememberScrollState()
  Column(Modifier.fillMaxSize().verticalScroll(s)) {
    TopButtons()
    val d = Store.data
    val e = d.identities.count { it.type == "email" }; val p = d.identities.count { it.type == "phone" }
    val l = d.identities.count { it.isLogin }; val a = d.accounts.size
    LargeTitle("账号", if (d.identities.isEmpty() && a == 0) "先添加一个邮箱或手机号"
      else "$e 个邮箱、$p 个手机号" + (if (l > 0) "、$l 个第三方登录" else "") + "，关联着 $a 个网站与 App")
    KindSwitch(Modifier.padding(horizontal = 20.dp).padding(top = 14.dp))
    AnimatedContent(Store.kind, transitionSpec = {
      val from = if (kindRank(targetState) > kindRank(initialState)) 1 else -1
      (androidx.compose.animation.slideInHorizontally(calm()) { it / 3 * from } + fadeIn(calm())) togetherWith fadeOut(calm())
    }, label = "deck") { k -> Deck(k) }
    AppGrid(Modifier.padding(horizontal = 20.dp).padding(top = 22.dp))
    Timeline(Modifier.padding(horizontal = 20.dp).padding(top = 34.dp))
    Spacer(Modifier.height(110.dp))
  }
}

@Composable
fun KindSwitch(modifier: Modifier) {
  val hap = haptics()
  BoxWithConstraints(modifier.fillMaxWidth().height(42.dp).clip(CircleShape).background(pal.glass).padding(3.dp)) {
    val seg = maxWidth / KINDS.size
    val x by animateDpAsState(seg * KINDS.indexOf(Store.kind).coerceAtLeast(0), calm(), label = "thumb")
    Box(Modifier.offset(x = x).width(seg).fillMaxSize().shadow(6.dp, CircleShape).clip(CircleShape).background(if (pal.dark) hex("#3a3a3c") else Color.White))
    Row(Modifier.fillMaxSize()) {
      listOf(Triple("email", "邮箱", Icons.Outlined.Email), Triple("phone", "手机号", Icons.Outlined.SimCard), Triple("login", "登录", Icons.Outlined.Key)).forEach { (k, name, icon) ->
        val on = Store.kind == k
        val c = if (on) pal.primary else pal.secondary
        Row(Modifier.weight(1f).fillMaxSize().press(1f) { if (!on) { hap(TICK); Store.setKind(k) } },
          horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
          Icon(icon, null, Modifier.size(16.dp), tint = c); Spacer(Modifier.width(5.dp))
          T(name, 15f, FontWeight.SemiBold, c, lines = 1); Spacer(Modifier.width(5.dp))
          T("${Store.data.identities.count { it.type == k }}", 13f, FontWeight.Medium, pal.secondary)
        }
      }
    }
  }
}

/** Coverflow of identity cards; the glow behind follows the front card. */
@Composable
fun Deck(kind: String) {
  val deck = Store.data.identities.filter { it.type == kind }
  val ids = deck.map { it.id } + "add"
  val start = ids.indexOf(Store.activeID).coerceAtLeast(0)
  val pager = rememberPagerState(start) { ids.size }
  val hap = haptics()
  LaunchedEffect(pager, ids) {
    snapshotFlow { pager.currentPage }.collect { pg ->
      val id = ids.getOrNull(pg) ?: return@collect
      if (Store.activeID != id && Store.kind == kind) { Store.activeID = id; hap(TICK) }
    }
  }
  // follow external changes (new card focused, demo)
  LaunchedEffect(Store.activeID, ids) {
    val k = ids.indexOf(Store.activeID)
    if (k >= 0 && k != pager.currentPage && !pager.isScrollInProgress) pager.animateScrollToPage(k, animationSpec = calm())
  }
  Column {
    HorizontalPager(pager, Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 34.dp, vertical = 26.dp), pageSpacing = 14.dp,
      beyondViewportPageCount = 1) { pg ->
      val off = (pager.currentPage - pg) + pager.currentPageOffsetFraction
      val m = Modifier.graphicsLayer {
        val a = abs(off).coerceAtMost(1f)
        rotationY = off * 18f
        cameraDistance = 12f * density
        scaleX = 1 - a * 0.1f; scaleY = 1 - a * 0.1f
        alpha = 1 - a * 0.35f
      }
      if (pg < deck.size) {
        val i = deck[pg]
        Box(m.press(0.97f) { Store.push(Route.IdentityR(i.id)) }) { WalletCard(i) }
      } else AddCard(kind, m)
    }
    Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.Center) {
      ids.forEachIndexed { k, _ ->
        val on = k == pager.currentPage
        val w by animateDpAsState(if (on) 16.dp else 6.dp, calm(), label = "dot")
        Box(Modifier.padding(horizontal = 3.dp).size(w, 6.dp).clip(CircleShape).background(if (on) pal.primary else pal.secondary.copy(alpha = 0.35f)))
      }
    }
  }
}

@Composable
fun AddCard(kind: String, modifier: Modifier = Modifier) {
  val shape = CardShape(kind == "phone")
  val stroke = pal.secondary.copy(alpha = 0.5f)
  Box(modifier.fillMaxWidth().aspectRatio(1.586f).press { Store.push(Route.NewIdentity(kind)) }.clip(shape).background(pal.glass)
    .drawBehind {
      val o = shape.createOutline(size, layoutDirection, this)
      drawOutline(o, stroke, style = Stroke(1.2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))))
    }, contentAlignment = Alignment.Center) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      Icon(Icons.Filled.Add, null, Modifier.size(30.dp), tint = pal.secondary)
      Spacer(Modifier.height(10.dp))
      T(when {
        kind == "login" -> "添加微信或 Apple 账号"
        Store.data.identities.isEmpty() -> "从一个邮箱或手机号开始"
        kind == "phone" -> "添加手机号"
        else -> "添加邮箱"
      }, 16f, FontWeight.SemiBold, pal.secondary)
    }
  }
}

/** Icons of what the front card unlocks. */
@Composable
fun AppGrid(modifier: Modifier) {
  val i = Store.active
  val list = i?.let { Store.accountsOf(it.id) } ?: Store.data.accounts.sortedWith(compareBy(nameOrder) { it.name })
  val more = if (list.size > 11) list.size - 11 else 0
  val shown = if (more > 0) list.take(11) else list
  val tint = hex(i?.color ?: "#0a84ff")
  Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Column(Modifier.weight(1f)) {
        T(i?.let { Store.title(it) } ?: "全部网站与 App", 22f, FontWeight.Bold, lines = 1)
        if (i != null) T("用它注册或绑定的 ${list.size} 个网站与 App", 13f, color = pal.secondary)
      }
      Row(Modifier.press(0.94f) { Store.push(Route.Picker(i?.id)) }.clip(CircleShape).background(tint.copy(alpha = 0.18f)).padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Add, null, Modifier.size(16.dp), tint = tint); Spacer(Modifier.width(4.dp)); T("添加", 14f, FontWeight.SemiBold, tint)
      }
    }
    AnimatedContent(i?.id, transitionSpec = { (fadeIn(calm()) + scaleIn(calm(), 0.92f)) togetherWith fadeOut(calm()) }, label = "grid") { _ ->
      val tiles: List<@Composable () -> Unit> = shown.map { a -> @Composable {
        Column(Modifier.press(0.9f) { Store.push(Route.AccountR(a.id)) }, horizontalAlignment = Alignment.CenterHorizontally) {
          AppIcon(a, 58.dp, Modifier.shadow(8.dp, squircle(58.dp), ambientColor = brandColor(a.name, a.domain), spotColor = brandColor(a.name, a.domain)))
          T(a.handle?.let { Store.handleText(it) } ?: a.name, 12f, lines = 1, modifier = Modifier.padding(top = 6.dp))
        }
      } } + (if (i != null) listOf(@Composable {
        Column(Modifier.press(0.9f) { if (more > 0) Store.push(Route.IdentityR(i.id)) else Store.push(Route.Picker(i.id)) }, horizontalAlignment = Alignment.CenterHorizontally) {
          Box(Modifier.size(58.dp).glass(squircle(58.dp)), contentAlignment = Alignment.Center) {
            if (more > 0) T("+$more", 18f, FontWeight.Bold) else Icon(Icons.Filled.Add, null, tint = pal.primary)
          }
          T(if (more > 0) "全部" else "添加", 12f, color = pal.secondary, modifier = Modifier.padding(top = 6.dp))
        }
      }) else emptyList())
      Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(pal.glass.copy(alpha = pal.glass.alpha * 0.7f)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        tiles.chunked(4).forEach { row ->
          Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) { it() } }
            repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
          }
        }
        if (tiles.isEmpty()) T("还没有网站，从右上角的 + 开始。", 14f, color = pal.secondary)
      }
    }
  }
}

fun dayOf(ms: Double): LocalDate = Instant.ofEpochMilli(ms.toLong()).atZone(ZoneId.systemDefault()).toLocalDate()

@Composable
fun Timeline(modifier: Modifier) {
  val list = Store.data.accounts.sortedByDescending { it.createdAt }.take(5)
  val groups = list.groupBy { dayOf(it.createdAt) }.toList()
  val today = LocalDate.now()
  Column(modifier.animateContentSize(calm()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
    T("最近添加", 22f, FontWeight.Bold)
    if (list.isEmpty()) T("添加的网站会按时间出现在这里。", 14f, color = pal.secondary)
    groups.forEach { (day, items) ->
      Row(verticalAlignment = Alignment.Top) {
        Box(Modifier.width(44.dp), contentAlignment = Alignment.TopCenter) {
          if (day == today || day == today.minusDays(1)) {
            T(if (day == today) "今天" else "昨天", 15f, FontWeight.Bold, if (day == today) pal.accent else pal.primary, Modifier.padding(top = 14.dp))
          } else Column(Modifier.padding(top = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            T("${day.monthValue}月", 11f, FontWeight.Medium, pal.secondary)
            T("${day.dayOfMonth}", 24f, FontWeight.Bold)
          }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          items.forEach { a -> ReelRow(a, a.category) { Store.push(Route.AccountR(a.id)) } }
        }
      }
    }
  }
}

