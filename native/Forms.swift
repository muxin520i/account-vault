import SwiftUI
import PhotosUI

struct IdentityForm: View {
  @Environment(Store.self) var store
  @Environment(\.dismiss) var dismiss
  let existing: Identity?
  let baseColor: String
  @State private var type: String
  @State private var value: String
  @State private var label: String
  @State private var color: String
  @State private var notes: String
  @State private var esim: Bool
  @State private var provider: String
  @State private var via: String
  @State private var colorTouched = false   // the user picked a skin here: stop following the type / provider

  init(existing: Identity?, type: String, defaultColor: String) {
    self.existing = existing
    let t = existing?.type ?? type
    let p = existing?.provider ?? "wechat"
    baseColor = defaultColor
    _type = State(initialValue: t)
    _value = State(initialValue: existing?.value ?? "")
    _label = State(initialValue: existing?.label ?? "")
    _color = State(initialValue: existing?.color ?? (t == "login" ? loginDefaultColor(p) : defaultColor))
    _notes = State(initialValue: existing?.notes ?? "")
    _esim = State(initialValue: existing?.esim == true)
    _provider = State(initialValue: p)
    _via = State(initialValue: existing?.via ?? "")
  }

  var isLogin: Bool { type == "login" }
  var placeholder: String {
    switch type {
    case "email": return "name@example.com"
    case "phone": return "+86 138 0000 0000"
    default: return provider == "apple" ? "name@icloud.com 或手机号" : "wxid 或自定义微信号"
    }
  }
  var sample: String {
    switch type {
    case "email": return "name@example.com"
    case "phone": return "+86 138 0000 0000"
    default: return provider == "apple" ? "name@icloud.com" : "wxid_example"
    }
  }
  /// emails and phones a login card can be bound to
  var bindable: [Identity] { (store.index.emails + store.index.phones).filter { $0.id != existing?.id } }
  var trimmed: String { value.trimmingCharacters(in: .whitespaces) }
  var valid: Bool {
    if trimmed.isEmpty { return false }
    return isLogin ? loginValueValid(provider: provider, value: trimmed) : true
  }
  func isLoginSkin(_ c: String) -> Bool { LOGIN_SKINS.contains { $0.0 == c } }

  var preview: Identity {
    var i = Identity(id: existing?.id ?? "_new", type: type, value: value.isEmpty ? sample : value, label: label, color: color, notes: notes,
                     esim: type == "phone" && esim, provider: isLogin ? provider : nil, via: isLogin ? via : nil)
    i.order = existing?.order
    return i
  }

