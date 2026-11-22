import SwiftUI

struct WalletView: View {
  @Environment(Store.self) var store
  @Namespace private var seg

  var body: some View {
    NavigationStack {
      ScrollView {
        VStack(alignment: .leading, spacing: 0) {
          header
          KindSwitch().padding(.horizontal, 20).padding(.top, 14)
          deck.padding(.top, 14)
          dots.padding(.top, 2)
          AppGrid().padding(.horizontal, 20).padding(.top, 26)
          Timeline().padding(.horizontal, 20).padding(.top, 34)
          Color.clear.frame(height: 40)
        }
      }
      .scrollIndicators(.hidden)
      .background(Glow().ignoresSafeArea())
      .toolbar { TopButtons() }
    }
  }

  var header: some View {
    VStack(alignment: .leading, spacing: 4) {
      Text("账号").font(.system(size: 34, weight: .bold))
      Text(summary).font(.system(size: 15)).foregroundStyle(.secondary)
    }
    .padding(.horizontal, 20)
  }
  var summary: String {
    let e = store.index.emails.count, p = store.index.phones.count, l = store.index.logins.count, a = store.data.accounts.count
    if store.data.identities.isEmpty && a == 0 { return "先添加一个邮箱或手机号" }
    return "\(e) 个邮箱、\(p) 个手机号" + (l > 0 ? "、\(l) 个登录账号" : "") + "，关联着 \(a) 个网站与 App"
  }

  // coverflow of identity cards
  var deck: some View {
    ScrollView(.horizontal) {
      LazyHStack(spacing: 14) {
        ForEach(store.deck) { i in
          Button { store.route = .identity(i.id) } label: { WalletCard(identity: i) }
            .buttonStyle(PressStyle(scale: 0.97))
            .containerRelativeFrame(.horizontal)
            .scrollTransition(.interactive, axis: .horizontal) { c, phase in
              c.rotation3DEffect(.degrees(phase.value * -18), axis: (x: 0, y: 1, z: 0), perspective: 0.6)
                .scaleEffect(1 - abs(phase.value) * 0.1)
                .opacity(1 - abs(phase.value) * 0.35)
            }
            .id(i.id)
        }
        AddCard(kind: store.kind)
          .containerRelativeFrame(.horizontal)
          .scrollTransition(.interactive, axis: .horizontal) { c, phase in
            c.scaleEffect(1 - abs(phase.value) * 0.1).opacity(1 - abs(phase.value) * 0.35)
          }
          .id("add")
      }
      .scrollTargetLayout()
      .padding(.vertical, 26)
    }
    .scrollIndicators(.hidden)
    .scrollClipDisabled()
    .scrollTargetBehavior(.viewAligned)
    .scrollPosition(id: Binding(get: { store.activeID }, set: { v in if let v { store.activeID = v } }))
    .safeAreaPadding(.horizontal, 34)
    .sensoryFeedback(.selection, trigger: store.activeID)
    .id(store.kind)
    .transition(.asymmetric(insertion: .move(edge: store.kind == "email" ? .leading : .trailing).combined(with: .opacity), removal: .opacity))
  }
  var dots: some View {
    HStack(spacing: 6) {
      ForEach(store.deck.map(\.id) + ["add"], id: \.self) { id in
        Capsule().fill(id == (store.activeID ?? store.deck.first?.id ?? "add") ? Color.primary : Color.secondary.opacity(0.35))
          .frame(width: id == store.activeID ? 16 : 6, height: 6)
      }
    }
    .frame(maxWidth: .infinity)
    .animation(Motion.snappy, value: store.activeID)
  }
}

struct Glow: View {
  @Environment(Store.self) var store
  @Environment(\.colorScheme) var scheme
  var body: some View {
    let c = Color(hex: store.active?.color ?? "#3a3a3c")
    ZStack {
      (scheme == .dark ? Color.black : Color(hex: "#f2f2f7"))
      RadialGradient(colors: [c.opacity(scheme == .dark ? 0.55 : 0.32), .clear], center: UnitPoint(x: 0.5, y: 0.18), startRadius: 0, endRadius: 420)
        .animation(Motion.gentle, value: store.activeID)
    }
  }
}

