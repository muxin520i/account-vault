import SwiftUI
import UIKit
import ImageIO

// MARK: - colour

extension Color {
  init(hex: String) {
    var s = hex.trimmingCharacters(in: .whitespaces); if s.hasPrefix("#") { s.removeFirst() }
    if s.count == 3 { s = s.map { "\($0)\($0)" }.joined() }
    let v = UInt64(s, radix: 16) ?? 0x3a3a3c
    self.init(red: Double((v >> 16) & 0xff) / 255, green: Double((v >> 8) & 0xff) / 255, blue: Double(v & 0xff) / 255)
  }
}

struct Skin { let name: String; let stops: [(String, Double)] }
let SKINS: [(String, Skin)] = [
  ("#0a84ff", Skin(name: "深海", stops: [("#56c6ff", 0), ("#0a84ff", 0.42), ("#3b2fd0", 1)])),
  ("#30d158", Skin(name: "青柠", stops: [("#d2f56a", 0), ("#30d158", 0.48), ("#0b7f6a", 1)])),
  ("#ff9f0a", Skin(name: "落日", stops: [("#ffdf5c", 0), ("#ff9f0a", 0.42), ("#ff3d5a", 1)])),
  ("#ff375f", Skin(name: "莓果", stops: [("#ff9bb8", 0), ("#ff375f", 0.46), ("#9e1470", 1)])),
  ("#bf5af2", Skin(name: "极光", stops: [("#ff86dc", 0), ("#bf5af2", 0.44), ("#4636ee", 1)])),
  ("#64d2ff", Skin(name: "冰川", stops: [("#9df0ff", 0), ("#4cc3fb", 0.40), ("#2a6cf0", 1)])),
  ("#ff453a", Skin(name: "珊瑚", stops: [("#ffa36b", 0), ("#ff453a", 0.46), ("#c1185f", 1)])),
  ("#5e5ce6", Skin(name: "靛蓝", stops: [("#9a98ff", 0), ("#5e5ce6", 0.46), ("#1f1b78", 1)])),
  ("#00c7be", Skin(name: "海湾", stops: [("#8af5d6", 0), ("#00c7be", 0.44), ("#0a62b0", 1)])),
  ("#3a3a3c", Skin(name: "曜石", stops: [("#76767c", 0), ("#3a3a3c", 0.46), ("#0d0d10", 1)])),
]
let COLORS = SKINS.map(\.0)
/// default faces of login cards: WeChat green and Apple graphite (the user can pick any skin)
let LOGIN_SKINS: [(String, Skin)] = [
  ("#07c160", Skin(name: "微信绿", stops: [("#6be39b", 0), ("#07c160", 0.45), ("#03704f", 1)])),
  ("#1d1d1f", Skin(name: "石墨", stops: [("#b4b6bb", 0), ("#55575d", 0.36), ("#1d1d1f", 1)])),
]

func skinGradient(_ c: String) -> LinearGradient {
  let stops: [Gradient.Stop]
  if let s = (SKINS.first(where: { $0.0 == c }) ?? LOGIN_SKINS.first(where: { $0.0 == c }))?.1 { stops = s.stops.map { Gradient.Stop(color: Color(hex: $0.0), location: $0.1) } }
  else { let k = Color(hex: c); stops = [.init(color: k.opacity(0.7), location: 0), .init(color: k, location: 0.5), .init(color: k, location: 1)] }
  // 145deg in CSS
  return LinearGradient(stops: stops, startPoint: UnitPoint(x: 0.21, y: 0), endPoint: UnitPoint(x: 0.79, y: 1))
}

private var brandCache: [String: Color] = [:]
/// Brand colour of an app: preset colour or a stable hash hue. Memoised, since rows, grid icons,
/// baked shadows and the wallet poster all ask for it on every render.
func brandColor(name: String, domain: String) -> Color {
  let ck = name + "\u{1}" + domain
  if let c = brandCache[ck] { return c }
  let c: Color
  if let p = presetFor(name: name, domain: domain) { c = Color(hex: p.color) }
  else {
    var h: UInt32 = 0
    let key = cleanDomain(domain).isEmpty ? name.lowercased() : cleanDomain(domain)
    for u in key.unicodeScalars { h = h &* 31 &+ u.value }
    c = Color(hue: Double(h % 360) / 360, saturation: 0.62, brightness: 0.74)
  }
  brandCache[ck] = c
  return c
}

