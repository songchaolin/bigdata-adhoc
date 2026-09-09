# 模块 10：限制与演进

> 本模块汇总当前版本的限制项、预留 SPI 扩展口子、明确不演进项，为后续版本演进提供基线。

## 1. 概述

### 1.1 文档职责

- **当前限制**：明确版本不实现或简化的功能，及后续演进方向
- **预留 SPI**：预留的扩展口子，后续可替换实现
- **不演进项**：明确不会做的功能，避免无效需求

### 1.2 设计原则

- **聚焦核心链路**：提交 -> 前置校验 -> 调度 -> executor 拆分执行 -> 结果存储 -> 日志
- **SPI 扩展优先**：引擎日志、资源采集、存储后端等通过 SPI 预留口子，内置默认实现
- **不做过度设计**：即席查询场景，不引入复杂共识算法、不建事件流水表、不做租户隔离、不做平台层鉴权（依赖引擎侧权限）

## 2. 当前版本限制

### 2.1 接入与认证

| 限制项 | 当前现状 | 后续演进 |
|---|---|---|
| 用户认证 | 信任请求头 userId/userName，不做认证 | 后续可接网关 SSO / API Key，server 校验 token |
| 多租户 | 无租户概念，所有指标无 tenant label | 不演进（即席场景不需要租户隔离） |
| 前端 | 只提供 REST API，不含 Web 控制台 | Web 控制台由独立前端项目承接 |
| 客户端 SDK | 提供 Java SDK（gRPC + REST） | 后续可补 Python SDK |

### 2.2 引擎与 SQL

| 限制项 | 当前现状 | 后续演进 |
|---|---|---|
| 执行引擎 | KYUUBI / STARROCKS 两引擎闭环 | 新引擎按需评估接入（SPI 已预留） |
| engine_type | 必须显式指定，未指定拒绝（ADHOC_ENGINE_TYPE_REQUIRED），非法拒绝（ADHOC_ENGINE_TYPE_INVALID） | 不演进（用户必须显式指定，避免平台层猜错） |
| HIVE 引擎 | 不支持 | 不演进（Hive 场景由 Kyuubi + Spark 承接） |
| SQL 类型 | 10 类 SqlType（DQL/DDL_CREATE/DDL_ALTER/DDL_DROP/DML_INSERT/DML_MODIFY/CTAS/AUX/DCL/UNKNOWN）+ SESSION_CONFIG（识别 SET/USE） | 不演进（覆盖即席场景） |
| DCL | 黑名单拦截，禁止执行 | 不演进 |
| STARROCKS 语句类型 | 仅支持 DQL/CTAS/SESSION_CONFIG/AUX，DDL/DML/DCL/UNKNOWN 在 server 前置校验拦截（ADHOC_ENGINE_SQL_TYPE_NOT_SUPPORTED） | 不演进（STARROCKS 定位 OLAP 查询，修改操作走专用数仓通道） |
| 多语句 | executor 按 `;` 拆分，SET/USE 作为 prefix_sql 合并到下一个 Task，Job 内 Task 串行执行 | 不演进（即席场景不需要并行） |
| UDF 支持 | 按 ANTLR4 函数名识别，未接 UDF 注册中心 | 后续可接 UDF 元数据 |
| 血缘粒度 | 只做表级（source_tables + sink_tables），存 table_ref 表，与 Task 1:1 绑定 | 后续可扩展字段级血缘（column_lineage_json） |
| 估算扫描量 | `estimated_scan_bytes` 缺省 -1，不触发大表扫描评分 | 后续可接 Hive Metastore 统计信息 |
| g4 解析 | 2 套 g4（Spark SQL for KYUUBI / StarRocks for STARROCKS），覆盖 2 引擎 | 后续按需扩展新引擎 g4 |

### 2.3 Session 与 Job 拆分

