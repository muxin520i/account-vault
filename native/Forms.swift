import SwiftUI

struct IdentityForm: View {
  @Environment(Store.self) var store
  @Environment(\.dismiss) var dismiss
  let existing: Identity?
  @State private var type: String
  @State private var value: String
  @State private var label: String
  @State private var color: String
  @State private var notes: String

  init(existing: Identity?, type: String, defaultColor: String) {
    self.existing = existing
    _type = State(initialValue: existing?.type ?? type)
    _value = State(initialValue: existing?.value ?? "")
    _label = State(initialValue: existing?.label ?? "")
    _color = State(initialValue: existing?.color ?? defaultColor)
    _notes = State(initialValue: existing?.notes ?? "")
  }

  var preview: Identity {
    var i = Identity(id: existing?.id ?? "_new", type: type, value: value.isEmpty ? (type == "email" ? "name@example.com" : "+86 138 0000 0000") : value, label: label, color: color, notes: notes)
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
          }.pickerStyle(.segmented)
        }
        .listRowBackground(Color.clear).listRowInsets(EdgeInsets(top: 4, leading: 0, bottom: 4, trailing: 0))
        Section {
          TextField(type == "email" ? "name@example.com" : "+86 138 0000 0000", text: $value)
            .keyboardType(type == "email" ? .emailAddress : .phonePad).textInputAutocapitalization(.never).autocorrectionDisabled()
          TextField("标签：主力、小号、工作…", text: $label)
        } header: { Text(type == "email" ? "地址" : "号码") }
        Section("卡面") {
          LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 10), count: 5), spacing: 12) {
            ForEach(SKINS, id: \.0) { s in
              Button { withAnimation(Motion.snappy) { color = s.0 } } label: {
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
          TextField("运营商、是否实名、找回方式", text: $notes, axis: .vertical).lineLimit(2...6)
        } header: { Text("备注") } footer: { Text("不要在这里填写密码。") }
      }
      .scrollContentBackground(.hidden)
      .background(skinGradient(color).opacity(0.18).ignoresSafeArea())
      .navigationTitle(existing == nil ? "新身份" : "编辑").navigationBarTitleDisplayMode(.inline)
      .toolbar {
        ToolbarItem(placement: .cancellationAction) { Button("取消") { dismiss() } }
        ToolbarItem(placement: .confirmationAction) { Button(existing == nil ? "添加" : "保存") { save() }.bold().disabled(value.trimmingCharacters(in: .whitespaces).isEmpty) }
      }
    }
  }

  func save() {
    let v = value.trimmingCharacters(in: .whitespaces)
    if let e = existing {
      store.mutate("已保存") { s in
        if let k = s.identities.firstIndex(where: { $0.id == e.id }) {
          s.identities[k].type = type; s.identities[k].value = v; s.identities[k].label = label.trimmingCharacters(in: .whitespaces)
          s.identities[k].color = color; s.identities[k].notes = notes.trimmingCharacters(in: .whitespacesAndNewlines)
        }
      }
      store.setKind(type, focus: e.id)
    } else {
      let i = Identity(type: type, value: v, label: label.trimmingCharacters(in: .whitespaces), color: color, notes: notes.trimmingCharacters(in: .whitespacesAndNewlines))
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

  init(existing: Account) {
    self.existing = existing
    _name = State(initialValue: existing.name); _domain = State(initialValue: existing.domain)
    _category = State(initialValue: existing.category); _handle = State(initialValue: existing.handle ?? "")
    _notes = State(initialValue: existing.notes); _ids = State(initialValue: existing.identityIds)
  }

  var body: some View {
    NavigationStack {
      Form {
        Section {
          HStack(spacing: 14) {
            AppIcon(name: name, domain: domain, size: 54)
            VStack(spacing: 8) {
              TextField("名称", text: $name).font(.system(size: 18, weight: .semibold))
              TextField("域名（可选）", text: $domain).keyboardType(.URL).textInputAutocapitalization(.never).autocorrectionDisabled().foregroundStyle(.secondary)
            }
          }.padding(.vertical, 4)
          TextField("账号名（可选）", text: $handle).textInputAutocapitalization(.never).autocorrectionDisabled()
          Picker("分类", selection: $category) { ForEach(CATS, id: \.self) { Text($0).tag($0) } }
        }
        Section("用的邮箱和手机号") {
          IdentityToggles(selected: $ids)
        }
        Section("备注") { TextField("两步验证、找回方式…", text: $notes, axis: .vertical).lineLimit(2...6) }
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
              }
            }
            dismiss()
          }.bold().disabled(name.trimmingCharacters(in: .whitespaces).isEmpty)
        }
      }
    }
  }
}

