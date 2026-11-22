import Foundation
import SwiftUI
import UIKit
import Observation

// MARK: - data (same JSON shape as the web app, so backups and sync interoperate)

struct Identity: Codable, Identifiable, Hashable {
  var id: String
  var type: String            // "email" | "phone"
  var value: String
  var label: String = ""
  var color: String
  var notes: String = ""
  var updatedAt: Double? = nil
  var order: Double? = nil

  init(id: String = newID(), type: String, value: String, label: String = "", color: String, notes: String = "") {
    self.id = id; self.type = type; self.value = value; self.label = label; self.color = color; self.notes = notes
  }
  init(from d: Decoder) throws {
    let c = try d.container(keyedBy: CodingKeys.self)
    id = try c.decode(String.self, forKey: .id)
    type = (try? c.decode(String.self, forKey: .type)) ?? "email"
    value = (try? c.decode(String.self, forKey: .value)) ?? ""
    label = (try? c.decode(String.self, forKey: .label)) ?? ""
    color = (try? c.decode(String.self, forKey: .color)) ?? "#0a84ff"
    notes = (try? c.decode(String.self, forKey: .notes)) ?? ""
    updatedAt = try? c.decode(Double.self, forKey: .updatedAt)
    order = try? c.decode(Double.self, forKey: .order)
  }
  var isPhone: Bool { type == "phone" }
}

struct Account: Codable, Identifiable, Hashable {
  var id: String
  var name: String
  var domain: String = ""
  var category: String = "其他"
  var identityIds: [String] = []
  var notes: String = ""
  var handle: String? = nil
  var createdAt: Double = nowMS()
  var updatedAt: Double? = nil

  init(id: String = newID(), name: String, domain: String, category: String, identityIds: [String], notes: String = "", handle: String? = nil, createdAt: Double = nowMS()) {
    self.id = id; self.name = name; self.domain = domain; self.category = category; self.identityIds = identityIds; self.notes = notes; self.handle = handle; self.createdAt = createdAt
  }
  init(from d: Decoder) throws {
    let c = try d.container(keyedBy: CodingKeys.self)
    id = try c.decode(String.self, forKey: .id)
    name = (try? c.decode(String.self, forKey: .name)) ?? "?"
    domain = (try? c.decode(String.self, forKey: .domain)) ?? ""
    category = (try? c.decode(String.self, forKey: .category)) ?? "其他"
    identityIds = (try? c.decode([String].self, forKey: .identityIds)) ?? []
    notes = (try? c.decode(String.self, forKey: .notes)) ?? ""
    let h = try? c.decode(String.self, forKey: .handle)
    handle = (h?.isEmpty ?? true) ? nil : h
    createdAt = (try? c.decode(Double.self, forKey: .createdAt)) ?? nowMS()
    updatedAt = try? c.decode(Double.self, forKey: .updatedAt)
  }
}

struct AtlasData: Codable {
  var identities: [Identity] = []
  var accounts: [Account] = []
  var deleted: [String: Double] = [:]
  var demo: Bool? = nil
  init(identities: [Identity] = [], accounts: [Account] = [], deleted: [String: Double] = [:], demo: Bool? = nil) {
    self.identities = identities; self.accounts = accounts; self.deleted = deleted; self.demo = demo
  }
  init(from d: Decoder) throws {
    let c = try d.container(keyedBy: CodingKeys.self)
    identities = (try? c.decode([Identity].self, forKey: .identities)) ?? []
    accounts = (try? c.decode([Account].self, forKey: .accounts)) ?? []
    deleted = (try? c.decode([String: Double].self, forKey: .deleted)) ?? [:]
    demo = try? c.decode(Bool.self, forKey: .demo)
  }
}

func newID() -> String {
  let a = "abcdefghijklmnopqrstuvwxyz0123456789"
  let r = String((0..<8).map { _ in a.randomElement()! })
  let t = String(Int(Date().timeIntervalSince1970 * 1000), radix: 36)
  return r + String(t.suffix(4))
}
func nowMS() -> Double { (Date().timeIntervalSince1970 * 1000).rounded() }