// MARK: - glass

/// Flat stand-in for .ultraThinMaterial on things that repeat or move (rows, tiles, map nodes).
/// Every material is a live backdrop blur re-sampled each frame while scrolling or animating;
/// over this app's soft gradient backgrounds the blur is invisible, so a tint looks the same.
struct GlassStyle: ShapeStyle {
  func resolve(in env: EnvironmentValues) -> Color {
    env.colorScheme == .dark ? Color(white: 1, opacity: 0.10) : Color(white: 1, opacity: 0.62)
  }
}
extension ShapeStyle where Self == GlassStyle { static var glass: GlassStyle { GlassStyle() } }

// MARK: - baked shadows

/// Coloured soft shadows rendered once into a bitmap (Core Graphics, off the frame loop) and then
/// shown as a plain image. A live .shadow on a view is an offscreen blur pass every frame, which
/// is what made the 3D-rotating coverflow and the icon grid drop frames.
@MainActor enum ShadowBaker {
  static var cache: [String: UIImage] = [:]
  static func image(key: String, size: CGSize, color: UIColor, radius: CGFloat, path: (CGRect) -> Path) -> UIImage? {
    guard size.width > 1, size.height > 1 else { return nil }
    let w = size.width.rounded(), h = size.height.rounded()
    let k = "\(key)|\(w)x\(h)|\(radius)"
    if let i = cache[k] { return i }
    let pad = ceil(radius * 3)
    let canvas = CGSize(width: w + pad * 2, height: h + pad * 2)
    let fmt = UIGraphicsImageRendererFormat(); fmt.scale = 1; fmt.opaque = false   // blurred: 1x is indistinguishable
    let img = UIGraphicsImageRenderer(size: canvas, format: fmt).image { ctx in
      let cg = ctx.cgContext
      // draw the shape far off-canvas and throw only its shadow back in
      let far: CGFloat = 10_000
      cg.setShadow(offset: CGSize(width: far, height: 0), blur: radius * 2, color: color.cgColor)
      cg.translateBy(x: pad - far, y: pad)
      cg.addPath(path(CGRect(x: 0, y: 0, width: w, height: h)).cgPath)
      cg.setFillColor(UIColor.black.cgColor)
      cg.fillPath()
    }
    if cache.count > 300 { cache.removeAll() }
    cache[k] = img
    return img
  }
}

struct BakedShadow<S: Shape>: View {
  let key: String
  let shape: S
  let color: Color
  let radius: CGFloat
  var y: CGFloat = 0
  var body: some View {
    GeometryReader { g in
      if let img = ShadowBaker.image(key: key, size: g.size, color: UIColor(color), radius: radius, path: { shape.path(in: $0) }) {
        Image(uiImage: img).resizable().interpolation(.medium)
          .frame(width: img.size.width, height: img.size.height)
          .position(x: g.size.width / 2, y: g.size.height / 2 + y)
      }
    }
    .allowsHitTesting(false)
    .accessibilityHidden(true)
  }
}
extension View {
  /// Same look as .shadow(color:radius:y:) for a view filling `shape`, without per-frame blur.
  func bakedShadow<S: Shape>(_ shape: S, key: String, color: Color, radius: CGFloat, y: CGFloat = 0) -> some View {
    background(BakedShadow(key: key, shape: shape, color: color, radius: radius, y: y))
  }
}

// MARK: - app icon

/// Icon pipeline: bundled PNGs and downloaded favicons are decoded and downsampled on a background
/// queue (ImageIO, decoded immediately), kept in memory, and favicons also on disk. Views read the
/// cache synchronously, so a row that scrolls back in or a tab you return to shows its icon on the
/// first frame with no decode on the main thread and no AsyncImage re-download/flash.
final class IconEntry { let image: UIImage; let favicon: Bool; init(_ i: UIImage, _ f: Bool) { image = i; favicon = f } }

