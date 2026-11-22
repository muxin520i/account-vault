import SwiftUI
import UniformTypeIdentifiers

struct BackupDoc: FileDocument {
  static var readableContentTypes: [UTType] { [.json] }
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
          Link(destination: URL(string: "https://github.com/muxin520i/account-vault/releases/latest")!) {
            HStack {
              Label("下载新版本", systemImage: "arrow.up.forward.app")
              Spacer()
              Text((Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String).map { "当前 " + $0 } ?? "").foregroundStyle(.secondary)
            }
          }
        } footer: { Text("在 GitHub Releases 下载，直接覆盖安装，数据不会丢失。") }
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
    }
    .navigationTitle("加密同步").navigationBarTitleDisplayMode(.inline)
    .confirmationDialog("关闭同步？", isPresented: $askOff, titleVisibility: .visible) {
      Button("关闭同步", role: .destructive) { sync.disable() }
    }
  }
}
