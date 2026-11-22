import Foundation
import CryptoKit
import CommonCrypto
import SwiftUI

// Same encrypted file as the web app: PBKDF2-SHA256 (310k) -> AES-GCM, stored in a private gist (Gitee or GitHub).
struct SyncConf: Codable {
  var provider: String?
  var token: String
  var salt: String
  var key: String
  var gistId: String?
  var at: Double?
}

enum SyncError: LocalizedError {
  case msg(String)
  var errorDescription: String? { if case .msg(let s) = self { return s }; return nil }
}

@MainActor
final class Sync: ObservableObject {
  static let FILE = "atlas.enc.json"
  static let DESC = "账号仓库 · 加密数据（只有同步密码能解开）"
  @Published var state = "off"     // off | idle | syncing | error
  @Published var err = ""
  @Published var at: Double = 0
  private(set) var conf: SyncConf?
  private var key: SymmetricKey?
  private var busy = false, again = false
  private var timer: DispatchWorkItem?
  weak var store: Store?

  var on: Bool { conf != nil }
  var provider: String { conf?.provider ?? "github" }

  init() {
    if let s = UserDefaults.standard.string(forKey: "atlas.sync"), let d = s.data(using: .utf8), let c = try? JSONDecoder().decode(SyncConf.self, from: d) {
      conf = c; at = c.at ?? 0; state = "idle"
      if let k = Data(base64Encoded: c.key) { key = SymmetricKey(data: k) } else { state = "error"; err = "本机的同步信息损坏，请重新开启" }
    }
  }
  private func persistConf() {
    if let c = conf, let d = try? JSONEncoder().encode(c) { UserDefaults.standard.set(String(data: d, encoding: .utf8), forKey: "atlas.sync") }
    else { UserDefaults.standard.removeObject(forKey: "atlas.sync") }
  }

  // MARK: crypto
  nonisolated static func derive(_ pass: String, salt: Data) -> SymmetricKey {
    var out = [UInt8](repeating: 0, count: 32)
    let pw = Array(pass.utf8)
    let saltBytes = [UInt8](salt)
    pw.withUnsafeBufferPointer { p in
      p.baseAddress!.withMemoryRebound(to: Int8.self, capacity: max(pw.count, 1)) { cp in
        _ = CCKeyDerivationPBKDF(CCPBKDFAlgorithm(kCCPBKDF2), cp, pw.count, saltBytes, saltBytes.count,
                                 CCPseudoRandomAlgorithm(kCCPRFHmacAlgSHA256), 310_000, &out, 32)
      }
    }
    return SymmetricKey(data: out)
  }
  private func seal(_ d: AtlasData, key k: SymmetricKey, salt: String) throws -> String {
    let plain = try JSONEncoder().encode(d)
    let box = try AES.GCM.seal(plain, using: k)
    let iv = box.nonce.withUnsafeBytes { Data($0) }
    let ct = box.ciphertext + box.tag
    let env: [String: Any] = ["v": 1, "salt": salt, "iv": iv.base64EncodedString(), "ct": ct.base64EncodedString(), "at": nowMS()]
    return String(data: try JSONSerialization.data(withJSONObject: env), encoding: .utf8) ?? ""
  }
  private static func open(_ text: String, key k: SymmetricKey) throws -> AtlasData {
    guard let o = try JSONSerialization.jsonObject(with: Data(text.utf8)) as? [String: Any],
          let ivs = o["iv"] as? String, let cts = o["ct"] as? String,
          let iv = Data(base64Encoded: ivs), let ct = Data(base64Encoded: cts), ct.count > 16 else { throw SyncError.msg("同步文件格式不对") }
    let box = try AES.GCM.SealedBox(nonce: AES.GCM.Nonce(data: iv), ciphertext: ct.prefix(ct.count - 16), tag: ct.suffix(16))
    return try JSONDecoder().decode(AtlasData.self, from: try AES.GCM.open(box, using: k))
  }

