# Agent Note: zhuoyiwan 三端改版集中发布与 macOS 本机签名

Status: implemented

## Problem

Windows C++ 工程不能改配置后成为 macOS InputMethodKit 输入法。用户要求 GitHub Actions 编译 Mac，并将已有 Windows、Android 安装包与 Mac 包放在自己仓库的同一 Release；用户没有 Apple Developer ID 证书。

## Decision

Android 与 Mac 源码固定在用户自己的 msime fork 分支，Windows 工作流按完整提交 SHA 获取 Mac 源码并调用原生 package-release.sh。三端发布在 zhuoyiwan/msime-windows：先暂存已验收的 Windows r7 和 Android r4，Mac 构建成功、校验产物后再公开 Release。不推送上游、不更新上游 Homebrew tap，也不进入 Windows main 自动发布链。

Mac 包明确为无 Apple 公证的本机安装版。随 DMG 带安装脚本：在当前用户钥匙串建立仅用于本机输入法的自签名代码证书、用户信任、重签名自己的 App 副本并启动设置宿主。保留原生设置的词库准备与输入源安装流程；不关闭全局 Gatekeeper。真实 Mac 输入源注册验收与 CI 编译必须分别报告，不能把签名检查当成打字验收。

CI 用专用临时钥匙串、明确的 verify-only 模式和管理员信任设置验证同一脚本的证书生成与签名路径，避免普通用户信任弹窗在无人值守 runner 上无限等待。临时钥匙串加入当前用户的搜索列表并设为默认，保证 codesign 能找到证书对应的私钥；只传 --keychain 不足以覆盖私钥查找。此模式必须显式指定临时钥匙串及密码，不改变默认用户安装的确认流程。DMG 在验证之前先作为原始构建 artifact 保存，证书检查有步骤超时，失败时仍可检查原始产物。

签名检查失败后的恢复可复用固定 run 的原始 DMG，但只允许该二进制源提交到当前提交的差异落在本机安装脚本、安装说明与此笔记；其他源码改动必须重新编译。恢复把新脚本放进 DMG 并重算摘要，分别记录原生二进制源码提交与重新打包源码提交。CI 的 sudo 信任调用使用系统钥匙串，私钥保留在用户临时钥匙串，避免 root 无法打开 runner 的钥匙串会话。

## Related decisions

2026-10-09-macos-ci-and-release-on-xcode-26.md 部分重叠：复用 Xcode 26.3、universal 打包与固定资源校验。2026-10-09-macos-first-install-login-required.md 部分重叠：保留首次安装重新登录的限制。Android 9 的既有验收缺口不由本次发布绕过或改成通过。

## Alternatives considered

只调用上游 Mac 工作流能少改文件，但不能保存改版身份及统一三端资产。直接使用 Windows CMake 配置避免维护另一源码仓库，但 TSF/Win32 无法替代 InputMethodKit。付费 Developer ID 签名、公证能提供最佳下载体验，但用户明确没有证书，选择本机签名；它不是 Apple 公证分发，也不能据此宣称所有 macOS 版本已验收。

## Verification

设置前端类型检查及生产构建、安装脚本 bash 语法、macOS 打包资源契约与发布工作流 actionlint 通过。运行 38210362225 的原生 universal 构建与四项打包测试通过，但在导入证书后的用户信任调用停住，取消后日志仍有 security 进程。运行 38214607101 再次通过原生 universal 构建与四项测试并保存原始产物；恢复运行 38233672304 全部成功，证书生成、信任、内嵌输入法及外层签名严格验证、msime-mcp 0.52.0 启动和 IMK 注册查询入口检查通过。普通用户真实 Mac 输入源注册与中文候选仍未验收。Windows r7、Android r4 的 SHA-256 与已交付包一致。三端 Release 公开前核对工作流结果与安装包资产。

## Consequences

三端来源决定由[全量 Actions 构建](2026-10-13-fork-cloud-three-platform-release.md)接续，本篇保留旧包汇集与 Mac 本机签名的历史边界。

用户本机的钥匙串访问/信任操作可能要求系统确认；首次注册需要重新登录。自签名方案未经真实 Mac 安装验收时必须明确说明。Mac 没有移植 Android/Windows 局域网剪贴板与前缀补全，不应宣传三端功能完全相同。
