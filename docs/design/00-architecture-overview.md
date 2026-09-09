# bigdata-adhoc 平台设计总览

> 本文档是 bigdata-adhoc 平台设计的主文档，统领各模块设计文档（01-06、08-13）。本文定义整体架构、模块划分、数据模型概览，各模块细节见对应模块文档。

## 1. 平台定位

bigdata-adhoc 是面向数据开发人员的即席查询平台，支持 KYUUBI / STARROCKS 两引擎，提供 SQL 提交、解析、前置校验、调度、执行、结果存储、日志查询的完整链路。

**功能范围**：
- Java SDK（双协议：gRPC 默认 + REST 兼容）+ REST API，不含 Web 控制台
- 两引擎闭环（KYUUBI / STARROCKS），用户提交时必须显式指定 engine_type，无 AUTO 模式
- 表级血缘（表级 source/sink + 字段级 transform）
- 单集群，多 server 对等 + 多 executor 对等
- 权限检查不在平台侧实现（如需，可在引擎侧自行部署权限插件，权限报错统一作为引擎错误处理）

**不做项**：租户隔离、脱敏/加密、UDF 注册中心、跨集群联邦、实时流式查询、raft/leader 选举、事件流水表、资源统计表、AUTO 引擎推算、平台侧权限检查、HIVE 引擎。

## 2. 设计原则

- **模块化**：按功能拆 Maven 模块，各自独立设计文档，边界清晰
- **SPI 扩展**：引擎日志、资源采集、引擎执行器、结果存储等通过 SPI 预留口子
- **职责分离**：server 不接触数据，executor 完全负责 Job 执行（拆分 + 调度 + 存储 + 状态）
- **session 绑定 executor**：session 生命周期与 executor 绑定，server 宕机不影响 RUNNING Job
- **不做过度设计**：即席场景，不引入复杂共识算法、不建事件流水表、不做租户隔离
- **user_id 隔离**：所有限制按 user 维度，所有含 user_id 的表冗余 user_name（中文名）
- **CAS 抢占**：多 server 部署下无锁抢占，不引入分布式锁
- **配置动态化**：流控阈值、引擎参数白名单、超时等通过 Apollo 推送

## 3. 整体架构

### 3.1 部署拓扑

```
                          ┌───────────────────┐
                          │  Java SDK / REST  │
                          │  (用户传 userId)   │
                          └─────────┬─────────┘
                                    │ HTTP (X-Adhoc-User-Id) / gRPC
                                    ▼
              ┌─────────────────────────────────────────┐
              │            前置网关 / 负载均衡            │
              └─────────┬───────────────┬───────────────┘
                        │               │
            ┌───────────▼───┐   ┌───────▼───────────┐
            │  adhoc-server │   │  adhoc-server     │  (多 server 对等)
            │   (实例 1)    │   │   (实例 2)        │
            │ 前端交互+校验 │   │ 前端交互+校验     │
            │ +调度+读路径 │   │ +调度+读路径      │
            │ +HA 补偿      │   │ +HA 补偿          │
            └──┬────────────┘   └──┬────────────────┘
               │ gRPC                │
               │ (dispatchJob /      │
               │  FetchLog /         │
               │  cancelJob)         │
               ▼                     ▼
        ┌─────────────────────┐  ┌─────────────────────┐
        │  adhoc-executor     │  │  adhoc-executor     │  (多 executor 对等)
        │  (实例 1)           │  │  (实例 2)           │
        │  Job 拆分 + 执行    │  │  Job 拆分 + 执行    │
        │  + 结果/日志存储    │  │  + 结果/日志存储    │
        │  + 直写 DB + 心跳   │  │  + 直写 DB + 心跳   │
        └──┬──────┬───────┬───┘  └─────────────────────┘
           │      │       │
           ▼      ▼       ▼
      ┌────────┐ ┌──────────┐    ┌────────────┐    ┌────────────┐
      │ MySQL  │ │  对象存储 │    │  Kyuubi    │    │ StarRocks  │
      │ (元数据)│ │ (local/  │    │  Engine    │    │  Engine    │
      │        │ │ 阿里云OSS)│    │  (Spark)   │    │            │
      └────────┘ └──────────┘    └────────────┘    └────────────┘

配置中心：Apollo（流控阈值 / 引擎实例 / 参数白名单 / 超时）
```

