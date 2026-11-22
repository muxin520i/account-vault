import SwiftUI
import UIKit

// identities on the left, apps on the right, joined by threads in the identity's colour
struct MapView: View {
  @Environment(Store.self) var store
  @State private var focus: String? = nil
  @Environment(\.selectedTab) private var tab
  @State private var drawn = false   // threads drawn in, nodes settled
  @State private var visible = false

  let cardH: CGFloat = 64, cardGap: CGFloat = 12, appH: CGFloat = 46, appGap: CGFloat = 8, groupGap: CGFloat = 30

  /// Every visit replays the build-up. The reset happens while the tab is hidden (when leaving),
  /// so entering only flips `drawn` on — one state change, no reset frame on screen.
  func play() {
    DispatchQueue.main.async { drawn = true }
  }
  func hide() {
    var tr = Transaction(); tr.disablesAnimations = true
    withTransaction(tr) { drawn = false; focus = nil }
  }

  var body: some View {
    NavigationStack {
      ScrollView {
        if store.data.identities.isEmpty {
          Text("添加邮箱或手机号后，这里会画出它们和网站之间的关系。").foregroundStyle(.secondary).padding(40)
        } else {
          let layout = MapLayout(store.index, cardH: cardH, cardGap: cardGap, appH: appH, appGap: appGap, groupGap: groupGap)
          GeometryReader { geo in content(layout, width: geo.size.width) }
            .frame(height: layout.height)
            .padding(.top, 10).padding(.bottom, 40)
        }
      }
      .scrollIndicators(.hidden)
      .navigationTitle("关系图")
      .toolbar { TopButtons() }
      .background(Glow().ignoresSafeArea())
      .sensoryFeedback(.selection, trigger: focus)
    }
    .onAppear { visible = tab == 2; if tab == 2 && !drawn { play() } }
    .onChange(of: store.demoFocus) { _, f in
      withAnimation(Motion.smooth) { focus = f == "none" ? nil : f }
    }
    .onChange(of: tab) { _, t in
      visible = t == 2
      if t == 2 { if !drawn { play() } }
      else {
        // reset only after the outgoing cross-fade has finished, so the map never empties on screen
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) { if !visible && (drawn || focus != nil) { hide() } }
      }
    }
  }

  func content(_ m: MapLayout, width: CGFloat) -> some View {
    let L = min(160, width * 0.42), rx = width - 16 - (width * 0.4)
    let linked: Set<String> = focus.map { f in Set(m.apps.filter { $0.identityIds.contains(f) }.map(\.id)) } ?? Set(m.apps.map(\.id))
    var threads: [ThreadSpec] = []
    for k in m.ids.indices {
      let i = m.ids[k]
      let y0 = m.idY[k] + cardH / 2
      for j in m.apps.indices where m.apps[j].identityIds.contains(i.id) {
        threads.append(ThreadSpec(id: i.id + ">" + m.apps[j].id, owner: i.id, color: i.color,
                                  from: CGPoint(x: 16 + L, y: y0), to: CGPoint(x: rx, y: m.appY[j] + appH / 2),
                                  delay: 0.18 + Double(k) * 0.07 + Double(j) * 0.012))
      }
    }
    return ZStack(alignment: .topLeading) {
      // all threads are CAShapeLayers animated by the render server: the draw-in costs the main
      // thread nothing, so it can't stutter the tab switch it plays during
      MapThreadsLayer(threads: threads, drawn: drawn, focus: focus)
        .frame(width: width, height: m.height)
        .allowsHitTesting(false)

      // group labels
      if m.ids.contains(where: { $0.type == "email" }) { label("邮箱").offset(x: 18, y: 0) }
      if let k = m.ids.firstIndex(where: { $0.type == "phone" }) { label("手机号").offset(x: 18, y: m.idY[k] - 22) }
      if let k = m.ids.firstIndex(where: { $0.isLogin }) { label("登录").offset(x: 18, y: m.idY[k] - 22) }

      ForEach(Array(m.ids.enumerated()), id: \.element.id) { e in
        let k = e.offset, i = e.element
        Button {
          withAnimation(Motion.smooth) {
            if focus == i.id { store.route = .identity(i.id) } else { focus = i.id }
          }
        } label: { mini(i, width: L) }
          .buttonStyle(PressStyle())
          .opacity(drawn ? (focus == nil || focus == i.id ? 1 : 0.4) : 0)
          .scaleEffect(focus == i.id ? 1.04 : drawn ? 1 : 0.92)
          .offset(x: drawn ? 16 : -10, y: m.idY[k])
          .animation(Animation.spring(duration: 0.5, bounce: 0).delay(drawn ? Double(k) * 0.05 : 0), value: drawn)
      }
      ForEach(Array(m.apps.enumerated()), id: \.element.id) { e in
        let j = e.offset, a = e.element
        Button { store.route = .account(a.id) } label: {
          HStack(spacing: 8) {
            AppIcon(name: a.name, domain: a.domain, icon: a.icon, size: 30)
            Text(a.handle.map { store.handleText($0) } ?? a.name).font(.system(size: 13, weight: .medium)).lineLimit(1)
            Spacer(minLength: 0)
          }
          .padding(.horizontal, 8).frame(width: width - 16 - rx, height: appH)
          .background(.glass, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
        }
        .buttonStyle(PressStyle())
        .foregroundStyle(.primary)
        .opacity(drawn ? (linked.contains(a.id) ? 1 : 0.3) : 0)
        .offset(x: drawn ? rx : rx + 18, y: m.appY[j])
        .animation(Animation.spring(duration: 0.5, bounce: 0).delay(drawn ? 0.1 + Double(j) * 0.02 : 0), value: drawn)
      }
    }
    .contentShape(Rectangle())
    .onTapGesture { withAnimation(Motion.smooth) { focus = nil } }
  }

  func label(_ s: String) -> some View {
    Text(s).font(.system(size: 12, weight: .semibold)).foregroundStyle(.secondary)
  }
  func mini(_ i: Identity, width: CGFloat) -> some View {
    let n = store.accounts(of: i.id).count
    let shape = CardShape(sim: i.isPhone)
    return VStack(alignment: .leading, spacing: 2) {
      HStack(spacing: 5) {
        IdentityGlyph(identity: i, size: 11)
        Text("\(n)").font(.system(size: 13, weight: .bold, design: .rounded))
      }.opacity(0.85)
      Text(store.title(i)).font(.system(size: 14, weight: .semibold)).lineLimit(1)
      if !i.label.isEmpty { Text(store.display(i)).font(.system(size: 11)).lineLimit(1).opacity(0.75) }
    }
    .foregroundStyle(.white)
    .padding(.horizontal, 11)
    .frame(width: width, height: cardH, alignment: .leading)
    .background(skinGradient(i.color), in: shape)
    .bakedShadow(shape, key: "mini\(i.isPhone)\(i.color)", color: Color(hex: i.color).opacity(0.4), radius: 10, y: 5)
  }
}