let CATS = ["社交", "购物", "支付金融", "开发", "AI", "娱乐", "游戏", "出行", "工具", "加密", "其他"]

struct Preset: Hashable { let name: String; let domain: String; let category: String; let color: String }

// MARK: - helpers

func cleanDomain(_ d: String) -> String {
  var s = d.trimmingCharacters(in: .whitespaces).lowercased()
  for p in ["https://", "http://"] where s.hasPrefix(p) { s.removeFirst(p.count) }
  if s.hasPrefix("www.") { s.removeFirst(4) }
  return String(s.split(whereSeparator: { "/?#".contains($0) }).first ?? "")
}

func presetFor(name: String, domain: String) -> Preset? {
  let d = cleanDomain(domain), n = name.lowercased()
  return Presets.all.first { p in (!d.isEmpty && (d == p.domain || d.hasSuffix("." + p.domain))) || p.name.lowercased() == n }
}

func carrier(_ v: String) -> String {
  var d = v.filter(\.isNumber)
  if d.hasPrefix("0086") { d.removeFirst(4) } else if d.hasPrefix("86") && d.count == 13 { d.removeFirst(2) }
  guard d.count == 11, d.hasPrefix("1") else {
    let t = v.trimmingCharacters(in: .whitespaces)
    if t.hasPrefix("+") { let cc = t.dropFirst().prefix(while: \.isNumber).prefix(3); if !cc.isEmpty && cc != "86" { return "+" + cc } }
    return ""
  }
  let p3 = String(d.prefix(3)), p4 = String(d.prefix(4))
  let mobile: Set<String> = ["134","135","136","137","138","139","147","148","150","151","152","157","158","159","172","178","182","183","184","187","188","195","197","198"]
  let unicom: Set<String> = ["130","131","132","145","146","155","156","166","167","171","175","176","185","186","196"]
  let telecom: Set<String> = ["133","149","153","173","174","177","180","181","189","190","191","193","199"]
  if p4 == "1349" { return "中国电信" }
  if mobile.contains(p3) { return "中国移动" }
  if unicom.contains(p3) { return "中国联通" }
  if telecom.contains(p3) { return "中国电信" }
  if p3 == "192" { return "中国广电" }
  return ""
}

// MARK: - store

struct Toast: Identifiable, Equatable {
  let id = UUID()
  var text: String
  var action: String? = nil
  static func == (a: Toast, b: Toast) -> Bool { a.id == b.id }
}

enum Route: Identifiable, Equatable {
  case identity(String), account(String), newIdentity(String), editIdentity(String), editAccount(String), picker(String?), settings
  var id: String {
    switch self {
    case .identity(let s): return "i" + s
    case .account(let s): return "a" + s
    case .newIdentity(let s): return "n" + s
    case .editIdentity(let s): return "ei" + s
    case .editAccount(let s): return "ea" + s
    case .picker(let s): return "p" + (s ?? "")
    case .settings: return "s"
    }
  }
}

/// Everything views read that is derived from `data`, built once per change instead of on every
/// body evaluation (sorting with localizedStandardCompare in every card/row was the main cost).
struct AtlasIndex {
  var byIdentity: [String: [Account]] = [:]     // sorted by name
  var identityByID: [String: Identity] = [:]
  var accountByID: [String: Account] = [:]
  var byName: [Account] = []
  var recent: [Account] = []                     // newest 5
  var emails: [Identity] = []
  var phones: [Identity] = []
  var categoryCount: [String: Int] = [:]
  var categories: [String] = []
  var byCategory: [String: [Account]] = [:]      // sorted by name
  init() {}
  init(_ d: AtlasData) {
    byName = d.accounts.sorted { $0.name.localizedStandardCompare($1.name) == .orderedAscending }
    for i in d.identities {
      identityByID[i.id] = i
      if i.type == "email" { emails.append(i) } else if i.type == "phone" { phones.append(i) }
    }
    for a in byName {
      accountByID[a.id] = a
      for id in a.identityIds { byIdentity[id, default: []].append(a) }
      categoryCount[a.category, default: 0] += 1
      byCategory[a.category, default: []].append(a)
    }
    recent = Array(d.accounts.sorted { $0.createdAt > $1.createdAt }.prefix(5))
    let have = Set(categoryCount.keys)
    categories = CATS.filter { have.contains($0) } + have.filter { !CATS.contains($0) }.sorted()
  }
}