  var body: some View {
    NavigationStack {
      Form {
        Section {
          WalletCard(identity: preview)
            .padding(.horizontal, 10).padding(.vertical, 6)
            .animation(Motion.smooth, value: color)
            .animation(Motion.smooth, value: type)
          Picker("类型", selection: $type) {
            Text("邮箱").tag("email")
            Text("手机号").tag("phone")
            Text("登录").tag("login")
          }.pickerStyle(.segmented)
        }
        .listRowBackground(Color.clear).listRowInsets(EdgeInsets(top: 4, leading: 0, bottom: 4, trailing: 0))
        if isLogin {
          Section {
            HStack(spacing: 10) {
              providerPill("wechat", "微信")
              providerPill("apple", "Apple")
            }
            .listRowBackground(Color.clear).listRowInsets(EdgeInsets(top: 0, leading: 0, bottom: 0, trailing: 0))
          }
        }
        Section {
          TextField(placeholder, text: $value)
            .keyboardType(type == "email" || (isLogin && provider == "apple") ? .emailAddress : type == "phone" ? .phonePad : .asciiCapable)
            .textInputAutocapitalization(.never).autocorrectionDisabled()
          TextField("标签：主力、小号、工作…", text: $label)
        } header: { Text(fieldTitle) } footer: {
          if isLogin && provider == "apple" && !trimmed.isEmpty && !valid { Text("Apple ID 是邮箱或手机号。") }
        }
        if type == "phone" {
          Section {
            Toggle("eSIM", isOn: $esim.animation(Motion.smooth))
          } footer: { Text("eSIM 没有实体卡，换手机时记得转移。") }
        }
        if isLogin {
          Section {
            Picker("绑定的手机号/邮箱", selection: $via) {
              Text("无").tag("")
              ForEach(bindable) { i in Text(i.kindName + " · " + store.title(i)).tag(i.id) }
            }
          } footer: { Text("很多 App 支持用它登录，记录在这里，换手机号时能看出受影响的网站。") }
        }
        Section("卡面") {
          LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 10), count: 5), spacing: 12) {
            ForEach(isLogin ? LOGIN_SKINS + SKINS : SKINS, id: \.0) { s in
              Button { colorTouched = true; withAnimation(Motion.snappy) { color = s.0 } } label: {
                VStack(spacing: 4) {
                  RoundedRectangle(cornerRadius: 8, style: .continuous).fill(skinGradient(s.0)).frame(height: 34)
                    .overlay(RoundedRectangle(cornerRadius: 8, style: .continuous).stroke(Color.primary, lineWidth: color == s.0 ? 2.5 : 0).padding(-3))
                  Text(s.1.name).font(.system(size: 11)).foregroundStyle(color == s.0 ? .primary : .secondary)
                }
              }.buttonStyle(.plain)
            }
          }.padding(.vertical, 6)
        }
        .sensoryFeedback(.selection, trigger: color)
        Section {
          TextField(isLogin ? "实名情况、找回方式" : "运营商、是否实名、找回方式", text: $notes, axis: .vertical).lineLimit(2...6)
        } header: { Text("备注") } footer: { Text("不要在这里填写密码。") }
      }
      .scrollContentBackground(.hidden)
      .background(skinGradient(color).opacity(0.18).ignoresSafeArea())
      .navigationTitle(existing == nil ? "新身份" : "编辑").navigationBarTitleDisplayMode(.inline)
      .toolbar {
        ToolbarItem(placement: .cancellationAction) { Button("取消") { dismiss() } }
        ToolbarItem(placement: .confirmationAction) { Button(existing == nil ? "添加" : "保存") { save() }.bold().disabled(!valid) }
      }
      .onAppear { if !via.isEmpty && !bindable.contains(where: { $0.id == via }) { via = "" } }
      .onChange(of: type) { old, new in
        if colorTouched { return }
        withAnimation(Motion.snappy) {
          if new == "login" && !isLoginSkin(color) { color = loginDefaultColor(provider) }
          else if old == "login" && new != "login" && isLoginSkin(color) {
            if let e = existing, !e.isLogin { color = e.color } else { color = baseColor }
          }
        }
      }
      .onChange(of: provider) { _, new in
        if !colorTouched && isLoginSkin(color) { withAnimation(Motion.snappy) { color = loginDefaultColor(new) } }
      }
    }
  }

  var fieldTitle: String {
    switch type {
    case "email": return "地址"
    case "phone": return "号码"
    default: return provider == "apple" ? "Apple ID" : "微信号"
    }
  }

  func providerPill(_ p: String, _ name: String) -> some View {
    let on = provider == p
    return Button { withAnimation(Motion.snappy) { provider = p } } label: {
      HStack(spacing: 6) {
        LoginGlyph(apple: p == "apple", size: 15, filled: true)
        Text(name).font(.system(size: 15, weight: .semibold))
      }
      .frame(maxWidth: .infinity).frame(height: 40)
      .foregroundStyle(on ? Color.white : Color.primary)
      .background(on ? AnyShapeStyle(skinGradient(loginDefaultColor(p))) : AnyShapeStyle(.glass), in: Capsule())
    }
    .buttonStyle(PressStyle(scale: 0.96))
    .sensoryFeedback(.selection, trigger: on)
  }

  func save() {
    let v = trimmed
    let isL = isLogin
    let prov: String? = isL ? provider : nil
    let bound: String? = isL && !via.isEmpty ? via : nil
    if let e = existing {
      store.mutate("已保存") { s in
        if let k = s.identities.firstIndex(where: { $0.id == e.id }) {
          s.identities[k].type = type; s.identities[k].value = v; s.identities[k].label = label.trimmingCharacters(in: .whitespaces)
          s.identities[k].color = color; s.identities[k].notes = notes.trimmingCharacters(in: .whitespacesAndNewlines)
          s.identities[k].esim = (type == "phone" && esim) ? true : nil
          s.identities[k].provider = prov
          s.identities[k].via = bound
        }
      }
      store.setKind(type, focus: e.id)
    } else {
      let i = Identity(type: type, value: v, label: label.trimmingCharacters(in: .whitespaces), color: color, notes: notes.trimmingCharacters(in: .whitespacesAndNewlines),
                       esim: type == "phone" && esim, provider: prov, via: bound)
      store.mutate("已添加") { s in
        s.identities.append(i)
      }
      store.setKind(type, focus: i.id)
    }
    dismiss()
  }
}