### 3.2 组件职责

| 组件 | 职责 |
|---|---|
| **SDK / REST 客户端** | 用户通过 Java SDK 或 HTTP 调用，请求头带 `X-Adhoc-User-Id` / `X-Adhoc-User-Name`，提交时显式指定 engine_type（KYUUBI / STARROCKS） |
| **adhoc-server** | 前端交互、前置校验（SQL 语法 + 段数 + 限流 + engine_type/params 合法性）、Job 调度到 executor（dispatchJob）、Job 状态管理、读路径（查 DB + StorageClient 直读结果/日志）、HA 补偿（executor 宕机检测 + 处理、心跳对账）。**不接触数据，不写日志/结果文件，不做拆分，不做权限检查** |
| **adhoc-executor** | 接收 Job（dispatchJob）、创建 session（JDBC connection）、拆分 Job（识别 SET/USE 作为 prefix_sql，创建 Task 记录）、顺序执行 Task（在 session 里）、结果内存序列化后经 `StorageClient.uploadResult` 一次性上传、日志内存 buffer + 定期快照 + 终态 flush、直写 DB 更新 Task/Job 状态、响应 FetchLog、关闭 session。**Job 执行的完全负责方，所有产物（日志、结果、session）都在 executor + 对象存储** |
| **MySQL** | 存储所有元数据（Job/Task/血缘/结果 summary/实例/目录树） |
| **对象存储** | 存储结果文件（长度前缀格式）+ Job/Task 执行日志文件。通过 `StorageClient` SPI 接入：内置 local（本地目录）与 aliyun（阿里云 OSS）实现，第三方可实现接口替换。executor 只写，server 直读 |
| **Apollo** | 配置中心，动态推送流控阈值、引擎实例、参数白名单 |
| **引擎** | KYUUBI（连接 Spark）/ STARROCKS，executor 通过 JDBC 连接 |

### 3.3 server / executor 职责划分

Job 执行的完全责任在 executor，数据/状态不频繁交互：

| 职责 | 承担方 |
|---|---|
| 前端交互 | server |
| SQL 语法校验 | server（前置校验） |
| Job 拆分 | executor |
| Task 调度 | executor（Job 内顺序执行） |
| 限流 | server |
| 引擎选择 | 用户指定（无 AUTO） |
| session 管理 | executor（完全自治） |
| 结果写入 | executor（内存序列化 + `StorageClient.uploadResult` 一次性上传） |
| 日志写入 | executor（内存 buffer + 定期快照 + 终态 flush） |
| 状态更新 | executor 直写库 |
| 结果/日志读取 | server 经 `StorageClient` 直读；日志运行中走 gRPC FetchLog 读内存 |
| HA 补偿 | server |

### 3.4 session 绑定 executor

- server 宕机 -> session 还在 executor -> Job 继续执行
- 只有 executor 宕机 -> session 丢失 -> Job FAILED

session 存储位置：executor 内存里维护 `Map<jobId, JDBC.Connection>`，Job 终态后清理。

### 3.5 关键设计决策

