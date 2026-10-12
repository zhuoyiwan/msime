# Agent Note: 本机改版从维护者 GitHub 更新

Status: implemented

## Problem

原生关于页强制按 Play 安装隐藏更新，并且原更新源会把本机改版升级回上游。用户要求从维护者 GitHub 发布在应用内更新。

## Decision

Android 检查 `zhuoyiwan/msime-windows` Releases，接受 full ARM64 同证书改版 APK。过滤草稿、错误架构、非改版 tag 或名称、非精确维护者下载 URL、异常大小及缺少 GitHub SHA-256 的资产。按基础版本和数字 r 修订比较。预览通道含本改版预发布；后台每日检查默认关闭，用户开启后通知，安装仍由 Android 系统确认。

Windows 同一发布源在其独立仓库实现，并保留页面检查及原生摘要下载边界。旧安装包未必提供 digest，缺摘要时不自动下载，仍可手工到发布页。

历史审计：[Rust 更新检查](../architecture/2026-10-09-update-check-in-rust.md)部分重叠，其上游共享平台查询继续保留，本机原生 Android 使用独立筛选政策；[三端集中发布](../process/2026-10-12-fork-three-platform-release.md)部分重叠，沿用发布仓与同证书安装，Mac 未在本轮接入应用内更新。

## Alternatives considered

继续上游源无需维护改版策略，但会丢失本机功能与账号裁剪。仅显示发布页链接能让用户下载，却没有版本检查及摘要验证。后台静默安装体验省步骤，但普通 Android 应用没有这项权限，保留系统安装确认。

## Consequences

维护者必须按固定平台名称发布并保留 GitHub 资产摘要，同基础版本修订也能识别更新。用户需要能访问 GitHub，下载镜像不再指向上游。r10/r9、草稿/预发布、外部 URL、当前版本和版本类型由定向政策测试覆盖。