struct AccountForm: View {
  @Environment(Store.self) var store
  @Environment(\.dismiss) var dismiss
  let existing: Account
  @State private var name: String
  @State private var domain: String
  @State private var category: String
  @State private var handle: String
  @State private var notes: String
  @State private var ids: [String]
  @State private var icon: String?
  @State private var art: String? = nil          // App Store artwork shown while its icon downloads
  @State private var iconJob = 0                 // newest icon change wins over a slower download
  @State private var typed = false               // suggestions only after the user edits the name
  @State private var appResults: [AppStoreApp] = []
  @State private var iconMenu = false
  @State private var photos = false
  @State private var photoItem: PhotosPickerItem? = nil
  @FocusState private var nameFocused: Bool

  init(existing: Account) {
    self.existing = existing
    _name = State(initialValue: existing.name); _domain = State(initialValue: existing.domain)
    _category = State(initialValue: existing.category); _handle = State(initialValue: existing.handle ?? "")
    _notes = State(initialValue: existing.notes); _ids = State(initialValue: existing.identityIds)
    _icon = State(initialValue: existing.icon)
  }

  var query: String { typed && nameFocused ? name.trimmingCharacters(in: .whitespaces) : "" }
  var presetHits: [Preset] {
    let q = query.lowercased()
    guard !q.isEmpty else { return [] }
    return Array(Presets.all.filter { $0.name.lowercased().contains(q) || $0.domain.contains(q) }.prefix(4))
  }
  var storeHits: [AppStoreApp] {
    let shown = Set(presetHits.map { $0.name.lowercased() })
    return appResults.filter { !shown.contains($0.cleanName.lowercased()) && !shown.contains($0.trackName.lowercased()) }
  }

  var body: some View {
    NavigationStack {
      Form {
        Section {
          HStack(spacing: 14) {
            Button { iconMenu = true } label: {
              AppIcon(name: name, domain: domain, icon: icon, url: icon == nil ? art : nil, size: 54)
                .overlay(alignment: .bottomTrailing) {
                  Image(systemName: "camera.fill").font(.system(size: 9, weight: .bold)).foregroundStyle(.white)
                    .frame(width: 20, height: 20).background(Circle().fill(Color.accentColor)).overlay(Circle().stroke(.background, lineWidth: 2))
                    .offset(x: 5, y: 5)
                }
            }
            .buttonStyle(PressStyle(scale: 0.92))
            .accessibilityLabel("更换图标")
            VStack(spacing: 8) {
              TextField("名称", text: $name).font(.system(size: 18, weight: .semibold)).focused($nameFocused)
              TextField("域名（可选）", text: $domain).keyboardType(.URL).textInputAutocapitalization(.never).autocorrectionDisabled().foregroundStyle(.secondary)
            }
          }.padding(.vertical, 4)
          TextField("账号名（可选）", text: $handle).textInputAutocapitalization(.never).autocorrectionDisabled()
          Picker("分类", selection: $category) { ForEach(CATS, id: \.self) { Text($0).tag($0) } }
        }
        if !presetHits.isEmpty || !storeHits.isEmpty {
          Section {
            ForEach(presetHits, id: \.name) { p in
              suggestion(AppIcon(name: p.name, domain: p.domain, size: 32), p.name, p.domain) { pick(p) }
            }
            if !storeHits.isEmpty {
              AppStoreBadge().padding(.top, presetHits.isEmpty ? 0 : 4)
              ForEach(storeHits) { a in
                suggestion(AppIcon(name: a.cleanName, domain: a.domain, url: a.thumbURL, size: 32), a.cleanName, a.domain.isEmpty ? a.category : a.domain) { pick(a) }
              }
            }
          } header: { Text("建议") }
          .transition(.opacity)
        }
        Section("用的邮箱和手机号") {
          IdentityToggles(selected: $ids)
        }
        if !store.index.logins.isEmpty {
          Section {
            IdentityToggles(selected: $ids, login: true)
          } header: { Text("第三方登录") } footer: { Text("选上表示这个网站用它登录。") }
        }
        Section("备注") { TextField("两步验证、找回方式…", text: $notes, axis: .vertical).lineLimit(2...6) }
      }
      .animation(Motion.snappy, value: presetHits.count + storeHits.count)
      .onChange(of: name) { if nameFocused { typed = true } }
      .appStoreResults(for: query, into: $appResults)
      .confirmationDialog("更换图标", isPresented: $iconMenu, titleVisibility: .visible) {
        Button("从相册选择") { photos = true }
        if icon != nil || art != nil { Button("使用默认图标") { setIcon(nil) } }
        Button("取消", role: .cancel) {}
      }
      .photosPicker(isPresented: $photos, selection: $photoItem, matching: .images)
      .onChange(of: photoItem) {
        guard let item = photoItem else { return }
        photoItem = nil
        iconJob &+= 1; let job = iconJob
        Task {
          let u = await IconCodec.dataURL(from: item)
          guard job == iconJob else { return }
          if let u { withAnimation(Motion.snappy) { icon = u; art = nil } } else { store.show("这张图片读不出来") }
        }
      }
      .navigationTitle("编辑").navigationBarTitleDisplayMode(.inline)
      .toolbar {
        ToolbarItem(placement: .cancellationAction) { Button("取消") { dismiss() } }
        ToolbarItem(placement: .confirmationAction) {
          Button("保存") {
            store.mutate("已保存") { s in
              if let k = s.accounts.firstIndex(where: { $0.id == existing.id }) {
                s.accounts[k].name = name.trimmingCharacters(in: .whitespaces); s.accounts[k].domain = cleanDomain(domain)
                s.accounts[k].category = category; s.accounts[k].notes = notes.trimmingCharacters(in: .whitespacesAndNewlines)
                let h = handle.trimmingCharacters(in: .whitespaces); s.accounts[k].handle = h.isEmpty ? nil : h
                s.accounts[k].identityIds = ids
                s.accounts[k].icon = icon
              }
            }
            IconStore.shared.prefetch(store.data.accounts.filter { $0.id == existing.id })
            dismiss()
          }.bold().disabled(name.trimmingCharacters(in: .whitespaces).isEmpty)
        }
      }
    }
  }

