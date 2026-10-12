# Agent Note: 安卓与 Windows 改版局域网剪贴板互通

Status: implemented

## Problem

Windows 改版使用设备 ID、TLS 1.3 外部 PSK 和认证 UDP 发现。安卓现有云剪贴板经过账号服务，不能与该协议互通；默认 SSLSocket 也不支持该外部 PSK。

## Decision

原生安卓宿主提供独立局域网开关、设备列表与扫码配对页。网络适配使用 Bouncy Castle 轻量 TLS，限定 TLS 1.3、AES128 GCM、PSK+DHE；事件与发现字节兼容 Windows engine/contracts/clipboard_{sync,lan}。协议不复制输入引擎状态。导入交给 client-core 的移动剪贴板入口，系统剪贴板写入和前台服务由[本地改版系统剪贴板](2026-10-12-local-fork-system-clipboard.md)接续；事件不再次广播。

只在 :ime 进程运行一份网络适配，通过不导出的 ContentProvider 让设置页操作。密钥与事件去重元数据用 Android Keystore AES-GCM 加密落盘；身份绑定设备 ID，IP 仅作提示。分享只接收历史开关开启时的新采集，不补发旧历史；停止或禁用清空待发队列。

## Alternatives considered

默认 SSLSocket 能避免第三方 TLS 依赖且沿用系统维护，但不能实现 Windows 的 TLS 1.3 外部 PSK，故不采用。NDK OpenSSL 能直接复用 Windows 的 TLS 机制，但新增大型原生依赖和 ABI 构建维护；采用可独立 JVM 验证的轻量 TLS。早期只依赖输入法进程生命周期以避免常驻通知，该取舍现由接续笔记的前台服务决定替代。

## Testing

协议黄金向量、非法 UTF8、身份与摘要检查、发现 HMAC/nonce 重放检查通过。Java↔Windows TLS 测试覆盖双向收发与错误密钥。安卓编译/lint、模拟器启动、加密密钥落盘与配对重载通过，产出签名 ARM64 APK。系统粘贴与键盘体验的验证见接续笔记。

## Consequences

系统剪贴板与前台服务行为由[本地改版系统剪贴板](2026-10-12-local-fork-system-clipboard.md)重新决定；本文保留协议与 TLS 选型理由。

AP 隔离可能阻止广播，需要手动 IP 提示。扫码密钥属于敏感凭据，不写日志、不发送给云剪贴板。系统强停后需打开设置或键盘恢复同步；前台服务的生命周期边界由接续笔记规定。所有大体积下载、Cargo/Gradle 构建缓存写到 F 盘，工作树 target 通过 junction 保持约定的产物路径。