struct TopButtons: ToolbarContent {
  @Environment(Store.self) var store
  var body: some ToolbarContent {
    ToolbarItemGroup(placement: .topBarTrailing) {
      Button {
        ThemeFX.set(store.theme == "light" ? "dark" : "light", store: store)
      } label: {
        Image(systemName: store.theme == "light" ? "moon.fill" : "sun.max.fill")
          .contentTransition(.symbolEffect(.replace.downUp))
          .background(ThemeOrigin())
      }
      Menu {
        Button { store.route = .picker(store.active?.id) } label: { Label("添加网站或 App", systemImage: "square.grid.2x2") }
        Button { store.route = .newIdentity("email") } label: { Label("添加邮箱", systemImage: "envelope") }
        Button { store.route = .newIdentity("phone") } label: { Label("添加手机号", systemImage: "simcard") }
        Button { store.route = .newIdentity("login") } label: { Label("添加微信或 Apple 账号", systemImage: "person.badge.key") }
      } label: { Image(systemName: "plus") }
      Button { store.route = .settings } label: { Image(systemName: "ellipsis") }
    }
  }
}

struct KindSwitch: View {
  @Environment(Store.self) var store
  @Namespace private var ns
  var body: some View {
    HStack(spacing: 0) {
      ForEach([("email", "邮箱", "envelope"), ("phone", "手机号", "simcard"), ("login", "登录", "person.badge.key")], id: \.0) { t in
        let k = t.0, name = t.1, icon = t.2
        let on = store.kind == k
        Button {
          withAnimation(Motion.smooth) { store.setKind(k) }
        } label: {
          HStack(spacing: 6) {
            Image(systemName: icon).font(.system(size: 14, weight: .semibold))
            Text(name).font(.system(size: 15, weight: .semibold)).lineLimit(1)
            Text("\(k == "phone" ? store.index.phones.count : k == "login" ? store.index.logins.count : store.index.emails.count)").font(.system(size: 13, weight: .medium)).foregroundStyle(.secondary)
          }
          .frame(maxWidth: .infinity).frame(height: 36)
          .foregroundStyle(on ? .primary : .secondary)
          .background {
            if on { Capsule().fill(.background.opacity(0.9)).shadow(color: .black.opacity(0.18), radius: 6, y: 2).matchedGeometryEffect(id: "thumb", in: ns) }
          }
          .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
      }
    }
    .padding(3)
    .background(.glass, in: Capsule())
    .sensoryFeedback(.selection, trigger: store.kind)
  }
}

struct AddCard: View {
  @Environment(Store.self) var store
  let kind: String
  var body: some View {
    Button { store.route = .newIdentity(kind) } label: {
      VStack(spacing: 10) {
        Image(systemName: "plus").font(.system(size: 26, weight: .medium))
        Text(kind == "login" ? "添加微信或 Apple 账号" : store.data.identities.isEmpty ? "从一个邮箱或手机号开始" : (kind == "phone" ? "添加手机号" : "添加邮箱")).font(.system(size: 16, weight: .semibold))
      }
      .foregroundStyle(.secondary)
      .frame(maxWidth: .infinity, maxHeight: .infinity)
      .aspectRatio(1.586, contentMode: .fit)
      .background(CardShape(sim: kind == "phone").fill(.glass))
      .overlay(CardShape(sim: kind == "phone").stroke(style: StrokeStyle(lineWidth: 1.2, dash: [6, 5])).foregroundStyle(.secondary.opacity(0.5)))
    }
    .buttonStyle(PressStyle())
  }
}

// icons of what the front card unlocks
struct AppGrid: View {
  @Environment(Store.self) var store
  var body: some View {
    let i = store.active
    let list = i.map { store.accounts(of: $0.id) } ?? store.index.byName
    let more = list.count > 11 ? list.count - 11 : 0
    let shown = more > 0 ? Array(list.prefix(11)) : list
    VStack(alignment: .leading, spacing: 14) {
      HStack(alignment: .firstTextBaseline) {
        VStack(alignment: .leading, spacing: 2) {
          Text(i.map { store.title($0) } ?? "全部网站与 App").font(.system(size: 22, weight: .bold)).lineLimit(1)
          if let i { Text((i.isLogin ? "用它登录的 " : "用它注册或绑定的 ") + "\(list.count) 个网站与 App").font(.system(size: 13)).foregroundStyle(.secondary) }
        }
        Spacer()
        Button { store.route = .picker(i?.id) } label: { Label("添加", systemImage: "plus").font(.system(size: 14, weight: .semibold)) }
          .buttonStyle(.bordered).buttonBorderShape(.capsule).tint(Color(hex: i?.color ?? "#0a84ff"))
      }
      LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 8, alignment: .top), count: 4), spacing: 16) {
        ForEach(shown) { a in
          Button { store.route = .account(a.id) } label: {
            VStack(spacing: 6) {
              AppIcon(name: a.name, domain: a.domain, icon: a.icon, size: 58)
                .bakedShadow(RoundedRectangle(cornerRadius: 58 * 0.235, style: .continuous), key: "icon58" + a.name + a.domain,
                             color: brandColor(name: a.name, domain: a.domain).opacity(0.35), radius: 8, y: 4)
              Text(a.handle.map { store.handleText($0) } ?? a.name).font(.system(size: 12)).lineLimit(1).foregroundStyle(.primary)
            }
          }
          .buttonStyle(PressStyle(scale: 0.9))
          .transition(.scale(scale: 0.4).combined(with: .opacity))
        }
        if more > 0, let i {
          Button { store.route = .identity(i.id) } label: {
            VStack(spacing: 6) {
              Text("+\(more)").font(.system(size: 18, weight: .bold, design: .rounded)).frame(width: 58, height: 58)
                .background(.glass, in: RoundedRectangle(cornerRadius: 58 * 0.235, style: .continuous))
              Text("全部").font(.system(size: 12)).foregroundStyle(.secondary)
            }
          }.buttonStyle(PressStyle(scale: 0.9))
        } else if let i {
          Button { store.route = .picker(i.id) } label: {
            VStack(spacing: 6) {
              Image(systemName: "plus").font(.system(size: 20, weight: .medium)).frame(width: 58, height: 58)
                .background(.glass, in: RoundedRectangle(cornerRadius: 58 * 0.235, style: .continuous))
              Text("添加").font(.system(size: 12)).foregroundStyle(.secondary)
            }
          }.buttonStyle(PressStyle(scale: 0.9))
        }
      }
      .animation(Motion.smooth, value: store.activeID)
      .padding(16)
      .background(.glass.opacity(0.7), in: RoundedRectangle(cornerRadius: 26, style: .continuous))
      if list.isEmpty && i == nil { Text("还没有网站，从右上角的 + 开始。").font(.system(size: 14)).foregroundStyle(.secondary) }
    }
  }
}

