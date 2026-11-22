# 账号仓库·加固版

端到端加密的账号密码本地管理工具（Android / Windows / iOS 三端）。

## 安全特性

- 主密码解锁，PBKDF2-SHA256（31 万次）派生密钥
- AES-256-GCM 加密本地数据，明文零残留
- 112 个本地图标，零外部网络请求（不泄露网站域名）
- 空闲自动锁定、剪贴板 30 秒自动清除
- 导出备份默认加密

## 下载

到 [Releases](../../releases) 页面下载三端安装包：

- **Android**：`账号仓库-加固版-v1.0.apk`（包名 `com.atlasvault.app`，固定签名可覆盖升级）
- **Windows**：`账号仓库-加固版-便携版.exe`（免安装，双击即用）
- **iOS**：`AtlasVault-unsigned.ipa`（未签名，请用你自己的 Apple 证书签名安装）

## 源码结构

- `www/`：加固版网页内核（vault.js 加密层 + app.js）
- `ios/`：Capacitor iOS Xcode 工程
- `.github/workflows/build-ios.yml`：云端编译 IPA 的 Actions 配置
- `downloads/`：APK 与 EXE 安装包

## 重要提示

主密码忘记无法找回。换设备请用应用内「加密备份」功能迁移。

加固基于开源项目 [Ckxkx/account-atlas](https://github.com/Ckxkx/account-atlas)，已做本地加密与零外传改造。
