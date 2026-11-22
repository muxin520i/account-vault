import SwiftUI

@main
struct AtlasApp: App {
  @State private var store = Store()
  @StateObject private var sync = Sync()
  @Environment(\.scenePhase) private var phase

  var body: some Scene {
    WindowGroup {
      RootView()
        .environment(store)
        .environmentObject(sync)
        .onAppear {
          ThemeFX.apply(store.theme)
          sync.store = store
          let s = sync
          store.onSaved = { [weak s] in s?.schedule() }
          if store.data.demo == true && UserDefaults.standard.string(forKey: "atlasDemo") == nil {
            store.show("这是示例数据", action: "清空") {
              store.mutate(nil) { s in s.identities = []; s.accounts = [] }
              store.setKind(store.kind)
            }
          }
          Task { await sync.run() }
        }
        .onChange(of: phase) { _, p in if p == .active { Task { await sync.run() } } }
    }
  }
}

struct RootView: View {
  @Environment(Store.self) var store
  @State private var tab = 0
  @State private var dir: CGFloat = 1
  @State private var showSplash = true

  var body: some View {
    TabView(selection: Binding(get: { tab }, set: { t in dir = t >= tab ? 1 : -1; tab = t })) {
      WalletView().tabEnter(0, tab: tab, dir: dir).tag(0).tabItem { Label("卡包", systemImage: "rectangle.stack.fill") }
      SitesView().tabEnter(1, tab: tab, dir: dir).tag(1).tabItem { Label("全部 App", systemImage: "square.grid.2x2.fill") }
      MapView().tabEnter(2, tab: tab, dir: dir).tag(2).tabItem { Label("关系图", systemImage: "point.3.connected.trianglepath.dotted") }
    }
    .environment(\.selectedTab, tab)
    .sensoryFeedback(.selection, trigger: tab)
    .onChange(of: store.theme) { _, t in ThemeFX.apply(t) }
    .sheet(item: Binding(get: { store.route }, set: { r in store.route = r })) { r in sheet(r) }
    .overlay(alignment: .top) { ToastView() }
    .overlay { if showSplash { SplashView { showSplash = false } } }
    .onAppear {
      // launch arguments used by the CI preview screenshots
      let d = UserDefaults.standard
      tab = d.integer(forKey: "atlasTab")
      switch d.string(forKey: "atlasRoute") {
      case "picker": store.route = .picker(store.active?.id)
      case "identity": if let i = store.active { store.route = .identity(i.id) }
      case "settings": store.route = .settings
      case "new": store.route = .newIdentity("email")
      default: break
      }
      if let demo = d.string(forKey: "atlasDemo") { Task { await runDemo(demo) } }
    }
  }

  // Scripted walkthroughs used to record the promo clips in CI (-atlasDemo <name>).
  func go(_ t: Int) { dir = t >= tab ? 1 : -1; tab = t }
  func wait(_ s: Double) async { try? await Task.sleep(for: .seconds(s)) }
  func swipe(_ id: String?) { withAnimation(.spring(duration: 0.55, bounce: 0)) { store.activeID = id } }
  @MainActor func runDemo(_ name: String) async {
    await wait(3.2)
    switch name {
    case "wallet":
      for id in store.deck.map(\.id).dropFirst() { swipe(id); await wait(1.5) }
      await wait(0.4); swipe(store.deck.first?.id)
    case "kind":
      withAnimation(Motion.smooth) { store.setKind("phone") }
      await wait(1.6)
      if store.deck.count > 1 { swipe(store.deck[1].id); await wait(1.5) }
      withAnimation(Motion.smooth) { store.setKind("email") }
    case "map":
      go(2); await wait(2.6)
      if let a = store.data.identities.first?.id { store.demoFocus = a; await wait(1.6) }
      if store.data.identities.count > 2 { store.demoFocus = store.data.identities[2].id; await wait(1.6) }
      store.demoFocus = "none"
    case "theme":
      ThemeFX.set("light", store: store); await wait(1.8)
      ThemeFX.set("dark", store: store); await wait(1.8)
      go(2); await wait(1.4)
      ThemeFX.set("light", store: store)
    case "tabs":
      go(1); await wait(1.4); go(2); await wait(2.2); go(0)
    case "picker":
      store.route = .picker(store.active?.id)
    default: break
    }
  }

  func sheet(_ r: Route) -> some View {
    SheetHost(route: r)
      .environment(store)
      .presentationDragIndicator(.visible)
      .presentationCornerRadius(34)
  }
}

struct SheetHost: View {
  @Environment(Store.self) var store
  let route: Route
  var body: some View {
    switch route {
    case .identity(let id): IdentityDetail(id: id)
    case .account(let id): AccountDetail(id: id)
    case .newIdentity(let t): IdentityForm(existing: nil, type: t, defaultColor: COLORS[store.data.identities.count % COLORS.count])
    case .editIdentity(let id): IdentityForm(existing: store.identity(id), type: "email", defaultColor: COLORS[0])
    case .editAccount(let id): AccountFormHost(id: id)
    case .picker(let pre): PickerSheet(presetIdentity: pre)
    case .settings: SettingsSheet()
    }
  }
}

