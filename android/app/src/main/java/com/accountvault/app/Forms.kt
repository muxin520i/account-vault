package com.accountvault.app

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// MARK: - form kit (grouped, like an iOS Form)

@Composable
fun SheetBar(left: String?, title: String, right: String?, rightEnabled: Boolean = true, onLeft: () -> Unit = { Store.pop() }, leftIcon: Boolean = false, onRight: () -> Unit = {}) {
  Box(Modifier.fillMaxWidth().padding(top = 18.dp).height(44.dp).padding(horizontal = 8.dp)) {
    if (left != null) Row(Modifier.align(Alignment.CenterStart).press(0.94f, onClick = onLeft).padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
      if (leftIcon) Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null, tint = pal.accent)
      T(left, 17f, color = pal.accent)
    }
    T(title, 17f, FontWeight.SemiBold, modifier = Modifier.align(Alignment.Center))
    if (right != null) T(right, 17f, FontWeight.SemiBold, if (rightEnabled) pal.accent else pal.tertiary,
      Modifier.align(Alignment.CenterEnd).press(0.94f, enabled = rightEnabled, onClick = onRight).padding(horizontal = 8.dp, vertical = 6.dp))
  }
}

@Composable
fun FormGroup(header: String? = null, footer: String? = null, clear: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
  Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 14.dp)) {
    if (header != null) T(header, 13f, color = pal.secondary, modifier = Modifier.padding(start = 16.dp, bottom = 6.dp))
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (clear) Color.Transparent else pal.field), content = content)
    if (footer != null) T(footer, 13f, color = pal.secondary, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp))
  }
}

@Composable
fun Divider() { Box(Modifier.fillMaxWidth().padding(start = 16.dp).height(0.5.dp).background(pal.stroke)) }

@Composable
fun Field(value: String, onChange: (String) -> Unit, hint: String, kb: KeyboardType = KeyboardType.Text, lines: Int = 1, size: Float = 17f, weight: FontWeight = FontWeight.Normal, color: Color? = null, raw: Boolean = false) {
  Box(Modifier.fillMaxWidth().padding(horizontal = if (raw) 0.dp else 16.dp, vertical = if (raw) 2.dp else 12.dp)) {
    if (value.isEmpty()) T(hint, size, weight, pal.tertiary, lines = 1)
    BasicTextField(value, onChange, Modifier.fillMaxWidth(), singleLine = lines == 1, minLines = 1, maxLines = if (lines == 1) 1 else 6,
      textStyle = TextStyle(color = color ?: pal.primary, fontSize = size.sp, fontWeight = weight), cursorBrush = SolidColor(pal.accent),
      keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = kb == KeyboardType.Text && lines > 1, keyboardType = kb))
  }
}

@Composable
fun Segmented(options: List<Pair<String, String>>, selected: String, onPick: (String) -> Unit) {
  val hap = haptics()
  BoxWithConstraints(Modifier.fillMaxWidth().height(34.dp).clip(RoundedCornerShape(9.dp)).background(pal.glass).padding(2.dp)) {
    val w = maxWidth / options.size
    val x by animateDpAsState(w * options.indexOfFirst { it.first == selected }.coerceAtLeast(0), calm(), label = "seg")
    Box(Modifier.offset(x = x).width(w).fillMaxSize().clip(RoundedCornerShape(7.dp)).background(if (pal.dark) hex("#636366") else Color.White))
    Row(Modifier.fillMaxSize()) {
      options.forEach { (k, name) ->
        Box(Modifier.weight(1f).fillMaxSize().press(1f) { if (k != selected) { hap(TICK); onPick(k) } }, contentAlignment = Alignment.Center) {
          T(name, 14f, FontWeight.SemiBold)
        }
      }
    }
  }
}

