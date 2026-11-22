package com.accountvault.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun SheetScroll(aura: (@Composable () -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
  Box(Modifier.fillMaxSize()) {
    aura?.invoke()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(top = 44.dp, bottom = 30.dp), horizontalAlignment = Alignment.CenterHorizontally, content = content)
    Box(Modifier.align(Alignment.TopEnd).padding(12.dp).size(32.dp).press(0.9f) { Store.pop() }.clip(CircleShape).background(pal.glass), contentAlignment = Alignment.Center) {
      Icon(Icons.Filled.Close, "关闭", Modifier.size(18.dp), tint = pal.secondary)
    }
  }
}

@Composable
fun SectionLabel(s: String) {
  T(s, 13f, FontWeight.Medium, pal.secondary, Modifier.fillMaxWidth().padding(horizontal = 36.dp).padding(top = 26.dp, bottom = 8.dp))
}

@Composable
fun ActionTile(t: String, icon: ImageVector, modifier: Modifier, destructive: Boolean = false, disabled: Boolean = false, fn: () -> Unit) {
  val c = if (destructive) pal.red else pal.primary
  Column(modifier.height(66.dp).graphicsLayer { alpha = if (disabled) 0.35f else 1f }.press(enabled = !disabled, onClick = fn).glass(RoundedCornerShape(18.dp)),
    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
    Icon(icon, null, Modifier.size(22.dp), tint = c); Spacer(Modifier.height(6.dp)); T(t, 13f, FontWeight.Medium, c)
  }
}

@Composable
fun Confirm(title: String, message: String, action: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
  AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(message) },
    confirmButton = { TextButton({ onDismiss(); onConfirm() }) { Text(action, color = pal.red) } },
    dismissButton = { TextButton(onDismiss) { Text("取消") } })
}

@Composable
fun IdentityDetail(id: String) {
  val i = Store.identity(id) ?: return
  val accs = Store.accountsOf(id)
  val ctx = LocalContext.current
  val hap = haptics()
  var askDelete by remember { mutableStateOf(false) }
  SheetScroll(aura = {
    Box(Modifier.fillMaxWidth().height(420.dp).fadeDown().blur(70.dp).graphicsLayer { alpha = 0.55f }.skin(i.color, RoundedCornerShape(0.dp)))
  }) {
    WalletCard(i, Modifier.padding(horizontal = 28.dp).padding(top = 8.dp))
    Row(Modifier.padding(horizontal = 20.dp).padding(top = 26.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      ActionTile("复制", Icons.Outlined.ContentCopy, Modifier.weight(1f)) {
        (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("账号", i.value))
        hap(CONFIRM); Store.show("已复制")
      }
      ActionTile("编辑", Icons.Outlined.Edit, Modifier.weight(1f)) { Store.push(Route.EditIdentity(i.id)) }
      ActionTile("删除", Icons.Outlined.Delete, Modifier.weight(1f), destructive = true) { askDelete = true }
    }
    if (i.notes.isNotEmpty()) {
      SectionLabel("备注")
      T(i.notes, 16f, modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth().glass(RoundedCornerShape(16.dp)).padding(14.dp))
    }
    if (i.isLogin) {
      // the email / phone this 微信 / Apple 账户 is bound to
      Store.viaOf(i)?.let { v ->
        SectionLabel("绑定")
        LinkRow(v, Store.title(v), if (v.label.isNotEmpty()) Store.display(v) else if (v.isPhone) "手机号" else "邮箱")
      }
    } else {
      val logins = Store.loginsVia(i.id)
      if (logins.isNotEmpty()) {
        SectionLabel("第三方登录")
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          logins.forEach { l -> LinkRow(l, "${providerName(l.provider)} 绑定了这个号", Store.display(l), padded = false) }
        }
      }
    }
    SectionLabel(if (i.isLogin) "用它登录的 ${accs.size} 个网站与 App" else "用它注册或绑定的 ${accs.size} 个网站与 App")
    Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      accs.forEach { a -> ReelRow(a) { Store.push(Route.AccountR(a.id)) } }
      Row(Modifier.fillMaxWidth().height(52.dp).press(0.98f) { Store.push(Route.Picker(i.id)) }.glass(RoundedCornerShape(18.dp)),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Add, null, tint = pal.accent); Spacer(Modifier.width(6.dp)); T("添加网站或 App", 16f, FontWeight.SemiBold, pal.accent)
      }
    }
    // sites reached through a 微信 / Apple card bound to this number: they are affected when it changes too
    if (!i.isLogin) Store.indirectAccounts(i.id).forEach { (l, list) ->
      SectionLabel("通过${if (l.isApple) " Apple " else "微信"}登录的 ${list.size} 个网站与 App")
      Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        list.forEach { a -> ReelRow(a) { Store.push(Route.AccountR(a.id)) } }
      }
    }
  }
  if (askDelete) Confirm("删除 ${Store.title(i)}？", if (accs.isEmpty()) "删除后可以撤销。" else "它会从 ${accs.size} 个网站与 App 的记录里移除，这些网站本身会保留。", "删除", { askDelete = false }) {
    Store.mutate("已删除") { s -> s.copy(identities = s.identities.filter { it.id != i.id }.map { if (it.via == i.id) it.copy(via = null) else it }, accounts = s.accounts.map { a -> a.copy(identityIds = a.identityIds.filter { it != i.id }) }) }
    Store.setKind(Store.kind)
    Store.pop()
  }
}