| 决策 | 选择 | 理由 |
|---|---|---|
| 任务模型 | Job + Task 两层 | 用户视角一次提交，调度视角一段 SQL |
| Task 串行 | Job 内按 segment_index 串行 | DDL/DML 隐式依赖 + session 复用（SET/USE 累积生效） |
| Job 拆分位置 | executor | session 绑定 executor，拆分在 executor 内完成 |
| 调度策略 | 纯 FIFO（submit_time ASC） | 不做优先级 |
| 状态机 | Job 多态（PENDING/DISPATCHING/RUNNING/SUCCESS/PARTIAL_FAILED/FAILED/CANCELED）+ Task status + stage | 位置/阶段/失败定位分离 |
| 多 server 协调 | CAS 抢占（WHERE status=?） | 不引入分布式锁 |
| 结果存储 | executor `StorageClient.uploadResult` 一次性上传 | executor 拉完结果内存序列化后一次性上传，server `StorageClient.download` 直读，不写本地文件 |
| storage_type | 2 态（NONE/PERSISTENT） | 结果写完存储即 PERSISTENT，无中间态 |
| Task 日志 | executor 内存 buffer + gRPC FetchLog + 存储定期快照 | 运行中 gRPC FetchLog 读内存（~100ms），定期覆盖快照，终态最后 flush；executor DOWN 读快照（丢最后 ≤2s） |
| Job 日志 | executor 内存 + 终态一次性上传 | Job 级事件少，无需定期 flush |
| 引擎日志采集 | JDBC getOperationLog / getWarnings | 不从 YARN / Spark History Server 聚合 |
| 血缘 | 表级，拆 adhoc_query_table_ref 新表 | source/sink 分开存 JSON |
| 高可用 | 多 server 对等 + 心跳超时 + CAS 补偿 | server 宕机不影响 RUNNING Job，只有 executor 宕机才影响 |
| 心跳对账 | executor 心跳带 running_tasks | 兜底 executor 写库失败，5s 内校正状态 |
| engine_type | KYUUBI / STARROCKS（无 AUTO） | 用户显式指定，简化引擎路由 |

## 4. Maven 模块划分

11 个 Maven 模块。配套各模块设计文档（01-06、08-13）。

### 4.1 模块清单

| 层 | 模块 | 职责 |
|---|---|---|
| **应用层** | adhoc-server | REST API + 前置校验 + Job 调度（dispatchJob）+ 读路径 + HA 补偿（executor 宕机检测 + 心跳对账） |
| | adhoc-executor | Job 拆分 + Task 顺序执行 + 结果/日志存储 + 直写 DB + 心跳对账 + 响应 FetchLog |
| **库层** | adhoc-sql-parser | 多引擎 g4 解析（Kyuubi/StarRocks）+ 血缘提取 |
| | adhoc-dao | MyBatis Mapper + Entity |
| | adhoc-storage | 结果存储（StorageClient SPI：local/aliyun 内置实现）+ 结果序列化 format/model + AdhocResultReader |
| | adhoc-metadata | 元数据补全（Hive metastore / StarRocks information_schema） |
| **共享层** | adhoc-api-sdk | Java SDK（双协议 REST+gRPC），可被外部项目依赖 |
| | adhoc-protocol | gRPC protobuf（internal: server<->executor / sdk: 对外契约，多语言 SDK 源头） |
| | adhoc-common | 工具类、枚举（SqlType/Stage/Status 等）、异常、统一配置 |
| **扩展模块** | adhoc-observability | 指标 + 审计 |
| **BOM** | adhoc-dependencies | 依赖版本统一管理（spring-boot/mybatis/g4/protobuf/aliyun-sdk-oss 等） |

### 4.2 模块依赖关系

```
                  adhoc-dependencies (BOM)
                        ↑
            ┌───────────┴───────────┐
            │                       │
      adhoc-common             adhoc-protocol
            ↑                       ↑
   ┌────────┼────────┐              │
   │        │        │              │
adhoc-dao  adhoc-    adhoc-          │
   │     storage  sql-parser         │
   │        │        │              │
   └────────┼────────┴──────────────┘
            │
   ┌────────┼─────────┐
   │        │         │
adhoc-              adhoc-
observ.             api-sdk
   │                  │
   └──────────────────┘
            │
      ┌─────┴─────┐
      │           │
adhoc-server  adhoc-executor
```

**依赖方向规则**：
- adhoc-server 不依赖 adhoc-executor（仅通过 gRPC 通信）
- adhoc-storage 主要被 executor 依赖（结果上传 + 日志定期快照）；server 依赖其 StorageClient + AdhocResultReader（storage_type=PERSISTENT 时直读分页）
- adhoc-metadata 仅被 server 依赖（元数据补全是读路径功能）
- adhoc-observability 独立模块，server 和 executor 均可引用
- adhoc-api-sdk 独立，用户引入即可调用，不依赖 server/executor

