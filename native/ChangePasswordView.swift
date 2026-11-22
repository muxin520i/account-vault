import SwiftUI

struct ChangePasswordView: View {
  @Environment(\.dismiss) var dismiss
  @State private var old = ""
  @State private var nw = ""
  @State private var nw2 = ""
  @State private var msg = ""

  var body: some View {
    Form {
      Section {
        SecureField("当前主密码", text: $old)
        SecureField("新主密码（至少 4 位）", text: $nw)
        SecureField("再次输入新主密码", text: $nw2)
      }
      Section {
        Button("确定") {
          if nw.count < 4 { msg = "新主密码至少 4 位"; return }
          if nw != nw2 { msg = "两次输入不一致"; return }
          if Vault.shared.changePassword(old, nw) { dismiss() } else { msg = "当前主密码错误" }
        }
      }
      if !msg.isEmpty { Section { Text(msg).foregroundStyle(.red) } }
    }
    .navigationTitle("修改主密码").navigationBarTitleDisplayMode(.inline)
  }
}