/// Positions computed once per body from the store's index (the old code re-filtered identities
/// for every node it placed).
struct MapLayout {
  let ids: [Identity]
  let apps: [Account]
  let idY: [CGFloat]
  let appY: [CGFloat]
  let height: CGFloat
  init(_ x: AtlasIndex, cardH: CGFloat, cardGap: CGFloat, appH: CGFloat, appGap: CGFloat, groupGap: CGFloat) {
    let ids = x.emails + x.phones + x.logins
    // apps follow the first identity they belong to, so threads cross as little as possible
    var rank: [String: Int] = [:]
    for k in ids.indices { rank[ids[k].id] = k }
    var order: [String: Int] = [:]
    for a in x.byName { order[a.id] = a.identityIds.compactMap { rank[$0] }.min() ?? 999 }
    let apps = x.byName.enumerated().sorted { l, r in
      let a = order[l.element.id] ?? 999, b = order[r.element.id] ?? 999
      return a != b ? a < b : l.offset < r.offset
    }.map(\.element)
    // a gap (with its label) between the email, phone and login groups
    var iy: [CGFloat] = [], gaps: CGFloat = 0
    for k in ids.indices {
      if k > 0 && ids[k].type != ids[k - 1].type { gaps += groupGap }
      iy.append(CGFloat(k) * (cardH + cardGap) + gaps + 22)
    }
    var ay: [CGFloat] = []
    for j in apps.indices { ay.append(CGFloat(j) * (appH + appGap) + 22) }
    self.ids = ids; self.apps = apps; self.idY = iy; self.appY = ay
    height = max((iy.last ?? 22) + cardH, (ay.last ?? 22) + appH) + 10
  }
}