  // MARK: api
  private func api(_ path: String, method: String = "GET", body: [String: Any]? = nil, token: String, prov: String) async throws -> Any {
    let gitee = prov == "gitee"
    var s = (gitee ? "https://gitee.com/api/v5" : "https://api.github.com") + path
    if gitee && method == "GET" { s += (s.contains("?") ? "&" : "?") + "access_token=" + (token.addingPercentEncoding(withAllowedCharacters: .alphanumerics) ?? token) }
    var r = URLRequest(url: URL(string: s)!)
    r.httpMethod = method; r.cachePolicy = .reloadIgnoringLocalCacheData; r.timeoutInterval = 20
    if gitee {
      if method != "GET" {
        var b = body ?? [:]; b["access_token"] = token
        r.httpBody = try JSONSerialization.data(withJSONObject: b); r.setValue("application/json", forHTTPHeaderField: "Content-Type")
      }
    } else {
      r.setValue("Bearer " + token, forHTTPHeaderField: "Authorization")
      r.setValue("application/vnd.github+json", forHTTPHeaderField: "Accept")
      if let body { r.httpBody = try JSONSerialization.data(withJSONObject: body); r.setValue("application/json", forHTTPHeaderField: "Content-Type") }
    }
    let name = gitee ? "Gitee" : "GitHub"
    let (data, resp): (Data, URLResponse)
    do { (data, resp) = try await URLSession.shared.data(for: r) }
    catch { throw SyncError.msg("连不上 \(name)" + (gitee ? "" : "，国内网络建议改用 Gitee")) }
    let code = (resp as? HTTPURLResponse)?.statusCode ?? 0
    if code == 401 { throw SyncError.msg("令牌无效或已过期") }
    if code == 403 || code == 404 { throw SyncError.msg(gitee ? "令牌没有 gists 权限" : "令牌没有 gist 权限") }
    if !(200..<300).contains(code) { throw SyncError.msg("\(name) 返回 \(code)") }
    return try JSONSerialization.jsonObject(with: data)
  }
  private func readGist(_ id: String, token: String, prov: String) async throws -> String? {
    guard let g = try await api("/gists/" + id, token: token, prov: prov) as? [String: Any],
          let files = g["files"] as? [String: Any], let f = files[Sync.FILE] as? [String: Any] else { return nil }
    if let c = f["content"] as? String, (f["truncated"] as? Bool) != true { return c }
    if let raw = f["raw_url"] as? String, let u = URL(string: raw) { return String(data: try await URLSession.shared.data(from: u).0, encoding: .utf8) }
    return nil
  }

  // MARK: merge (per item, newest wins, tombstones)
  static func merge(_ a: AtlasData, _ b: AtlasData) -> AtlasData {
    var del = a.deleted
    for (id, t) in b.deleted { del[id] = max(del[id] ?? 0, t) }
    func pick<T: Identifiable>(_ xs: [T], _ ys: [T], _ up: (T) -> Double) -> [T] where T.ID == String {
      var order: [String] = [], map: [String: T] = [:]
      for x in xs + ys {
        if let c = map[x.id] { if up(x) > up(c) { map[x.id] = x } } else { map[x.id] = x; order.append(x.id) }
      }
      return order.compactMap { map[$0] }.filter { !((del[$0.id] ?? -1) >= up($0)) }
    }
    var out = AtlasData()
    out.identities = pick(a.identities, b.identities) { $0.updatedAt ?? 0 }
    out.accounts = pick(a.accounts, b.accounts) { $0.updatedAt ?? 0 }
    for id in del.keys where out.identities.contains(where: { $0.id == id }) || out.accounts.contains(where: { $0.id == id }) { del[id] = nil }
    out.identities = out.identities.enumerated().sorted { ($0.element.order ?? 1e9, Double($0.offset)) < ($1.element.order ?? 1e9, Double($1.offset)) }.map(\.element)
    let ids = Set(out.identities.map(\.id))
    for k in out.accounts.indices { out.accounts[k].identityIds = out.accounts[k].identityIds.filter { ids.contains($0) } }
    let old = nowMS() - 90 * 86_400_000
    del = del.filter { $0.value >= old }
    out.deleted = del
    return out
  }
  private static func sig(_ d: AtlasData) -> String {
    let e = JSONEncoder(); e.outputFormatting = .sortedKeys
    let a = (try? e.encode(d.identities)) ?? Data(), b = (try? e.encode(d.accounts)) ?? Data(), c = (try? e.encode(d.deleted)) ?? Data()
    return String(decoding: a + b + c, as: UTF8.self)
  }