@Composable
fun IdentityToggles(selected: List<String>, onChange: (List<String>) -> Unit) {
  val hap = haptics()
  Store.data.identities.forEachIndexed { k, i ->
    if (k > 0) Divider()
    val on = i.id in selected
    val sc by animateFloatAsState(if (on) 1f else 0.85f, calm(), label = "chk")
    Row(Modifier.fillMaxWidth().press(1f) { hap(TICK); onChange(if (on) selected - i.id else selected + i.id) }.padding(horizontal = 16.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically) {
      SmallCard(i, if (i.isPhone) Icons.Filled.SimCard else Icons.Filled.Email, 36.dp, 24.dp)
      Spacer(Modifier.width(12.dp))
      Column(Modifier.weight(1f)) {
        T(Store.title(i), 16f)
        if (i.label.isNotEmpty()) T(Store.display(i), 12f, color = pal.secondary)
      }
      Icon(if (on) Icons.Filled.CheckCircle else Icons.Outlined.Circle, null, Modifier.size(24.dp).graphicsLayer { scaleX = sc; scaleY = sc },
        tint = if (on) hex(i.color) else pal.secondary.copy(alpha = 0.5f))
    }
  }
}

@Composable
fun FormScroll(content: @Composable ColumnScope.() -> Unit) {
  Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 30.dp), content = content)
}

// MARK: - identity form

@Composable
fun IdentityForm(existing: Identity?, type0: String, defaultColor: String) {
  var type by remember { mutableStateOf(existing?.type ?: type0) }
  var value by remember { mutableStateOf(existing?.value ?: "") }
  var label by remember { mutableStateOf(existing?.label ?: "") }
  var color by remember { mutableStateOf(existing?.color ?: defaultColor) }
  var notes by remember { mutableStateOf(existing?.notes ?: "") }
  val hap = haptics()
  val preview = Identity(existing?.id ?: "_new", type, value.ifEmpty { if (type == "email") "name@example.com" else "+86 138 0000 0000" }, label, color, notes, order = existing?.order)

  fun save() {
    val v = value.trim()
    if (existing != null) {
      Store.mutate("已保存") { s -> s.copy(identities = s.identities.map { if (it.id == existing.id) it.copy(type = type, value = v, label = label.trim(), color = color, notes = notes.trim()) else it }) }
      Store.setKind(type, existing.id)
    } else {
      val i = Identity(newID(), type, v, label.trim(), color, notes.trim())
      Store.mutate("已添加") { s -> s.copy(identities = s.identities + i) }
      Store.setKind(type, i.id)
    }
    Store.pop()
  }

  Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(hex(color).copy(alpha = 0.18f), Color.Transparent)))) {
    Column {
      SheetBar("取消", if (existing == null) "新身份" else "编辑", if (existing == null) "添加" else "保存", value.isNotBlank(), onRight = ::save)
      FormScroll {
        Box(Modifier.padding(horizontal = 26.dp).padding(top = 10.dp)) {
          AnimatedContent(type, transitionSpec = { fadeIn(calm()) togetherWith fadeOut(calm()) }, label = "pv") { WalletCard(preview.copy(type = it)) }
        }
        Box(Modifier.padding(horizontal = 16.dp).padding(top = 18.dp)) {
          Segmented(listOf("email" to "邮箱", "phone" to "手机号"), type) { type = it }
        }
        FormGroup(if (type == "email") "地址" else "号码") {
          Field(value, { value = it }, if (type == "email") "name@example.com" else "+86 138 0000 0000", if (type == "email") KeyboardType.Email else KeyboardType.Phone)
          Divider()
          Field(label, { label = it }, "标签：主力、小号、工作…")
        }
        FormGroup("卡面") {
          Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SKINS.chunked(5).forEach { row ->
              Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (c, s) ->
                  val on = color == c
                  Column(Modifier.weight(1f).press(0.92f) { if (!on) hap(TICK); color = c }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.fillMaxWidth().height(40.dp).border(if (on) 2.5.dp else 0.dp, if (on) pal.primary else Color.Transparent, RoundedCornerShape(11.dp)).padding(3.dp)
                      .skin(c, RoundedCornerShape(8.dp)))
                    T(s.name, 11f, color = if (on) pal.primary else pal.secondary, modifier = Modifier.padding(top = 4.dp))
                  }
                }
              }
            }
          }
        }
        FormGroup("备注", "不要在这里填写密码。") {
          Field(notes, { notes = it }, "运营商、是否实名、找回方式", lines = 4)
        }
      }
    }
  }
}

// MARK: - account form