### 4.3 打包与外部依赖

**可单独打包的模块**（4 个，各自独立部署或发布）：

| 模块 | 打包方式 | 部署形态 |
|---|---|---|
| adhoc-server | fat-jar / Docker | 独立进程，多实例对等 |
| adhoc-executor | fat-jar / Docker | 独立进程，多实例对等 |
| adhoc-sql-parser | jar | 库依赖，发布到 Maven 仓库 |
| adhoc-api-sdk | jar | 库依赖，发布到 Maven 仓库 |

**可被其他项目直接依赖的模块**（2 个，对外暴露）：

| 模块 | 外部依赖场景 | 引入方式 |
|---|---|---|
| adhoc-api-sdk | 业务方接入即席查询平台 | `<dependency>` 引入，配置 endpoint 即可调用 |
| adhoc-sql-parser | 其他项目需要 SQL 解析/血缘提取（不依赖 server） | `<dependency>` 引入，直接调 parse API |

**不对外暴露的模块**：
- adhoc-server / adhoc-executor：独立进程，不作为 jar 依赖
- adhoc-dao / adhoc-storage / adhoc-observability / adhoc-metadata：内部库，仅被 server/executor 引用
- adhoc-protocol / adhoc-common：内部共享，仅被 server/executor/sdk 引用
- adhoc-dependencies：BOM，仅做版本管理

### 4.4 SDK 认证方式

Java SDK 同时支持 REST 和 gRPC 双协议，默认 gRPC（性能更好，且 proto 文件可作为多语言 SDK 契约源头）：

```java
AdhocClient client = AdhocClient.builder()
    .endpoint("grpc://adhoc.example.com:9090")   // gRPC 默认
    // 或 "http://adhoc.example.com:8080"          // REST 兼容模式
    .build();

JobResult result = client.submitJob(SubmitJobRequest.builder()
    .userId("user123")
    .userName("张三")
    .sqlContent("select * from mydb.dim_user")
    .engineType("KYUUBI")  // 必须显式指定：KYUUBI 或 STARROCKS
    .build());
```

**认证传递**：
- REST 模式：HTTP 头 `X-Adhoc-User-Id` / `X-Adhoc-User-Name`
- gRPC 模式：metadata `x-adhoc-user-id` / `x-adhoc-user-name`

server 从头/metadata 提取 userId / userName，写入 Job 表（user_id + user_name），并通过 gRPC dispatchJob 传递给 executor。

**身份透传**：userId / userName 通过 dispatchJob 传递给 executor，executor 在创建 JDBC connection 时透传给引擎（Kyuubi 的 proxyUser 或 StarRocks 的 user），供引擎侧按提交人身份执行/鉴权（如引擎侧配置了权限插件）。平台侧不做权限检查。

**多语言 SDK**：gRPC proto 文件位于 `adhoc-protocol/src/main/proto/sdk/`，可从同一份 proto 生成 Python/Go/JS 客户端，接口契约一致。详见模块 12。

## 5. 数据模型概览

10 张 MySQL 表。完整 DDL 见模块 11（11-schema.md，权威）。

### 5.1 表清单

| 表名 | 用途 |
|---|---|
| adhoc_query_job | Job 主表（用户一次提交，含 executor_instance） |
| adhoc_query_task | Task 主表（一段 SQL，含 prefix_sql） |
| adhoc_query_governance | 前置校验结果 |
| adhoc_query_table_ref | 血缘表（source/sink tables JSON） |
| adhoc_engine_param_rule | 引擎参数白名单规则（server 前置校验用） |
| adhoc_result_summary | 结果摘要（行数/字节/存储 key/storage_type 两态/upload 状态） |
| adhoc_server_instance | server 实例心跳 |
| adhoc_executor_instance | executor 实例心跳（含 cpu/mem/disk/running_tasks） |
| adhoc_jvm_metric_sample | JVM 指标时序采样（监控大盘） |
| adhoc_file_node | 用户目录树（DIRECTORY/FILE） |