  // MARK: run
  func schedule() {
    guard conf != nil else { return }
    timer?.cancel()
    let w = DispatchWorkItem { [weak self] in Task { await self?.run() } }
    timer = w
    DispatchQueue.main.asyncAfter(deadline: .now() + 1.2, execute: w)
  }

  func run() async {
    guard let c = conf, let k = key, let gid = c.gistId, let store else { return }
    if busy { again = true; return }
    busy = true; state = "syncing"
    do {
      let prov = c.provider ?? "github"
      let text = try await readGist(gid, token: c.token, prov: prov)
      let remote = try text.map { try Sync.open($0, key: k) } ?? AtlasData()
      let local = store.data
      let merged = Sync.merge(local, remote)
      if Sync.sig(merged) != Sync.sig(local) { store.adopt(merged) }
      if text == nil || Sync.sig(merged) != Sync.sig(remote) {
        let content = try seal(merged, key: k, salt: c.salt)
        _ = try await api("/gists/" + gid, method: "PATCH", body: ["description": Sync.DESC, "files": [Sync.FILE: ["content": content]]], token: c.token, prov: prov)
      }
      state = "idle"; err = ""; at = nowMS(); conf?.at = at; persistConf()
    } catch {
      state = "error"; err = (error as? SyncError)?.errorDescription ?? (error is CryptoKitError ? "同步密码不对" : "同步失败")
    }
    busy = false
    if again { again = false; await run() }
  }

  /// returns true when existing data was found in the cloud
  func enable(token raw: String, pass: String, prov: String) async throws -> Bool {
    guard let store else { return false }
    let token = raw.trimmingCharacters(in: .whitespacesAndNewlines)
    let list = (try await api("/gists?per_page=100", token: token, prov: prov) as? [[String: Any]]) ?? []
    let g = list.first { (($0["files"] as? [String: Any])?[Sync.FILE]) != nil }
    var salt: String, k: SymmetricKey, gid: String? = nil
    var foundRemote = false
    if let g, let id = (g["id"] as? String) ?? (g["id"] as? Int).map({ String($0) }) {
      gid = id
      guard let text = try await readGist(id, token: token, prov: prov),
            let env = try JSONSerialization.jsonObject(with: Data(text.utf8)) as? [String: Any], let s = env["salt"] as? String,
            let sd = Data(base64Encoded: s) else { throw SyncError.msg("云端的同步文件损坏") }
      salt = s
      k = await Task.detached { Sync.derive(pass, salt: sd) }.value
      do { _ = try Sync.open(text, key: k) } catch { throw SyncError.msg("同步密码不对（和第一次设置的不一样）") }
      foundRemote = true
    } else {
      var b = [UInt8](repeating: 0, count: 16); _ = SecRandomCopyBytes(kSecRandomDefault, 16, &b)
      salt = Data(b).base64EncodedString()
      let sd = Data(b)
      k = await Task.detached { Sync.derive(pass, salt: sd) }.value
    }
    if store.data.demo == true { store.adopt(AtlasData()) }
    if gid == nil {
      let content = try seal(store.data, key: k, salt: salt)
      let made = try await api("/gists", method: "POST", body: ["description": Sync.DESC, "public": false, "files": [Sync.FILE: ["content": content]]], token: token, prov: prov) as? [String: Any]
      gid = (made?["id"] as? String) ?? (made?["id"] as? Int).map({ String($0) })
    }
    let keyData = k.withUnsafeBytes { Data($0) }
    conf = SyncConf(provider: prov, token: token, salt: salt, key: keyData.base64EncodedString(), gistId: gid, at: 0)
    key = k; persistConf(); state = "idle"
    await run()
    return foundRemote
  }

  func disable() {
    conf = nil; key = nil; state = "off"; err = ""; persistConf()
  }
}