  func suggestion(_ icon: some View, _ title: String, _ sub: String, _ action: @escaping () -> Void) -> some View {
    Button(action: action) {
      HStack(spacing: 12) {
        icon
        VStack(alignment: .leading, spacing: 1) {
          Text(title).foregroundStyle(.primary).lineLimit(1)
          if !sub.isEmpty { Text(sub).font(.system(size: 12)).foregroundStyle(.secondary).lineLimit(1) }
        }
        Spacer(minLength: 0)
      }
    }
  }

  func setIcon(_ v: String?) {
    iconJob &+= 1
    withAnimation(Motion.snappy) { icon = v; art = nil }
  }
  func pick(_ p: Preset) {
    typed = false; nameFocused = false
    name = p.name; domain = p.domain; category = p.category
    setIcon(nil)
  }
  func pick(_ a: AppStoreApp) {
    typed = false; nameFocused = false
    name = a.cleanName
    if !a.domain.isEmpty { domain = a.domain }
    category = a.category
    iconJob &+= 1; let job = iconJob
    withAnimation(Motion.snappy) { icon = nil; art = a.thumbURL.isEmpty ? nil : a.thumbURL }
    Task {
      let u = await a.iconDataURL()
      guard job == iconJob else { return }
      withAnimation(Motion.snappy) { icon = u; art = nil }
    }
  }
}

struct IdentityToggles: View {
  @Environment(Store.self) var store
  @Binding var selected: [String]
  var login = false     // true: only 微信 / Apple cards; false: emails and phones
  var body: some View {
    ForEach(login ? store.index.logins : store.data.identities.filter { !$0.isLogin }) { i in
      let on = selected.contains(i.id)
      Button {
        withAnimation(Motion.snappy) { if on { selected.removeAll { $0 == i.id } } else { selected.append(i.id) } }
      } label: {
        HStack(spacing: 12) {
          IdentityGlyph(identity: i, size: 12).foregroundStyle(.white).frame(width: 36, height: 24)
            .background(skinGradient(i.color), in: CardShape(sim: i.isPhone))
          VStack(alignment: .leading, spacing: 1) {
            Text(store.title(i)).foregroundStyle(.primary)
            if !i.label.isEmpty { Text(store.display(i)).font(.system(size: 12)).foregroundStyle(.secondary) }
          }
          Spacer()
          Image(systemName: on ? "checkmark.circle.fill" : "circle").font(.system(size: 22))
            .foregroundStyle(on ? Color(hex: i.color) : Color.secondary.opacity(0.5))
            .symbolEffect(.bounce, value: on)
        }
      }
      .sensoryFeedback(.selection, trigger: on)
    }
  }
}