@Composable
fun AccountForm(existing: Account) {
  var name by remember { mutableStateOf(existing.name) }
  var domain by remember { mutableStateOf(existing.domain) }
  var category by remember { mutableStateOf(existing.category) }
  var handle by remember { mutableStateOf(existing.handle ?: "") }
  var notes by remember { mutableStateOf(existing.notes) }
  var ids by remember { mutableStateOf(existing.identityIds) }
  var catMenu by remember { mutableStateOf(false) }
  Column {
    SheetBar("取消", "编辑", "保存", name.isNotBlank()) {
      Store.mutate("已保存") { s -> s.copy(accounts = s.accounts.map {
        if (it.id == existing.id) it.copy(name = name.trim(), domain = cleanDomain(domain), category = category, notes = notes.trim(), handle = handle.trim().ifEmpty { null }, identityIds = ids) else it
      }) }
      Store.pop()
    }
    FormScroll {
      FormGroup {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
          AppIcon(name, domain, 54.dp)
          Spacer(Modifier.width(14.dp))
          Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Field(name, { name = it }, "名称", size = 18f, weight = FontWeight.SemiBold, raw = true)
            Field(domain, { domain = it }, "域名（可选）", KeyboardType.Uri, color = pal.secondary, raw = true)
          }
        }
        Divider()
        Field(handle, { handle = it }, "账号名（可选）")
        Divider()
        Box {
          Row(Modifier.fillMaxWidth().press(1f) { catMenu = true }.padding(horizontal = 16.dp, vertical = 12.dp)) {
            T("分类", 17f, modifier = Modifier.weight(1f)); T(category, 17f, color = pal.secondary)
          }
          DropdownMenu(catMenu, { catMenu = false }) {
            CATS.forEach { c -> DropdownMenuItem({ Text(c) }, { category = c; catMenu = false }) }
          }
        }
      }
      FormGroup("用的邮箱和手机号") { IdentityToggles(ids) { ids = it } }
      FormGroup("备注") { Field(notes, { notes = it }, "两步验证、找回方式…", lines = 4) }
    }
  }
}

// MARK: - add apps: pick sites, then pick which email / phone each uses

