import SwiftUI
import UniformTypeIdentifiers

struct BackupDoc: FileDocument {
  static var readableContentTypes: [UTType] { [.json] }
  var data: Data
  init(data: Data) { self.data = data }
  init(configuration: ReadConfiguration) throws { data = configuration.file.regularFileContents ?? Data() }
  func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper { FileWrapper(regularFileWithContents: data) }
}

struct EncBackupDoc: FileDocument {
  static var readableContentTypes: [UTType] { [.data] }
  var data: Data
  init(data: Data) { self.data = data }
  init(configuration: ReadConfiguration) throws { data = configuration.file.regularFileContents ?? Data() }
  func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper { FileWrapper(regularFileWithContents: data) }
}

struct SettingsSheet: View {
  @Environment(Store.self) var store
  @EnvironmentObject var sync: Sync
  @Environment(\.dismiss) var dismiss
  @State private var exporting = false
  @State private var importing = false
  @State private var doc = BackupDoc(data: Data())
  @State private var encExporting = false
  @State private var encImporting = false
  @State private var encDoc = EncBackupDoc(data: Data())
  @State private var bioEnabled = Biometric.isEnabled()
  @State private var bioAskPass = false
  @State private var bioPass = ""
  @State private var bioMsg = ""

  var body: some View {
    NavigationStack {
      Form {
        Section {
          NavigationLink { SyncView() } label: {
            HStack {
              Label("加密同步", systemImage: "icloud")
              Spacer()
              Text(syncStatus).foregroundStyle(.secondary).font(.system(size: 14))
            }
          }
        }
        Section {
          Toggle(isOn: Binding(get: { store.privacy }, set: { v in store.privacy = v }).animation(Motion.smooth)) { Label("隐藏号码", systemImage: "eye.slash") }
          Picker(selection: Binding(get: { store.theme }, set: { ThemeFX.set($0, store: store) })) {
            Text("深色").tag("dark")
            Text("浅色").tag("light")
            Text("跟随系统").tag("system")
          } label: { Label("外观", systemImage: "circle.lefthalf.filled") }
          NavigationLink { OrderView() } label: { Label("调整卡片顺序", systemImage: "arrow.up.arrow.down") }
        }
        Section {
          Button { doc = BackupDoc(data: store.backupJSON()); exporting = true } label: { Label("导出备份", systemImage: "square.and.arrow.up") }
          Button { importing = true } label: { Label("导入备份", systemImage: "square.and.arrow.down") }
          if !sync.on {
            Button { store.mutate("已载入示例") { s in s = Store.demo() }; store.data.demo = true; store.persist(); store.setKind("email") } label: { Label("载入示例", systemImage: "text.badge.plus") }
          }
        } footer: { Text("备份是 JSON 文件，和网页版通用。") }
        Section {
          NavigationLink { ChangePasswordView() } label: { Label("修改密码", systemImage: "lock.rotation") }
        } footer: { Text("数据在密码解锁后才可读取，锁定后内存中的明文会立即清空。") }
        if Biometric.available() {
          Section {
            Toggle(isOn: Binding(
              get: { bioEnabled },
              set: { on in
                if on {
                  bioPass = ""; bioMsg = ""; bioAskPass = true
                } else {
                  Biometric.disable(); bioEnabled = false; bioMsg = ""
                }
              }
            )) { Label("指纹 / 人脸解锁", systemImage: "faceid") }
          } footer: { Text(bioMsg.isEmpty ? "开启时需验证当前密码；录入新指纹或面容后需重新开启。" : bioMsg) }
        }
        Section {
          Button { if let s = Vault.shared.exportEncrypted() { encDoc = EncBackupDoc(data: Data(s.utf8)); encExporting = true } } label: {
            Label("导出加密备份", systemImage: "square.and.arrow.up.on.square")
          }
          Button { encImporting = true } label: { Label("导入加密备份", systemImage: "square.and.arrow.down.on.square") }
        } footer: { Text("加密备份用密码加密，只有账号仓库能解开。") }
        Section {
          VStack(spacing: 6) {
            Text("账号仓库").font(.system(size: 15, weight: .semibold))
            Text("不保存任何密码 · 数据只在本机和你自己的私有 Gist").font(.system(size: 12)).foregroundStyle(.secondary)
          }.frame(maxWidth: .infinity)
        }.listRowBackground(Color.clear)
      }
      .tint(.accentColor)
      .navigationTitle("设置").navigationBarTitleDisplayMode(.inline)
      .toolbar { ToolbarItem(placement: .confirmationAction) { Button("完成") { dismiss() }.bold() } }
      .fileExporter(isPresented: $exporting, document: doc, contentType: .json, defaultFilename: "账号备份-\(Date().formatted(.iso8601.year().month().day()))") { r in
        if case .success = r { store.show("备份已导出") }
      }
      .fileImporter(isPresented: $importing, allowedContentTypes: [.json]) { r in
        guard case .success(let url) = r else { return }
        let ok = url.startAccessingSecurityScopedResource(); defer { if ok { url.stopAccessingSecurityScopedResource() } }
        if let d = try? Data(contentsOf: url), store.importBackup(d) { dismiss() } else { store.show("这不是账号仓库的备份文件") }
      }
      .fileExporter(isPresented: $encExporting, document: encDoc, contentType: .data, defaultFilename: "账号仓库-加密备份.avbackup") { r in
        if case .success = r { store.show("加密备份已导出") }
      }
      .fileImporter(isPresented: $encImporting, allowedContentTypes: [.data]) { r in
        guard case .success(let url) = r else { return }
        let ok = url.startAccessingSecurityScopedResource(); defer { if ok { url.stopAccessingSecurityScopedResource() } }
        if let d = try? Data(contentsOf: url), let s = String(data: d, encoding: .utf8), Vault.shared.importEncrypted(s) {
          store.loadFromVault(); store.show("加密备份已导入")
        } else { store.show("这不是有效的账号仓库加密备份") }
      }
      .alert("开启指纹 / 人脸解锁", isPresented: $bioAskPass) {
        SecureField("当前密码", text: $bioPass)
        Button("取消", role: .cancel) { bioPass = "" }
        Button("开启") {
          let p = bioPass; bioPass = ""
          Biometric.enable(password: p, reason: "验证以开启指纹 / 人脸解锁") { ok, msg in
            bioEnabled = ok
            bioMsg = ok ? "已开启，解锁时可用指纹或人脸" : msg
          }
        }
      } message: {
        Text("验证当前密码后，将用系统安全芯片加密保存。")
      }
    }
  }
  var syncStatus: String {
    switch sync.state {
    case "off": return "未开启"
    case "syncing": return "同步中…"
    case "error": return "出错了"
    default: return sync.at > 0 ? "已同步 " + Date(timeIntervalSince1970: sync.at / 1000).formatted(.relative(presentation: .named)) : "已开启"
    }
  }
}

