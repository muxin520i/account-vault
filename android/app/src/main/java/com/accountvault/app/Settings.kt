package com.accountvault.app

import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.zIndex
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.PlaylistAdd
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.roundToInt

@Composable
fun SettingRow(icon: ImageVector?, title: String, color: Color? = null, trailing: (@Composable () -> Unit)? = null, chevron: Boolean = false, onClick: (() -> Unit)? = null) {
  Row(Modifier.fillMaxWidth().then(if (onClick != null) Modifier.press(1f, onClick = onClick) else Modifier).padding(horizontal = 16.dp, vertical = 13.dp),
    verticalAlignment = Alignment.CenterVertically) {
    if (icon != null) { Icon(icon, null, Modifier.size(22.dp), tint = color ?: pal.accent); Spacer(Modifier.width(14.dp)) }
    T(title, 17f, color = color ?: pal.primary, modifier = Modifier.weight(1f))
    trailing?.invoke()
    if (chevron) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = pal.tertiary)
  }
}

fun syncStatus(): String = when (Sync.state) {
  "off" -> "未开启"; "syncing" -> "同步中…"; "error" -> "出错了"
  else -> if (Sync.at > 0) "已同步 " + DateUtils.getRelativeTimeSpanString(Sync.at.toLong(), System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS) else "已开启"
}

@Composable
fun SettingsSheet(inTab: Boolean = false) {
  var page by remember { mutableStateOf("main") }
  AnimatedContent(page, transitionSpec = {
    if (targetState != "main") (slideInHorizontally(calm()) { it } + fadeIn()) togetherWith (slideOutHorizontally(calm()) { -it / 3 } + fadeOut())
    else (slideInHorizontally(calm()) { -it / 3 } + fadeIn()) togetherWith (slideOutHorizontally(calm()) { it } + fadeOut())
  }, label = "settings") { p ->
    when (p) {
      "sync" -> SyncPage { page = "main" }
      "order" -> OrderPage { page = "main" }
      else -> SettingsMain(inTab, go = { page = it })
    }
  }
}

@Composable
fun SettingsMain(inTab: Boolean = false, go: (String) -> Unit) {
  val ctx = LocalContext.current
  var themeMenu by remember { mutableStateOf(false) }
  val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
    if (uri != null) runCatching { ctx.contentResolver.openOutputStream(uri)!!.use { it.write(Store.backupJSON().toByteArray()) } }
      .onSuccess { Store.show("备份已导出") }.onFailure { Store.show("导出失败") }
  }
  val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
    if (uri != null) {
      val text = runCatching { ctx.contentResolver.openInputStream(uri)!!.use { String(it.readBytes()) } }.getOrNull()
      if (text != null && Store.importBackup(text)) Store.popAll() else Store.show("这不是账号仓库的备份文件")
    }
  }
  Column {
    SheetBar(null, "设置", if (inTab) null else "完成") { Store.pop() }
    FormScroll {
      FormGroup {
        SettingRow(Icons.Outlined.Cloud, "加密同步", trailing = { T(syncStatus(), 14f, color = pal.secondary) }, chevron = true) { go("sync") }
      }
      FormGroup {
        SettingRow(Icons.Outlined.VisibilityOff, "隐藏号码", trailing = {
          Switch(Store.privacy, { Store.putPrivacy(it) }, colors = SwitchDefaults.colors(checkedTrackColor = hex("#34c759"), checkedThumbColor = Color.White, checkedBorderColor = Color.Transparent))
        }) { Store.putPrivacy(!Store.privacy) }
        Divider()
        Box {
          SettingRow(Icons.Outlined.Contrast, "外观", trailing = { T(mapOf("dark" to "深色", "light" to "浅色", "system" to "跟随系统")[Store.theme] ?: "深色", 17f, color = pal.secondary) }) { themeMenu = true }
          DropdownMenu(themeMenu, { themeMenu = false }, Modifier.align(Alignment.TopEnd)) {
            listOf("dark" to "深色", "light" to "浅色", "system" to "跟随系统").forEach { (k, n) ->
              DropdownMenuItem({ Text(n) }, { themeMenu = false; Store.putTheme(k) })
            }
          }
        }
        Divider()
        SettingRow(Icons.Outlined.SwapVert, "调整卡片顺序", chevron = true) { go("order") }
      }
      FormGroup(footer = "备份是 JSON 文件，和网页版、iOS 版通用。") {
        SettingRow(Icons.Outlined.Upload, "导出备份") { exporter.launch("账号备份-${LocalDate.now()}.json") }
        Divider()
        SettingRow(Icons.Outlined.Download, "导入备份") { importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
        if (!Sync.on) {
          Divider()
          SettingRow(Icons.Outlined.PlaylistAdd, "载入示例") { Store.loadDemo() }
        }
      }
      FormGroup(footer = "在 GitHub Releases 下载，直接覆盖安装，数据不会丢失。") {
        val ver = remember { runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() }
        val uri = LocalUriHandler.current
        SettingRow(Icons.Outlined.NewReleases, "下载新版本", trailing = { if (ver != null) T("当前 $ver", 14f, color = pal.secondary) }, chevron = true) {
          runCatching { uri.openUri("https://github.com/muxin520i/account-vault/releases/latest") }
        }
      }
      Column(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        T("账号仓库", 15f, FontWeight.SemiBold)
        T("不保存任何密码 · 数据只在本机和你自己的私有 Gist", 12f, color = pal.secondary, modifier = Modifier.padding(top = 6.dp), align = TextAlign.Center)
      }
    }
  }
}

