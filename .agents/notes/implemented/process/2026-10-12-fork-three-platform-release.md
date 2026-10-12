# Agent Note: zhuoyiwan 三端改版集中发布与 macOS 本机签名

Status: implemented

## Problem

Windows C++ 工程不能改配置后成为 macOS InputMethodKit 输入法。用户要求 GitHub Actions 编译 Mac，并将已有 Windows、Android 安装包与 Mac 包放在自己仓库的同一 Release；用户没有 Apple Developer ID 证书。

## Decision

Android 与 Mac 源码固定在用户自己的 msime fork 分支，Windows 工作流按完整提交 SHA 获取 Mac 源码并调用原生 package-release.sh。三端发布在 zhuoyiwan/msime-windows：先暂存已验收的 Windows r6 和 Android r4，Mac 构建成功、校验产物后再公开 Release。不推送上游、不更新上游 Homebrew tap，也不进入 Windows main 自动发布链。

Mac 包明确为无 Apple 公证的本机安装版。随 DMG 带安装脚本：在当前用户钥匙串建立仅用于本机输入法的自签名代码证书、用户信任、重签名自己的 App 副本并启动设置宿主。保留原生设置的词库准备与输入源安装流程；不关闭全局 Gatekeeper。真实 Mac 输入源注册验收与 CI 编译必须分别报告，不能把签名检查当成打字验收。

## Related decisions

2026-10-09-macos-ci-and-release-on-xcode-26.md 部分重叠：复用 Xcode 26.3、universal 打包与固定资源校验。2026-10-09-macos-first-install-login-required.md 部分重叠：保留首次安装重新登录的限制。Android 9 的既有验收缺口不由本次发布绕过或改成通过。

## Alternatives considered

只调用上游 Mac 工作流能少改文件，但不能保存改版身份及统一三端资产。直接使用 Windows CMake 配置避免维护另一源码仓库，但 TSF/Win32 无法替代 InputMethodKit。付费 Developer ID 签名、公证能提供最佳下载体验，但用户明确没有证书，选择本机签名；它不是 Apple 公证分发，也不能据此宣称所有 macOS 版本已验收。

## Verification

设置前端类型检查及生产构建、安装脚本 bash 语法、macOS 打包资源契约与发布工作流 actionlint 通过。Mac 原生构建、DMG 签名与本机签名路径由本次 GitHub Actions 执行；普通用户真实 Mac 输入源注册仍未验收。Windows r6、Android r4 的 SHA-256 与已交付包一致。三端 Release 公开前核对工作流结果与安装包资产。

## Consequences

用户本机的钥匙串访问/信任操作可能要求系统确认；首次注册需要重新登录。自签名方案未经真实 Mac 安装验收时必须明确说明。Mac 没有移植 Android/Windows 局域网剪贴板与前缀补全，不应宣传三端功能完全相同。