@Composable
fun PickerSheet(presetIdentity: String?) {
  var query by remember { mutableStateOf("") }
  var cat by remember { mutableStateOf("常用") }
  val picked = remember { mutableStateListOf<Preset>() }
  val handles = remember { mutableStateMapOf<String, String>() }
  var ids by remember { mutableStateOf(listOfNotNull(presetIdentity)) }
  var step2 by remember { mutableStateOf(false) }
  var manual by remember { mutableStateOf(false) }
  val hap = haptics()
  fun key(p: Preset) = p.name.lowercase()
  val custom = Store.customApps

  fun save() {
    val now = nowMS()
    val made = picked.mapIndexed { k, p ->
      Account(newID(), p.name, p.domain, p.category, ids, handle = handles[key(p)]?.trim()?.ifEmpty { null }, createdAt = now - k)
    }
    Store.mutate(if (made.size == 1) "已添加 ${made[0].name}" else "已添加 ${made.size} 个网站与 App") { s -> s.copy(accounts = s.accounts + made) }
    Store.pop()
  }

  AnimatedContent(step2, transitionSpec = {
    if (targetState) (slideInHorizontally(calm()) { it } + fadeIn()) togetherWith (slideOutHorizontally(calm()) { -it / 3 } + fadeOut())
    else (slideInHorizontally(calm()) { -it / 3 } + fadeIn()) togetherWith (slideOutHorizontally(calm()) { it } + fadeOut())
  }, label = "step") { s2 ->
    if (!s2) {
      val cats = (if (custom.isEmpty()) emptyList() else listOf("我添加的")) + "常用" + CATS.filter { c -> Presets.all.any { it.category == c } }
      val q = query.trim().lowercase()
      val all = custom + Presets.all
      val list = if (q.isNotEmpty()) all.filter { it.name.lowercase().contains(q) || it.domain.contains(q) } else when (cat) {
        "我添加的" -> custom
        "常用" -> custom.take(4) + Presets.popular.mapNotNull { n -> Presets.all.firstOrNull { it.name == n } }
        else -> Presets.all.filter { it.category == cat }
      }
      Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
          SheetBar("取消", "添加网站或 App", if (picked.isEmpty()) "下一步" else "下一步 (${picked.size})", picked.isNotEmpty()) { step2 = true }
          SearchField(query, "搜索 App 或网站", Modifier.padding(horizontal = 16.dp).padding(bottom = 10.dp)) { query = it }
          Chips(cats.map { it to null }, if (q.isEmpty()) cat else null) { cat = it; query = "" }
          LazyVerticalGrid(GridCells.Fixed(4), Modifier.weight(1f).imePadding(), contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp, 14.dp, 12.dp, 110.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(list, key = { "t-" + key(it) }) { p ->
              val on = picked.any { key(it) == key(p) }
              val has = Store.data.accounts.any { it.name.lowercase() == p.name.lowercase() }
              val sc by animateFloatAsState(if (on) 0.92f else 1f, calm(), label = "pk")
              Column(Modifier.press(1f) { hap(TICK); if (on) picked.removeAll { key(it) == key(p) } else picked.add(p) }, horizontalAlignment = Alignment.CenterHorizontally) {
                Box {
                  AppIcon(p.name, p.domain, 60.dp, Modifier.graphicsLayer { scaleX = sc; scaleY = sc })
                  androidx.compose.animation.AnimatedVisibility(on, Modifier.align(Alignment.TopEnd).offset(7.dp, (-7).dp), enter = scaleIn(calm()) + fadeIn(), exit = scaleOut(calm()) + fadeOut()) {
                    Box(Modifier.size(24.dp).clip(CircleShape).background(Color.White).padding(1.dp)) { Icon(Icons.Filled.CheckCircle, null, tint = pal.accent) }
                  }
                }
                T(p.name, 12f, lines = 1, modifier = Modifier.padding(top = 6.dp))
                if (has) T("已记录", 9.5f, FontWeight.Medium, pal.secondary)
              }
            }
            item(key = "manual") {
              Column(Modifier.press(0.9f) { manual = true }, horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(60.dp).glass(RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Filled.Add, null, tint = pal.primary) }
                T(if (query.isEmpty()) "手动添加" else "添加“$query”", 12f, color = pal.secondary, lines = 1, modifier = Modifier.padding(top = 6.dp))
              }
            }
          }
        }
        AnimatedVisibility(picked.isNotEmpty(), Modifier.align(Alignment.BottomCenter), enter = slideInVertically(calm()) { it } + fadeIn(), exit = slideOutVertically(calm()) { it } + fadeOut()) {
          Row(Modifier.fillMaxWidth().background(if (pal.dark) hex("#1c1c1e").copy(alpha = 0.96f) else Color.White.copy(alpha = 0.96f))
            .navigationBarsPadding().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            picked.forEach { p -> AppIcon(p.name, p.domain, 38.dp) }
          }
        }
      }
      if (manual) ManualAdd(query, { manual = false }) { n, d ->
        val p = Preset(n, cleanDomain(d), presetFor(n, d)?.category ?: "其他", "")
        Store.rememberApp(p)
        if (picked.none { key(it) == key(p) }) picked.add(p)
        query = ""
      }
    } else {
      Column {
        SheetBar("返回", "选择身份", "保存", onLeft = { step2 = false }, leftIcon = true) { save() }
        FormScroll {
          FormGroup("${picked.size} 个网站与 App", "同一个网站有几个账号，可以分别记一条，用账号名区分。") {
            picked.forEachIndexed { k, p ->
              if (k > 0) Divider()
              Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                AppIcon(p.name, p.domain, 36.dp); Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                  T(p.name, 16f, FontWeight.SemiBold)
                  Field(handles[key(p)] ?: "", { handles[key(p)] = it }, "账号名（可选）", size = 14f, raw = true)
                }
              }
            }
          }
          FormGroup("用哪个邮箱或手机号注册、绑定的") {
            if (Store.data.identities.isEmpty()) T("还没有邮箱或手机号，先保存，之后再关联也行。", 15f, color = pal.secondary, modifier = Modifier.padding(16.dp))
            IdentityToggles(ids) { ids = it }
          }
        }
      }
    }
  }
}

@Composable
fun ManualAdd(initial: String, onDismiss: () -> Unit, onAdd: (String, String) -> Unit) {
  var n by remember { mutableStateOf(initial) }
  var d by remember { mutableStateOf("") }
  AlertDialog(onDismissRequest = onDismiss, title = { Text("手动添加") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("名称必填，填了域名会自动找图标。")
        OutlinedTextField(n, { n = it }, label = { Text("名称") }, singleLine = true)
        OutlinedTextField(d, { d = it }, label = { Text("域名（可选）") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
      }
    },
    confirmButton = { TextButton({ if (n.isNotBlank()) { onAdd(n.trim(), d); onDismiss() } }, enabled = n.isNotBlank()) { Text("添加") } },
    dismissButton = { TextButton(onDismiss) { Text("取消") } })
}
