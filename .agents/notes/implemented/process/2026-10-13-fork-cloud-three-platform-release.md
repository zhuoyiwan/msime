# Agent Note: 三端改版全部从固定源码在 Actions 构建

Status: implemented

## Problem

旧集中发布只在云端构建 Mac，Windows 与 Android 为预上传的本地包，不能满足用户明确要求三端 Actions 打包、上传和发布。

## Decision

Windows fork 的 release-three-platforms.yml 完整构建 x64/x86 TSF、Server、页面、full ARM64 Android 和 universal 原生 Mac。输入固定客户端源码 SHA，草稿 tag 固定 Windows 工作流 SHA。Android 使用既有正式证书，签名凭据只存在于 Windows 用户 fork encrypted secrets，产物核对证书指纹、versionCode 和 ABI；不推上游、不合 main、不更新上游分发渠道。

Android 签名脚本允许显式证书别名，改版指定 `zhuoyiwan-android`，未指定仍用原 `msime-release`；不能把自己的 p12 误按上游别名打开。lint 调用仓库已有的 Gradle wrapper 并指定原生宿主工程；发布 full 包取固定语言词库与离线释义，并要求已发布的语言资源全部存在。

发布依赖 API 28/35 设备门禁，含 client-core 测试、原生装包输入、本轮长句与多错第一页选择、真实软件主题与词库加载状态、合成系统语音 final 提交。API 28 候选浮层无法从无障碍树读取时，用真实默认页 1–6 数字选词并检查编辑器中的整句；不将前缀上屏当作通过。模拟服务在旧 API 由应用直接绑定，现代系统由系统服务绑定，两种测试组件分别设置权限。

标准偏好验收从各数字预设共用的 `#+=` 标点行检查半角、全角与错误恢复；邮箱验收从完整符号面板的英文分类输入 `@`。电话式数字页不包含旧数字行的逗号和 `@`，测试不强制修改默认布局，也不因键位改变跳过既有断言。

图片传输套件保存本地设置、使用标准键盘高度并在结束后原样恢复；增高 109 的旧测试设置会在 1080×1920 小屏遮住合成复制按钮。固定这项前置条件后仍驱动真实按钮、剪贴板 URI、权限与 commitContent 字节校验，不修改用户默认值。键盘增高与旋转由独立高度套件验收。

三端记录固定 source_commit 和同一次 workflow_run，打包后再核对每项 GitHub digest；全部满足才将草稿公开为 prerelease。失败恢复只接受字节完全相同的同名草稿资产，不覆盖公开资产。Mac 完整重建，保留本机自签名脚本与普通用户登录会话尚未验收的说明。

草稿资产校验先解析 Release ID，再调用 `/releases/{id}`。已存在并有权限读取的草稿在 `/releases/tags/{tag}` 仍返回 404；保留按 tag 定位的 CLI，但不把这个 REST 接口当作草稿查询入口。

历史审计：[旧集中发布](2026-10-12-fork-three-platform-release.md)保留历史本地包汇集及 Mac 签名决定，本篇替代预上传双端包的发布来源决定；[应用内更新](../feature/2026-10-13-local-fork-github-updates.md)部分重叠，复用固定命名和同证书升级。

## Alternatives considered

沿用预上传本地包最省云资源，但不能满足三端云构建来源。直接使用上游发版工作流能复用发布逻辑，却会改上游版本名或发布仓。复用旧 Mac 二进制能缩短等待，但本轮引擎改动要求新源完整构建，不能以脚本-only 恢复机制伪称新源码产物。

## Consequences

构建时间增加，任一平台或设备门禁失败都会保留草稿并阻止发布。Actions 状态和摘要核对才构成云端交付事实，本地编译不是发布完成。工作流 actionlint、来源脚本的来源/摘要/草稿恢复拒绝测试在 Windows 仓通过；实际运行与最终下载链接记录在 GitHub Release。

本机 Windows quick 已运行但失败，原 HEAD 对照证实账号命名、主题共享标题、同步信号路由、剪贴板大小文本检查和语音默认配置检查已有失败，另外存在 fcntl、Unix 路径和 pipe-only 工具链限制；没有新增 known-failures 豁免。语音默认检查随后与 Android 改版实际配置对齐，设备 client-core 仍完整执行。示例资源视图在 Windows 用文件复制替代无条件 Unix symlink；input-runtime 的 JSON 测试显式导入宏，仅 Unix socket 用例按平台编译。引擎 all-targets 与 runtime lib/tests 编译通过。全量引擎本机曾有 1785 通过、16 失败、14 ignored，剩余失败都在原 HEAD 基线复现；Actions Linux 引擎 1830 通过、0 失败、14 ignored，Clippy 通过。
