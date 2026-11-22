import Foundation
import CryptoKit

/// 账号仓库 · 本地加密保险层（原生 iOS）
/// vault.dat = {"v":1,"salt":b64,"iv":b64,"ct":b64(ciphertext||tag)}
/// key = PBKDF2-HMAC-SHA256(主密码, salt[16], 310000, 32)
enum VaultError: Error { case badPassword, badFormat }

@MainActor
final class Vault: ObservableObject {
  static let shared = Vault()
  enum State { case needSetup, locked, unlocked }

  @Published var state: State = .needSetup

  private let dir = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
  private var dataURL: URL { dir.appendingPathComponent("vault.dat") }
  private var metaURL: URL { dir.appendingPathComponent("vault.meta.json") }
  private var key: SymmetricKey?
  private let probe = "accountvault-probe-v1"

  private struct Meta: Codable { var salt: String; var probeIv: String; var probeCt: String }
  private struct Envelope: Codable { var app: String; var v: Int; var salt: String?; var iv: String; var ct: String }

  init() {
    state = (try? Data(contentsOf: metaURL)) != nil && (try? Data(contentsOf: dataURL)) != nil ? .locked : .needSetup
  }

  var isUnlocked: Bool { key != nil }

  private func readMeta() -> Meta? {
    guard let d = try? Data(contentsOf: metaURL), let m = try? JSONDecoder().decode(Meta.self, from: d) else { return nil }
    return m
  }

  private func derive(_ pass: String, _ salt: Data) -> SymmetricKey { Sync.derive(pass, salt: salt) }

  private func seal(_ plain: Data, _ k: SymmetricKey, _ saltB64: String, app: String = "accountvault-local") -> String {
    let box = try! AES.GCM.seal(plain, using: k)
    let iv = box.nonce.withUnsafeBytes { Data($0) }
    let ct = box.ciphertext + box.tag
    let env = Envelope(app: app, v: 1, salt: saltB64, iv: iv.base64EncodedString(), ct: ct.base64EncodedString())
    return String(data: try! JSONEncoder().encode(env), encoding: .utf8) ?? ""
  }

  private func openEnv(_ text: String, _ k: SymmetricKey) throws -> Data {
    guard let d = text.data(using: .utf8), let env = try? JSONDecoder().decode(Envelope.self, from: d),
          let iv = Data(base64Encoded: env.iv), let ct = Data(base64Encoded: env.ct), ct.count > 16 else { throw VaultError.badFormat }
    let box = try AES.GCM.SealedBox(nonce: AES.GCM.Nonce(data: iv), ciphertext: ct.prefix(ct.count - 16), tag: ct.suffix(16))
    return try AES.GCM.open(box, using: k)
  }

  /// 首次设置主密码；迁移旧明文 atlas.json
  func create(_ password: String) -> Bool {
    guard password.count >= 4 else { return false }
    let salt = Data((0..<16).map { _ in UInt8.random(in: 0...255) })
    let k = derive(password, salt)
    let saltB64 = salt.base64EncodedString()
    let piv = Data((0..<12).map { _ in UInt8.random(in: 0...255) })
    let pbox = try! AES.GCM.seal(Data(probe.utf8), using: k)
    let pct = pbox.ciphertext + pbox.tag
    let meta = Meta(salt: saltB64, probeIv: piv.base64EncodedString(), probeCt: pct.base64EncodedString())
    try? JSONEncoder().encode(meta).write(to: metaURL, options: .atomic)
    key = k
    // 迁移旧明文
    let legacy = dir.appendingPathComponent("atlas.json")
    var plain = Data("{}".utf8)
    if let old = try? Data(contentsOf: legacy) { plain = old }
    try? seal(plain, k, saltB64).data(using: .utf8)?.write(to: dataURL, options: .atomic)
    try? FileManager.default.removeItem(at: legacy)
    state = .unlocked
    return true
  }

  func unlock(_ password: String) -> Bool {
    guard let m = readMeta(), let salt = Data(base64Encoded: m.salt),
          let piv = Data(base64Encoded: m.probeIv), let pct = Data(base64Encoded: m.probeCt), pct.count > 16 else { return false }
    let k = derive(password, salt)
    do {
      let box = try AES.GCM.SealedBox(nonce: AES.GCM.Nonce(data: piv), ciphertext: pct.prefix(pct.count - 16), tag: pct.suffix(16))
      let p = try AES.GCM.open(box, using: k)
      guard String(data: p, encoding: .utf8) == probe else { return false }
    } catch { return false }
    key = k
    state = .unlocked
    return true
  }

  func verify(_ password: String) -> Bool {
    guard let m = readMeta(), let salt = Data(base64Encoded: m.salt),
          let piv = Data(base64Encoded: m.probeIv), let pct = Data(base64Encoded: m.probeCt), pct.count > 16 else { return false }
    let k = derive(password, salt)
    do {
      let box = try AES.GCM.SealedBox(nonce: AES.GCM.Nonce(data: piv), ciphertext: pct.prefix(pct.count - 16), tag: pct.suffix(16))
      return String(data: try AES.GCM.open(box, using: k), encoding: .utf8) == probe
    } catch { return false }
  }

  func changePassword(_ old: String, _ new: String) -> Bool {
    guard verify(old), new.count >= 4, let k0 = key, let plain = try? readDataRaw(using: k0) else { return false }
    let salt = Data((0..<16).map { _ in UInt8.random(in: 0...255) })
    let k = derive(new, salt)
    let saltB64 = salt.base64EncodedString()
    let piv = Data((0..<12).map { _ in UInt8.random(in: 0...255) })
    let pbox = try! AES.GCM.seal(Data(probe.utf8), using: k)
    let pct = pbox.ciphertext + pbox.tag
    let meta = Meta(salt: saltB64, probeIv: piv.base64EncodedString(), probeCt: pct.base64EncodedString())
    try? JSONEncoder().encode(meta).write(to: metaURL, options: .atomic)
    key = k
    try? seal(plain, k, saltB64).data(using: .utf8)?.write(to: dataURL, options: .atomic)
    return true
  }

  func readDataRaw(using k: SymmetricKey) throws -> Data {
    guard let text = try? String(contentsOf: dataURL, encoding: .utf8) else { return Data("{}".utf8) }
    return try openEnv(text, k)
  }

  func readData() -> Data {
    guard let k = key, let raw = try? readDataRaw(using: k) else { return Data("{}".utf8) }
    return raw
  }

  func save(_ plain: Data) {
    guard let k = key, let salt = readMeta()?.salt else { return }
    try? seal(plain, k, salt).data(using: .utf8)?.write(to: dataURL, options: .atomic)
  }

  func lock() { key = nil; state = .locked }

  func exportEncrypted() -> String? {
    guard let k = key, let salt = readMeta()?.salt, let raw = try? readDataRaw(using: k) else { return nil }
    return seal(raw, k, salt, app: "accountvault-backup")
  }

  func importEncrypted(_ text: String) -> Bool {
    guard let k = key, let raw = try? openEnv(text, k), let salt = readMeta()?.salt else { return false }
    try? seal(raw, k, salt).data(using: .utf8)?.write(to: dataURL, options: .atomic)
    return true
  }
}