### 5.2 跨模块字段

| 字段 | 所在表 | 用途 |
|---|---|---|
| source_file_node_id | adhoc_query_job | 从文件提交时记录来源 |
| executor_instance | adhoc_query_job | 执行 Job 的 executor（dispatchJob 时确定，session 绑定） |
| prefix_sql | adhoc_query_task | SET/USE 前缀语句（Job 内累积，不含 sql_content 本身） |
| log_path | adhoc_query_task | Task 日志存储 key（persistent_log_path，内存 buffer + 定期快照 + 终态 flush） |
| scan_rows / scan_bytes | adhoc_query_task | 引擎资源采集 |
| fail_stage | adhoc_query_task | 失败阶段精确定位 |
| cpu_usage / mem_usage / disk_usage | adhoc_executor_instance | 心跳上报，load_score 打分依据 |
| running_tasks | adhoc_executor_instance | 心跳上报的当前执行 Task 列表（JSON），用于心跳对账 |

## 6. 核心流程

### 6.1 提交流程

```
SDK 提交 SQL（显式指定 engine_type: KYUUBI / STARROCKS）
  │ HTTP 头带 X-Adhoc-User-Id
  ▼
server 接收
  │ 前置校验：
  │   1. SQL 语法校验（g4 解析整个 sql_content，不通过直接报错）
  │   2. Task 段数检查（按 ';' 计数，含 SET/USE）
  │   3. 限流（PENDING，全局 + per-user）
  │   4. engine_type 合法性（必须为 KYUUBI 或 STARROCKS）
  │   5. engine_params 合法性（JSON 格式）
  │   校验失败：直接返回错误给前端，不创建 Job 记录
  ▼
校验通过，创建 Job 记录（status=PENDING）
  │
  ▼ server worker 抢占（CAS）
Job status -> DISPATCHING -> RUNNING，调度到 executor
  │   候选 executor 选择：status=UP 且 accepting、支持目标引擎
  │
  ▼ server gRPC dispatchJob 给 executor（下发整个 Job）
executor 接收 Job
  │
  ▼ 创建 session（JDBC connection，engineParams 作为初始 params）
  │
  ▼ 拆分 Job（识别 SET/USE 作为 prefix_sql，创建 Task 记录，直写 DB）
  │
  ▼ 顺序执行 Task（segment_index ASC，在 session 里）：
  │   for each task:
  │     在 session 里执行 prefix_sql + sql_content（prefix 幂等）
  │     拉结果（强制 LIMIT），内存序列化 -> StorageClient.uploadResult 一次性上传
  │     日志内存 buffer + 定期快照覆盖 + 终态最后 flush
  │     成功 storage_type=PERSISTENT
  │     UPDATE task.status（直写 DB，失败走 L1-L4 + 心跳对账）
  │     前 Task FAILED -> 跳过后续 Task（session 状态异常风险）
  │
  ▼ 所有 Task 终态
executor 关闭 session
  │
  ▼ Job 聚合终态（SUCCESS / PARTIAL_FAILED / FAILED），直写 DB
```

### 6.2 前置校验

server 接收 Job 提交时执行，校验失败直接返回错误给前端，不创建 Job 记录：

1. **SQL 语法校验**：g4 解析整个 sql_content，不通过直接报错（`ADHOC_SQL_SYNTAX_ERROR`）
2. **Task 段数检查**：按 ';' 拆分计数，超过 max_tasks_per_job 拒绝（`ADHOC_JOB_TOO_MANY_TASKS`）
3. **限流检查**：全局 + per-user 的 PENDING Job 上限（`ADHOC_JOB_LIMIT_EXCEEDED`）
4. **engine_type 合法性**：必须为 KYUUBI 或 STARROCKS，不指定拒绝（`ADHOC_ENGINE_TYPE_REQUIRED`），非法值拒绝（`ADHOC_ENGINE_TYPE_INVALID`）
5. **engine_params 合法性**：JSON 格式校验（`ADHOC_ENGINE_PARAMS_INVALID`）

