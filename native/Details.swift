import SwiftUI
import UIKit

struct IdentityDetail: View {
  @Environment(Store.self) var store
  @Environment(\.dismiss) var dismiss
  let id: String
  @State private var askDelete = false
  @State private var copied = 0

  var body: some View {
    if let i = store.identity(id) {
      let accs = store.accounts(of: id)
      NavigationStack {
        ScrollView {
          VStack(spacing: 0) {
            WalletCard(identity: i).padding(.horizontal, 28).padding(.top, 8)
            HStack(spacing: 10) {
              action("复制", "doc.on.doc") { UIPasteboard.general.string = i.value; copied += 1; store.show("已复制") }
              action("编辑", "pencil") { store.route = .editIdentity(i.id) }
              action("删除", "trash", role: .destructive) { askDelete = true }
            }
            .padding(.horizontal, 20).padding(.top, 26)
            .sensoryFeedback(.success, trigger: copied)
            if !i.notes.isEmpty {
              section("备注")
              Text(i.notes).frame(maxWidth: .infinity, alignment: .leading).padding(14)
                .background(.glass, in: RoundedRectangle(cornerRadius: 16, style: .continuous)).padding(.horizontal, 20)
            }
            section("用它注册或绑定的 \(accs.count) 个网站与 App")
            VStack(spacing: 8) {
              ForEach(accs) { a in
                Button { store.route = .account(a.id) } label: { ReelRow(account: a) }.buttonStyle(PressStyle(scale: 0.98))
              }
              Button { store.route = .picker(i.id) } label: {
                Label("添加网站或 App", systemImage: "plus").font(.system(size: 16, weight: .semibold))
                  .frame(maxWidth: .infinity).frame(height: 52)
                  .background(.glass, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
              }.buttonStyle(PressStyle(scale: 0.98))
            }.padding(.horizontal, 20)
            Color.clear.frame(height: 30)
          }
        }
        .background(alignment: .top) {
          skinGradient(i.color).frame(height: 420).blur(radius: 70).opacity(0.55).ignoresSafeArea()
        }
        .toolbar { ToolbarItem(placement: .topBarTrailing) { Button { dismiss() } label: { Image(systemName: "xmark") } } }
        .confirmationDialog("删除 \(store.title(i))？", isPresented: $askDelete, titleVisibility: .visible) {
          Button("删除", role: .destructive) {
            store.mutate("已删除") { s in
              s.identities.removeAll { $0.id == i.id }
              for k in s.accounts.indices { s.accounts[k].identityIds.removeAll { $0 == i.id } }
            }
            store.setKind(store.kind)
            dismiss()
          }
        } message: { Text(accs.isEmpty ? "删除后可以撤销。" : "它会从 \(accs.count) 个网站与 App 的记录里移除，这些网站本身会保留。") }
      }
    }
  }

  func section(_ s: String) -> some View {
    Text(s).font(.system(size: 13, weight: .medium)).foregroundStyle(.secondary)
      .frame(maxWidth: .infinity, alignment: .leading).padding(.horizontal, 36).padding(.top, 26).padding(.bottom, 8)
  }
  func action(_ t: String, _ icon: String, role: ButtonRole? = nil, _ fn: @escaping () -> Void) -> some View {
    Button(role: role, action: fn) {
      VStack(spacing: 6) {
        Image(systemName: icon).font(.system(size: 19, weight: .medium))
        Text(t).font(.system(size: 13, weight: .medium))
      }
      .foregroundStyle(role == .destructive ? Color.red : Color.primary)
      .frame(maxWidth: .infinity).frame(height: 66)
      .background(.glass, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
    }.buttonStyle(PressStyle())
  }
}

struct AccountDetail: View {
  @Environment(Store.self) var store
  @Environment(\.dismiss) var dismiss
  @Environment(\.openURL) var openURL
  let id: String
  @State private var askDelete = false

  var body: some View {
    if let a = store.account(id) {
      let d = cleanDomain(a.domain), b = brandColor(name: a.name, domain: a.domain)
      NavigationStack {
        ScrollView {
          VStack(spacing: 0) {
            AppIcon(name: a.name, domain: a.domain, size: 96)
              .bakedShadow(RoundedRectangle(cornerRadius: 96 * 0.235, style: .continuous), key: "icon96" + a.name + a.domain, color: b.opacity(0.5), radius: 24, y: 10).padding(.top, 10)
            Text(a.name).font(.system(size: 26, weight: .bold)).padding(.top, 14)
            if let h = a.handle { Text(store.handleText(h)).font(.system(size: 15)).foregroundStyle(.secondary).padding(.top, 2) }
            HStack(spacing: 6) {
              tag(a.category)
              if !d.isEmpty { tag(d) }
            }.padding(.top, 10)
            HStack(spacing: 10) {
              act("打开网站", "safari", disabled: d.isEmpty) { if let u = URL(string: "https://" + d) { openURL(u) } }
              act("编辑", "pencil") { store.route = .editAccount(a.id) }
              act("删除", "trash", destructive: true) { askDelete = true }
            }.padding(.horizontal, 20).padding(.top, 24)
            Text("用的邮箱和手机号").font(.system(size: 13, weight: .medium)).foregroundStyle(.secondary)
              .frame(maxWidth: .infinity, alignment: .leading).padding(.horizontal, 36).padding(.top, 26).padding(.bottom, 8)
            VStack(spacing: 10) {
              let list = a.identityIds.compactMap { store.identity($0) }
              if list.isEmpty { Text("还没有关联，点编辑选一个。").foregroundStyle(.secondary).padding() }
              ForEach(list) { i in
                Button { store.route = .identity(i.id) } label: {
                  HStack(spacing: 12) {
                    Image(systemName: i.isPhone ? "simcard.fill" : "envelope.fill").foregroundStyle(.white).frame(width: 40, height: 28)
                      .background(skinGradient(i.color), in: CardShape(sim: i.isPhone))
                    VStack(alignment: .leading, spacing: 2) {
                      Text(store.title(i)).font(.system(size: 16, weight: .semibold))
                      if !i.label.isEmpty { Text(store.display(i)).font(.system(size: 13)).foregroundStyle(.secondary) }
                    }
                    Spacer()
                    Image(systemName: "chevron.right").font(.system(size: 13, weight: .semibold)).foregroundStyle(.tertiary)
                  }
                  .padding(12).background(.glass, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
                }.buttonStyle(PressStyle(scale: 0.98)).foregroundStyle(.primary)
              }
            }.padding(.horizontal, 20)
            if !a.notes.isEmpty {
              Text("备注").font(.system(size: 13, weight: .medium)).foregroundStyle(.secondary)
                .frame(maxWidth: .infinity, alignment: .leading).padding(.horizontal, 36).padding(.top, 26).padding(.bottom, 8)
              Text(a.notes).frame(maxWidth: .infinity, alignment: .leading).padding(14)
                .background(.glass, in: RoundedRectangle(cornerRadius: 16, style: .continuous)).padding(.horizontal, 20)
            }
            Text("添加于 " + Date(timeIntervalSince1970: a.createdAt / 1000).formatted(date: .long, time: .omitted))
              .font(.system(size: 12)).foregroundStyle(.tertiary).padding(.top, 24).padding(.bottom, 30)
          }
        }
        .background(alignment: .top) { b.frame(height: 360).blur(radius: 90).opacity(0.45).ignoresSafeArea() }
        .toolbar { ToolbarItem(placement: .topBarTrailing) { Button { dismiss() } label: { Image(systemName: "xmark") } } }
        .confirmationDialog("删除 \(a.name)？", isPresented: $askDelete, titleVisibility: .visible) {
          Button("删除", role: .destructive) { store.mutate("已删除") { s in s.accounts.removeAll { $0.id == a.id } }; dismiss() }
        } message: { Text("只删除这条记录，不影响你在那个网站上的账号。") }
      }
    }
  }
  func tag(_ s: String) -> some View {
    Text(s).font(.system(size: 12, weight: .medium)).padding(.horizontal, 9).padding(.vertical, 4).background(.glass, in: Capsule())
  }
  func act(_ t: String, _ icon: String, disabled: Bool = false, destructive: Bool = false, _ fn: @escaping () -> Void) -> some View {
    Button(action: fn) {
      VStack(spacing: 6) {
        Image(systemName: icon).font(.system(size: 19, weight: .medium))
        Text(t).font(.system(size: 13, weight: .medium))
      }
      .foregroundStyle(destructive ? Color.red : Color.primary)
      .frame(maxWidth: .infinity).frame(height: 66)
      .background(.glass, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
    }.buttonStyle(PressStyle()).disabled(disabled).opacity(disabled ? 0.35 : 1)
  }
}