// MARK: - add apps: pick sites, then pick which email / phone each uses

struct PickerSheet: View {
  @Environment(Store.self) var store
  @Environment(\.dismiss) var dismiss
  let presetIdentity: String?
  @State private var query = ""
  @State private var cat = "常用"
  @State private var picked: [Preset] = []
  @State private var handles: [String: String] = [:]
  @State private var ids: [String] = []
  @State private var step2 = false
  @State private var manual = false
  @State private var mName = ""
  @State private var removing: Preset? = nil          // 我添加的 tile waiting on the remove confirmation
  @State private var appResults: [AppStoreApp] = []
  @State private var art: [String: String] = [:]      // App Store artwork per picked key, until its icon lands
  @State private var iconFor: String? = nil           // picked key whose icon menu is open
  @State private var uploadFor: String? = nil         // picked key the photo picker is choosing for
  @State private var photos = false
  @State private var photoItem: PhotosPickerItem? = nil

  var cats: [String] {
    (store.customApps.isEmpty ? [] : ["我添加的"]) + ["常用"] + CATS.filter { c in Presets.all.contains { $0.category == c } }
  }
  var list: [Preset] {
    let q = query.trimmingCharacters(in: .whitespaces).lowercased()
    let all = store.customApps + Presets.all
    if !q.isEmpty { return all.filter { $0.name.lowercased().contains(q) || $0.domain.contains(q) } }
    switch cat {
    case "我添加的": return store.customApps
    case "常用": return Array(Presets.popular.compactMap { n in Presets.all.first { $0.name == n } }.prefix(12))
    default: return Presets.all.filter { $0.category == cat }
    }
  }
  func key(_ p: Preset) -> String { p.name.lowercased() }
  var storeHits: [AppStoreApp] {
    guard !query.trimmingCharacters(in: .whitespaces).isEmpty else { return [] }
    let shown = Set(list.map(key))
    return appResults.filter { !shown.contains($0.cleanName.lowercased()) && !shown.contains($0.trackName.lowercased()) }
  }
  func preset(_ a: AppStoreApp) -> Preset { Preset(name: a.cleanName, domain: a.domain, category: a.category, color: "") }
  func setIcon(_ k: String, _ v: String?) {
    if let i = picked.firstIndex(where: { key($0) == k }) { picked[i].icon = v }
    art[k] = nil
  }

  var body: some View {
    NavigationStack {
      Group { if step2 { chooseIdentities } else { grid } }
        .animation(Motion.smooth, value: step2)
    }
    .onAppear { if let p = presetIdentity { ids = [p] } }
  }