前置校验只做"能否接受提交"的判断，不做"能否执行成功"的判断（执行时的权限、SQL 语义错误由引擎层处理）。

### 6.3 SET + session 复用

executor 接收整个 Job 的 sql_content 后，按 `;` 切分，识别 SET/USE 为"会话配置前缀"，合并到下一个真正执行的 Task：

```
用户提交：
  SET spark.sql.shuffle.partitions=100;
  USE db_ods;
  SELECT * FROM table_a WHERE dt='2026-07-01';
  SET spark.executor.memory=4g;
  SELECT * FROM table_b LIMIT 10;

executor 拆分结果（2 个 Task，不是 5 个）：
  Task 1:
    prefix_sql = "SET spark.sql.shuffle.partitions=100; USE db_ods;"
    sql_content = "SELECT * FROM table_a WHERE dt='2026-07-01';"
  Task 2:
    prefix_sql = "SET spark.sql.shuffle.partitions=100; USE db_ods; SET spark.executor.memory=4g;"
    sql_content = "SELECT * FROM table_b LIMIT 10;"
```

边界规则：
- 整个窗口全是 SET/USE（无真正查询）：executor 拆分时发现，标记 Job FAILED（`ADHOC_JOB_NO_EXECUTABLE_SQL`）
- 最后一段是 SET/USE（无后续查询）：该 SET/USE 丢弃（无意义）
- SET/USE 识别：SqlType 增加 `SESSION_CONFIG` 类型，g4 解析阶段判定
- sql_hash 计算变更：`sql_hash = SHA256(prefix_sql + sql_content)`（前缀参与 hash，因为前缀影响结果）

session 在 executor 内存里维护 `Map<jobId, JDBC.Connection>`，Job 终态后清理。详见模块 01。

### 6.4 结果分级存储

| storage_type | 含义 | 对象存储 | 读取路径 |
|---|---|---|---|
| NONE | 无结果集 | 无 | 无结果可读 |
| PERSISTENT | 已上传 | 有 | server `StorageClient.download(persistent_path)` -> `AdhocResultReader` 分页直读 |

核心约束：
- **强制 LIMIT**：查询请求自动追加 LIMIT（可配置），限制单结果规模
- **executor 不写本地文件**：结果在内存序列化（`AdhocResultSerializer` MAGIC+schema+rows 长度前缀格式）后一次性上传
- **server 直读**：`StorageClient.download(persistent_path)` -> InputStream -> `AdhocResultReader` skip 分页（复用现有 reader，只换数据源）
- **存储 key 命名**：`result/{day}/{jobId}/{taskId}/result.part-0`（local/aliyun 实现统一按此 key 布局）
- **生命周期**：local 实现需自行清理，aliyun 实现依赖 OSS 生命周期规则

详细见模块 04。

### 6.5 日志统一在 executor

```
存储 key 布局：
  log/{day}/{jobId}/job.log                       ← Job 日志（executor 终态一次性上传）
  log/{day}/{jobId}/{taskId1}/task.log            ← Task 日志（内存 buffer + 定期覆盖快照 + 终态 flush）
  log/{day}/{jobId}/{taskId2}/task.log
```

`{day}` = Job 提交日期（yyyy-MM-dd），用于分区清理。

| 文件 | 产生方 | 内容 | 写入策略 |
|---|---|---|---|
| job.log | executor | session 创建、Job 拆分、Task 调度顺序、Task 状态变更、session 关闭 | 内存 + 终态一次性上传 |
| task.log | executor | 引擎连接、SQL 执行、结果拉取、引擎报错 | 内存 buffer + gRPC FetchLog 实时读 + 定期覆盖快照 + 终态最后 flush |

**server 端不产生日志文件**：前置校验失败直接返回错误给前端，不创建 Job 记录。