@MainActor final class IconStore {
  static let shared = IconStore()
  private var mem: [String: IconEntry] = [:]
  private var failed = Set<String>()
  private var waiting: [String: [(IconEntry?) -> Void]] = [:]
  private var keyCache: [String: String] = [:]
  private let work = DispatchQueue(label: "atlas.icons", qos: .userInitiated, attributes: .concurrent)
  private let disk: URL = {
    let u = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0].appendingPathComponent("favicons", isDirectory: true)
    try? FileManager.default.createDirectory(at: u, withIntermediateDirectories: true)
    return u
  }()

  /// "b:<preset domain>" for a bundled icon, "f:<domain>" for a favicon, "" for a letter tile
  func key(name: String, domain: String) -> String {
    let ck = name + "\u{1}" + domain
    if let k = keyCache[ck] { return k }
    let d = cleanDomain(domain), n = name.lowercased()
    var k = ""
    if let p = Presets.all.first(where: { (!d.isEmpty && (d == $0.domain || d.hasSuffix("." + $0.domain))) || (d.isEmpty && $0.name.lowercased() == n) }),
       Bundle.main.path(forResource: p.domain, ofType: "png", inDirectory: "icons") != nil {
      k = "b:" + p.domain
    } else if !d.isEmpty { k = "f:" + d }
    keyCache[ck] = k
    return k
  }
  /// "c:<len>:<hash>" for an account's own icon (data URL); the string is kept to decode on demand
  private var custom: [String: String] = [:]
  func key(name: String, domain: String, icon: String?) -> String {
    guard let icon, !icon.isEmpty else { return key(name: name, domain: domain) }
    let k = "c:\(icon.utf8.count):\(icon.hashValue)"
    if custom[k] == nil { custom[k] = icon }
    return k
  }
  /// "u:<https url>" for a remote image (App Store artwork in suggestions), memory only
  func key(url: String) -> String { url.isEmpty ? "" : "u:" + url }

  func cached(_ key: String) -> IconEntry? { mem[key] }
  func hasFailed(_ key: String) -> Bool { failed.contains(key) }

  func prefetch(_ accounts: [Account]) {
    for a in accounts { load(key(name: a.name, domain: a.domain, icon: a.icon)) { _ in } }
  }

  func load(_ key: String, _ done: @escaping (IconEntry?) -> Void) {
    if key.isEmpty || failed.contains(key) { done(nil); return }
    if let e = mem[key] { done(e); return }
    if waiting[key] != nil { waiting[key]!.append(done); return }
    waiting[key] = [done]
    let body = String(key.dropFirst(2)), disk = self.disk
    if key.hasPrefix("c:") {
      let src = custom[key] ?? ""
      work.async {
        let img = IconCodec.data(fromDataURL: src).flatMap { IconStore.decode(data: $0) }
        Task { @MainActor in self.finish(key, img.map { IconEntry($0, false) }) }
      }
      return
    }
    if key.hasPrefix("u:") {
      guard let u = URL(string: body) else { finish(key, nil); return }
      URLSession.shared.dataTask(with: u) { data, resp, _ in
        let ok = ((resp as? HTTPURLResponse)?.statusCode ?? 0) == 200
        let img = ok ? data.flatMap { IconStore.decode(data: $0) } : nil
        Task { @MainActor in
          // a failed remote load may succeed later (offline now), so don't remember it as failed
          if img == nil { self.waiting.removeValue(forKey: key)?.forEach { $0(nil) } } else { self.finish(key, IconEntry(img!, false)) }
        }
      }.resume()
      return
    }
    if key.hasPrefix("b:") {
      work.async {
        let path = Bundle.main.path(forResource: body, ofType: "png", inDirectory: "icons")
        let img = path.flatMap { IconStore.decode(url: URL(fileURLWithPath: $0)) }
        Task { @MainActor in self.finish(key, img.map { IconEntry($0, false) }) }
      }
      return
    }
    let file = disk.appendingPathComponent(body.replacingOccurrences(of: "/", with: "_") + ".img")
    work.async {
      if let img = IconStore.decode(url: file) {
        Task { @MainActor in self.finish(key, IconEntry(img, true)) }
        return
      }
      guard let u = URL(string: "https://favicon.im/\(body)?larger=true") else {
        Task { @MainActor in self.finish(key, nil) }; return
      }
      URLSession.shared.dataTask(with: u) { data, resp, _ in
        let ok = ((resp as? HTTPURLResponse)?.statusCode ?? 0) == 200
        let img = ok ? data.flatMap { IconStore.decode(data: $0) } : nil
        if img != nil, let data { try? data.write(to: file, options: .atomic) }
        Task { @MainActor in self.finish(key, img.map { IconEntry($0, true) }) }
      }.resume()
    }
  }
  private func finish(_ key: String, _ e: IconEntry?) {
    if let e { mem[key] = e } else { failed.insert(key) }
    let cbs = waiting.removeValue(forKey: key) ?? []
    for c in cbs { c(e) }
  }

  nonisolated static func decode(url: URL) -> UIImage? {
    guard let src = CGImageSourceCreateWithURL(url as CFURL, nil) else { return nil }
    return thumb(src)
  }
  nonisolated static func decode(data: Data) -> UIImage? {
    guard let src = CGImageSourceCreateWithData(data as CFData, nil) else { return nil }
    return thumb(src)
  }
  /// decoded right here (kCGImageSourceShouldCacheImmediately), capped at 288 px = 96 pt @3x
  nonisolated private static func thumb(_ src: CGImageSource) -> UIImage? {
    let opts: [CFString: Any] = [
      kCGImageSourceCreateThumbnailFromImageAlways: true,
      kCGImageSourceShouldCacheImmediately: true,
      kCGImageSourceCreateThumbnailWithTransform: true,
      kCGImageSourceThumbnailMaxPixelSize: 288,
    ]
    guard let cg = CGImageSourceCreateThumbnailAtIndex(src, 0, opts as CFDictionary) else { return nil }
    return UIImage(cgImage: cg)
  }
}

