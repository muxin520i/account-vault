package com.accountvault.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.ComponentActivity

/**
 * 账号仓库 · 解锁门（背景图 + 密码）
 * NEED_SETUP -> 设置密码；LOCKED -> 输入密码解锁。
 */
@Composable
fun UnlockGate(onUnlocked: (firstSetup: Boolean) -> Unit) {
  val setup = Vault.state == Vault.State.NEED_SETUP
  var pass by remember { mutableStateOf("") }
  var pass2 by remember { mutableStateOf("") }
  var err by remember { mutableStateOf("") }
  val ctx = LocalContext.current
  val activity = ctx as? ComponentActivity
  val bioEnabled = !setup && activity != null && Biometric.isEnabled(ctx) && Biometric.available(ctx)

  MaterialTheme(colorScheme = darkColorScheme(
    primary = DarkPal.accent, background = DarkPal.bg, surface = DarkPal.glassStrong,
    onBackground = DarkPal.primary, onSurface = DarkPal.primary, error = DarkPal.red,
  )) {
    androidx.compose.runtime.CompositionLocalProvider(LocalPal provides DarkPal) {
      Box(Modifier.fillMaxSize()) {
        // 背景图铺满
        Image(
          painter = painterResource(R.drawable.unlock_bg),
          contentDescription = null,
          modifier = Modifier.fillMaxSize(),
          contentScale = ContentScale.Crop,
        )
        // 半透明遮罩，保证文字与输入框可读
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.42f)))

        Column(
          Modifier.fillMaxSize().imePadding().padding(horizontal = 32.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center,
        ) {
          Image(painterResource(R.mipmap.ic_launcher_flat), null, Modifier.size(72.dp).clip(RoundedCornerShape(18.dp)))
          Spacer(Modifier.height(22.dp))
          Icon(Icons.Filled.Lock, null, Modifier.size(30.dp), tint = Color.White)
          Spacer(Modifier.height(12.dp))
          T(if (setup) "设置密码" else "解锁账号仓库", 24f, FontWeight.Bold, Color.White)
          Spacer(Modifier.height(8.dp))
          T(
            if (setup) "这个密码用来加密你保存在本机的数据，忘了无法找回。" else "输入密码以解密你的数据",
            14f, color = Color.White.copy(alpha = 0.85f), align = androidx.compose.ui.text.style.TextAlign.Center,
          )
          Spacer(Modifier.height(30.dp))

          PassBox(pass, { pass = it; err = "" }, if (setup) "密码（至少 4 位）" else "密码")
          if (setup) {
            Spacer(Modifier.height(10.dp))
            PassBox(pass2, { pass2 = it; err = "" }, "再次输入密码")
          }
          if (err.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            T(err, 13f, color = Color.Red, align = androidx.compose.ui.text.style.TextAlign.Center)
          }
          Spacer(Modifier.height(26.dp))

          Button(
            onClick = {
              when {
                pass.isBlank() -> err = "请输入密码"
                setup && pass.length < 4 -> err = "密码至少 4 位"
                setup && pass != pass2 -> err = "两次输入不一致"
                setup -> {
                  if (Vault.create(pass)) { onUnlocked(true) } else err = "设置失败，请重试"
                }
                else -> {
                  if (Vault.unlock(pass)) { onUnlocked(false) } else err = "密码错误"
                }
              }
            },
            Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(14.dp)),
            colors = ButtonDefaults.buttonColors(containerColor = DarkPal.accent, contentColor = Color.White),
          ) { T(if (setup) "创建保险库" else "解锁", 16f, FontWeight.SemiBold, Color.White) }

          if (bioEnabled && activity != null) {
            Spacer(Modifier.height(12.dp))
            androidx.compose.material3.OutlinedButton(
              onClick = {
                err = ""
                Biometric.unlock(activity,
                  onPassword = { p -> if (Vault.unlock(p)) onUnlocked(false) else err = "生物识别数据已失效，请用密码解锁" },
                  onErr = { err = it },
                )
              },
              Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(14.dp)),
              colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
              border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.5f)),
            ) {
              Icon(Icons.Filled.Fingerprint, null, Modifier.size(20.dp), tint = Color.White)
              Spacer(Modifier.size(8.dp))
              T("指纹 / 人脸解锁", 15f, FontWeight.SemiBold, Color.White)
            }
          }
        }
      }
    }
  }
}

@Composable
private fun PassBox(value: String, onChange: (String) -> Unit, hint: String) {
  Box(
    Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(14.dp))
      .background(Color.White.copy(alpha = 0.92f)).padding(horizontal = 18.dp),
    contentAlignment = Alignment.CenterStart,
  ) {
    if (value.isEmpty()) T(hint, 16f, color = Color(0xFF8A8A8E))
    BasicTextField(
      value = value, onValueChange = onChange, Modifier.fillMaxWidth(), singleLine = true,
      textStyle = TextStyle(color = Color(0xFF1C1C1E), fontSize = 17.sp),
      cursorBrush = SolidColor(DarkPal.accent), visualTransformation = PasswordVisualTransformation(),
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
    )
  }
}