| 限制项 | 当前现状 | 后续演进 |
|---|---|---|
| Session 绑定 | Job 内所有 Task 必须在同一 session（executor 内存维护的 JDBC connection），session 绑定 executor 不绑定 server | 不演进（session 模式核心约束） |
| SET/USE 处理 | SET/USE 不独立成 Task，作为 prefix_sql 合并到下一个真正执行的 Task，Job 内累积传递 | 不演进 |
| 全 SET/USE Job | Job 全是 SET/USE 无可执行 SQL 拒绝（ADHOC_JOB_NO_EXECUTABLE_SQL），executor 拆分时发现标记 Job FAILED | 不演进 |
| 末尾 SET/USE | 最后一段是 SET/USE（无后续查询）：该 SET/USE 丢弃（无意义） | 不演进 |
| Task 失败传播 | 前一 Task 失败跳过后续 Task（fail_reason_category = SKIPPED_DUE_TO_PRIOR_FAILURE），避免 session 状态异常 | 不演进 |
| engineParams | 在 connection 创建时作为初始 params 设置，SET 语句在 session 里执行可覆盖/追加，SET 优先级更高 | 不演进 |
| Session 生命周期 | executor 接收 dispatchJob 创建 connection，Job 终态后清理 `Map<jobId, Connection>` | 不演进 |
| Job 拆分位置 | executor 端拆分（不是 server 端） | 不演进 |
| Task 状态 REJECTED | 不产生（权限拒绝由引擎层处理，统一作为 ENGINE_ERROR），枚举保留备用 | 不演进 |

### 2.4 调度与并发

| 限制项 | 当前现状 | 后续演进 |
|---|---|---|
| 调度策略 | 纯 FIFO（enqueue_time ASC），无优先级 | 后续可支持用户优先级，避免大用户饿死 |
| Job 内 Task 执行 | 串行执行（一个 Task 完成后才执行下一个），由 executor 在 session 里顺序执行 | 不演进（即席场景 Task 间通常有依赖） |
| 跨集群 | 单集群，每个 engine_type 多 instance | 后续可支持多集群，按 engineInstance 路由 |
| 实时查询 | 仅批量即席 | 不演进（实时走 OLAP 引擎自有入口） |
| 跨引擎联邦 | 不支持 | 不演进（用 Trino/Presto 自有联邦能力） |
| 调度到 executor | 按 load_score 升序选最低分 executor（load_score = cpu*0.3 + mem*0.3 + disk*0.2 + running_tasks_ratio*0.2） | 不演进 |

### 2.5 前置校验与限流

server 在创建 Job 记录前执行前置校验，校验失败直接返回错误给前端，不创建 Job 记录、不创建日志文件。

| 限制项 | 当前现状 | 后续演进 |
|---|---|---|
| SQL 语法校验 | g4 解析整个 sql_content，不通过直接报错 | 不演进 |
| engine_type 合法性 | 必须在 (KYUUBI, STARROCKS) 内，否则拒绝（ADHOC_ENGINE_TYPE_INVALID）；未指定拒绝（ADHOC_ENGINE_TYPE_REQUIRED） | 不演进 |
| engine_params 合法性 | JSON 格式校验 | 不演进 |
| 限流 | 全局 + per-user，PENDING + RUNNING 双维度 | 不演进 |
| 权限校验 | 不做（依赖引擎侧权限） | 不演进 |

**限流参数**：

| 参数 | 默认值 | 说明 |
|---|---|---|
| max_tasks_per_job | 20 | 单 Job 最大段数（含 SET/USE，按 `;` 计数，超限返回 ADHOC_JOB_TOO_MANY_TASKS） |
| max_pending_jobs_global | 100 | 全局排队中 Job 上限（超限返回 ADHOC_JOB_LIMIT_EXCEEDED） |
| max_pending_jobs_per_user | 5 | per-user 排队中 Job 上限 |
| max_running_jobs_global | 50 | 全局运行中 Job 上限 |
| max_running_jobs_per_user | 3 | per-user 运行中 Job 上限 |

### 2.6 结果与存储

