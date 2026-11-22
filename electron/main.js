const { app, BrowserWindow, protocol, shell, session } = require('electron');
const path = require('path');
const fs = require('fs');

// 自定义安全协议：https 是浏览器内建协议，无法用 registerFileProtocol 覆盖，
// 否则 loadURL 会走真实网络、DNS 解析失败导致白屏。改用私有协议并标记为安全
// 上下文，保证 crypto.subtle(WebCrypto) 仍可用。
const SCHEME = 'accountvault';
const HOST = 'local';
const WWW = path.join(__dirname, 'www');
const ORIGIN = `${SCHEME}://${HOST}`;

protocol.registerSchemesAsPrivileged([
  { scheme: SCHEME, privileges: { standard: true, secure: true, supportFetchAPI: true, bypassCSP: true, stream: true } },
]);

let win = null;

function createWindow() {
  win = new BrowserWindow({
    width: 1100,
    height: 800,
    minWidth: 360,
    minHeight: 600,
    title: '账号仓库',
    icon: path.join(__dirname, 'icon.ico'),
    show: false,
    webPreferences: {
      nodeIntegration: false,
      contextIsolation: true,
      spellcheck: false,
      devTools: false,
    },
  });

  win.setMenuBarVisibility(false);
  win.loadURL(`${ORIGIN}/index.html`);
  win.once('ready-to-show', () => win.show());

  // 外部链接一律用系统浏览器打开，不在应用内加载
  win.webContents.setWindowOpenHandler(({ url }) => {
    if (url.startsWith('http') && !url.startsWith(ORIGIN)) shell.openExternal(url);
    return { action: 'deny' };
  });
}

app.whenReady().then(() => {
  // accountvault://local/xxx -> www/xxx
  protocol.registerFileProtocol(SCHEME, (request, callback) => {
    try {
      const u = new URL(request.url);
      if (u.hostname !== HOST) return callback({ error: -6 });
      let rel = decodeURIComponent(u.pathname);
      if (rel === '/' || rel === '') rel = '/index.html';
      let file = path.normalize(path.join(WWW, rel));
      if (!file.startsWith(WWW)) return callback({ error: -6 });
      callback({ path: file });
    } catch (e) {
      callback({ error: -6 });
    }
  });

  // 精确白名单：只放行加密同步 API 与 App Store 图标搜索，其余外部请求一律掐断。
  // 同步的明文永不外传——传的是用同步密码加密后的密文，存到用户自己的私有 gist。
  const ALLOW_HOSTS = new Set([
    'gitee.com',            // Gitee API + gist raw 文件
    'api.github.com',       // GitHub API
    'gist.githubusercontent.com', // GitHub gist raw 文件
    'raw.githubusercontent.com',
    'gist.github.com',
    'itunes.apple.com',     // App Store 图标搜索
  ]);
  session.defaultSession.webRequest.onBeforeRequest((details, cb) => {
    const url = details.url;
    if (url.startsWith(ORIGIN) || url.startsWith('data:') || url.startsWith('blob:') || url.startsWith('devtools:')) {
      return cb({ cancel: false });
    }
    try {
      const u = new URL(url);
      if (u.protocol === 'https:' && ALLOW_HOSTS.has(u.hostname)) {
        return cb({ cancel: false });
      }
    } catch (e) { /* 非法 URL，走掐断 */ }
    cb({ cancel: true }); // 掐断其余一切外传
  });

  createWindow();

  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) createWindow();
  });
});

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit();
});
