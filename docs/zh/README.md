# bigdata-adhoc · 文档索引

## 用户手册

| 文档 | 说明 |
|---|---|
| [接口文档](api/README.md) | REST 接口总览：统一响应、鉴权、错误码、分页约定；分篇详述 Job/结果/日志/文件/元数据/大盘接口 |
| [部署指南](deployment.md) | 生产部署：多节点拓扑、Apollo 接入、nginx 网关与用户头注入、存储选型 |
| [配置参考](configuration.md) | 配置优先级与生效机制、常用配置项速查（完整清单见 [apollo-prod-config.md](apollo-prod-config.md)） |
| [SDK 指南](sdk.md) | Java SDK：REST/gRPC 双协议客户端、方法说明、完整示例 |
| [FAQ 与术语](faq.md) | 常见问题解答、术语表 |

接口文档分篇：

| 文档 | 内容 |
|---|---|
| [api/job.md](api/job.md) | Job 提交 / 分页 / 详情 / 状态 / 进度 / 取消、Task 详情 |
| [api/result.md](api/result.md) | Task 结果分页、Job 结果聚合 |
| [api/log.md](api/log.md) | Task / Job 执行日志（增量轮询） |
| [api/file.md](api/file.md) | 文件节点（SQL 脚本目录树） |
| [api/metadata.md](api/metadata.md) | 元数据自动补全、运行时配置查询 |
| [api/dashboard.md](api/dashboard.md) | 监控大盘与 SQL 控制台接口 |

## 设计文档（docs/design/）

面向贡献者与二次开发，阐述模块内部实现机制。

| 文档 | 说明 |
|---|---|
| [00-architecture-overview.md](design/00-architecture-overview.md) | 总体架构：模块划分、部署拓扑、设计原则 |
| [01-task-scheduling-execution.md](design/01-task-scheduling-execution.md) | 调度与执行：QueueWorker、JobExecutionRunner、Task 生命周期 |
| [02-sql-parsing-governance.md](design/02-sql-parsing-governance.md) | SQL 解析与治理：g4 解析、SqlType 分类、治理规则 |
| [03-engine-routing.md](design/03-engine-routing.md) | 引擎路由：Kyuubi/StarRocks 选择、多实例、代理用户 |
| [04-result-storage.md](design/04-result-storage.md) | 结果存储：StorageClient SPI、local/aliyun 实现、序列化格式 |
| [05-high-availability.md](design/05-high-availability.md) | 高可用：server/executor 宕机补偿、reconcile 机制 |
| [06-observability.md](design/06-observability.md) | 可观测性：指标、追踪、日志规范 |
| [08-directory-tree.md](design/08-directory-tree.md) | 目录结构：完整项目目录树 |
| [09-execution-log.md](design/09-execution-log.md) | 执行日志：日志格式、收集机制、COMPLETE_MARKER |
| [10-limitations.md](design/10-limitations.md) | 限制与约束：已知限制、后续规划 |
| [11-schema.md](design/11-schema.md) | 数据库 Schema：完整 DDL、索引说明 |
| [12-sdk.md](design/12-sdk.md) | SDK 设计：Java SDK 接口、gRPC/REST 协议 |

## 其他参考

| 路径 | 内容 |
|---|---|
| [apollo-prod-config.md](apollo-prod-config.md) | Apollo 生产配置完整清单（机制、必配项、约束） |
| [技术方案设计/最终版/即席查询项目架构介绍.md](技术方案设计/最终版/即席查询项目架构介绍.md) | 早期技术方案介绍（含架构图与部署拓扑图） |
| [../sql/create.sql](../sql/create.sql) | 建表 DDL（幂等，含索引与注释） |
| [../CONTRIBUTING.md](../CONTRIBUTING.md) | 贡献指南 |

## 阅读建议

- **使用方 / 接入开发**：[快速开始](../README.md) → [接口文档](api/README.md) → [SDK 指南](sdk.md)
- **运维部署**：[部署指南](deployment.md) → [配置参考](configuration.md) → [apollo-prod-config.md](apollo-prod-config.md)
- **源码贡献 / 二次开发**：[design/00 总体架构](design/00-architecture-overview.md) → [01 调度执行](design/01-task-scheduling-execution.md) → [04 结果存储](design/04-result-storage.md)