| 限制项 | 当前现状 | 后续演进 |
|---|---|---|
| 结果大小 | LIMIT 1000000 强制约束，所有查询请求强制追加 `LIMIT 1000000`，单结果最大 100w 行（约 200MB-500MB） | 不演进（硬上限） |
| 结果存储位置 | executor 序列化后一次性 `storageClient.uploadResult` 上传存储后端，不写本地文件 | 不演进 |
| 持久化后端 | StorageClient SPI，内置 local（本地文件系统）/ aliyun（阿里云 OSS）两种实现，第三方可注册 Bean 覆盖 | 不演进 |
| storage_type | 2 态（NONE/PERSISTENT），结果写完存储即 PERSISTENT（无 LOCAL 态） | 不演进 |
| 持久化文件 TTL | 30 天（local 自扫描 / aliyun 桶生命周期规则，表行 + 文件） | 不演进 |
| 持久化上传方式 | 一次性上传（uploadResult），不分段；上传成功更新 storage_type=PERSISTENT、oss_upload_status=SUCCESS | 不演进 |
| 持久化上传失败 | oss_upload_status=FAILED，重试（指数退避）；无本地兜底，重试耗尽仍未成功则结果不可读（Task FAILED，fail_stage=OSS_UPLOAD） | 不演进 |
| 持久化上传重试 | 指数退避（1s, 2s, 4s, 8s, 16s, 30s，最多 6 次，总等待 ~60s） | 不演进 |
| 文件格式 | MAGIC(4B) + schema JSON 行 + 数据行（长度前缀），格式不变 | 不演进 |
| schema 存储 | 存文件头（不建 adhoc_result_schema 表）+ 内存 | 不演进 |
| 文件元信息 | 存 adhoc_result_summary 表（一个 Task 一行 summary，不建 adhoc_result_file 表） | 不演进 |
| 结果复用 | sql_hash + 复用前提：原 Task storage_type=PERSISTENT，复用结果直接读存储 | 后续可支持手动刷新（强制重跑） |
| 内存结果缓存 | 不做（结果在 executor upload 存储 + server 直读存储） | 不演进 |
| 导出 | 不支持 Excel/CSV 导出 | 后续可支持 server 端流式导出 |

### 2.7 高可用与运维

| 限制项 | 当前现状 | 后续演进 |
|---|---|---|
| 集群拓扑 | 多 server 对等 + 多 executor 对等，无 leader 选举 | 不演进（不引入 raft/leader） |
| executor 心跳 | 5s 心跳，30s 超时判定 DOWN（容忍长 GC 和网络抖动，减少假 DOWN） | 不演进 |
| server 心跳 | 5s 心跳，15s 超时判定 DOWN | 不演进 |
| 健康检查 | 全量扫描实例表，不分片 | 不演进（实例数 < 1000，全量扫够用） |
| 主动探活 | 不做，依赖心跳超时 | 不演进 |
| server 宕机补偿 | PENDING Job 由其他 server 抢占接管；RUNNING Job 不受影响（session 在 executor，executor 直写 DB），processing_server_instance 置 NULL | 不演进（关键：server 宕机不影响 RUNNING Job） |
| executor 宕机补偿 | DISPATCHED/RUNNING Task 标记 FAILED（EXECUTOR_CRASHED）；PENDING Task 标记 FAILED（SKIPPED_DUE_TO_SESSION_LOSS） | 不演进（不自动重试，避免 session 状态异常） |
| executor 假 DOWN | executor 心跳恢复后 UPDATE 为 UP，不恢复已 FAILED 的 Task（避免状态回退）；不本地存储，无需补传本地文件 | 不演进 |
| executor 重启 | 不恢复已 FAILED 的 Task；不本地存储（无本地文件扫描/补传）；提交本地 WAL；重新加入调度池 | 不演进 |
| executor 写库失败 | L1 直写 -> L2 重试 -> L3 接口转发 server -> L4 本地 WAL + 心跳对账兜底 | 不演进 |
| 心跳对账 | executor 心跳带 running_tasks 列表，server 对账 DB 的 RUNNING Task，状态不一致校正，缺失的 Task 标记 TASK_LOST | 不演进 |
| 历史实例归档 | 不建 history 表，DOWN 24h 直接物理删除 | 不演进 |
| 网络分区 | 依赖心跳超时判定 | 后续可加 gRPC HealthCheck 二次探活 |
| 配置变更 | engineInstance/endpoint 通过 Apollo 推送 | 已支持动态刷新 |
| 优雅下线 | drain（accepting=0）+ 等活跃 Task 完成 | 不演进 |

