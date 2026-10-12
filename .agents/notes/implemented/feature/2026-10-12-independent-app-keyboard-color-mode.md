# Agent Note: 软件界面与输入法分别设置深浅模式

Status: implemented

## Problem

Android 的应用主题分段直接写共享偏好 `theme`，软件界面和键盘同时切换。强制软件深色后，皮肤预览还把 activity 的深色当成设备实际模式，无法预览独立跟随系统的键盘。

## Decision

软件深浅模式使用 AndroidLocalSettings 的 `platform.android.app_color_mode`，可选 `system`、`light`、`dark`，默认独立跟随设备；只存本机，不进入共享 Rust 偏好或同步字段表。AppMode 在 activity 首帧前读取它，并继续保留季节和 Monet 叠加。原有键盘 `theme` 值保持原样，键盘页增加直接入口。

键盘皮肤也有本机 `platform.android.keyboard_color_mode`，选项为 system/light/dark，在皮肤页和键盘页共用同一入口；未设置时用 legacy 沿用既有 screen_keyboard_theme 与 theme 的规则。选择键盘深浅色不写全局 theme、不更改其他面板。输入法服务、输入法内皮肤选择器与软件预览使用相同的优先级。旧 Rust 偏好格式保持不变，预览使用的独立模式只进入临时 JSON，不落进共享配置。

键盘皮肤预览使用 Resources.getSystem 的当前夜间配置，再应用键盘自己的覆盖，避免 AppCompat 的软件主题混入输入法解析。软件和键盘的季节、皮肤及壁纸取色继续使用各自原有机制。

旧版软件没有独立配置，升级后软件默认跟随系统，原先设置的键盘模式继续保留。应用主题摘要同时显示深浅模式和配色。

历史笔记审计：[默认键盘跟随季节配色](2026-10-08-touch-keyboard-default-follows-app-theme.md)部分重叠，保留它的默认皮肤与强调色决定；本篇只拆开深浅模式，不改变默认季节配色。检索 AppMode、settings_theme 和颜色模式的其余记录只涉及 Windows 或鸿蒙，与本次 Android 配置无直接重叠。

## Alternatives considered

复用 Rust `settings_theme` 能沿用共享配置，但它只有 follow/light/dark，follow 仍跟随全局键盘模式；新增 system 会扩大到其他平台共享契约。当前需求是本机软件独立模式，因此选择 Android 本地设置，避免增加跨平台枚举与迁移成本。

## Consequences

软件可深色、键盘可浅色，也可任一界面独立跟随设备。新增本地配置参与原有备份，但不跨设备同步。升级后软件深浅默认回到设备选择，这是拆开原先绑定时的明确行为。

API 28 与 API 35 专用模拟器上真实软件 activity 的九组软件/键盘模式组合、activity 重启、设备模式来源不受软件覆盖均通过；API 35 在设备深浅模式下分别重复验收。实际 :ime 窗口的字母键截图验证深色/浅色确实显示，键盘独立模式覆盖了共享浅色默认。AndroidLocalSettingsSmoke、KeyboardSkinSmoke、AppThemePaletteSmoke、LAN 协议拒绝用例通过。设置搜索索引新增两处模式行，避免搜索不到入口。

全量 Windows JVM 辅助验证为 204/215 通过：本次搜索索引遗漏由新增关键词修复；其余失败涉及 Windows 文件系统安全判断、android.jar 桩及已移除账号。原仓库 quick 脚本还受 Git Bash 路径转换、Python 默认编码与未配置跨平台工具链限制，未标为通过。正式原生 Gradle 构建与 release lint、两代安卓系统上的定向设备验收用于验证本次行为。