/** Long-press a row and drag it; the first card in each group shows first in the wallet. */
@Composable
fun OrderPage(back: () -> Unit) {
  Column {
    SheetBar("设置", "卡片顺序", null, onLeft = back, leftIcon = true)
    FormScroll {
      // the hint goes under the last group that has cards
      val last = listOf("login", "phone", "email").firstOrNull { t -> Store.data.identities.any { it.type == t } }
      val hint = "长按拖动排序，排在第一位的卡会在卡包里最先出现。"
      OrderGroup("email", "邮箱", if (last == "email") hint else null)
      OrderGroup("phone", "手机号", if (last == "phone") hint else null)
      OrderGroup("login", "登录", if (last == "login") hint else null)
    }
  }
}

@Composable
fun OrderGroup(type: String, title: String, footer: String?) {
  val list = Store.data.identities.filter { it.type == type }
  if (list.isEmpty()) return
  val hap = haptics()
  val scope = rememberCoroutineScope()
  val rowH = 58.dp
  val slot = with(LocalDensity.current) { rowH.toPx() }
  val ids = list.map { it.id }
  // Local order; rows never move in the layout while a finger is down. Neighbours slide out of the
  // way with a translation, the lifted row follows the finger, and the new order is committed once,
  // after the lifted row has settled into its slot (same frame the translations are dropped).
  var order by remember { mutableStateOf(ids) }
  var dragId by remember { mutableStateOf<String?>(null) }
  var settling by remember { mutableStateOf(false) }
  var from by remember { mutableIntStateOf(0) }
  var to by remember { mutableIntStateOf(0) }
  var dy by remember { mutableFloatStateOf(0f) }
  LaunchedEffect(ids) { if (dragId == null) order = ids }
  FormGroup(title, footer) {
    order.forEachIndexed { k, id ->
      key(id) {
        val i = list.firstOrNull { it.id == id }
        if (i != null) {
          val dragging = dragId == id
          val target = when {
            dragId == null || dragging -> 0f
            k in (from + 1)..to -> -slot
            k in to until from -> slot
            else -> 0f
          }
          val shift = remember { Animatable(0f) }
          LaunchedEffect(target, dragId) { if (dragId == null) shift.snapTo(0f) else shift.animateTo(target, calm(Spring.StiffnessMedium)) }
          val lift by animateFloatAsState(if (dragging && !settling) 1.03f else 1f, calm(), label = "lift")
          val shadow by animateFloatAsState(if (dragging && !settling) 14f else 0f, calm(), label = "shadow")
          val sep = pal.stroke
          Row(Modifier.fillMaxWidth().height(rowH).zIndex(if (dragging) 1f else 0f)
            .graphicsLayer {
              translationY = if (dragId == null) 0f else if (dragging) dy else shift.value
              scaleX = lift; scaleY = lift
              shadowElevation = shadow; shape = RoundedCornerShape(12.dp); clip = false
            }
            .background(if (dragging) pal.field else Color.Transparent, RoundedCornerShape(12.dp))
            .drawBehind { if (k > 0 && dragId == null) drawRect(sep, Offset(16.dp.toPx(), 0f), Size(size.width - 16.dp.toPx(), 0.5.dp.toPx())) }
            .pointerInput(id) {
              detectDragGesturesAfterLongPress(
                onDragStart = {
                  if (dragId == null && !settling) {
                    from = order.indexOf(id); to = from; dy = 0f; dragId = id
                    hap(android.view.HapticFeedbackConstants.LONG_PRESS)
                  }
                },
                onDragEnd = {
                  if (dragId == id && !settling) {
                    settling = true
                    scope.launch {
                      animate(dy, (to - from) * slot, animationSpec = calm(Spring.StiffnessMedium)) { v, _ -> dy = v }
                      val next = order.toMutableList().apply { removeAt(from); add(to, id) }
                      val moved = from != to
                      order = next; dragId = null; dy = 0f; settling = false
                      if (moved) Store.mutate(null) { s ->
                        val ranked = next.mapIndexed { n, x -> x to n.toDouble() }.toMap()
                        val updated = s.identities.map { if (it.id in ranked) it.copy(order = ranked[it.id]) else it }
                        val grp = next.mapNotNull { x -> updated.firstOrNull { it.id == x } }
                        val others = updated.filter { it.type != type }
                        val groups = KINDS.map { t -> if (type == t) grp else others.filter { it.type == t } }
                        s.copy(identities = groups.flatten() + others.filter { it.type !in KINDS })
                      }
                    }
                  }
                },
                onDragCancel = {
                  if (dragId == id && !settling) {
                    settling = true
                    scope.launch { animate(dy, 0f, animationSpec = calm(Spring.StiffnessMedium)) { v, _ -> dy = v }; to = from; dragId = null; dy = 0f; settling = false }
                  }
                },
              ) { ch, d ->
                if (dragId != id || settling) return@detectDragGesturesAfterLongPress
                ch.consume()
                dy = (dy + d.y).coerceIn(-from * slot, (order.size - 1 - from) * slot)
                val n = (from + (dy / slot).roundToInt()).coerceIn(0, order.size - 1)
                if (n != to) { to = n; hap(TICK) }
              }
            }
            .padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            SmallCard(i, kindIcon(i), 46.dp, 30.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
              T(Store.title(i), 16f, FontWeight.Medium, lines = 1)
              if (i.label.isNotEmpty() || i.isLogin) T(Store.display(i), 13f, color = pal.secondary, lines = 1)
            }
            Icon(Icons.Filled.DragHandle, "拖动", tint = pal.tertiary)
          }
        }
      }
    }
  }
}