### 2.8 执行日志

| 限制项 | 当前现状 | 后续演进 |
|---|---|---|
| 日志产生方 | 全部在 executor（server 端不产生日志文件） | 不演进 |
| Task 日志存储 | executor 内存 buffer（`List<String>` per Task，按行）+ `ReentrantLock`；gRPC FetchLog 读内存（实时）；定期（2s / 100 行）storageClient.uploadLog 覆盖快照存存储后端（路径存 task.persistent_log_path）；Task 终态最后 flush 一次完整日志 | 不演进 |
| Job 日志存储 | Job 级事件少，终态一次性 storageClient.uploadLog 到存储后端（路径存 job 表），无需定期 flush | 不演进 |
| 日志内容 | job.log：session 创建、Job 拆分、Task 调度顺序、Task 状态变更、session 关闭；task.log：引擎连接、SQL 执行、结果拉取、引擎报错 | 不演进 |
| 日志格式 | 纯文本，每行一条（时间戳 + 级别 + 线程 + 内容） | 不演进 |
| 实时读取 | gRPC FetchLog 读 executor 内存 buffer（从 offset 行起），实时性 ~100ms；executor DOWN 或 Task 终态时 server 读存储快照（丢最后 ≤2s） | 不演进 |
| 日志存储快照 | 定期全量覆盖上传（每 2s 或 100 行阈值），覆盖同名 fileName；异步 flush 线程独立于执行线程 | 不演进 |
| 日志查询 | Task 运行中 + executor UP：gRPC FetchLog 读内存（实时）；executor DOWN 或 Task 终态：storageClient.download 读存储快照；存储未上传（executor 宕机且未 flush）返回 ADHOC_LOG_INCOMPLETE | 不演进 |
| 持久化日志 TTL | 30 天（local 自扫描 / aliyun 桶生命周期规则，与结果文件同周期清理） | 不演进 |
| JDBC 日志采集 | QueryLogIterator SPI，Kyuubi 走 getOperationLog，StarRocks 走 getWarnings，采集的日志写内存 buffer | 后续可补 StarRocks profile 解析 |
| 引擎日志聚合 | 不从 YARN / Spark History Server 聚合 | 不演进（只走 JDBC） |
| 内存缓存 | 不做（日志在 executor 内存 buffer + 存储快照） | 不演进 |
| 事件流水表 | 不建 adhoc_query_event 表（用执行日志替代） | 不演进 |
| 资源统计表 | 不建 adhoc_query_resource 表（scan_rows/scan_bytes 在 task 表） | 不演进 |

### 2.9 目录树

| 限制项 | 当前现状 | 后续演进 |
|---|---|---|
| 目录树模型 | 每用户独立树，user_id 隔离 | 不演进 |
| 节点类型 | DIRECTORY / FILE 两类 | 不演进 |
| 文件与引擎 | FILE 不绑定引擎，引擎参数在提交查询时指定 | 不演进 |
| 软删除 | is_deleted=1，支持恢复 | 不演进 |
| 永久删除 | 不提供 API | 后续可加回收站清空 API |
| 搜索 | 按名称 LIKE 搜索 | 后续可接 Elasticsearch 做全文检索（sql_content 内搜索） |
| 单用户节点上限 | 10000 | 不演进 |
| 单文件 sql_content | 最大 1MB | 不演进 |
| 目录深度 | 不限制（防环靠移动校验） | 不演进 |
| 节点级权限 | 不做（user_id 隔离已足够） | 不演进 |