struct Timeline: View {
  @Environment(Store.self) var store
  var body: some View {
    let list = store.index.recent
    let cal = Calendar.current
    let groups: [(Date, [Account])] = list.reduce(into: []) { g, a in
      let d = cal.startOfDay(for: Date(timeIntervalSince1970: a.createdAt / 1000))
      if let k = g.firstIndex(where: { $0.0 == d }) { g[k].1.append(a) } else { g.append((d, [a])) }
    }
    VStack(alignment: .leading, spacing: 14) {
      Text("最近添加").font(.system(size: 22, weight: .bold))
      if list.isEmpty { Text("添加的网站会按时间出现在这里。").font(.system(size: 14)).foregroundStyle(.secondary) }
      ForEach(groups, id: \.0) { g in
        let day = g.0, items = g.1
        HStack(alignment: .top, spacing: 12) {
          dateLabel(day).frame(width: 44)
          VStack(spacing: 8) {
            ForEach(items) { a in
              Button { store.route = .account(a.id) } label: { ReelRow(account: a, meta: a.category) }.buttonStyle(PressStyle(scale: 0.98))
            }
          }
        }
      }
    }
  }
  @ViewBuilder func dateLabel(_ d: Date) -> some View {
    let cal = Calendar.current
    if cal.isDateInToday(d) || cal.isDateInYesterday(d) {
      Text(cal.isDateInToday(d) ? "今天" : "昨天").font(.system(size: 15, weight: .bold)).foregroundStyle(cal.isDateInToday(d) ? Color.accentColor : .primary).padding(.top, 14)
    } else {
      VStack(spacing: 0) {
        Text("\(cal.component(.month, from: d))月").font(.system(size: 11, weight: .medium)).foregroundStyle(.secondary)
        Text("\(cal.component(.day, from: d))").font(.system(size: 24, weight: .bold, design: .rounded))
      }.padding(.top, 6)
    }
  }
}