struct OrderView: View {
  @Environment(Store.self) var store
  var body: some View {
    List {
      group("email", "邮箱")
      group("phone", "手机号")
    }
    .environment(\.editMode, .constant(.active))
    .navigationTitle("卡片顺序").navigationBarTitleDisplayMode(.inline)
  }
  @ViewBuilder func group(_ type: String, _ title: String) -> some View {
    let list = store.data.identities.filter { $0.type == type }
    if !list.isEmpty {
      Section {
        ForEach(list) { i in
          HStack(spacing: 12) {
            Image(systemName: i.isPhone ? "simcard.fill" : "envelope.fill").font(.system(size: 13)).foregroundStyle(.white).frame(width: 46, height: 30)
              .background(skinGradient(i.color), in: CardShape(sim: i.isPhone))
              .shadow(color: Color(hex: i.color).opacity(0.35), radius: 5, y: 2)
            VStack(alignment: .leading, spacing: 1) {
              Text(store.title(i)).font(.system(size: 16, weight: .medium))
              if !i.label.isEmpty { Text(store.display(i)).font(.system(size: 13)).foregroundStyle(.secondary) }
            }
          }.padding(.vertical, 2)
        }
        .onMove { from, to in move(type, from, to) }
      } header: { Text(title) } footer: { if type == "phone" || !store.data.identities.contains(where: { $0.type == "phone" }) { Text("拖动右侧把手排序，排在第一位的卡会在卡包里最先出现。") } }
    }
  }
  func move(_ type: String, _ from: IndexSet, _ to: Int) {
    store.mutate(nil) { s in
      var list = s.identities.filter { $0.type == type }
      list.move(fromOffsets: from, toOffset: to)
      for k in list.indices { list[k].order = Double(k) }
      let others = s.identities.filter { $0.type != type }
      let emails = type == "email" ? list : others.filter { $0.type == "email" }
      let phones = type == "phone" ? list : others.filter { $0.type == "phone" }
      s.identities = emails + phones + others.filter { $0.type != "email" && $0.type != "phone" }
    }
  }
}

