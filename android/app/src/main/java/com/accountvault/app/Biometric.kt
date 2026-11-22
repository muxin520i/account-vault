package com.accountvault.app

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.fragment.app.FragmentActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 账号仓库 · 生物识别解锁（指纹 / 人脸 / 设备凭据）
 *
 * 设计：不做第二套密码体系。把用户已设的「密码」用 AndroidKeyStore 硬件密钥加密后
 * 存进 SharedPreferences；该密钥 setUserAuthenticationRequired(true)，且录入新生物
 * 特征后自动失效。解锁时经 BiometricPrompt 授权，取出密码走原有 Vault.unlock 流程。
 * 因此：密码不以明文落盘、不改动已加密的数据、关掉生物识别即销毁密钥。
 */
object Biometric {
  private const val PREF = "accountvault.bio"
  private const val KEY_ALIAS = "accountvault.bio.key"
  private const val K_CT = "ct"
  private const val K_IV = "iv"
  private const val AUTH = BIOMETRIC_STRONG or DEVICE_CREDENTIAL

  private fun prefs(ctx: Context): SharedPreferences =
    ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)

  /** 设备是否具备生物识别 / 设备凭据能力 */
  fun available(ctx: Context): Boolean =
    BiometricManager.from(ctx).canAuthenticate(AUTH) == BiometricManager.BIOMETRIC_SUCCESS

  /** 是否已开启生物识别解锁 */
  fun isEnabled(ctx: Context): Boolean = prefs(ctx).getString(K_CT, null) != null

  private fun getKey(): SecretKey {
    val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    (ks.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
    val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
    kg.init(
      KeyGenParameterSpec.Builder(
        KEY_ALIAS,
        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
      )
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        .setKeySize(256)
        .setUserAuthenticationRequired(true)
        .setInvalidatedByBiometricEnrollment(true)
        .build(),
    )
    return kg.generateKey()
  }

  private fun makeCipher(mode: Int, ctx: Context): Cipher {
    val c = Cipher.getInstance("AES/GCM/NoPadding")
    if (mode == Cipher.ENCRYPT_MODE) {
      c.init(mode, getKey())
    } else {
      val iv = prefs(ctx).getString(K_IV, null)?.let { AtlasCrypto.unb64(it) }
        ?: throw IllegalStateException("no iv")
      c.init(mode, getKey(), GCMParameterSpec(128, iv))
    }
    return c
  }

  private fun promptInfo() = BiometricPrompt.PromptInfo.Builder()
    .setTitle("生物识别解锁")
    .setSubtitle("验证指纹、人脸或锁屏密码")
    .setAllowedAuthenticators(AUTH)
    .build()

  /** 开启：先验证当前密码，再经生物识别授权把密码加密保存 */
  fun enable(
    activity: FragmentActivity,
    password: String,
    onOk: () -> Unit,
    onErr: (String) -> Unit,
  ) {
    if (!Vault.verify(password)) { onErr("当前密码错误"); return }
    if (!available(activity)) { onErr("此设备不支持指纹或人脸"); return }
    val cipher = try {
      makeCipher(Cipher.ENCRYPT_MODE, activity)
    } catch (e: Exception) { onErr("无法准备加密：${e.message ?: e}"); return }
    val executor = ContextCompat.getMainExecutor(activity)
    val prompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
      override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
        val c = result.cryptoObject?.cipher ?: cipher
        try {
          val ct = c.doFinal(password.toByteArray(Charsets.UTF_8))
          prefs(activity).edit()
            .putString(K_CT, AtlasCrypto.b64(ct))
            .putString(K_IV, AtlasCrypto.b64(c.iv))
            .apply()
          onOk()
        } catch (e: Exception) { onErr("保存失败：${e.message ?: e}") }
      }
      override fun onAuthenticationError(code: Int, msg: CharSequence) {
        if (code != BiometricPrompt.ERROR_USER_CANCELED && code != BiometricPrompt.ERROR_NEGATIVE_BUTTON) onErr(msg.toString())
      }
      override fun onAuthenticationFailed() { onErr("指纹或人脸未匹配") }
    })
    prompt.authenticate(promptInfo(), BiometricPrompt.CryptoObject(cipher))
  }

  /** 解锁：经生物识别授权取出密码，回调给解锁页走 Vault.unlock */
  fun unlock(
    activity: FragmentActivity,
    onPassword: (String) -> Unit,
    onErr: (String) -> Unit,
  ) {
    if (!isEnabled(activity)) { onErr("尚未开启生物识别解锁"); return }
    val cipher = try {
      makeCipher(Cipher.DECRYPT_MODE, activity)
    } catch (e: Exception) { onErr("生物识别已失效，请用密码解锁后重新开启"); return }
    val ct = prefs(activity).getString(K_CT, null)?.let { AtlasCrypto.unb64(it) }
      ?: run { onErr("无生物识别数据"); return }
    val executor = ContextCompat.getMainExecutor(activity)
    val prompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
      override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
        val c = result.cryptoObject?.cipher ?: cipher
        try {
          onPassword(String(c.doFinal(ct), Charsets.UTF_8))
        } catch (e: Exception) { onErr("解密失败，请重新开启生物识别") }
      }
      override fun onAuthenticationError(code: Int, msg: CharSequence) {
        if (code != BiometricPrompt.ERROR_USER_CANCELED && code != BiometricPrompt.ERROR_NEGATIVE_BUTTON) onErr(msg.toString())
      }
      override fun onAuthenticationFailed() { onErr("指纹或人脸未匹配") }
    })
    prompt.authenticate(promptInfo(), BiometricPrompt.CryptoObject(cipher))
  }

  /** 关闭：清除密文并销毁硬件密钥（改密码后也需调用，因为旧密文对应旧密码） */
  fun disable(ctx: Context) {
    prefs(ctx).edit().clear().apply()
    runCatching { KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(KEY_ALIAS) }
  }
}