struct ThreadSpec: Equatable {
  let id: String
  let owner: String
  let color: String
  let from: CGPoint
  let to: CGPoint
  let delay: Double
}

struct MapThreadsLayer: UIViewRepresentable {
  let threads: [ThreadSpec]
  let drawn: Bool
  let focus: String?
  func makeUIView(context: Context) -> MapThreadsView { MapThreadsView() }
  func updateUIView(_ v: MapThreadsView, context: Context) { v.update(threads, drawn: drawn, focus: focus) }
}

final class MapThreadsView: UIView {
  private var layers: [String: CAShapeLayer] = [:]
  private var specs: [ThreadSpec] = []
  private var isDrawn = false
  private var focus: String? = nil
  private var styled = false

  override init(frame: CGRect) {
    super.init(frame: frame)
    isUserInteractionEnabled = false
    backgroundColor = .clear
    layer.masksToBounds = false
  }
  required init?(coder: NSCoder) { fatalError() }

  static func path(_ s: ThreadSpec) -> CGPath {
    let p = UIBezierPath()
    let mx = (s.from.x + s.to.x) / 2
    p.move(to: s.from)
    p.addCurve(to: s.to, controlPoint1: CGPoint(x: mx, y: s.from.y), controlPoint2: CGPoint(x: mx, y: s.to.y))
    return p.cgPath
  }

  func update(_ new: [ThreadSpec], drawn: Bool, focus f: String?) {
    let geometryChanged = new != specs
    if geometryChanged {
      CATransaction.begin(); CATransaction.setDisableActions(true)
      var keep = Set<String>()
      for s in new {
        keep.insert(s.id)
        let l: CAShapeLayer
        if let old = layers[s.id] { l = old } else {
          l = CAShapeLayer()
          l.fillColor = nil; l.lineCap = .round
          l.strokeEnd = isDrawn ? 1 : 0
          self.layer.addSublayer(l); layers[s.id] = l
        }
        l.path = MapThreadsView.path(s)
        l.strokeColor = UIColor(Color(hex: s.color)).cgColor
        l.contentsScale = window?.screen.scale ?? 3
      }
      for (id, l) in layers where !keep.contains(id) { l.removeFromSuperlayer(); layers[id] = nil }
      specs = new
      CATransaction.commit()
    }
    if drawn != isDrawn {
      isDrawn = drawn
      if drawn {
        let now = CACurrentMediaTime()
        for s in specs {
          guard let l = layers[s.id] else { continue }
          let a = CASpringAnimation(perceptualDuration: 0.75, bounce: 0)
          a.keyPath = "strokeEnd"
          a.fromValue = 0; a.toValue = 1
          a.duration = a.settlingDuration
          a.beginTime = now + s.delay
          a.fillMode = .backwards
          CATransaction.begin(); CATransaction.setDisableActions(true)
          l.strokeEnd = 1
          l.add(a, forKey: "draw")
          CATransaction.commit()
        }
      } else {
        CATransaction.begin(); CATransaction.setDisableActions(true)
        for l in layers.values { l.removeAnimation(forKey: "draw"); l.strokeEnd = 0 }
        CATransaction.commit()
      }
    }
    if f != focus || geometryChanged || !styled {
      let animate = styled && f != focus
      focus = f; styled = true
      CATransaction.begin()
      if animate {
        CATransaction.setAnimationDuration(0.3)
        CATransaction.setAnimationTimingFunction(CAMediaTimingFunction(controlPoints: 0.25, 0.1, 0.25, 1))
      } else { CATransaction.setDisableActions(true) }
      for s in specs {
        guard let l = layers[s.id] else { continue }
        let on = f == nil || f == s.owner
        l.opacity = on ? 0.75 : 0.08
        l.lineWidth = on && f != nil ? 2 : 1.3
      }
      CATransaction.commit()
    }
  }
}