**读日志路径**：
- Task 运行中 + executor UP：gRPC `FetchLog` 读内存 buffer（实时，~100ms）
- executor DOWN 或 Task 终态：`StorageClient.download(logKey)` 读快照
- executor 宕机：存储上有最后一次 flush 快照（丢最后 ≤2s，内存 buffer 未 flush 部分）

详细见模块 09。

### 6.6 心跳与宕机补偿

- **executor 心跳**：定期上报 heartbeat_time + cpu_usage + mem_usage + disk_usage + running_tasks（JSON，当前在执行的 Task 列表）
- **server 心跳**：定期上报 heartbeat_time + status
- **心跳超时阈值**：executor 30s（容忍长 GC 和网络抖动），server 15s
- **executor 宕机补偿**：server 后台线程 CAS 标记 executor DOWN，RUNNING Task 直接 FAILED（fail_reason_category=EXECUTOR_CRASHED），不自动重试；session 模式下未开始的 PENDING Task 标记 FAILED（fail_reason_category=SKIPPED_DUE_TO_PRIOR_FAILURE）；result_summary 按 upload 状态分流；日志按 persistent_log_path 读快照（丢最后 ≤2s）
- **server 宕机补偿**：PENDING Job 由其他 server 抢占重调度（processing_server_instance 切换）；RUNNING Job 不受影响（session 在 executor），processing_server_instance 置 NULL，executor 继续执行并直写 DB
- **心跳对账**：executor 心跳带 running_tasks，server 对账兜底 executor 写库失败（DB=RUNNING 但心跳=SUCCESS -> 更新；DB=RUNNING 但心跳里没有 -> TASK_LOST）
- **executor 假 DOWN 处理**：心跳恢复后主动 UPDATE UP，不恢复已 FAILED 的 Task（避免状态回退）；结果未上传的重跑（无本地文件兜底）；日志已定期 flush 到快照，无需补传

详细见模块 05。

### 6.7 引擎执行失败处理

executor 执行 Task 时，引擎报错（权限不足、表不存在、SQL 语义错误等）：
- 标记 Task FAILED
- fail_reason_category = ENGINE_ERROR
- error_message = 引擎报错详情
- 跳过后续 Task（session 模式，前 Task 失败可能使 session 状态异常）
- Job 聚合：含 SUCCESS + FAILED -> PARTIAL_FAILED；全 FAILED -> FAILED

不区分权限失败、SQL 错误等，统一作为 ENGINE_ERROR，由 error_message 区分，executor 透传引擎返回的错误消息。

## 7. RPC 接口

本节简要列出 RPC，详细 protobuf 定义见模块 12（12-sdk.md）。

### 7.1 server -> executor

| RPC | 用途 |
|---|---|
| dispatchJob | 下发整个 Job（job_id + user_id + sql_content + engine_type + engine_params） |
| FetchLog | 拉日志分页（jobId + offset + limit，读 executor 内存 buffer） |
| cancelJob | 取消整个 Job |
| getRunningJobs | 探查 executor 当前在跑 Job（孤儿 RUNNING Job 兜底） |
| clearJobLog | 通知 executor 清理已落盘的日志 buffer |

### 7.2 executor -> server

| RPC | 用途 |
|---|---|
| heartbeat | 心跳 + 对账（带 cpu/mem/disk + running_tasks） |
| reportTaskStatus | 接口转发 Task 状态（executor 直写 DB 失败时，gRPC 调 server 代写） |
| reportJobStatus | 接口转发 Job 状态 |

### 7.3 写库失败四层保障（L1-L4 + 心跳对账）

executor 状态更新走四层保障：
- **L1**：executor 直写 DB（UPDATE task.status + INSERT result_summary，含 persistent_path=存储 key / upload 状态 / storage_type=PERSISTENT）
- **L2**：L1 失败则重试（指数退避：1s, 2s, 4s，3 次，总等待约 7s）
- **L3**：L2 失败则接口转发，gRPC 调用 server.reportTaskStatus，server 代为更新 DB
- **L4**：L3 失败则写本地 WAL（`wal/{taskId}.json`），后台线程定期重试，executor 重启时优先提交 WAL
- **心跳对账兜底**：executor 心跳带 running_tasks，server 收到后对账，DB 状态与心跳不一致时校正