### 2.10 可观测性

| 限制项 | 当前现状 | 后续演进 |
|---|---|---|
| 阶段耗时 | task 表时间戳，分段耗时公式 | 不演进 |
| fail_stage | 失败阶段标记 | 不演进 |
| fail_reason_category | 粗分类（executor 回写），含 ENGINE_ERROR / EXECUTOR_CRASHED / SKIPPED_DUE_TO_PRIOR_FAILURE / SKIPPED_DUE_TO_SESSION_LOSS / TASK_LOST 等 | 不演进 |
| error_code | ADHOC_* 前缀细分类 | 不演进 |
| Prometheus 指标 | server/executor 两套指标集 | 不演进 |
| label 约束 | 禁止 jobId/taskId/SQL 原文做 label | 不演进 |
| 集群健康看板 | REST API 暴露实例状态 + drain/restore | 不演进 |
| Grafana 大盘 | 后续建设 | 后续补齐 |
| 告警规则 | Prometheus 告警规则 | 后续可补 Grafana 告警 |
| EngineResourceCollector | SPI，Kyuubi 实现，StarRocks 不采集 | 后续可补 StarRocks profile 解析 |
| 存储上传成功率 | 监控 oss_upload_status=FAILED 的 Task，告警 | 不演进 |
| executor 磁盘水位 | 心跳上报 disk_usage，作为 load_score 打分依据（不硬性拒绝） | 不演进 |

### 2.11 executor 执行模型

| 限制项 | 当前现状 | 后续演进 |
|---|---|---|
| 磁盘水位 | 作为 load_score 打分依据，不硬性拒绝（水位高打分高，少分配 Task） | 不演进 |
| executor 心跳超时 | 30s 标记 DOWN | 不演进 |
| session 存储 | executor 内存 `Map<jobId, JDBC.Connection>`，Job 终态后清理 | 不演进 |
| WAL | 写库失败 L4 兜底，存 `wal/{taskId}.json`，单条约 200 字节，后台线程每 30s 重试 | 不演进 |

## 3. 预留 SPI 与扩展口子

| 扩展点 | 所在模块 | 用途 | 内置实现 |
|---|---|---|---|
| `QueryLogIterator` | 模块 9 | JDBC 引擎日志采集 | KyuubiQueryLogIterator / StarRocksQueryLogIterator |
| `EngineResourceCollector` | 模块 6 | 引擎资源采集（scan_rows/scan_bytes） | KyuubiResourceCollector（StarRocks 不采集） |
| `EngineExecutor` | 模块 1 | 引擎执行器（JDBC 连接 + 执行 + 拉取） | KyuubiEngineExecutor / StarRocksEngineExecutor |
| `LineageExtractor` | 模块 2 | 血缘提取（表级 + 字段级） | g4 解析实现 |
| `SqlParserEngine` | 模块 2 | SQL 解析（多引擎 g4） | KyuubiParser（Spark SQL g4） / StarRocksParser |
| `EngineEndpointSelector` | 模块 3 | endpoint 负载选择策略 | LoadBasedEndpointSelector |
| `SessionParamSetter` | 模块 1 | session 参数设置，按引擎适配 | DefaultSessionParamSetter |
| `StorageClient` | 模块 4 | 存储后端（结果/日志上传下载） | LocalStorageClient / AliyunStorageClient |

## 4. 不演进项（明确不做）

### 4.1 不做 SQL 编辑器前端

平台只提供 REST API，前端由独立项目承接。

### 4.2 不做跨集群联邦查询

用 Presto/Trino 自有联邦能力。

### 4.3 不做实时流式查询

即席场景是批量，实时走 OLAP 引擎自有入口。

### 4.4 不做 UDF 注册中心

按 ANTLR4 函数名识别，不接 UDF 元数据。

### 4.5 不做脱敏/加密

