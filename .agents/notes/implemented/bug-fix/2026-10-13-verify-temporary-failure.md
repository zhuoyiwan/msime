# Agent Note: 验证脚本临时文件失败立即退出

Status: implemented

## Problem

Windows quick 验证的 TMPDIR 不存在时，mktemp 返回失败但脚本继续执行，空的 collected 使清理通配符变为工作树根目录的点文件，删除 Git 指针等文件。

## Decision

两个初始 mktemp 调用失败立即退出；第二次失败只删除第一次已成功创建的临时文件。安装清理 trap 前保证 collected 非空。隔离 fixture 用不可用临时目录验证非零退出和点文件完整保留。

## Alternatives considered

仅检查 cleanup 的空变量可避免这次误删，但其后验证仍会用不存在的日志文件运行而产生误判。创建失败立即停止，避免把环境失败当成测试结果。

## Consequences

临时目录缺失会清楚中止验证。此次已从 Git 元数据恢复工作树指针，并恢复被删除的已跟踪点文件；源码改动未丢失。quick 的其他 Windows 工具链限制仍需单独报告，不能声称全绿。
