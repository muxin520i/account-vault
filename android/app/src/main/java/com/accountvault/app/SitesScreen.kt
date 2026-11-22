package com.accountvault.app

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SearchField(q: String, hint: String, modifier: Modifier = Modifier, onChange: (String) -> Unit) {
  Row(modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(12.dp)).background(pal.glass).padding(horizontal = 10.dp),
    verticalAlignment = Alignment.CenterVertically) {
    Icon(Icons.Filled.Search, null, Modifier.size(18.dp), tint = pal.secondary)
    Spacer(Modifier.width(6.dp))
    Box(Modifier.weight(1f)) {
      if (q.isEmpty()) T(hint, 16f, color = pal.secondary, lines = 1)
      BasicTextField(q, onChange, singleLine = true, textStyle = TextStyle(color = pal.primary, fontSize = 16.sp), cursorBrush = SolidColor(pal.accent), modifier = Modifier.fillMaxWidth())
    }
    if (q.isNotEmpty()) Icon(Icons.Filled.Close, "清除", Modifier.size(18.dp).press(0.9f) { onChange("") }, tint = pal.secondary)
  }
}

@Composable
fun Chips(items: List<Pair<String, String?>>, selected: String?, onPick: (String) -> Unit) {
  val hap = haptics()
  Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    items.forEach { (c, n) ->
      val on = c == selected
      Row(Modifier.press(0.94f) { if (!on) hap(TICK); onPick(c) }.height(32.dp).clip(CircleShape).background(if (on) pal.accent else pal.glass).padding(horizontal = 13.dp),
        verticalAlignment = Alignment.CenterVertically) {
        T(c, 14f, FontWeight.SemiBold, if (on) Color.White else pal.primary)
        if (n != null) { Spacer(Modifier.width(4.dp)); T(n, 14f, FontWeight.SemiBold, if (on) Color.White.copy(alpha = 0.75f) else pal.secondary) }
      }
    }
  }
}

@Composable
fun SitesScreen() {
  var cat by rememberSaveable { mutableStateOf("全部") }
  var query by rememberSaveable { mutableStateOf("") }
  val all = Store.data.accounts
  val q = query.trim().lowercase()
  val filtered = if (q.isEmpty()) all else all.filter { a ->
    a.name.lowercase().contains(q) || a.domain.lowercase().contains(q) || (a.handle ?: "").lowercase().contains(q) || a.notes.lowercase().contains(q) ||
      a.identityIds.mapNotNull { Store.identity(it) }.any { it.value.lowercase().contains(q) || it.label.lowercase().contains(q) }
  }
  val have = all.map { it.category }.toSet()
  val cats = CATS.filter { it in have } + have.filter { it !in CATS }.sorted()
  val shown = if (cat == "全部" || cat !in cats) cats else listOf(cat)

  LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 110.dp)) {
    item { TopButtons() }
    item { LargeTitle("网站与 App") }
    item { SearchField(query, "搜索名称、域名、账号名", Modifier.padding(horizontal = 16.dp).padding(top = 10.dp, bottom = 10.dp)) { query = it } }
    if (all.isEmpty()) {
      item {
        Column(Modifier.fillMaxWidth().padding(40.dp).padding(top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Icon(Icons.Outlined.Apps, null, Modifier.size(48.dp), tint = pal.secondary)
          T("还没有网站", 20f, FontWeight.Bold)
          T("记下一个网站或 App，选上它用的是哪个邮箱、哪个手机号。", 15f, color = pal.secondary, align = TextAlign.Center)
          Box(Modifier.padding(top = 6.dp).press { Store.push(Route.Picker(null)) }.clip(CircleShape).background(pal.accent).padding(horizontal = 18.dp, vertical = 10.dp)) {
            T("添加网站或 App", 15f, FontWeight.SemiBold, Color.White)
          }
        }
      }
    } else {
      item {
        Chips(listOf("全部" to "${all.size}") + cats.map { c -> c to "${all.count { it.category == c }}" }, cat) { cat = it }
      }
      shown.forEach { c ->
        val list = filtered.filter { it.category == c }.sortedWith(compareBy(nameOrder) { it.name })
        if (list.isNotEmpty()) {
          item(key = "h-$c") {
            Row(Modifier.animateItem(fadeInSpec = calm(), placementSpec = calm(), fadeOutSpec = calm()).padding(start = 20.dp, top = 18.dp, bottom = 8.dp), verticalAlignment = Alignment.Bottom) {
              T(c, 20f, FontWeight.Bold); Spacer(Modifier.width(6.dp)); T("${list.size}", 14f, FontWeight.Medium, pal.secondary)
            }
          }
          items(list, key = { it.id }) { a ->
            Box(Modifier.animateItem(fadeInSpec = calm(), placementSpec = calm(), fadeOutSpec = calm()).padding(horizontal = 16.dp, vertical = 4.dp)) {
              ReelRow(a) { Store.push(Route.AccountR(a.id)) }
            }
          }
        }
      }
    }
  }
}
