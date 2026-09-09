# 贡献指南

感谢关注 bigdata-adhoc！欢迎提交 Issue 与 Pull Request。

## 开发环境

- JDK 8 + Maven 3.6+
- `mvn clean test` 全绿是合入前提（连真实 MySQL/Kyuubi/StarRocks 的集成测试在对应
  `ADHOC_*` 环境变量未设置时自动跳过；设置后可本地复跑）
- IDE 建议 Lombok 插件 + annotation processing 开启

## 分支与提交

- 分支命名：`feature/xxx`、`fix/xxx`、`hotfix/xxx`
- 提交格式：`<type>: <subject>`，type 取值 `feat` / `fix` / `docs` / `style` / `refactor` / `test` / `chore`
- PR 标题：`[模块] 简要描述`（如 `[adhoc-executor] 修复 cancel 失效问题`）
- PR 描述包含：变更背景、变更内容、测试情况、影响范围

## 代码规范摘要

- 包名 `io.gitee.songchaolin.adhoc.*`，按模块分层（server/executor/common/dao/storage/sqlparser/protocol/observability/metadata/sdk）
- 命名约定：Service 不用 Impl 后缀；Runner 用动作 + Runner；执行器用 引擎名 + EngineExecutor；枚举不加 Enum 后缀
- 查询单个 `get/find`、列表 `list/select`、状态变更 `mark`、校验 `validate/check`
- MyBatis-Plus 一律 Lambda 查询（禁字符串列名）；复杂 SQL 写 XML mapper，用 `#{}` 防注入
- 业务异常统一 `AdhocException` + `AdhocErrorCode`（`defaultMessage` 为面向用户的中文提示），禁止裸 RuntimeException
- 日志使用 SLF4J，关键日志必须带 jobId/taskId 上下文；异常日志带堆栈参数

## Issue 规范

- Bug 报告请附：jobId / taskId、执行日志片段、引擎类型、复现 SQL（脱敏后）
- 新功能建议先开 Issue 讨论方案，达成一致后再提交 PR

## 许可

提交即代表你同意贡献内容按 [Apache-2.0](./LICENSE) 协议授权。