@MainActor
@Observable
final class Store {
  // Observation tracks each property separately, so swiping the card deck (activeID) or showing a
  // toast no longer invalidates every view of every tab the way one ObservableObject did.
  var data = AtlasData() { didSet { index = AtlasIndex(data) } }
  private(set) var index = AtlasIndex()
  var privacy: Bool = UserDefaults.standard.bool(forKey: "atlas.privacy") { didSet { UserDefaults.standard.set(privacy, forKey: "atlas.privacy") } }
  var kind: String = UserDefaults.standard.string(forKey: "atlas.kind") ?? "email" { didSet { UserDefaults.standard.set(kind, forKey: "atlas.kind") } }
  var theme: String = UserDefaults.standard.string(forKey: "atlas.theme") ?? "dark" { didSet { UserDefaults.standard.set(theme, forKey: "atlas.theme") } }
  var activeID: String? = nil
  var route: Route? = nil
  var toast: Toast? = nil
  var demoFocus: String? = nil
  @ObservationIgnored var toastAction: (() -> Void)? = nil
  @ObservationIgnored var onSaved: (() -> Void)? = nil

  @ObservationIgnored private var seenI: [String: Identity] = [:]
  @ObservationIgnored private var seenA: [String: Account] = [:]
  @ObservationIgnored private var undoSnap: AtlasData? = nil
  private let io = DispatchQueue(label: "atlas.persist", qos: .utility)

  init() {
    data = AtlasData()
    index = AtlasIndex(data)
  }

  /// 解锁成功后从保险层加载明文数据；首次设置密码时载入示例。
  func loadFromVault(firstSetup: Bool = false) {
    let raw = Vault.shared.readData()
    if let s = try? JSONDecoder().decode(AtlasData.self, from: raw) {
      data = s
    } else if firstSetup {
      data = Store.demo()
    } else {
      data = AtlasData()
    }
    index = AtlasIndex(data)
    remember()
    activeID = deck.first?.id
    IconStore.shared.prefetch(data.accounts)
  }

  /// 锁定后清空内存数据
  func clearOnLock() {
    data = AtlasData()
    index = AtlasIndex(data)
    activeID = nil
  }

  var deck: [Identity] { kind == "phone" ? index.phones : kind == "email" ? index.emails : data.identities.filter { $0.type == kind } }
  var active: Identity? { deck.first { $0.id == activeID } ?? (activeID == "add" ? nil : deck.first) }
  func identity(_ id: String) -> Identity? { index.identityByID[id] }
  func account(_ id: String) -> Account? { index.accountByID[id] }
  func accounts(of id: String) -> [Account] { index.byIdentity[id] ?? [] }

  func display(_ i: Identity) -> String {
    guard privacy else { return i.value }
    if i.type == "email" {
      let parts = i.value.split(separator: "@", maxSplits: 1).map(String.init)
      return String((parts.first ?? "").prefix(2)) + "•••" + (parts.count > 1 ? "@" + parts[1] : "")
    }
    let digits = i.value.filter(\.isNumber)
    return digits.count > 7 ? "••• •••• " + digits.suffix(4) : "••••"
  }
  func handleText(_ h: String) -> String { privacy ? String(h.prefix(1)) + "•••" : h }
  func title(_ i: Identity) -> String { i.label.isEmpty ? display(i) : i.label }

