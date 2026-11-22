/* ============================================================================
 * Account Vault — 本地加密保险层 (路线 B 安全加固)
 * ----------------------------------------------------------------------------
 * 设计目标：让"账号密码"真正加密落盘，而不是明文躺在 localStorage。
 *
 * 机制：
 *   1. 密码 (vault password) 经 PBKDF2-HMAC-SHA256 (31 万次) + 随机盐派生 256 位密钥
 *   2. 所有敏感数据用 AES-256-GCM 加密，密文 (含 salt/iv/tag) 存进 localStorage 的单一条目
 *   3. 解锁后明文只存在于本闭包内存 (cache)；锁定 / 空闲超时立即清零
 *   4. 网络图标请求被禁用：App 图标一律走 icons/ 目录本地资源，杜绝域名外传
 *
 * 对外接口（供 app.js 使用，签名与 localStorage 一致，便于机械替换）：
 *   Vault.status()            -> 'new' | 'locked' | 'unlocked'
 *   Vault.unlock(pass)        -> Promise<boolean>  解锁（校验密码）
 *   Vault.create(pass)        -> Promise<void>     首次创建保险库
 *   Vault.lock()              -> void              立即锁定并清零内存
 *   Vault.getItem(k)          -> string|null       同步读（仅解锁后可用）
 *   Vault.setItem(k, v)       -> void              同步写（异步加密落盘）
 *   Vault.removeItem(k)       -> void
 *   Vault.changePassword(old,new) -> Promise<boolean>
 *   Vault.enableAutoUnlock()  -> Promise<boolean>  开启免密启动（保存会话密钥到本机）
 *   Vault.disableAutoUnlock() -> void              关闭免密启动
 *   Vault.hasAutoUnlock()     -> boolean           是否已开启
 *   Vault.tryAutoUnlock()     -> Promise<boolean>  用保存的密钥自动恢复会话
 *   Vault.remainingMs()       -> number            距自动锁定剩余毫秒
 *   Vault.touch()             -> void              重置空闲计时
 *
 * 注意：WebCrypto (crypto.subtle) 要求安全上下文 (https 或 localhost)。
 * 直接双击 file:// 打开时 crypto.subtle 可能不可用，界面会给出提示。
 * ==========================================================================*/