struct AppIcon: View {
  let name: String
  let domain: String
  var icon: String? = nil       // account.icon: wins over preset / favicon / letter
  var url: String? = nil        // remote artwork (App Store suggestions)
  var size: CGFloat = 52
  @State private var loaded = 0   // bumps when an async load lands, to re-read the cache
  @State private var drew = Drew() // what the last body pass showed (plain box, never invalidates)
  final class Drew { var image = false }

  var body: some View {
    let r = RoundedRectangle(cornerRadius: size * 0.235, style: .continuous)
    let key = url.map { IconStore.shared.key(url: $0) } ?? IconStore.shared.key(name: name, domain: domain, icon: icon)
    let _ = loaded
    let entry = IconStore.shared.cached(key)
    let _ = { drew.image = entry != nil }()
    ZStack {
      if let entry {
        if entry.favicon {
          ZStack { Color.white; Image(uiImage: entry.image).resizable().interpolation(.high).scaledToFit().padding(size * 0.14) }
        } else {
          Image(uiImage: entry.image).resizable().interpolation(.high).scaledToFill()
        }
      } else { letter }
    }
    .frame(width: size, height: size)
    .clipShape(r)
    .overlay(r.strokeBorder(.white.opacity(0.12), lineWidth: 0.5))
    .task(id: key) {
      guard !key.isEmpty, !IconStore.shared.hasFailed(key) else { return }
      if IconStore.shared.cached(key) == nil {
        await withCheckedContinuation { (c: CheckedContinuation<Void, Never>) in IconStore.shared.load(key) { _ in c.resume() } }
      }
      // the image may have landed (here or via the launch prefetch) after this body was drawn
      if IconStore.shared.cached(key) != nil && !drew.image {
        var t = Transaction(); t.disablesAnimations = true
        withTransaction(t) { loaded &+= 1 }
      }
    }
  }
  var letter: some View {
    let b = brandColor(name: name, domain: domain)
    return ZStack {
      LinearGradient(colors: [b.opacity(0.78), b], startPoint: .top, endPoint: .bottom)
      Text(String(name.trimmingCharacters(in: .whitespaces).prefix(1)).uppercased())
        .font(.system(size: size * 0.44, weight: .semibold, design: .rounded)).foregroundStyle(.white)
    }
  }
}

