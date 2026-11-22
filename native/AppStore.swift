import SwiftUI
import UIKit
import PhotosUI

// MARK: - custom icons: square, ≤128 px, stored on the account as a data URL

enum IconCodec {
  static let side: CGFloat = 128
  static let budget = 24 * 1024

  /// the bytes inside a data:image/...;base64, URL
  static func data(fromDataURL s: String) -> Data? {
    guard s.hasPrefix("data:image/"), let r = s.range(of: ";base64,") else { return nil }
    return Data(base64Encoded: String(s[r.upperBound...]), options: .ignoreUnknownCharacters)
  }

  /// center-crops any image to a square, downscales it to 128x128 and encodes it (PNG, or JPEG
  /// q 0.85 when the PNG is over budget). Runs fine off the main thread.
  static func dataURL(from raw: Data) -> String? {
    guard let src = UIImage(data: raw), let cg = normalized(src) else { return nil }
    let w = CGFloat(cg.width), h = CGFloat(cg.height), m = min(w, h)
    guard m >= 1, let sq = cg.cropping(to: CGRect(x: ((w - m) / 2).rounded(.down), y: ((h - m) / 2).rounded(.down), width: m, height: m)) else { return nil }
    let out = min(side, m)
    let fmt = UIGraphicsImageRendererFormat(); fmt.scale = 1; fmt.opaque = false
    let img = UIGraphicsImageRenderer(size: CGSize(width: out, height: out), format: fmt).image { ctx in
      ctx.cgContext.interpolationQuality = .high
      UIImage(cgImage: sq).draw(in: CGRect(x: 0, y: 0, width: out, height: out))
    }
    if let png = img.pngData(), png.count <= budget { return "data:image/png;base64," + png.base64EncodedString() }
    // JPEG has no alpha: flatten onto white so transparent corners don't turn black
    let flat = UIGraphicsImageRenderer(size: img.size, format: { let f = UIGraphicsImageRendererFormat(); f.scale = 1; f.opaque = true; return f }()).image { _ in
      UIColor.white.setFill(); UIRectFill(CGRect(origin: .zero, size: img.size)); img.draw(at: .zero)
    }
    guard let jpg = flat.jpegData(compressionQuality: 0.85) else { return nil }
    return "data:image/jpeg;base64," + jpg.base64EncodedString()
  }

  /// bakes in the EXIF orientation of photos, so the crop is taken from what the user sees
  private static func normalized(_ i: UIImage) -> CGImage? {
    if i.imageOrientation == .up, let cg = i.cgImage { return cg }
    let fmt = UIGraphicsImageRendererFormat(); fmt.scale = 1
    return UIGraphicsImageRenderer(size: i.size, format: fmt).image { _ in i.draw(at: .zero) }.cgImage
  }

  static func dataURL(downloading url: URL) async -> String? {
    guard let res = try? await URLSession.shared.data(from: url), ((res.1 as? HTTPURLResponse)?.statusCode ?? 0) == 200 else { return nil }
    let d = res.0
    return await Task.detached(priority: .userInitiated) { IconCodec.dataURL(from: d) }.value
  }

  static func dataURL(from item: PhotosPickerItem) async -> String? {
    guard let d = try? await item.loadTransferable(type: Data.self) else { return nil }
    return await Task.detached(priority: .userInitiated) { IconCodec.dataURL(from: d) }.value
  }
}

// MARK: - App Store search (iTunes Search API: free, no key)

struct AppStoreApp: Decodable, Hashable, Identifiable {
  let trackId: Int
  let trackName: String
  let sellerUrl: String?
  let primaryGenreName: String?
  let genres: [String]?
  let artworkUrl100: String?
  let artworkUrl512: String?
  var id: Int { trackId }

  /// "支付宝-你的AI生活伙伴" -> "支付宝": cut at the first dash when what's left is at least 2 chars
  var cleanName: String {
    let t = trackName.trimmingCharacters(in: .whitespaces)
    guard let r = t.rangeOfCharacter(from: CharacterSet(charactersIn: "-–—")) else { return t }
    let head = t[..<r.lowerBound].trimmingCharacters(in: .whitespaces)
    return head.count >= 2 ? head : t
  }
  var domain: String { sellerUrl.flatMap { URL(string: $0)?.host }.map { cleanDomain($0) } ?? "" }
  var category: String {
    let map: [String: String] = [
      "Social Networking": "社交", "社交": "社交",
      "Shopping": "购物", "购物": "购物",
      "Finance": "支付金融", "财务": "支付金融",
      "Developer Tools": "开发", "开发者工具": "开发",
      "Games": "游戏", "游戏": "游戏",
      "Entertainment": "娱乐", "Music": "娱乐", "Video": "娱乐", "Photo & Video": "娱乐",
      "娱乐": "娱乐", "音乐": "娱乐", "摄影与录像": "娱乐",
      "Travel": "出行", "Navigation": "出行", "旅游": "出行", "导航": "出行",
    ]
    for g in [primaryGenreName].compactMap({ $0 }) + (genres ?? []) { if let c = map[g] { return c } }
    return "工具"
  }
  var thumbURL: String { artworkUrl100 ?? "" }
  var bigURL: URL? {
    if let s = artworkUrl512, let u = URL(string: s) { return u }
    return artworkUrl100.flatMap { URL(string: $0.replacingOccurrences(of: "100x100", with: "256x256")) }
  }
  func iconDataURL() async -> String? {
    guard let u = bigURL else { return nil }
    return await IconCodec.dataURL(downloading: u)
  }
}

enum AppStoreSearch {
  private struct Reply: Decodable { let results: [AppStoreApp] }
  /// nil when offline or anything else goes wrong: callers just show no App Store section
  static func search(_ q: String) async -> [AppStoreApp]? {
    var c = URLComponents(string: "https://itunes.apple.com/search")!
    c.queryItems = [.init(name: "term", value: q), .init(name: "country", value: "cn"), .init(name: "entity", value: "software"), .init(name: "limit", value: "8")]
    guard let u = c.url, let res = try? await URLSession.shared.data(from: u), ((res.1 as? HTTPURLResponse)?.statusCode ?? 0) == 200 else { return nil }
    return (try? JSONDecoder().decode(Reply.self, from: res.0))?.results
  }
}

@MainActor extension View {
  /// debounced App Store lookup for `query` (≥1 char). `.task(id:)` cancels the previous run, so an
  /// answer to an older query is never applied after a newer one.
  func appStoreResults(for query: String, into results: Binding<[AppStoreApp]>) -> some View {
    task(id: query) {
      let q = query.trimmingCharacters(in: .whitespaces)
      guard !q.isEmpty else { results.wrappedValue = []; return }
      try? await Task.sleep(for: .milliseconds(350))
      guard !Task.isCancelled else { return }
      let r = await AppStoreSearch.search(q)
      guard !Task.isCancelled else { return }
      withAnimation(Motion.snappy) { results.wrappedValue = r ?? [] }
    }
  }
}

/// the "App Store" label shown over store results
struct AppStoreBadge: View {
  var body: some View {
    Label("App Store", systemImage: "apple.logo").font(.system(size: 13, weight: .semibold)).foregroundStyle(.secondary)
  }
}