(() => {
  'use strict';

  const META_KEY = 'atlas.vault.meta';   // 派生参数（盐、验证头），不含明文
  const DATA_KEY = 'atlas.vault.data';   // 加密后的数据块
  const QUICK_KEY = 'atlas.vault.quick'; // 免密启动：导出后的原始 AES 密钥（仅本机）
  const LEGACY_KEYS = ['atlas.v1', 'atlas.sync', 'atlas.custom']; // 加固前的明文键，迁移后清除
  const SENSITIVE = new Set(['atlas.v1', 'atlas.sync', 'atlas.custom']); // 需要加密的键
  const IDLE_MS = 5 * 60 * 1000;          // 空闲 5 分钟自动锁定
  const ITER = 310000;

  const enc = new TextEncoder(), dec = new TextDecoder();
  const b64 = buf => btoa(String.fromCharCode(...new Uint8Array(buf)));
  const unb64 = s => Uint8Array.from(atob(s), c => c.charCodeAt(0));
  const hasCrypto = !!(window.crypto && window.crypto.subtle);

  let AES_KEY = null;       // CryptoKey，仅内存，锁定后清零
  let cache = null;         // { atlas.v1, atlas.sync, atlas.custom, ... } 明文内存
  let idleTimer = null;
  let lastTouch = Date.now();
  const listeners = new Set();

  const emit = () => listeners.forEach(f => { try { f(Vault.status()); } catch (_) {} });

  function status() {
    if (AES_KEY && cache) return 'unlocked';
    return localStorage.getItem(META_KEY) ? 'locked' : 'new';
  }

  async function derive(pass, saltB64) {
    const salt = unb64(saltB64);
    const base = await crypto.subtle.importKey('raw', enc.encode(pass), 'PBKDF2', false, ['deriveKey']);
    return crypto.subtle.deriveKey(
      { name: 'PBKDF2', salt, iterations: ITER, hash: 'SHA-256' },
      base, { name: 'AES-GCM', length: 256 }, true, ['encrypt', 'decrypt']
    );
  }

  // 用密码加密整个 cache，写入单一数据块
  async function persist() {
    if (!AES_KEY || !cache) return;
    const iv = crypto.getRandomValues(new Uint8Array(12));
    const plain = enc.encode(JSON.stringify(cache));
    const ct = await crypto.subtle.encrypt({ name: 'AES-GCM', iv }, AES_KEY, plain);
    localStorage.setItem(DATA_KEY, JSON.stringify({ v: 1, iv: b64(iv), ct: b64(ct), at: Date.now() }));
  }

  function readPlain(k) {
    // 非敏感键（主题、语言等偏好）走原生 localStorage
    if (!SENSITIVE.has(k)) return localStorage.getItem(k);
    return cache ? (k in cache ? cache[k] : null) : null;
  }

  function writePlain(k, v) {
    if (!SENSITIVE.has(k)) { localStorage.setItem(k, v); return; }
    if (!cache) throw new Error('VAULT_LOCKED');
    cache[k] = String(v);
    persist(); // fire-and-forget；写入内存是同步的，UI 不受影响
  }

  function removePlain(k) {
    if (!SENSITIVE.has(k)) { localStorage.removeItem(k); return; }
    if (!cache) throw new Error('VAULT_LOCKED');
    delete cache[k];
    persist();
  }

  async function unlock(pass) {
    if (!hasCrypto) return false;
    const meta = (() => { try { return JSON.parse(localStorage.getItem(META_KEY)); } catch { return null; } })();
    if (!meta || !meta.salt || !meta.probe) return false;
    let key;
    try {
      key = await derive(pass, meta.salt);
      // 用探针校验密码：解密一段固定明文
      const probe = await crypto.subtle.decrypt({ name: 'AES-GCM', iv: unb64(meta.piv) }, key, unb64(meta.probe));
      if (dec.decode(probe) !== 'atlas-vault-probe-v1') return false;
    } catch (_) { return false; }

    // 解密数据块
    let data = {};
    const blob = (() => { try { return JSON.parse(localStorage.getItem(DATA_KEY)); } catch { return null; } })();
    if (blob && blob.iv && blob.ct) {
      try {
        const pt = await crypto.subtle.decrypt({ name: 'AES-GCM', iv: unb64(blob.iv) }, key, unb64(blob.ct));
        data = JSON.parse(dec.decode(pt));
      } catch (_) { /* 数据块损坏则视为空，避免误锁死用户 */ }
    } else {
      // 兼容迁移：首次加固时把已有的明文敏感键收进保险库
      for (const k of LEGACY_KEYS) {
        const old = localStorage.getItem(k);
        if (old != null) data[k] = old;
      }
    }

    AES_KEY = key;
    cache = data;
    // 迁移完成后清除明文残留
    LEGACY_KEYS.forEach(k => localStorage.removeItem(k));
    await persist();
    startIdle();
    emit();
    return true;
  }

  async function create(pass) {
    if (!hasCrypto) throw new Error('NO_CRYPTO');
    const saltB64 = b64(crypto.getRandomValues(new Uint8Array(16)));
    const key = await derive(pass, saltB64);
    const piv = crypto.getRandomValues(new Uint8Array(12));
    const probe = await crypto.subtle.encrypt({ name: 'AES-GCM', iv: piv }, key, enc.encode('atlas-vault-probe-v1'));
    localStorage.setItem(META_KEY, JSON.stringify({ v: 1, salt: saltB64, piv: b64(piv), probe: b64(probe), iter: ITER }));
    AES_KEY = key;
    cache = {};
    // 把已有的明文敏感键纳入（若有）
    for (const k of LEGACY_KEYS) {
      const old = localStorage.getItem(k);
      if (old != null) cache[k] = old;
    }
    await persist();
    LEGACY_KEYS.forEach(k => localStorage.removeItem(k));
    startIdle();
    emit();
  }

  async function changePassword(oldPass, newPass) {
    if (status() !== 'unlocked') return false;
    const meta = JSON.parse(localStorage.getItem(META_KEY));
    const oldKey = await derive(oldPass, meta.salt);
    try {
      await crypto.subtle.decrypt({ name: 'AES-GCM', iv: unb64(meta.piv) }, oldKey, unb64(meta.probe));
    } catch (_) { return false; }
    if (!newPass || newPass.length < 8) return false;
    const saltB64 = b64(crypto.getRandomValues(new Uint8Array(16)));
    const key = await derive(newPass, saltB64);
    const piv = crypto.getRandomValues(new Uint8Array(12));
    const probe = await crypto.subtle.encrypt({ name: 'AES-GCM', iv: piv }, key, enc.encode('atlas-vault-probe-v1'));
    localStorage.setItem(META_KEY, JSON.stringify({ v: 1, salt: saltB64, piv: b64(piv), probe: b64(probe), iter: ITER }));
    AES_KEY = key;
    await persist();
    // 免密启动开启时，同步刷新保存的会话密钥，避免旧密钥失效导致无法免密解锁
    if (hasAutoUnlock()) await enableAutoUnlock();
    emit();
    return true;
  }

  // ---- 免密启动：把解锁后的会话密钥保存到本机，下次打开自动恢复 ----
  // 说明：保存的是 256 位 AES 原始密钥（非用户密码本身），仅存于本机 localStorage。
  // 开启后任何能访问本机数据的人都可绕过密码直接查看，属于便利性与安全的取舍。
  async function enableAutoUnlock() {
    if (!hasCrypto || !AES_KEY) return false;
    try {
      const raw = await crypto.subtle.exportKey('raw', AES_KEY);
      localStorage.setItem(QUICK_KEY, b64(raw));
      return true;
    } catch (_) { return false; }
  }
  function disableAutoUnlock() { localStorage.removeItem(QUICK_KEY); }
  function hasAutoUnlock() { return !!localStorage.getItem(QUICK_KEY); }
  async function tryAutoUnlock() {
    if (!hasCrypto) return false;
    const rawB64 = localStorage.getItem(QUICK_KEY);
    if (!rawB64) return false;
    try {
      const raw = unb64(rawB64);
      const key = await crypto.subtle.importKey('raw', raw, 'AES-GCM', true, ['encrypt', 'decrypt']);
      const blob = (() => { try { return JSON.parse(localStorage.getItem(DATA_KEY)); } catch { return null; } })();
      let data = {};
      if (blob && blob.iv && blob.ct) {
        const pt = await crypto.subtle.decrypt({ name: 'AES-GCM', iv: unb64(blob.iv) }, key, unb64(blob.ct));
        data = JSON.parse(dec.decode(pt));
      }
      AES_KEY = key;
      cache = data;
      startIdle();
      emit();
      return true;
    } catch (_) { disableAutoUnlock(); return false; }
  }

  function clearMemory() {
    // 尽力清零（JS 无法保证物理内存抹除，但断开引用，避免被脚本读取）
    AES_KEY = null;
    cache = null;
    if (idleTimer) { clearTimeout(idleTimer); idleTimer = null; }
  }

  function lock() {
    clearMemory();
    emit();
  }

  function startIdle() {
    if (idleTimer) clearTimeout(idleTimer);
    lastTouch = Date.now();
    idleTimer = setTimeout(() => { if (status() === 'unlocked') lock(); }, IDLE_MS);
  }

  function touch() {
    if (status() !== 'unlocked') return;
    lastTouch = Date.now();
    startIdle();
  }

  function remainingMs() {
    return status() === 'unlocked' ? Math.max(0, IDLE_MS - (Date.now() - lastTouch)) : 0;
  }

  // 任意用户操作都重置空闲计时
  ['pointerdown', 'keydown', 'touchstart'].forEach(ev =>
    addEventListener(ev, () => { if (status() === 'unlocked') touch(); }, { passive: true })
  );

  // 用密码把当前数据导出为加密备份（即使在任何设备上，也只有同一密码能导入）
  async function exportEncrypted(pass) {
    if (!hasCrypto || status() !== 'unlocked') throw new Error('VAULT_LOCKED');
    const meta = JSON.parse(localStorage.getItem(META_KEY));
    // 校验传入密码确实能解开当前库
    const k = await derive(pass, meta.salt);
    try { await crypto.subtle.decrypt({ name: 'AES-GCM', iv: unb64(meta.piv) }, k, unb64(meta.probe)); }
    catch (_) { throw new Error('BAD_PASSWORD'); }
    const salt = crypto.getRandomValues(new Uint8Array(16));
    const key = await derive(pass, b64(salt));
    const iv = crypto.getRandomValues(new Uint8Array(12));
    const ct = await crypto.subtle.encrypt({ name: 'AES-GCM', iv }, key, enc.encode(JSON.stringify(cache)));
    const piv = crypto.getRandomValues(new Uint8Array(12));
    const probe = await crypto.subtle.encrypt({ name: 'AES-GCM', iv: piv }, key, enc.encode('atlas-vault-probe-v1'));
    return JSON.stringify({ app: 'atlas-vault-backup', v: 1, exportedAt: new Date().toISOString(),
      salt: b64(salt), piv: b64(piv), probe: b64(probe), iv: b64(iv), ct: b64(ct) }, null, 2);
  }

  // 从加密备份恢复：返回明文数据对象（调用方负责写入 cache 后重启）
  async function importEncrypted(pass, text) {
    if (!hasCrypto) throw new Error('NO_CRYPTO');
    const box = JSON.parse(text);
    if (box.app !== 'atlas-vault-backup' || !box.salt || !box.ct) throw new Error('BAD_FORMAT');
    const key = await derive(pass, box.salt);
    try {
      await crypto.subtle.decrypt({ name: 'AES-GCM', iv: unb64(box.piv) }, key, unb64(box.probe));
      const pt = await crypto.subtle.decrypt({ name: 'AES-GCM', iv: unb64(box.iv) }, key, unb64(box.ct));
      return JSON.parse(dec.decode(pt));
    } catch (_) { throw new Error('BAD_PASSWORD'); }
  }

  const Vault = {
    status, unlock, create, lock, changePassword, remainingMs, touch,
    exportEncrypted, importEncrypted,
    enableAutoUnlock, disableAutoUnlock, hasAutoUnlock, tryAutoUnlock,
    getItem: readPlain, setItem: writePlain, removeItem: removePlain,
    onStatus: f => { listeners.add(f); return () => listeners.delete(f); },
    get available() { return hasCrypto; },
    get idleMs() { return IDLE_MS; },
  };
  window.AtlasVault = Vault;
})();