@Composable
fun SyncPage(back: () -> Unit) {
  var prov by remember { mutableStateOf("gitee") }
  var token by remember { mutableStateOf("") }
  var pass by remember { mutableStateOf("") }
  var busy by remember { mutableStateOf(false) }
  var error by remember { mutableStateOf("") }
  var askOff by remember { mutableStateOf(false) }
  val scope = rememberCoroutineScope()
  val uri = LocalUriHandler.current
  Column {
    SheetBar("设置", "加密同步", null, onLeft = back, leftIcon = true)
    FormScroll {
      if (Sync.on) {
        FormGroup {
          Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            val (ic, c) = when (Sync.state) { "error" -> Icons.Outlined.ErrorOutline to hex("#ff9f0a"); "syncing" -> Icons.Outlined.CloudSync to pal.accent; else -> Icons.Outlined.CheckCircle to pal.accent }
            Icon(ic, null, Modifier.size(32.dp), tint = c)
            Spacer(Modifier.width(12.dp))
            Column {
              T(if (Sync.state == "error") Sync.err else if (Sync.state == "syncing") "正在同步…" else "已开启加密同步", 16f, FontWeight.SemiBold)
              T("存放在 " + (if (Sync.provider == "gitee") "Gitee" else "GitHub") + " 私有 Gist" +
                (if (Sync.at > 0) " · " + java.text.SimpleDateFormat("HH:mm", java.util.Locale.CHINA).format(java.util.Date(Sync.at.toLong())) else ""), 13f, color = pal.secondary)
            }
          }
          Divider()
          SettingRow(null, "立即同步", color = if (Sync.state == "syncing") pal.tertiary else pal.accent) { if (Sync.state != "syncing") Sync.runNow() }
        }
        FormGroup(footer = "关闭后这台设备不再同步，云端和本机的数据都会保留。") {
          SettingRow(null, "关闭同步", color = pal.red) { askOff = true }
        }
      } else {
        FormGroup("存放在") {
          listOf("gitee" to "Gitee · 国内直连", "github" to "GitHub").forEachIndexed { k, (v, n) ->
            if (k > 0) Divider()
            SettingRow(null, n, trailing = { if (prov == v) Icon(Icons.Outlined.CheckCircle, null, tint = pal.accent) }) { prov = v }
          }
        }
        FormGroup(footer = "数据先用同步密码加密，再存进你自己的私有 Gist，别人看不到内容。每台设备填同一个令牌和同步密码即可。同步密码忘了就解不开。") {
          SecretField(token, { token = it }, if (prov == "gitee") "Gitee 私人令牌（勾选 gists）" else "GitHub 令牌（gist 权限）")
          Divider()
          SecretField(pass, { pass = it }, "同步密码")
          Divider()
          SettingRow(null, if (prov == "gitee") "去 Gitee 创建令牌" else "去 GitHub 创建令牌", color = pal.accent) {
            uri.openUri(if (prov == "gitee") "https://gitee.com/profile/personal_access_tokens/new" else "https://github.com/settings/tokens/new?scopes=gist&description=%E8%B4%A6%E5%8F%B7%E6%98%9F%E5%9B%BE")
          }
        }
        if (error.isNotEmpty()) FormGroup { T(error, 15f, color = pal.red, modifier = Modifier.padding(16.dp)) }
        FormGroup {
          val ok = !busy && token.isNotBlank() && pass.length >= 4
          Box(Modifier.fillMaxWidth().height(50.dp).press(1f, enabled = ok) {
            busy = true; error = ""
            scope.launch {
              try {
                val found = Sync.enable(token, pass, prov)
                Store.setKind(Store.kind)
                Store.show(if (found) "已连接，云端数据已合并" else "同步已开启")
              } catch (e: Exception) { error = e.message ?: "同步失败" }
              busy = false
            }
          }, contentAlignment = Alignment.Center) {
            if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            else T("开启同步", 17f, FontWeight.SemiBold, if (ok) pal.accent else pal.tertiary)
          }
        }
      }
    }
  }
  if (askOff) Confirm("关闭同步？", "关闭后这台设备不再同步，云端和本机的数据都会保留。", "关闭同步", { askOff = false }) { Sync.disable() }
}

@Composable
fun SecretField(value: String, onChange: (String) -> Unit, hint: String) {
  Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
    if (value.isEmpty()) T(hint, 17f, color = pal.tertiary, lines = 1)
    androidx.compose.foundation.text.BasicTextField(value, onChange, Modifier.fillMaxWidth(), singleLine = true,
      textStyle = androidx.compose.ui.text.TextStyle(color = pal.primary, fontSize = androidx.compose.ui.unit.TextUnit(17f, androidx.compose.ui.unit.TextUnitType.Sp)),
      cursorBrush = androidx.compose.ui.graphics.SolidColor(pal.accent), visualTransformation = PasswordVisualTransformation(),
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false))
  }
}