struct AccountFormHost: View {
  @Environment(Store.self) var store
  let id: String
  var body: some View {
    if let a = store.account(id) { AccountForm(existing: a) }
  }
}

struct ToastView: View {
  @Environment(Store.self) var store
  var body: some View {
    if let t = store.toast {
      HStack(spacing: 12) {
        Image(uiImage: UIImage(named: "AppIcon60x60") ?? UIImage(systemName: "rectangle.stack.fill")!)
          .resizable().frame(width: 38, height: 38).clipShape(RoundedRectangle(cornerRadius: 9, style: .continuous))
        VStack(alignment: .leading, spacing: 1) {
          Text("账号仓库").font(.system(size: 13, weight: .semibold)).foregroundStyle(.secondary)
          Text(t.text).font(.system(size: 15, weight: .semibold))
        }
        Spacer(minLength: 8)
        if let a = t.action {
          Button(a) { store.toastAction?(); withAnimation(Motion.snappy) { store.toast = nil } }.font(.system(size: 15, weight: .semibold))
        }
      }
      .padding(.horizontal, 14).padding(.vertical, 11)
      .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
      .bakedShadow(RoundedRectangle(cornerRadius: 24, style: .continuous), key: "toast", color: .black.opacity(0.25), radius: 20, y: 8)
      .padding(.horizontal, 14)
      .transition(.move(edge: .top).combined(with: .opacity))
      .id(t.id)
      .gesture(DragGesture().onEnded { v in if v.translation.height < -10 { withAnimation(Motion.snappy) { store.toast = nil } } })
      .sensoryFeedback(.success, trigger: t.id)
    }
  }
}

// MARK: - Motion

private struct SelectedTabKey: EnvironmentKey { static let defaultValue = 0 }
extension EnvironmentValues {
  var selectedTab: Int { get { self[SelectedTabKey.self] } set { self[SelectedTabKey.self] = newValue } }
}

/// The page you switch to slides in a little from the side you came from and settles with a
/// critically-damped spring, like a navigation push but lighter. Hidden state is set while the
/// page is off screen, so it never flashes before animating.
struct TabEnter: ViewModifier {
  let index: Int, tab: Int, dir: CGFloat
  @State private var shown: Bool
  @State private var selected: Bool
  init(index: Int, tab: Int, dir: CGFloat) {
    self.index = index; self.tab = tab; self.dir = dir
    _shown = State(initialValue: index == tab); _selected = State(initialValue: index == tab)
  }
  func body(content: Content) -> some View {
    // Scoped animation: only the opacity/offset animate. The page's own layout (large title,
    // scroll insets) is never pulled into the spring, so the title doesn't slide around.
    content
      .animation(Motion.tab) {
        $0.opacity(shown ? 1 : 0.35).offset(x: shown ? 0 : dir * 28)
      }
      .onChange(of: tab) { old, t in
        selected = t == index
        if t == index { shown = true }
        else if old == index {
          // The tab bar cross-fades the outgoing page for a moment; dimming it right away showed
          // as a flicker. Re-arm the entrance only once it's fully off screen.
          DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
            guard !selected else { return }
            var tr = Transaction(); tr.disablesAnimations = true
            withTransaction(tr) { shown = false }
          }
        }
      }
  }
}
extension View {
  func tabEnter(_ index: Int, tab: Int, dir: CGFloat) -> some View { modifier(TabEnter(index: index, tab: tab, dir: dir)) }
}

