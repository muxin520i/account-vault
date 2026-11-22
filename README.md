# 账号仓库 · 加固版（真原生三端）

端到端加密的账号密码本地管理工具。三端均为**真原生实现**（非网页套壳），数据在主密码解锁后才可读取，本地落盘为 AES-256-GCM 密文。

## 三端技术

| 平台 | 技术 | 源码目录 | 编译 |
|---|---|---|---|
| Android | Kotlin + Jetpack Compose | `android/` | `build-apk.yml`（ubuntu） |
| Windows | .NET 6 WinForms | `windows/` | `build-exe.yml`（windows） |
| iOS | SwiftUI + CryptoKit | `native/` | `build-ios.yml`（macos，swiftc） |

## 安全设计

- 主密码经 PBKDF2-HMAC-SHA256（31 万次）派生密钥，AES-256-GCM 加密落盘
- 明文仅在解锁期间存在于内存；锁定/退到后台立即清空
- 验证头校验主密码正确性，密码错误无法解密
- 支持修改主密码、导出/导入加密备份（备份密码即主密码）
- 112 个网站图标全部本地内置，零外部网络请求
- iOS 未签名 IPA，需自行用个人证书签名安装

## 下载

Releases：`https://github.com/muxin520i/account-vault/releases/tag/v2.0`

## 重新编译

在 Actions 页面手动触发对应 workflow 即可，产物自动上传到 v2.0 Release。

## 说明

本项目基于开源项目 [Ckxkx/account-atlas](https://github.com/Ckxkx/account-atlas) 加固改造，真原生源码亦源自该项目（Android Compose / iOS SwiftUI），在其上增加本地加密保险层。