// MARK: - wallet card

struct SIMShape: Shape {
  var cut: CGFloat = 26
  var radius: CGFloat = 22
  func path(in r: CGRect) -> Path {
    var p = Path()
    p.move(to: CGPoint(x: r.minX + radius, y: r.minY))
    p.addLine(to: CGPoint(x: r.maxX - cut, y: r.minY))
    p.addLine(to: CGPoint(x: r.maxX, y: r.minY + cut))
    p.addLine(to: CGPoint(x: r.maxX, y: r.maxY - radius))
    p.addQuadCurve(to: CGPoint(x: r.maxX - radius, y: r.maxY), control: CGPoint(x: r.maxX, y: r.maxY))
    p.addLine(to: CGPoint(x: r.minX + radius, y: r.maxY))
    p.addQuadCurve(to: CGPoint(x: r.minX, y: r.maxY - radius), control: CGPoint(x: r.minX, y: r.maxY))
    p.addLine(to: CGPoint(x: r.minX, y: r.minY + radius))
    p.addQuadCurve(to: CGPoint(x: r.minX + radius, y: r.minY), control: CGPoint(x: r.minX, y: r.minY))
    p.closeSubpath()
    return p
  }
}

struct CardShape: Shape {
  var sim: Bool
  func path(in r: CGRect) -> Path {
    sim ? SIMShape(cut: r.width * 0.085, radius: r.width * 0.065).path(in: r)
        : RoundedRectangle(cornerRadius: r.width * 0.065, style: .continuous).path(in: r)
  }
}

struct Chip: View {
  var body: some View {
    ZStack {
      RoundedRectangle(cornerRadius: 5, style: .continuous)
        .fill(LinearGradient(colors: [Color(hex: "#f7e3a1"), Color(hex: "#d4a84b"), Color(hex: "#f1d78a")], startPoint: .topLeading, endPoint: .bottomTrailing))
      Path { p in
        p.move(to: CGPoint(x: 0, y: 8)); p.addLine(to: CGPoint(x: 9, y: 8))
        p.move(to: CGPoint(x: 0, y: 14)); p.addLine(to: CGPoint(x: 9, y: 14))
        p.move(to: CGPoint(x: 23, y: 8)); p.addLine(to: CGPoint(x: 32, y: 8))
        p.move(to: CGPoint(x: 23, y: 14)); p.addLine(to: CGPoint(x: 32, y: 14))
        p.addRoundedRect(in: CGRect(x: 9, y: 4, width: 14, height: 14), cornerSize: CGSize(width: 3, height: 3))
      }.stroke(Color(hex: "#8a6a24").opacity(0.55), lineWidth: 0.8)
    }
    .frame(width: 32, height: 22)
  }
}

/// Stands in for the gold chip on an eSIM: there is no physical card.
struct ESimBadge: View {
  var body: some View {
    Text("eSIM").font(.system(size: 10, weight: .bold, design: .rounded))
      .frame(width: 32, height: 22)
      .background(.white.opacity(0.18), in: RoundedRectangle(cornerRadius: 5, style: .continuous))
      .overlay(RoundedRectangle(cornerRadius: 5, style: .continuous).stroke(.white.opacity(0.55), lineWidth: 0.8))
  }
}

/// The small glyph on mini cards: envelope, SIM card, the word eSIM, or a login provider.
struct IdentityGlyph: View {
  let identity: Identity
  var size: CGFloat
  var body: some View {
    if identity.isESim { Text("eSIM").font(.system(size: size * 0.8, weight: .bold, design: .rounded)) }
    else if identity.isLogin { LoginGlyph(apple: identity.isApple, size: size, filled: true) }
    else { Image(systemName: identity.isPhone ? "simcard.fill" : "envelope.fill").font(.system(size: size, weight: .semibold)) }
  }
}

/// 微信: a plain two-bubble outline (not the official logo artwork). Apple: the SF Symbol.
struct LoginGlyph: View {
  let apple: Bool
  var size: CGFloat
  var filled = false
  var body: some View {
    Image(systemName: apple ? "apple.logo" : (filled ? "bubble.left.and.bubble.right.fill" : "bubble.left.and.bubble.right"))
      .font(.system(size: size, weight: apple ? .semibold : .medium))
  }
}