  // every change goes through here: snapshot for undo, stamp, persist, sync.
  // Works on a copy and assigns once, so observers and the index update exactly once per change.
  func mutate(_ message: String? = nil, undo: Bool = true, _ f: (inout AtlasData) -> Void) {
    undoSnap = data
    var d = data
    f(&d)
    d.demo = nil
    stamp(&d)
    data = d
    afterSave()
    if let message { show(message, action: undo ? "撤销" : nil) { [weak self] in self?.undo() } }
  }
  func undo() {
    guard let s = undoSnap else { return }
    data = s; save()
  }

  private static func plain(_ x: Identity) -> Identity { var y = x; y.updatedAt = nil; return y }
  private static func plain(_ x: Account) -> Account { var y = x; y.updatedAt = nil; return y }
  func remember() {
    seenI = [:]; seenA = [:]
    for x in data.identities { seenI[x.id] = Store.plain(x) }
    for x in data.accounts { seenA[x.id] = Store.plain(x) }
  }
  /// stamps changed records (compared field by field, ignoring updatedAt) and tombstones removed ones
  private func stamp(_ d: inout AtlasData) {
    let now = nowMS()
    var live = Set<String>()
    for k in d.identities.indices {
      let x = d.identities[k]; live.insert(x.id)
      if seenI[x.id] != Store.plain(x) || x.updatedAt == nil { d.identities[k].updatedAt = now }
      d.deleted[x.id] = nil
    }
    for k in d.accounts.indices {
      let x = d.accounts[k]; live.insert(x.id)
      if seenA[x.id] != Store.plain(x) || x.updatedAt == nil { d.accounts[k].updatedAt = now }
      d.deleted[x.id] = nil
    }
    for id in seenI.keys where !live.contains(id) { d.deleted[id] = now }
    for id in seenA.keys where !live.contains(id) { d.deleted[id] = now }
  }
  func save() {
    var d = data
    stamp(&d)
    data = d
    afterSave()
  }
  private func afterSave() {
    remember()
    persist()
    onSaved?()
  }
  /// 数据经主密码加密后落盘（Vault 为 @MainActor）
  func persist() {
    guard Vault.shared.isUnlocked, let d = try? JSONEncoder().encode(data) else { return }
    Vault.shared.save(d)
  }
  // replace everything (sync merge), without new stamps
  func adopt(_ d: AtlasData) {
    data = d; remember(); persist()
    IconStore.shared.prefetch(d.accounts)
    if active == nil || !deck.contains(where: { $0.id == activeID }) { activeID = deck.first?.id }
  }

  func show(_ text: String, action: String? = nil, _ fn: (() -> Void)? = nil) {
    toastAction = fn
    withAnimation(Motion.smooth) { toast = Toast(text: text, action: action) }
    let t = toast
    DispatchQueue.main.asyncAfter(deadline: .now() + (action == nil ? 2.2 : 4.5)) { [weak self] in
      if self?.toast == t { withAnimation(.easeInOut(duration: 0.3)) { self?.toast = nil } }
    }
  }

  func setKind(_ k: String, focus: String? = nil) {
    kind = k
    activeID = focus ?? deck.first?.id ?? "add"
  }

  // apps the user typed in by hand come back in the picker
  var customApps: [Preset] {
    var out: [Preset] = [], seenN = Set<String>()
    let saved = (UserDefaults.standard.array(forKey: "atlas.custom") as? [[String]]) ?? []
    let all = saved + data.accounts.reversed().map { [$0.name, $0.domain, $0.category] }
    for t in all where t.count >= 1 {
      let k = t[0].lowercased()
      if k.isEmpty || seenN.contains(k) || Presets.all.contains(where: { $0.name.lowercased() == k }) { continue }
      seenN.insert(k)
      out.append(Preset(name: t[0], domain: t.count > 1 ? t[1] : "", category: t.count > 2 ? t[2] : "其他", color: ""))
    }
    return out
  }
  func rememberApp(_ p: Preset) {
    if Presets.all.contains(where: { $0.name.lowercased() == p.name.lowercased() }) { return }
    var saved = (UserDefaults.standard.array(forKey: "atlas.custom") as? [[String]]) ?? []
    saved.removeAll { $0.first?.lowercased() == p.name.lowercased() }
    saved.insert([p.name, p.domain, p.category], at: 0)
    UserDefaults.standard.set(Array(saved.prefix(80)), forKey: "atlas.custom")
  }