  var grid: some View {
    ScrollView {
      VStack(alignment: .leading, spacing: 14) {
        ScrollView(.horizontal) {
          HStack(spacing: 8) {
            ForEach(cats, id: \.self) { c in
              Button { withAnimation(.snappy) { cat = c } } label: {
                Text(c).font(.system(size: 14, weight: .semibold)).padding(.horizontal, 13).frame(height: 32)
                  .foregroundStyle(cat == c && query.isEmpty ? Color.white : Color.primary)
                  .background(cat == c && query.isEmpty ? AnyShapeStyle(Color.accentColor) : AnyShapeStyle(.glass), in: Capsule())
              }.buttonStyle(PressStyle(scale: 0.94))
            }
          }.padding(.horizontal, 16)
        }.scrollIndicators(.hidden)
        LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 6, alignment: .top), count: 4), spacing: 18) {
          ForEach(list, id: \.self) { p in
            if cat == "我添加的" && query.trimmingCharacters(in: .whitespaces).isEmpty {
              tile(p)
                .contextMenu {
                  Button(role: .destructive) { askRemove(p) } label: { Label("从列表移除", systemImage: "minus.circle") }
                }
                .transition(.scale(scale: 0.6).combined(with: .opacity))
            } else { tile(p) }
          }
          Button { mName = query; manual = true } label: {
            VStack(spacing: 6) {
              Image(systemName: "plus").font(.system(size: 22, weight: .medium)).frame(width: 60, height: 60)
                .background(.glass, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
              Text(query.isEmpty ? "手动添加" : "添加“\(query)”").font(.system(size: 12)).lineLimit(1).foregroundStyle(.secondary)
            }
          }.buttonStyle(PressStyle(scale: 0.9))
        }.padding(.horizontal, 12)
        if !storeHits.isEmpty {
          AppStoreBadge().padding(.horizontal, 18).padding(.top, 4)
          LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 6, alignment: .top), count: 4), spacing: 18) {
            ForEach(storeHits) { a in storeTile(a) }
          }.padding(.horizontal, 12)
          .transition(.opacity)
        }
      }.padding(.top, 6).padding(.bottom, 90)
    }
    .scrollIndicators(.hidden)
    .appStoreResults(for: query, into: $appResults)
    .searchable(text: $query, placement: .navigationBarDrawer(displayMode: .always), prompt: "搜索 App 或网站")
    .navigationTitle("添加网站或 App").navigationBarTitleDisplayMode(.inline)
    .toolbar {
      ToolbarItem(placement: .cancellationAction) { Button("取消") { dismiss() } }
      ToolbarItem(placement: .confirmationAction) { Button(picked.isEmpty ? "下一步" : "下一步 (\(picked.count))") { step2 = true }.bold().disabled(picked.isEmpty) }
    }
    .safeAreaInset(edge: .bottom) { if !picked.isEmpty { pickedBar } }
    .sensoryFeedback(.selection, trigger: picked.count)
    .sheet(isPresented: $manual) {
      ManualAppSheet(initialName: mName) { p in
        store.rememberApp(p)
        withAnimation(Motion.snappy) {
          if let i = picked.firstIndex(where: { key($0) == key(p) }) { picked[i] = p } else { picked.append(p) }
        }
        mName = ""; query = ""
      }
    }
    .confirmationDialog("从列表移除“\(removing?.name ?? "")”？", isPresented: Binding(get: { removing != nil }, set: { if !$0 { removing = nil } }), titleVisibility: .visible) {
      Button("从列表移除", role: .destructive) { if let p = removing { remove(p) }; removing = nil }
      Button("取消", role: .cancel) { removing = nil }
    } message: { Text("只会从这个列表移除，已记录的账号不受影响。") }
    .onChange(of: store.customApps.isEmpty) { _, empty in
      if empty && cat == "我添加的" { withAnimation(Motion.snappy) { cat = "常用" } }
    }
  }

  func hasAccounts(_ p: Preset) -> Bool { store.data.accounts.contains { $0.name.lowercased() == p.name.lowercased() } }
  func askRemove(_ p: Preset) {
    if hasAccounts(p) { removing = p } else { remove(p) }
  }
  func remove(_ p: Preset) {
    withAnimation(Motion.snappy) {
      store.hideApp(p.name)
      picked.removeAll { key($0) == key(p) }
      if store.customApps.isEmpty && cat == "我添加的" { cat = "常用" }
    }
  }

  func tile(_ p: Preset) -> some View {
    let on = picked.contains { key($0) == key(p) }
    let has = hasAccounts(p)
    return Button {
      withAnimation(Motion.snappy) {
        if on { picked.removeAll { key($0) == key(p) } } else { picked.append(p) }
      }
    } label: {
      VStack(spacing: 6) {
        AppIcon(name: p.name, domain: p.domain, icon: p.icon, size: 60)
          .overlay(alignment: .topTrailing) {
            if on {
              Image(systemName: "checkmark.circle.fill").font(.system(size: 22)).symbolRenderingMode(.palette)
                .foregroundStyle(.white, Color.accentColor).background(Circle().fill(.white).padding(3)).offset(x: 7, y: -7)
                .transition(.scale.combined(with: .opacity))
            }
          }
          .scaleEffect(on ? 0.92 : 1)
        Text(p.name).font(.system(size: 12)).lineLimit(1).foregroundStyle(.primary)
        if has { Text("已记录").font(.system(size: 9.5, weight: .medium)).foregroundStyle(.secondary).padding(.top, -4) }
      }
    }.buttonStyle(.plain)
  }

  func storeTile(_ a: AppStoreApp) -> some View {
    let p = preset(a), k = key(p)
    let on = picked.contains { key($0) == k }
    return Button {
      withAnimation(Motion.snappy) {
        if on { picked.removeAll { key($0) == k }; art[k] = nil; return }
        picked.append(p)
        if !a.thumbURL.isEmpty { art[k] = a.thumbURL }
      }
      Task {
        let u = await a.iconDataURL()
        // only if it's still picked and the user hasn't chosen another icon meanwhile
        guard art[k] != nil, let u else { art[k] = nil; return }
        setIcon(k, u)
      }
    } label: {
      VStack(spacing: 6) {
        AppIcon(name: p.name, domain: p.domain, url: a.thumbURL, size: 60)
          .overlay(alignment: .topTrailing) {
            if on {
              Image(systemName: "checkmark.circle.fill").font(.system(size: 22)).symbolRenderingMode(.palette)
                .foregroundStyle(.white, Color.accentColor).background(Circle().fill(.white).padding(3)).offset(x: 7, y: -7)
                .transition(.scale.combined(with: .opacity))
            }
          }
          .scaleEffect(on ? 0.92 : 1)
        Text(p.name).font(.system(size: 12)).lineLimit(1).foregroundStyle(.primary)
      }
    }.buttonStyle(.plain)
  }
  func pickedIcon(_ p: Preset, size: CGFloat) -> AppIcon {
    AppIcon(name: p.name, domain: p.domain, icon: p.icon, url: p.icon == nil ? art[key(p)] : nil, size: size)
  }

  var pickedBar: some View {
    ScrollView(.horizontal) {
      HStack(spacing: 10) {
        ForEach(picked, id: \.name) { p in
          pickedIcon(p, size: 38).transition(.scale.combined(with: .opacity))
        }
      }.padding(.horizontal, 16).padding(.vertical, 10)
    }
    .scrollIndicators(.hidden)
    .background(.bar)
  }

  var chooseIdentities: some View {
    Form {
      Section {
        ForEach(picked, id: \.name) { p in
          HStack(spacing: 12) {
            Button { iconFor = key(p) } label: { pickedIcon(p, size: 36) }
              .buttonStyle(PressStyle(scale: 0.9)).accessibilityLabel("更换图标")
            VStack(alignment: .leading, spacing: 2) {
              Text(p.name).font(.system(size: 16, weight: .semibold))
              TextField("账号名（可选）", text: Binding(get: { handles[key(p)] ?? "" }, set: { handles[key(p)] = $0 }))
                .font(.system(size: 14)).textInputAutocapitalization(.never).autocorrectionDisabled()
            }
          }
        }
      } header: { Text("\(picked.count) 个网站与 App") } footer: { Text("同一个网站有几个账号，可以分别记一条，用账号名区分。点图标可以换成相册里的图片。") }
      Section("用哪个邮箱或手机号注册、绑定的") {
        if !store.data.identities.contains(where: { !$0.isLogin }) { Text("还没有邮箱或手机号，先保存，之后再关联也行。").foregroundStyle(.secondary) }
        IdentityToggles(selected: $ids)
      }
      if !store.index.logins.isEmpty {
        Section {
          IdentityToggles(selected: $ids, login: true)
        } header: { Text("第三方登录") } footer: { Text("选上表示用它登录这个网站。") }
      }
    }
    .confirmationDialog("更换图标", isPresented: Binding(get: { iconFor != nil }, set: { if !$0 { iconFor = nil } }), titleVisibility: .visible) {
      Button("从相册选择") { uploadFor = iconFor; iconFor = nil; photos = true }
      if let k = iconFor, let p = picked.first(where: { key($0) == k }), p.icon != nil || art[k] != nil {
        Button("使用默认图标") { withAnimation(Motion.snappy) { setIcon(k, nil) }; iconFor = nil }
      }
      Button("取消", role: .cancel) { iconFor = nil }
    }
    .photosPicker(isPresented: $photos, selection: $photoItem, matching: .images)
    .onChange(of: photoItem) {
      guard let item = photoItem, let k = uploadFor else { return }
      photoItem = nil
      art[k] = nil   // a pending App Store download no longer applies
      Task {
        if let u = await IconCodec.dataURL(from: item) { withAnimation(Motion.snappy) { setIcon(k, u) } } else { store.show("这张图片读不出来") }
      }
    }
    .navigationTitle("选择身份").navigationBarTitleDisplayMode(.inline)
    .toolbar {
      ToolbarItem(placement: .cancellationAction) { Button { step2 = false } label: { Label("返回", systemImage: "chevron.left") } }
      ToolbarItem(placement: .confirmationAction) { Button("保存") { save() }.bold() }
    }
  }

  func save() {
    let now = nowMS()
    let made: [Account] = picked.enumerated().map { e in
      let k = e.offset, p = e.element
      return Account(name: p.name, domain: p.domain, category: p.category, identityIds: ids,
              handle: (handles[key(p)] ?? "").trimmingCharacters(in: .whitespaces).isEmpty ? nil : handles[key(p)]!.trimmingCharacters(in: .whitespaces),
              createdAt: now - Double(k), icon: p.icon)
    }
    store.unhideApps(made.map(\.name))
    store.mutate(made.count == 1 ? "已添加 \(made[0].name)" : "已添加 \(made.count) 个网站与 App") { s in
      s.accounts.append(contentsOf: made)
    }
    IconStore.shared.prefetch(made)
    dismiss()
  }
}