无敏感字段标记需求，不做。

### 4.6 不做 raft / leader 选举

多 server 对等 + CAS 抢占 + 心跳超时足够，不引入复杂共识算法。

### 4.7 不做事件流水表

用执行日志（executor 内存 buffer + gRPC FetchLog 实时 + 定期存储快照 + 终态 flush）替代，不建 adhoc_query_event 表。

### 4.8 不做资源统计表

scan_rows/scan_bytes 加到 task 表，不建 adhoc_query_resource 表。

### 4.9 不做租户隔离

无 tenant_id，所有表和指标无 tenant 维度。

### 4.10 不做 INSERT/UPDATE/DELETE 即席执行

即席查询只读，写操作走调度系统。DML_INSERT/DML_MODIFY 虽在 SqlType 枚举中，但不用于即席执行。

### 4.11 不做任务依赖编排

Job 内 Task 串行执行，无依赖关系。多 Task 之间无 DAG 编排。

### 4.12 不做 Exactly-Once 调度层承诺

靠 sql_hash 复用 + 存储一次性上传 + 业务表幂等兜底。

### 4.13 不做 executor 宕机自动重试

executor 宕机（EXECUTOR_CRASHED）后 Task 直接 FAILED，不自动重新入队。由用户决定是否重试。

### 4.14 不做主动探活

依赖心跳超时判定实例 DOWN，不主动调 executor 接口探活。

### 4.15 不做历史实例归档表

DOWN 实例 24h 后直接物理删除，不归档到 history 表。

### 4.16 不做平台层鉴权

依赖底层引擎（Kyuubi/StarRocks）侧权限控制，平台不做权限校验，无 GRANTING 阶段，无赋权 SPI。无权限由引擎报错，executor 捕获标记 Task FAILED（ENGINE_ERROR）。

### 4.17 不做 HIVE 引擎

engine_type 只保留 KYUUBI 和 STARROCKS，Hive 场景由 Kyuubi + Spark 承接。无 HiveEngineExecutor、无 Hive g4 解析器、无 HIVE 引擎实例配置。

### 4.18 不做 server 端内存结果缓存

结果在 executor upload 存储 + server 直读存储，不做 server 端内存缓存（不建 adhoc_result_schema/adhoc_result_file 表，schema 存文件头）。

### 4.19 不做 server 端日志写入

日志全部在 executor 产生（内存 buffer + gRPC FetchLog 实时 + 定期存储快照 + 终态 flush），server 端不产生日志文件，前置校验失败直接返回错误给前端。

## 5. 后续演进路线（建议）

### 5.1 优先级

1. **Grafana 大盘**：补齐面板（任务总览/队列/阶段耗时/集群/结果/异常）
2. **多集群支持**：按 engineInstance 路由，支持跨集群
3. **UDF 元数据接入**：接 UDF 注册中心，自动识别可用 UDF
4. **全文检索**：目录树 sql_content 内搜索，接 Elasticsearch
5. **结果导出**：server 端流式导出 Excel/CSV

### 5.2 后续可评估项

- 行级权限（按 userId 注入 where 条件）
- SQL 风险评分 + 审批
- 用户优先级调度（避免大用户饿死）
- 新引擎接入（按需评估）
- gRPC HealthCheck 二次探活（网络分区场景）

## 6. 验收标准

1. 限制项覆盖各模块（接入认证/引擎SQL/Session与Job拆分/调度并发/前置校验限流/结果存储/高可用/执行日志/目录树/可观测/executor 执行模型）
2. SPI 扩展口子 8 个，均有内置实现
3. 不演进项明确列出
4. 后续演进路线 5 项优先级 + 5 项可评估
5. 所有限制项标注"当前现状"和"后续演进"两列
6. 不演进项标注"不演进"而非"后续补齐"
7. 不再引用 HIVE 引擎、平台层鉴权、adhoc_result_schema/adhoc_result_file 表、server 端内存结果缓存、gRPC FetchResult（已删）