struct IdentityToggles: View {
  @Environment(Store.self) var store
  @Binding var selected: [String]
  var body: some View {
    ForEach(store.data.identities) { i in
      let on = selected.contains(i.id)
      Button {
        withAnimation(Motion.snappy) { if on { selected.removeAll { $0 == i.id } } else { selected.append(i.id) } }
      } label: {
        HStack(spacing: 12) {
          Image(systemName: i.isPhone ? "simcard.fill" : "envelope.fill").font(.system(size: 12)).foregroundStyle(.white).frame(width: 36, height: 24)
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
  @State private var mDomain = ""

  var cats: [String] {
    (store.customApps.isEmpty ? [] : ["我添加的"]) + ["常用"] + CATS.filter { c in Presets.all.contains { $0.category == c } }
  }
  var list: [Preset] {
    let q = query.trimmingCharacters(in: .whitespaces).lowercased()
    let all = store.customApps + Presets.all
    if !q.isEmpty { return all.filter { $0.name.lowercased().contains(q) || $0.domain.contains(q) } }
    switch cat {
    case "我添加的": return store.customApps
    case "常用": return Array(store.customApps.prefix(4)) + Presets.popular.compactMap { n in Presets.all.first { $0.name == n } }
    default: return Presets.all.filter { $0.category == cat }
    }
  }
  func key(_ p: Preset) -> String { p.name.lowercased() }

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
          ForEach(list, id: \.self) { p in tile(p) }
          Button { manual = true; mName = query } label: {
            VStack(spacing: 6) {
              Image(systemName: "plus").font(.system(size: 22, weight: .medium)).frame(width: 60, height: 60)
                .background(.glass, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
              Text(query.isEmpty ? "手动添加" : "添加“\(query)”").font(.system(size: 12)).lineLimit(1).foregroundStyle(.secondary)
            }
          }.buttonStyle(PressStyle(scale: 0.9))
        }.padding(.horizontal, 12)
      }.padding(.top, 6).padding(.bottom, 90)
    }
    .scrollIndicators(.hidden)
    .searchable(text: $query, placement: .navigationBarDrawer(displayMode: .always), prompt: "搜索 App 或网站")
    .navigationTitle("添加网站或 App").navigationBarTitleDisplayMode(.inline)
    .toolbar {
      ToolbarItem(placement: .cancellationAction) { Button("取消") { dismiss() } }
      ToolbarItem(placement: .confirmationAction) { Button(picked.isEmpty ? "下一步" : "下一步 (\(picked.count))") { step2 = true }.bold().disabled(picked.isEmpty) }
    }
    .safeAreaInset(edge: .bottom) { if !picked.isEmpty { pickedBar } }
    .sensoryFeedback(.selection, trigger: picked.count)
    .alert("手动添加", isPresented: $manual) {
      TextField("名称", text: $mName)
      TextField("域名（可选）", text: $mDomain).textInputAutocapitalization(.never).keyboardType(.URL)
      Button("取消", role: .cancel) {}
      Button("添加") {
        let n = mName.trimmingCharacters(in: .whitespaces); guard !n.isEmpty else { return }
        let p = Preset(name: n, domain: cleanDomain(mDomain), category: presetFor(name: n, domain: mDomain)?.category ?? "其他", color: "")
        store.rememberApp(p)
        if !picked.contains(where: { key($0) == key(p) }) { picked.append(p) }
        mName = ""; mDomain = ""; query = ""
      }
    } message: { Text("名称必填，填了域名会自动找图标。") }
  }

  func tile(_ p: Preset) -> some View {
    let on = picked.contains { key($0) == key(p) }
    let has = store.data.accounts.contains { $0.name.lowercased() == p.name.lowercased() }
    return Button {
      withAnimation(Motion.snappy) {
        if on { picked.removeAll { key($0) == key(p) } } else { picked.append(p) }
      }
    } label: {
      VStack(spacing: 6) {
        AppIcon(name: p.name, domain: p.domain, size: 60)
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

  var pickedBar: some View {
    ScrollView(.horizontal) {
      HStack(spacing: 10) {
        ForEach(picked, id: \.self) { p in
          AppIcon(name: p.name, domain: p.domain, size: 38).transition(.scale.combined(with: .opacity))
        }
      }.padding(.horizontal, 16).padding(.vertical, 10)
    }
    .scrollIndicators(.hidden)
    .background(.bar)
  }

  var chooseIdentities: some View {
    Form {
      Section {
        ForEach(picked, id: \.self) { p in
          HStack(spacing: 12) {
            AppIcon(name: p.name, domain: p.domain, size: 36)
            VStack(alignment: .leading, spacing: 2) {
              Text(p.name).font(.system(size: 16, weight: .semibold))
              TextField("账号名（可选）", text: Binding(get: { handles[key(p)] ?? "" }, set: { handles[key(p)] = $0 }))
                .font(.system(size: 14)).textInputAutocapitalization(.never).autocorrectionDisabled()
            }
          }
        }
      } header: { Text("\(picked.count) 个网站与 App") } footer: { Text("同一个网站有几个账号，可以分别记一条，用账号名区分。") }
      Section("用哪个邮箱或手机号注册、绑定的") {
        if store.data.identities.isEmpty { Text("还没有邮箱或手机号，先保存，之后再关联也行。").foregroundStyle(.secondary) }
        IdentityToggles(selected: $ids)
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
              createdAt: now - Double(k))
    }
    store.mutate(made.count == 1 ? "已添加 \(made[0].name)" : "已添加 \(made.count) 个网站与 App") { s in
      s.accounts.append(contentsOf: made)
    }
    dismiss()
  }
}