  func backupJSON() -> Data {
    let iso = ISO8601DateFormatter().string(from: Date())
    let ids = (try? JSONSerialization.jsonObject(with: JSONEncoder().encode(data.identities))) ?? []
    let accs = (try? JSONSerialization.jsonObject(with: JSONEncoder().encode(data.accounts))) ?? []
    let o: [String: Any] = ["app": "atlas", "version": 1, "exportedAt": iso, "identities": ids, "accounts": accs]
    return (try? JSONSerialization.data(withJSONObject: o, options: [.prettyPrinted, .sortedKeys])) ?? Data()
  }
  func importBackup(_ d: Data) -> Bool {
    guard let b = try? JSONDecoder().decode(AtlasData.self, from: d),
          let raw = try? JSONSerialization.jsonObject(with: d) as? [String: Any], raw["identities"] is [Any], raw["accounts"] is [Any] else { return false }
    mutate("已导入 \(b.identities.count) 个身份和 \(b.accounts.count) 个网站") { s in s.identities = b.identities; s.accounts = b.accounts }
    setKind(kind)
    return true
  }

  static func demo() -> AtlasData {
    let ids = [
      Identity(type: "email", value: "me@gmail.com", label: "主力邮箱", color: "#0a84ff"),
      Identity(type: "phone", value: "+86 138 0000 1234", label: "主力手机号", color: "#30d158", notes: "移动，已实名"),
      Identity(type: "email", value: "shopping.fun@qq.com", label: "购物小号", color: "#ff375f"),
      Identity(type: "email", value: "dev@outlook.com", label: "开发专用", color: "#bf5af2"),
      Identity(type: "phone", value: "+86 170 0000 5678", label: "副卡", color: "#ff9f0a"),
    ]
    let g = ids[0].id, p1 = ids[1].id, q = ids[2].id, o = ids[3].id, p2 = ids[4].id
    let day = 86_400_000.0, now = nowMS()
    func mk(_ n: String, _ d: String, _ c: String, _ i: [String], _ ago: Double, _ notes: String = "") -> Account {
      Account(name: n, domain: d, category: c, identityIds: i, notes: notes, createdAt: now - ago * day - Double.random(in: 0..<3e6))
    }
    return AtlasData(identities: ids, accounts: [
      mk("GitHub", "github.com", "开发", [o, p1], 0, "已开两步验证"), mk("Claude", "claude.ai", "AI", [g], 0),
      mk("Cloudflare", "cloudflare.com", "开发", [o], 1), mk("ChatGPT", "chatgpt.com", "AI", [g], 1),
      mk("小红书", "xiaohongshu.com", "社交", [p2], 3), mk("Binance", "binance.com", "加密", [o, p1], 3, "已完成 KYC"),
      mk("淘宝", "taobao.com", "购物", [q, p1], 9), mk("京东", "jd.com", "购物", [q, p2], 12),
      mk("微信", "weixin.qq.com", "社交", [p1], 30), mk("支付宝", "alipay.com", "支付金融", [p1], 30),
      mk("哔哩哔哩", "bilibili.com", "娱乐", [p2], 40), mk("Steam", "steampowered.com", "游戏", [q], 60, "令牌在主力手机上"),
      mk("Apple ID", "apple.com", "工具", [g, p1], 90), mk("Notion", "notion.so", "工具", [g], 120), mk("网易云音乐", "music.163.com", "娱乐", [p2], 150),
    ], demo: true)
  }
}
