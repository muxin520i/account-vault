import SwiftUI

/// 账号仓库 · 解锁门
struct UnlockView: View {
  @StateObject private var vault = Vault.shared
  @State private var pass = ""
  @State private var pass2 = ""
  @State private var err = ""
  /// 解锁/创建成功后调用，参数为是否为首次设置
  var onUnlocked: (Bool) -> Void

  private var setup: Bool { vault.state == .needSetup }

  var body: some View {
    ZStack {
      Color.black.ignoresSafeArea()
      VStack(spacing: 14) {
        Image(uiImage: UIImage(named: "AppIcon-512") ?? UIImage())
          .resizable().frame(width: 76, height: 76).cornerRadius(18)
        Image(systemName: "lock.fill").font(.system(size: 28)).foregroundColor(.accentColor)
        Text(setup ? "设置主密码" : "解锁账号仓库")
          .font(.title2.bold()).foregroundColor(.white)
        Text(setup ? "这个密码用来加密你保存在本机的数据，忘了无法找回。" : "输入主密码以解密你的数据")
          .font(.footnote).foregroundColor(.gray).multilineTextAlignment(.center)
          .padding(.horizontal, 32)

        SecureField("主密码（至少 4 位）", text: $pass)
          .textFieldStyle(.roundedBorder).padding(.horizontal, 40)
        if setup {
          SecureField("再次输入主密码", text: $pass2)
            .textFieldStyle(.roundedBorder).padding(.horizontal, 40)
        }
        if !err.isEmpty { Text(err).font(.footnote).foregroundColor(.red) }

        Button(action: submit) {
          Text(setup ? "创建保险库" : "解锁")
            .frame(maxWidth: .infinity).padding().background(Color.accentColor)
            .foregroundColor(.white).cornerRadius(12)
        }
        .padding(.horizontal, 40).padding(.top, 10)
      }
    }
  }

  private func submit() {
    if pass.isEmpty { err = "请输入主密码"; return }
    if pass.count < 4 { err = "主密码至少 4 位"; return }
    if setup {
      if pass != pass2 { err = "两次输入不一致"; return }
      if vault.create(pass) { onUnlocked(true) } else { err = "设置失败，请重试" }
    } else {
      if vault.unlock(pass) { onUnlocked(false) } else { err = "主密码错误" }
    }
  }
}