struct WalletCard: View {
  @Environment(Store.self) var store
  let identity: Identity
  var body: some View {
    let i = identity
    let accs = store.accounts(of: i.id)
    let shape = CardShape(sim: i.isPhone)
    ZStack(alignment: .topLeading) {
      skinGradient(i.color)
      poster(accs.sorted { $0.createdAt > $1.createdAt })
      // soft light from the top-left, like a printed card
      RadialGradient(colors: [.white.opacity(0.32), .clear], center: UnitPoint(x: 0.12, y: 0), startRadius: 0, endRadius: 260)
        .blendMode(.softLight)
      VStack(alignment: .leading, spacing: 0) {
        HStack(alignment: .top) {
          HStack(spacing: 8) {
            if i.isESim { ESimBadge() } else if i.isPhone { Chip() } else if i.isLogin { LoginGlyph(apple: i.isApple, size: 17) } else { Image(systemName: "envelope").font(.system(size: 17, weight: .medium)) }
            Text(cardTitle(i)).font(.system(size: 16, weight: .semibold)).lineLimit(1)
          }
          Spacer()
          VStack(alignment: .trailing, spacing: 0) {
            Text("\(accs.count)").font(.system(size: 34, weight: .bold, design: .rounded)).contentTransition(.numericText())
            Text("个网站与 App").font(.system(size: 11, weight: .medium)).opacity(0.75)
          }
        }
        Spacer(minLength: 6)
        Text(store.display(i))
          .font(.system(size: 21, weight: .semibold, design: .rounded)).monospacedDigit()
          .lineLimit(1).minimumScaleFactor(0.6)
        HStack(alignment: .bottom) {
          Text(footNote(i)).font(.system(size: 12.5, weight: .medium)).opacity(0.75).lineLimit(1)
          Spacer()
          IconStack(accounts: accs)
        }.padding(.top, 8)
      }
      .foregroundStyle(.white)
      .shadow(color: .black.opacity(0.18), radius: 1, y: 0.5)
      .padding(18)
    }
    .aspectRatio(1.586, contentMode: .fit)
    .clipShape(shape)
    .overlay(shape.stroke(.white.opacity(0.22), lineWidth: 0.6))
    // The face (gradient, blurred poster, soft-light blend, text, icons) is flattened into one
    // Metal-rendered layer, so the coverflow's per-frame 3D transform moves a single texture
    // instead of re-compositing a dozen blended layers.
    .drawingGroup()
    .bakedShadow(shape, key: "card\(i.isPhone)\(i.color)", color: Color(hex: i.color).opacity(0.45), radius: 22, y: 14)
  }
  func cardTitle(_ i: Identity) -> String {
    if i.isLogin { return i.label.isEmpty ? i.kindName : i.kindName + " · " + i.label }
    return i.label.isEmpty ? i.kindName : i.label
  }
  func footNote(_ i: Identity) -> String {
    if i.isLogin {
      if let v = store.via(i) { return "绑定 " + store.title(v) }
      return i.notes.isEmpty ? (i.isApple ? "Apple ID" : "微信号") : i.notes
    }
    if i.isPhone {
      let c = carrier(i.value)
      let parts = [(!c.isEmpty && !i.notes.contains(String(c.dropFirst(2)))) ? c : "", i.notes].filter { !$0.isEmpty }
      return parts.isEmpty ? "手机号" : parts.joined(separator: " · ")
    }
    return i.notes.isEmpty ? "邮箱" : i.notes
  }
  // a blurred mosaic of the brand colours of what this identity unlocks
  func poster(_ accs: [Account]) -> some View {
    var colors: [Color] = []
    for k in 0..<9 {
      if accs.isEmpty { colors.append(.clear) } else { let a = accs[k % accs.count]; colors.append(brandColor(name: a.name, domain: a.domain)) }
    }
    return VStack(spacing: 0) {
      ForEach(0..<3, id: \.self) { r in
        HStack(spacing: 0) {
          ForEach(0..<3, id: \.self) { c in Rectangle().fill(colors[r * 3 + c]).aspectRatio(1.586, contentMode: .fit) }
        }
      }
    }
    .blur(radius: 30).opacity(accs.isEmpty ? 0 : 0.35).blendMode(.overlay)
  }
}

