import Foundation
import LocalAuthentication
import Security

/// 账号仓库 · 生物识别解锁（Face ID / Touch ID）
///
/// 不做第二套密码体系。把用户已设的「密码」存进 Keychain，访问控制设为
/// biometryCurrentSet（录入新指纹/面容后自动失效），读取时必须经系统生物识别授权。
/// 解锁时取出密码走原有 Vault.unlock 流程：密码不明文落盘、不改已加密数据、
/// 关闭生物识别即删除 Keychain 条目。
enum Biometric {
  private static let service = "com.accountvault.bio"
  private static let account = "vault-pass"

  private static func query() -> [String: Any] {
    [
      kSecClass as String: kSecClassGenericPassword,
      kSecAttrService as String: service,
      kSecAttrAccount as String: account,
    ]
  }

  /// 设备是否具备生物识别能力
  static func available() -> Bool {
    var err: NSError?
    return LAContext().canEvaluatePolicy(.deviceOwnerAuthentication, error: &err)
  }

  /// 是否已开启（Keychain 里有条目）
  static func isEnabled() -> Bool {
    var q = query()
    q[kSecReturnData as String] = false
    q[kSecMatchLimit as String] = kSecMatchLimitOne
    var item: CFTypeRef?
    return SecItemCopyMatching(q as CFDictionary, &item) == errSecSuccess
  }

  /// 开启：验证当前密码 → 弹生物识别 → 把密码存入带 biometry 门禁的 Keychain
  static func enable(password: String, reason: String, completion: @escaping (Bool, String) -> Void) {
    // verify 是 @MainActor，先切主线程校验密码
    DispatchQueue.main.async {
      guard Vault.shared.verify(password) else { completion(false, "当前密码错误"); return }
      guard let access = SecAccessControlCreateWithFlags(
        nil,
        kSecAttrAccessibleWhenUnlockedThisDeviceOnly,
        .biometryCurrentSet,
        nil
      ) else { completion(false, "此设备不支持生物识别"); return }

      let ctx = LAContext()
      ctx.localizedReason = reason
      var err: NSError?
      guard ctx.canEvaluatePolicy(.deviceOwnerAuthentication, error: &err) else {
        completion(false, "此设备未设置指纹或面容"); return
      }
      ctx.evaluatePolicy(.deviceOwnerAuthentication, localizedReason: reason) { ok, error in
        DispatchQueue.main.async {
          guard ok else { completion(false, error?.localizedDescription ?? "验证失败"); return }
          var q = query()
          q[kSecValueData as String] = Data(password.utf8)
          q[kSecAttrAccessControl as String] = access
          SecItemDelete(q as CFDictionary) // 先删旧条目，避免重复
          let status = SecItemAdd(q as CFDictionary, nil)
          if status == errSecSuccess { completion(true, "") }
          else { completion(false, "保存失败（\(status)）") }
        }
      }
    }
  }

  /// 解锁：弹生物识别 → 取出密码 → 成功回调密码，失败回调原因
  static func unlock(reason: String, onPassword: @escaping (String) -> Void, onError: @escaping (String) -> Void) {
    guard isEnabled() else { onError("尚未开启生物识别解锁"); return }
    let ctx = LAContext()
    ctx.localizedReason = reason
    var err: NSError?
    guard ctx.canEvaluatePolicy(.deviceOwnerAuthentication, error: &err) else {
      onError("此设备未设置指纹或面容"); return
    }
    ctx.evaluatePolicy(.deviceOwnerAuthentication, localizedReason: reason) { ok, error in
      DispatchQueue.main.async {
        guard ok else { onError(error?.localizedDescription ?? "验证失败"); return }
        var q = query()
        q[kSecReturnData as String] = true
        q[kSecMatchLimit as String] = kSecMatchLimitOne
        q[kSecUseAuthenticationContext as String] = ctx
        var item: CFTypeRef?
        guard SecItemCopyMatching(q as CFDictionary, &item) == errSecSuccess,
              let data = item as? Data,
              let pass = String(data: data, encoding: .utf8) else {
          onError("读取凭据失败，请重新开启生物识别"); return
        }
        onPassword(pass)
      }
    }
  }

  /// 关闭 / 改密码后失效：删除 Keychain 条目
  static func disable() { SecItemDelete(query() as CFDictionary) }
}