## 8. 模块清单

各模块设计文档，每份独立成文：

| 编号 | 模块 | 文件 | 核心内容 |
|---|---|---|---|
| 01 | 任务调度与执行 | 01-task-scheduling-execution.md | Job/Task 两层模型 + executor 拆分 + session 绑定 executor + 顺序执行 + Task 失败传播 |
| 02 | SQL 解析与治理 | 02-sql-parsing-governance.md | SqlType 分类 + 多引擎 g4（Kyuubi/StarRocks）+ 前置校验关卡 + 血缘 |
| 03 | 引擎路由 | 03-engine-routing.md | KYUUBI/STARROCKS 实例选择 + 多实例配置 + 身份透传（无 AUTO，无降级链） |
| 04 | 结果存储 | 04-result-storage.md | executor 内存序列化 + StorageClient 一次性上传 + 长度前缀格式 + 2 态 storage_type |
| 05 | 高可用 | 05-high-availability.md | 多 server 对等 + 心跳对账 + 宕机补偿 + server 宕机不影响 RUNNING Job + L1-L4 写库保障 |
| 06 | 可观测性 | 06-observability.md | 时间戳 + fail_stage + 指标 + 资源采集 + JVM 采样大盘 |
| 08 | 目录树管理 | 08-directory-tree.md | 每用户独立树 + 软删除 + 路径解析 |
| 09 | 执行日志 | 09-execution-log.md | executor 内存 buffer + gRPC FetchLog 实时 + 定期快照覆盖 + 终态 flush + 宕机读快照 |
| 10 | 限制与演进 | 10-limitations.md | 已知限制 + 后续规划 |
| 11 | 表结构清单 | 11-schema.md | 完整 DDL |
| 12 | Java SDK | 12-sdk.md | 双协议（gRPC 默认 + REST）+ 独立打包 + Builder API + 多语言 SDK 契约源头 + RPC 接口定义 |
| 13 | 配置目录 | 13-config-catalog.md | server/executor 全量配置项 |

## 9. SPI 扩展口子

| SPI | 模块 | 内置实现 |
|---|---|---|
| QueryLogIterator | 模块 09 | KyuubiQueryLogIterator / StarRocksQueryLogIterator |
| EngineResourceCollector | 模块 06 | KyuubiResourceCollector |
| EngineExecutor | 模块 01 | KyuubiEngineExecutor / StarRocksEngineExecutor |
| LineageExtractor | 模块 02 | g4 解析实现 |
| SqlParserEngine | 模块 02 | KyuubiParser / StarRocksParser |
| EngineEndpointSelector | 模块 03 | LoadBasedEndpointSelector |
| SessionParamSetter | 模块 01 | DefaultSessionParamSetter |
| ResultStorageHandler | 模块 04 | StorageClientResultHandler（executor 内存序列化 + 一次性上传） |
| StorageClient | 模块 04 | LocalStorageClient / AliyunStorageClient（Spring Bean 注册，`@ConditionalOnMissingBean` 支持第三方覆盖） |

## 10. 已知限制汇总

核心限制（详见模块 10）：

| 类别 | 限制项 |
|---|---|
| 认证 | 信任网关注入的请求头身份，平台自身不做认证 |
| 鉴权 | 平台侧不做权限检查（如需可在引擎侧自行部署权限插件） |
| 引擎 | 仅 KYUUBI / STARROCKS，用户提交时显式指定 engine_type |
| 调度 | 纯 FIFO，无优先级 |
| 结果 | 单 Task 强制 LIMIT 行数上限 |
| 高可用 | 不自动重试 executor 宕机任务 |
| 日志 | 只走 JDBC，不聚合 YARN |
| 目录树 | 按名称 LIKE 搜索 |