struct IconStack: View {
  let accounts: [Account]
  var body: some View {
    HStack(spacing: -9) {
      ForEach(accounts.prefix(4)) { a in
        AppIcon(name: a.name, domain: a.domain, icon: a.icon, size: 28)
          .overlay(RoundedRectangle(cornerRadius: 28 * 0.235, style: .continuous).stroke(.white.opacity(0.55), lineWidth: 1.5))
      }
      if accounts.count > 4 {
        Text("+\(accounts.count - 4)").font(.system(size: 11, weight: .bold, design: .rounded))
          .frame(height: 28).padding(.horizontal, 7).background(Color.white.opacity(0.24), in: Capsule()).padding(.leading, 12)
      }
    }
  }
}

// MARK: - rows

struct IdentityCaps: View {
  @Environment(Store.self) var store
  let ids: [String]
  var body: some View {
    let list = ids.compactMap { store.identity($0) }
    HStack(spacing: 5) {
      if list.isEmpty { Text("未关联").font(.caption).foregroundStyle(.secondary) }
      ForEach(list) { i in
        HStack(spacing: 4) {
          Circle().fill(Color(hex: i.color)).frame(width: 6, height: 6)
          Text(store.title(i)).lineLimit(1)
        }
        .font(.system(size: 12, weight: .medium))
        .padding(.horizontal, 7).padding(.vertical, 3)
        .background(Color(hex: i.color).opacity(0.16), in: Capsule())
      }
    }
  }
}

struct ReelRow: View {
  @Environment(Store.self) var store
  let account: Account
  var meta: String? = nil
  var body: some View {
    let a = account, b = brandColor(name: a.name, domain: a.domain)
    HStack(spacing: 12) {
      AppIcon(name: a.name, domain: a.domain, icon: a.icon, size: 44)
      VStack(alignment: .leading, spacing: 4) {
        HStack(spacing: 6) {
          Text(a.name).font(.system(size: 16, weight: .semibold)).lineLimit(1)
          if let h = a.handle { Text(store.handleText(h)).font(.system(size: 13)).foregroundStyle(.secondary).lineLimit(1) }
        }
        IdentityCaps(ids: a.identityIds)
      }
      Spacer(minLength: 4)
      if let meta { Text(meta).font(.system(size: 12, weight: .medium)).foregroundStyle(.secondary) }
      else { Image(systemName: "chevron.right").font(.system(size: 13, weight: .semibold)).foregroundStyle(.tertiary) }
    }
    .padding(.horizontal, 12).padding(.vertical, 10)
    .background {
      // even colour wash: flat glass + brand tint (no per-row live blur while scrolling)
      let r = RoundedRectangle(cornerRadius: 18, style: .continuous)
      ZStack { r.fill(.glass); r.fill(b.opacity(0.14)) }
    }
    .contentShape(Rectangle())
  }
}

// press feedback that feels like a card being pushed
struct PressStyle: ButtonStyle {
  var scale: CGFloat = 0.96
  func makeBody(configuration: Configuration) -> some View {
    configuration.label
      .scaleEffect(configuration.isPressed ? scale : 1)
      // interactive, critically damped: follows the finger, settles without wobble
      .animation(Motion.press, value: configuration.isPressed)
  }
}

// MARK: - motion

/// One set of Apple-like, no-bounce springs used everywhere. Springs keep velocity when retargeted,
/// so an interrupted animation (tap during a swipe, quick tab hops) never jumps.
enum Motion {
  static let press = Animation.interactiveSpring(response: 0.26, dampingFraction: 0.86, blendDuration: 0.12)
  static let snappy = Animation.spring(duration: 0.32, bounce: 0)
  static let smooth = Animation.spring(duration: 0.45, bounce: 0)
  static let gentle = Animation.spring(duration: 0.6, bounce: 0)
  static let tab = Animation.spring(duration: 0.4, bounce: 0)
}