/// Theme switch, done outside SwiftUI so it can't stutter:
/// 1. `snapshotView(afterScreenUpdates: false)` of the screen — a render-server copy of what's on
///    screen right now (no CPU redraw, unlike drawHierarchy/UIGraphicsImageRenderer, which cost a
///    full-screen render on the main thread before anything moved).
/// 2. It goes into an overlay window above the app, and in the same CA transaction the app windows'
///    interface style flips once (the store change runs with animations disabled; there's no
///    preferredColorScheme, so SwiftUI doesn't run a second implicit cross-fade).
/// 3. A circular hole opens in the overlay with a CAShapeLayer path animation. Path animations run
///    in the render server at the display's refresh rate (120 Hz on ProMotion), and because the
///    animation is committed in the same transaction as the re-rendered UI, the first revealed
///    pixels are already the new theme. The haptic fires from animationDidStart, i.e. on the frame
///    the circle starts moving.
@MainActor enum ThemeFX {
  static var origin: CGPoint? = nil
  static var overlay: UIWindow? = nil
  static func style(_ t: String) -> UIUserInterfaceStyle { t == "light" ? .light : t == "system" ? .unspecified : .dark }
  static func appWindows() -> [UIWindow] {
    UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.flatMap(\.windows).filter { $0 !== overlay }
  }
  private static func commit(_ theme: String, store: Store, _ wins: [UIWindow]) {
    var tx = Transaction(); tx.disablesAnimations = true
    withTransaction(tx) { store.theme = theme }
    let st = style(theme)
    wins.forEach { if $0.overrideUserInterfaceStyle != st { $0.overrideUserInterfaceStyle = st } }
  }
  /// Entry point for user actions: snapshot BEFORE any state changes.
  static func set(_ theme: String, store: Store, from point: CGPoint? = nil) {
    guard store.theme != theme else { return }
    let wins = appWindows()
    let key = wins.first(where: \.isKeyWindow) ?? wins.first
    let screenSnap: UIView? = key?.windowScene?.screen.snapshotView(afterScreenUpdates: false)
    let snapOpt: UIView? = screenSnap ?? key?.snapshotView(afterScreenUpdates: false)
    let haptic = UIImpactFeedbackGenerator(style: .light)
    haptic.prepare()
    guard let win = key, let scene = win.windowScene,
          !UIAccessibility.isReduceMotionEnabled, overlay == nil, let snap = snapOpt else {
      commit(theme, store: store, wins); haptic.impactOccurred(); return
    }
    let b = scene.screen.bounds
    let ov = UIWindow(windowScene: scene)
    ov.frame = b; ov.windowLevel = .statusBar + 1; ov.isUserInteractionEnabled = false
    ov.backgroundColor = .clear
    let holder = UIView(frame: b)
    holder.isUserInteractionEnabled = false
    snap.frame = b
    holder.addSubview(snap)
    ov.addSubview(holder)

    let c = point ?? origin ?? CGPoint(x: b.maxX - 40, y: b.minY + 70)
    let r = hypot(max(c.x, b.width - c.x), max(c.y, b.height - c.y)) + 4
    func hole(_ rad: CGFloat) -> CGPath {
      let p = UIBezierPath(rect: b)
      p.append(UIBezierPath(arcCenter: c, radius: rad, startAngle: 0, endAngle: .pi * 2, clockwise: true))
      return p.cgPath
    }
    let mask = CAShapeLayer(); mask.fillRule = .evenOdd; mask.frame = b
    holder.layer.mask = mask

    CATransaction.begin()
    CATransaction.setDisableActions(true)
    mask.path = hole(r)
    ov.isHidden = false; overlay = ov
    commit(theme, store: store, wins)

    let a = CABasicAnimation(keyPath: "path")
    a.fromValue = hole(0.5); a.toValue = hole(r)
    a.duration = 0.52
    a.timingFunction = CAMediaTimingFunction(controlPoints: 0.32, 0.72, 0, 1)   // fast out, long settle
    a.fillMode = .backwards
    let d = RevealDelegate()
    d.started = { haptic.impactOccurred() }
    d.finished = { ov.isHidden = true; ov.subviews.forEach { $0.removeFromSuperview() }; if overlay === ov { overlay = nil } }
    a.delegate = d
    mask.add(a, forKey: "reveal")
    CATransaction.commit()
  }
  /// Keeps windows in sync with the stored theme (launch, external changes). No-op when equal.
  static func apply(_ theme: String) {
    let st = style(theme)
    appWindows().forEach { if $0.overrideUserInterfaceStyle != st { $0.overrideUserInterfaceStyle = st } }
  }
}

final class RevealDelegate: NSObject, CAAnimationDelegate {
  var started: (() -> Void)?
  var finished: (() -> Void)?
  func animationDidStart(_ anim: CAAnimation) { started?(); started = nil }
  func animationDidStop(_ anim: CAAnimation, finished flag: Bool) { finished?(); finished = nil }
}

/// Records where a control sits on screen, so the theme reveal starts from it.
struct ThemeOrigin: View {
  var body: some View {
    GeometryReader { g in
      Color.clear.preference(key: OriginKey.self, value: CGPoint(x: g.frame(in: .global).midX, y: g.frame(in: .global).midY))
    }
    .onPreferenceChange(OriginKey.self) { p in Task { @MainActor in ThemeFX.origin = p } }
  }
}
struct OriginKey: PreferenceKey {
  static var defaultValue: CGPoint = .zero
  static func reduce(value: inout CGPoint, nextValue: () -> CGPoint) { value = nextValue() }
}

/// 启动开屏图：显示约 1.6 秒后淡出。图片由编译脚本打进 App bundle 根目录。
struct SplashView: View {
  var onDone: () -> Void
  @State private var opacity = 1.0
  var body: some View {
    ZStack {
      if let path = Bundle.main.path(forResource: "unlock-bg", ofType: "jpg"),
         let img = UIImage(contentsOfFile: path) {
        Image(uiImage: img).resizable().scaledToFill().ignoresSafeArea().clipped()
      } else {
        Color.black.ignoresSafeArea()
      }
      Color.black.opacity(0.28).ignoresSafeArea()
      VStack(spacing: 14) {
        Text("账号仓库").font(.system(size: 30, weight: .bold)).foregroundStyle(.white)
        Text("ACCOUNT VAULT").font(.system(size: 13, weight: .medium)).foregroundStyle(.white.opacity(0.8))
      }
    }
    .opacity(opacity)
    .onAppear {
      withAnimation(.easeOut(duration: 0.42)) { opacity = 0 }
      DispatchQueue.main.asyncAfter(deadline: .now() + 1.6) { onDone() }
    }
  }
}
