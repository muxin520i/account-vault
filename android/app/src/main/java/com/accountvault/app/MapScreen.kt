package com.accountvault.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private fun ease(t: Float) = FastOutSlowInEasing.transform(t.coerceIn(0f, 1f))

/** Identities on the left, apps on the right, joined by threads in the identity's colour. */
@Composable
fun MapScreen() {
  var focus by remember { mutableStateOf<String?>(null) }
  val hap = haptics()
  // every visit replays the build-up: cards settle in, then threads draw from the cards to the apps
  val clock = remember { Animatable(0f) }
  LaunchedEffect(Unit) { clock.animateTo(3.2f, tween(3200, easing = LinearEasing)) }
  LaunchedEffect(Store.demoFocus) { Store.demoFocus?.let { focus = if (it == "none") null else it } }
  LaunchedEffect(focus) { if (focus != null) hap(TICK) }

  val all = Store.data.identities
  val ids = all.filter { it.type == "email" } + all.filter { it.type == "phone" }
  val rank = ids.withIndex().associate { it.value.id to it.index }
  val apps = Store.data.accounts.sortedWith(Comparator { x, y ->
    val a = x.identityIds.mapNotNull { rank[it] }.minOrNull() ?: 999; val b = y.identityIds.mapNotNull { rank[it] }.minOrNull() ?: 999
    if (a != b) a - b else nameOrder.compare(x.name, y.name)
  })
  val cardH = 64f; val cardGap = 12f; val appH = 46f; val appGap = 8f; val groupGap = 30f
  val emails = all.count { it.type == "email" }
  fun idY(k: Int) = k * (cardH + cardGap) + (if (k >= emails && emails > 0) groupGap else 0f) + 22f
  fun appY(j: Int) = j * (appH + appGap) + 22f
  val height = maxOf(idY(maxOf(ids.size - 1, 0)) + cardH, appY(maxOf(apps.size - 1, 0)) + appH) + 10f

  Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
    TopButtons()
    LargeTitle("关系图")
    if (all.isEmpty()) {
      T("添加邮箱或手机号后，这里会画出它们和网站之间的关系。", 15f, color = pal.secondary, modifier = Modifier.padding(40.dp))
    } else BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 120.dp).height(height.dp)
      .clickable(remember { MutableInteractionSource() }, null) { focus = null }) {
      val width = maxWidth.value
      val L = minOf(160f, width * 0.42f); val rx = width - 16f - width * 0.4f
      val t = clock.value
      val linked = apps.filter { a -> focus?.let { it in a.identityIds } ?: true }.map { it.id }.toSet()
      val appIndex = apps.withIndex().associate { it.value.id to it.index }

      // threads
      val focusAlpha = ids.associate { i -> i.id to animateFloatAsState(if (focus == null || focus == i.id) 1f else 0.1f, calm(), label = "f").value }
      Canvas(Modifier.fillMaxSize()) {
        val pm = PathMeasure()
        ids.forEachIndexed { k, i ->
          val y0 = (idY(k) + cardH / 2).dp.toPx()
          val col = hex(i.color)
          apps.filter { i.id in it.identityIds }.forEach { a ->
            val j = appIndex[a.id] ?: return@forEach
            val p = ease((t - (0.18f + k * 0.07f + j * 0.012f)) / 0.75f)
            if (p <= 0f) return@forEach
            val full = Path().thread(Offset((16 + L).dp.toPx(), y0), Offset(rx.dp.toPx(), (appY(j) + appH / 2).dp.toPx()))
            pm.setPath(full, false)
            val seg = Path(); pm.getSegment(0f, pm.length * p, seg, true)
            val on = focus == null || focus == i.id
            drawPath(seg, col.copy(alpha = 0.75f * (focusAlpha[i.id] ?: 1f)), style = Stroke((if (on && focus != null) 2f else 1.3f).dp.toPx(), cap = StrokeCap.Round))
          }
        }
      }

      if (ids.any { it.type == "email" }) T("邮箱", 12f, FontWeight.SemiBold, pal.secondary, Modifier.offset(18.dp, 0.dp))
      ids.indexOfFirst { it.type == "phone" }.takeIf { it >= 0 }?.let { k -> T("手机号", 12f, FontWeight.SemiBold, pal.secondary, Modifier.offset(18.dp, (idY(k) - 22).dp)) }

      ids.forEachIndexed { k, i ->
        val p = ease((t - k * 0.05f) / 0.5f)
        val sel by animateFloatAsState(if (focus == i.id) 1.04f else 1f, calm(), label = "sel")
        val dim by animateFloatAsState(if (focus == null || focus == i.id) 1f else 0.4f, calm(), label = "dim")
        Box(Modifier.offset((16 - 26 * (1 - p)).dp, idY(k).dp).graphicsLayer {
          alpha = p * dim; val s = (0.92f + 0.08f * p) * sel; scaleX = s; scaleY = s
        }.press { if (focus == i.id) Store.push(Route.IdentityR(i.id)) else focus = i.id }) { MiniCard(i, L.dp, cardH.dp) }
      }
      apps.forEachIndexed { j, a ->
        val p = ease((t - (0.1f + j * 0.02f)) / 0.5f)
        val dim by animateFloatAsState(if (a.id in linked) 1f else 0.3f, calm(), label = "adim")
        Row(Modifier.offset((rx + 18 * (1 - p)).dp, appY(j).dp).graphicsLayer { alpha = p * dim }
          .width((width - 16 - rx).dp).height(appH.dp).press { Store.push(Route.AccountR(a.id)) }
          .clip(RoundedCornerShape(14.dp)).background(pal.glass).padding(horizontal = 8.dp),
          verticalAlignment = Alignment.CenterVertically) {
          AppIcon(a.name, a.domain, 30.dp); Spacer(Modifier.width(8.dp))
          T(a.handle?.let { Store.handleText(it) } ?: a.name, 13f, FontWeight.Medium, lines = 1)
        }
      }
    }
  }
}

@Composable
fun MiniCard(i: Identity, w: Dp, h: Dp) {
  val n = Store.accountsOf(i.id).size
  val c = hex(i.color)
  Column(Modifier.size(w, h).shadow(10.dp, CardShape(i.isPhone), ambientColor = c, spotColor = c).skin(i.color, CardShape(i.isPhone)).padding(horizontal = 11.dp),
    verticalArrangement = Arrangement.Center) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.graphicsLayer { alpha = 0.85f }) {
      Icon(if (i.isPhone) Icons.Filled.SimCard else Icons.Filled.Email, null, Modifier.size(12.dp), tint = Color.White)
      Spacer(Modifier.width(5.dp)); T("$n", 13f, FontWeight.Bold, Color.White)
    }
    T(Store.title(i), 14f, FontWeight.SemiBold, Color.White, lines = 1)
    if (i.label.isNotEmpty()) T(Store.display(i), 11f, color = Color.White.copy(alpha = 0.75f), lines = 1)
  }
}