struct SyncView: View {
  @Environment(Store.self) var store
  @EnvironmentObject var sync: Sync
  @State private var prov = "gitee"
  @State private var token = ""
  @State private var pass = ""
  @State private var busy = false
  @State private var error = ""
  @State private var askOff = false

  var body: some View {
    Form {
      if sync.on {
        Section {
          HStack {
            Image(systemName: sync.state == "error" ? "exclamationmark.icloud.fill" : sync.state == "syncing" ? "arrow.triangle.2.circlepath.icloud.fill" : "checkmark.icloud.fill")
              .font(.system(size: 30)).foregroundStyle(sync.state == "error" ? Color.orange : Color.accentColor)
              .symbolEffect(.pulse, isActive: sync.state == "syncing")
            VStack(alignment: .leading, spacing: 2) {
              Text(sync.state == "error" ? sync.err : sync.state == "syncing" ? "正在同步…" : "已开启加密同步").font(.system(size: 16, weight: .semibold))
              Text("存放在 " + (sync.provider == "gitee" ? "Gitee" : "GitHub") + " 私有 Gist" + (sync.at > 0 ? " · " + Date(timeIntervalSince1970: sync.at / 1000).formatted(date: .omitted, time: .shortened) : ""))
                .font(.system(size: 13)).foregroundStyle(.secondary)
            }
          }.padding(.vertical, 4)
          Button("立即同步") { Task { await sync.run() } }.disabled(sync.state == "syncing")
        }
        Section { Button("关闭同步", role: .destructive) { askOff = true } } footer: { Text("关闭后这台设备不再同步，云端和本机的数据都会保留。") }
      } else {
        Section {
          Picker("存放在", selection: $prov) {
            Text("Gitee · 国内直连").tag("gitee")
            Text("GitHub").tag("github")
          }.pickerStyle(.inline).labelsHidden()
        } header: { Text("存放在") }
        Section {
          SecureField(prov == "gitee" ? "Gitee 私人令牌（勾选 gists）" : "GitHub 令牌（gist 权限）", text: $token).textInputAutocapitalization(.never).autocorrectionDisabled()
          SecureField("同步密码", text: $pass)
          Link(prov == "gitee" ? "去 Gitee 创建令牌" : "去 GitHub 创建令牌", destination: URL(string: prov == "gitee" ? "https://gitee.com/profile/personal_access_tokens/new" : "https://github.com/settings/tokens/new?scopes=gist&description=账号仓库")!)
        } footer: { Text("数据先用同步密码加密，再存进你自己的私有 Gist，别人看不到内容。每台设备填同一个令牌和同步密码即可。同步密码忘了就解不开。") }
        if !error.isEmpty { Section { Text(error).foregroundStyle(.red) } }
        Section {
          Button {
            busy = true; error = ""
            Task {
              do {
                let found = try await sync.enable(token: token, pass: pass, prov: prov)
                store.setKind(store.kind)
                store.show(found ? "已连接，云端数据已合并" : "同步已开启")
              } catch { self.error = (error as? SyncError)?.errorDescription ?? error.localizedDescription }
              busy = false
            }
          } label: {
            HStack {
              Spacer()
              if busy { ProgressView() } else { Text("开启同步").bold() }
              Spacer()
            }
          }.disabled(busy || token.trimmingCharacters(in: .whitespaces).isEmpty || pass.count < 4)
        }
      }
    }
    .navigationTitle("加密同步").navigationBarTitleDisplayMode(.inline)
    .confirmationDialog("关闭同步？", isPresented: $askOff, titleVisibility: .visible) {
      Button("关闭同步", role: .destructive) { sync.disable() }
    }
  }
}
