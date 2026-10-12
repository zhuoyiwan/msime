# Agent Note: 改版默认表情候选与本机壁纸配色

Status: implemented

## Problem

用户需要直接输入 si'kao 得到 🤔，并可选择随手机壁纸变化的主题。现有混输默认关闭，目录编码没有音节分隔符；现有应用主题只支持四季色。

## Decision

保留词后锚定规则，查询时去掉拼音分隔符，候选原始输入不变。安卓默认开启 Emoji；既有偏好用本机一次性迁移开启，之后尊重用户关闭。

新增本地 `platform.android.wallpaper_colors` 开关，不扩大共享的 APP_THEME 枚举。Android 12+ 使用系统 Monet 色板；旧系统用 WallpaperColors 主色和 Material 色彩角色推导明暗色阶。主色不可读取时使用稳定回退，不索取照片或存储权限。动态种子由宿主和跟随系统的键盘共用，用户选定的自定义键盘保留。选择手动季节主题会关闭取色。前台应用通过壁纸监听刷新，键盘在新会话或最长一分钟的主题检查时刷新。

一次性启用标记使用跨进程文件设置 `platform.android.fork_emoji_migrated`，键盘建会话前也通过 HostStore 读取和迁移，用户不打开设置页仍能获得新版默认。查询先保留原编码，查不到才去掉分隔符回退，候选读音保留原始输入，使分隔全拼和双拼都完整消费。

## Alternatives considered

直接修改词库能统一全平台，但词库是锁定发布产物，不能为分隔符兼容修改数据。只改默认值简单，但已有文档保存了 false，无法满足升级使用。每次启动强制打开会覆盖用户关闭。

读取壁纸图片可自行采样，但新系统限制访问，系统色板能直接使用 Monet 并避免图片权限。保持四季主题兼容旧产品，但不能实现壁纸跟随，因此作为手动选项保留。

## Consequences

旧系统或厂商禁止 WallpaperColors 时只能回退；没有可读取的静态壁纸时 Windows 同样使用回退。壁纸只在本机解析，不参与剪贴板或账号同步。

API 28 的 UiAutomation 会保留输入前的快捷栏缓存，动态候选虽然实际绘制，却无法通过测试无障碍树定位。专用模拟器使用固定默认字号，表情测试按字母行的位置触摸第二候选，并严格断言编辑器收到 🤔；字母使用真实 KeyEvent，编辑按键按键面位置触摸。API 35 仍使用实际按钮的无障碍点击。设置壁纸后立即退出的夹具不会绘制首帧，不能用 `am start -W` 等它，否则 API 28 会一直等待。测试 APK 才申请设置合成壁纸权限，产品不申请。

## Testing

Rust 表情查询 14 项与候选查询 17 项通过，Rustfmt 通过。Android 双 ABI 构建、最终 ARM64 构建与 lint 通过；签名与 16 KB 对齐核验通过。API 28 和 35 均通过紧凑和分隔拼音表情上屏、完整消费组合、壁纸明暗配色和轮换刷新；关闭开关在 API 28 测实时加载，在 API 35 测重启持久化。API 28 另有基础中文上屏与删除验收通过。最终设备记录为 `android-emoji-device28-release.log` 和 `android-emoji-device35-release.log`。

Windows 服务端 675 项测试及 3 项 ctest 通过，前端 144 项测试、类型检查和生产构建通过。Windows 本地主题采用壁纸图片缩图量化和柔和色阶，与 Android 系统 Monet 算法不同。

Windows 宿主运行 Rust 全量库与集成测试得到 1779 通过、18 失败、13 忽略；包含 Unix 路径预期、Windows 映射文件限制、分配计数与负载时序失败，未宣称全量绿。默认 cargo test 还受 Unix-only 示例影响；本次针对功能的测试通过，不向既有失败基线新增豁免。

## Related notes

[目录开关](../../implemented/feature/2026-10-08-android-symbol-catalog-and-emoji-candidates.md)与默认值决定部分重叠，此改版改变本机默认，不改变符号分类。 [词后锚定](../../implemented/feature/2026-10-08-expressive-rows-follow-their-word.md)与[默认皮肤](../../implemented/feature/2026-10-08-touch-keyboard-default-follows-app-theme.md)保留。已按 wallpaper、Monet、AppThemeController、混输检索三棵活跃笔记树，其余命中为无关的测试或其他平台行为，无过时提案需要处理。