// MARK: - 手动添加: name, domain and an icon slot (photo or the default letter / favicon)

struct ManualAppSheet: View {
  @Environment(Store.self) var store
  @Environment(\.dismiss) var dismiss
  let onAdd: (Preset) -> Void
  @State private var name: String
  @State private var domain = ""
  @State private var icon: String? = nil
  @State private var iconMenu = false
  @State private var photos = false
  @State private var photoItem: PhotosPickerItem? = nil
  @State private var iconJob = 0
  @FocusState private var nameFocused: Bool

  init(initialName: String, onAdd: @escaping (Preset) -> Void) {
    self.onAdd = onAdd
    _name = State(initialValue: initialName.trimmingCharacters(in: .whitespaces))
  }

  var trimmed: String { name.trimmingCharacters(in: .whitespaces) }

  var body: some View {
    NavigationStack {
      Form {
        Section {
          VStack(spacing: 10) {
            Button { iconMenu = true } label: {
              AppIcon(name: trimmed.isEmpty ? "?" : trimmed, domain: cleanDomain(domain), icon: icon, size: 72)
                .overlay(alignment: .bottomTrailing) {
                  Image(systemName: icon == nil ? "camera.fill" : "pencil").font(.system(size: 10, weight: .bold)).foregroundStyle(.white)
                    .frame(width: 24, height: 24).background(Circle().fill(Color.accentColor)).overlay(Circle().stroke(.background, lineWidth: 2))
                    .offset(x: 6, y: 6)
                }
            }
            .buttonStyle(PressStyle(scale: 0.92))
            .accessibilityLabel("更换图标")
            Text("点图标可以换成相册里的图片").font(.system(size: 12)).foregroundStyle(.secondary)
          }
          .frame(maxWidth: .infinity).padding(.vertical, 6)
        }
        .listRowBackground(Color.clear)
        Section {
          TextField("名称", text: $name).font(.system(size: 17, weight: .semibold)).focused($nameFocused)
            .submitLabel(.done).onSubmit { add() }
          TextField("域名（可选）", text: $domain).keyboardType(.URL).textInputAutocapitalization(.never).autocorrectionDisabled()
        } footer: { Text("名称必填，填了域名会自动找图标。") }
      }
      .animation(Motion.snappy, value: icon)
      .confirmationDialog("更换图标", isPresented: $iconMenu, titleVisibility: .visible) {
        Button("从相册选择") { photos = true }
        if icon != nil { Button("使用默认图标") { iconJob &+= 1; withAnimation(Motion.snappy) { icon = nil } } }
        Button("取消", role: .cancel) {}
      }
      .photosPicker(isPresented: $photos, selection: $photoItem, matching: .images)
      .onChange(of: photoItem) {
        guard let item = photoItem else { return }
        photoItem = nil
        iconJob &+= 1; let job = iconJob
        Task {
          let u = await IconCodec.dataURL(from: item)
          guard job == iconJob else { return }
          if let u { withAnimation(Motion.snappy) { icon = u } } else { store.show("这张图片读不出来") }
        }
      }
      .navigationTitle("手动添加").navigationBarTitleDisplayMode(.inline)
      .toolbar {
        ToolbarItem(placement: .cancellationAction) { Button("取消") { dismiss() } }
        ToolbarItem(placement: .confirmationAction) { Button("添加") { add() }.bold().disabled(trimmed.isEmpty) }
      }
      .onAppear { if trimmed.isEmpty { nameFocused = true } }
    }
    .presentationDetents([.medium, .large])
    .presentationDragIndicator(.visible)
  }

  func add() {
    let n = trimmed; guard !n.isEmpty else { return }
    let p = Preset(name: n, domain: cleanDomain(domain), category: presetFor(name: n, domain: domain)?.category ?? "其他", color: "", icon: icon)
    onAdd(p)
    dismiss()
  }
}