@Composable
fun AccountDetail(id: String) {
  val a = Store.account(id) ?: return
  val d = cleanDomain(a.domain); val b = brandColor(a.name, a.domain)
  val ctx = LocalContext.current
  var askDelete by remember { mutableStateOf(false) }
  SheetScroll(aura = {
    Box(Modifier.fillMaxWidth().height(360.dp).fadeDown().blur(90.dp).graphicsLayer { alpha = 0.45f }.background(Brush.verticalGradient(listOf(b, b.copy(alpha = 0.3f), Color.Transparent))))
  }) {
    AppIcon(a, 96.dp, Modifier.padding(top = 10.dp).shadow(24.dp, squircle(96.dp), ambientColor = b, spotColor = b))
    T(a.name, 26f, FontWeight.Bold, modifier = Modifier.padding(top = 14.dp))
    a.handle?.let { T(Store.handleText(it), 15f, color = pal.secondary, modifier = Modifier.padding(top = 2.dp)) }
    Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      Tag(a.category); if (d.isNotEmpty()) Tag(d)
    }
    Row(Modifier.padding(horizontal = 20.dp).padding(top = 24.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      ActionTile("打开网站", Icons.Outlined.Explore, Modifier.weight(1f), disabled = d.isEmpty()) {
        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://$d"))) }
      }
      ActionTile("编辑", Icons.Outlined.Edit, Modifier.weight(1f)) { Store.push(Route.EditAccount(a.id)) }
      ActionTile("删除", Icons.Outlined.Delete, Modifier.weight(1f), destructive = true) { askDelete = true }
    }
    SectionLabel(if (a.identityIds.any { Store.identity(it)?.isLogin == true }) "用的邮箱、手机号和第三方登录" else "用的邮箱和手机号")
    Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      val list = a.identityIds.mapNotNull { Store.identity(it) }
      if (list.isEmpty()) T("还没有关联，点编辑选一个。", 15f, color = pal.secondary, modifier = Modifier.padding(16.dp))
      list.forEach { i ->
        Row(Modifier.fillMaxWidth().press(0.98f) { Store.push(Route.IdentityR(i.id)) }.glass(RoundedCornerShape(18.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
          SmallCard(i, kindIcon(i), 40.dp, 28.dp)
          Spacer(Modifier.width(12.dp))
          Column(Modifier.weight(1f)) {
            T(Store.title(i), 16f, FontWeight.SemiBold)
            if (i.label.isNotEmpty() || i.isLogin) T(Store.display(i), 13f, color = pal.secondary)
          }
          Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = pal.tertiary)
        }
      }
    }
    if (a.notes.isNotEmpty()) {
      SectionLabel("备注")
      T(a.notes, 16f, modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth().glass(RoundedCornerShape(16.dp)).padding(14.dp))
    }
    T("添加于 " + dayOf(a.createdAt).format(DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA)), 12f, color = pal.tertiary, modifier = Modifier.padding(top = 24.dp))
  }
  if (askDelete) Confirm("删除 ${a.name}？", "只删除这条记录，不影响你在那个网站上的账号。", "删除", { askDelete = false }) {
    Store.mutate("已删除") { s -> s.copy(accounts = s.accounts.filter { it.id != a.id }) }
    Store.pop()
  }
}

/** A tappable row with a small card that opens that identity. */
@Composable
fun LinkRow(i: Identity, title: String, sub: String, padded: Boolean = true) {
  Row(Modifier.then(if (padded) Modifier.padding(horizontal = 20.dp) else Modifier).fillMaxWidth().press(0.98f) { Store.push(Route.IdentityR(i.id)) }
    .glass(RoundedCornerShape(18.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
    SmallCard(i, kindIcon(i), 40.dp, 28.dp)
    Spacer(Modifier.width(12.dp))
    Column(Modifier.weight(1f)) {
      T(title, 16f, FontWeight.SemiBold, lines = 1)
      if (sub.isNotEmpty()) T(sub, 13f, color = pal.secondary, lines = 1)
    }
    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = pal.tertiary)
  }
}

@Composable
fun Tag(s: String) {
  T(s, 12f, FontWeight.Medium, modifier = Modifier.clip(CircleShape).background(pal.glass).padding(horizontal = 9.dp, vertical = 4.dp))
}

/** Soft bottom edge (stands in for blur on Android < 12). */
fun Modifier.fadeDown() = this.graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
  .drawWithContent {
    drawContent()
    drawRect(Brush.verticalGradient(listOf(Color.Black, Color.Black.copy(alpha = 0.6f), Color.Transparent)), blendMode = androidx.compose.ui.graphics.BlendMode.DstIn)
  }
