# zhuoyiwan 改版的 Mac 本机安装

本包使用原生 InputMethodKit 工程，支持 macOS 13 及以上，包含 Apple 芯片和 Intel 两种架构。没有 Apple Developer ID 签名或公证；随包的脚本在本机创建自签名代码证书，供当前用户使用。系统可能要求确认钥匙串访问与证书信任。脚本不关闭 Gatekeeper，仅清除本输入法副本的隔离属性。

1. 先用 Command+Q 退出正在运行的旧版水杉设置应用，再双击 DMG 挂载。
2. 打开「终端」，输入 `bash `（最后留一个空格），把 DMG 内的「安装 zhuoyiwan 版.command」拖进终端，再按回车。
3. 按系统提示允许本机证书与钥匙串操作。脚本把应用安装到 `~/Applications/MSIME-zhuoyiwan.app`，然后打开设置窗口。
4. 点「安装 / 更新」（首次可能显示「立即安装」），等待核心词库准备，在「系统设置 → 键盘 → 文字输入 → 输入源」添加水杉输入法。
5. 首次安装若找不到输入源，请退出登录并重新登录，再添加输入源。

不要只把 DMG 里的原始 App 拖入应用程序。原始 App 是 CI 的 ad-hoc 签名副本，本机安装脚本会重新签名后再启动。更新时仍从新 DMG 运行脚本，已存在且有效的本机证书会复用。卸载可用设置应用的卸载功能；本机证书可在「钥匙串访问」中按 `MSIME zhuoyiwan Local Code Signing` 查找并手动移除。

此方式的签名检查与构建在 CI 执行；普通用户真实 Mac 上的输入源注册、重新登录后候选显示仍需验收，不能等同于 Apple 公证安装。Mac 尚未移植本改版 Android/Windows 的局域网剪贴板、去账号模式和手机号等前缀补全，当前保留原有 Mac 功能。

安装包、后续改版与三端下载：[zhuoyiwan/msime-windows Releases](https://github.com/zhuoyiwan/msime-windows/releases)。源码保留原作者版权和 GPL-3.0 许可。
