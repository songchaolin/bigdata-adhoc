# 模块 8：目录树管理

> 本模块负责 Job/Task 执行期间在 executor 端产生的目录树管理，包括日志文件、结果文件、WAL 文件的存储布局、路径规范、文件格式、TTL 清理。

## 1. 概述

### 1.1 模块职责

- **存储 key 结构管理**：`result/{jobId}/{taskId}/result.part-0`、`log/{jobId}/{taskId}/task.log`、`log/{jobId}/job.log`，executor 上传存储（StorageClient SPI），存储是唯一持久化层
- **WAL 目录管理**：`wal/{taskId}.json`，executor 写库失败时的兜底（WAL 是本地文件，不是结果/日志）
- **文件格式约定**：result.part-0 二进制格式（MAGIC + schema + 数据行）、job.log / task.log 纯文本格式
- **TTL 清理**：存储对象 TTL 由后端实现控制（local 自扫描 / aliyun 桶生命周期规则）、WAL 提交成功后立即删

### 1.2 设计原则

- **结果/日志在存储 + executor 内存**：结果 executor 序列化后一次性 `uploadResult` 上传存储；日志 executor 内存 buffer + 定期(2s)`uploadLog` 上传存储快照 + 终态 flush；executor 不写本地结果/日志文件，server 端不创建任何 Job/Task 目录
- **存储可插拔**：StorageClient SPI 内置 local（本地文件系统）/ aliyun（阿里云 OSS），第三方可自行实现并注册为 Bean
- **统一 key 结构**：`result/` / `log/` 前缀区分类型，`{jobId}/` 为 Job 顶层 key，job.log 与各 `{taskId}/` 子 key 平级，子 key 内含 result.part-0 + task.log
- **schema 存文件头**：schema JSON 直接写在 result.part-0 文件头，不单独建表
- **前置校验失败不落盘**：server 前置校验失败直接返回错误给前端，不创建 Job 记录，executor 不产生任何存储文件
- **实时可见**：日志 gRPC `FetchLog` 读 executor 内存 buffer（~100ms）；结果写完存储即可读（server `download` 直读）

## 2. 目录结构

### 2.1 存储 key 结构

executor 不写本地文件，结果/日志通过 StorageClient 上传存储。key 结构如下：

```
result/{jobId}/{taskId1}/result.part-0      ← Task 结果
log/{jobId}/job.log                         ← Job 日志（Job 终态一次性 uploadLog）
log/{jobId}/{taskId1}/task.log              ← Task 日志（定期 2s 快照 + 终态 flush）
result/{jobId}/{taskId2}/result.part-0
log/{jobId}/{taskId2}/task.log
result/{jobId}/{taskId3}/result.part-0
log/{jobId}/{taskId3}/task.log
```

- **`result/` / `log/`**：实现内部按前缀区分类型（local 写入 baseDir 下 result/ 与 log/ 目录；aliyun 写入 bucket 下 result/ 与 log/ 前缀）
- **`{jobId}`**：Job ID，与 `adhoc_query_job.job_id` 一致
- **`{taskIdN}`**：Task ID，与 `adhoc_query_task.query_id` 一致；Task 数量由 executor 拆分后确定
- **`log/{jobId}/job.log`**：Job 级日志，记录 session 创建、Job 拆分、Task 调度顺序、Task 状态变更、session 关闭（Job 终态一次性 uploadLog，无需定期 flush）
- **`result/{jobId}/{taskId}/result.part-0`**：Task 结果文件，二进制格式（见 §3.1），executor 序列化后一次性 `uploadResult`
- **`log/{jobId}/{taskId}/task.log`**：Task 级日志，记录引擎连接、SQL 执行、结果拉取、引擎报错；executor 内存 buffer + 定期(2s)`uploadLog` 覆盖快照 + 终态最后 flush
- **覆盖上传**：`uploadLog` 同 fileName 覆盖写快照（日志定期快照用），`uploadResult` 一次性上传结果（不涉及覆盖）

### 2.2 存储唯一持久化层

存储（StorageClient SPI）是唯一持久化层：

- **结果**：executor 序列化后一次性 `storageClient.uploadResult(ossKey, data)` 到 `result/{jobId}/{taskId}/result.part-0`（不分段、不加 tmp 后缀）
- **日志**：executor 内存 buffer + 定期(2s)`storageClient.uploadLog(logKey, data)` 覆盖 `log/{jobId}/{taskId}/task.log` 快照 + 终态最后 flush；job.log 终态一次性 uploadLog
- 上传成功后 `result_summary.storage_type` = `PERSISTENT`，`persistent_path` = fullKey
- 上传失败（结果）：无本地兜底，`storage_type` 保持 `NONE`，`oss_upload_status` = `FAILED`（重试上限后），Task FAILED（WRITE_ERROR，fail_stage=OSS_UPLOAD）
- 上传失败（日志）：不失败 Task，下次定期 flush 重试；executor 宕机时丢最后 ≤2s 日志（内存 buffer 未 flush）
- executor DOWN 且结果已上传存储：server 直接读存储（`storage_type=PERSISTENT`），不依赖 executor

### 2.3 WAL 目录

```
wal/
  ├── {taskId1}.json
  ├── {taskId2}.json
  └── ...
```

- **WAL 文件命名**：`{taskId}.json`，一个 Task 一个 WAL 文件
- **WAL 文件格式**：单条 JSON，约 200 字节
- **WAL 内容**：只存"状态变更事件"（task_id、status、stage、fail_reason_category、error_code、error_message、finish_time、result_summary 摘要），**不存结果数据**（结果上传存储，不在本地）
- **写入时机**：executor 直写 DB 失败 -> 重试失败 -> gRPC 接口转发失败（L4 兜底）
- **删除时机**：WAL 提交成功后立即删除（executor 后台线程每 30s 重试）
- **重启优先**：executor 重启时优先提交本地 WAL，再接收新 Task

WAL 文件示例（约 200 字节）：

```json
{
  "task_id": "t-9c4f3a1b",
  "status": "SUCCESS",
  "stage": "WRITING",
  "fail_reason_category": null,
  "error_code": null,
  "error_message": null,
  "finish_time": 1720936835123,
  "result_summary": {
    "result_rows": 1000,
    "result_bytes": 131072,
    "persistent_path": "result/job-xxx/t-9c4f3a1b/result.part-0",
    "storage_type": "PERSISTENT",
    "oss_upload_status": "SUCCESS"
  }
}
```

### 2.4 日期分区

- 存储对象 TTL 由后端实现控制（见 §7），不依赖 key 中的日期分区
- `adhoc_query_job.submit_time` 记录 Job 提交日期，用于 DB 记录清理（见 §7.4）

## 3. 文件格式

### 3.1 result.part-0 文件格式

二进制格式，长度前缀编码：

```
┌─────────────────────────────────────────┐
│ MAGIC (4 bytes) = 0xADH0C                │
├─────────────────────────────────────────┤
│ schema 行                                │  INT_LEN (4 bytes) + schema JSON bytes
├─────────────────────────────────────────┤
│ data 行 0                                │  INT_LEN (4 bytes) + row bytes
├─────────────────────────────────────────┤
│ data 行 1                                │  INT_LEN (4 bytes) + row bytes
├─────────────────────────────────────────┤
│ ...                                      │
└─────────────────────────────────────────┘
```

- **MAGIC**：4 字节，固定值 `0xADH0C`，用于文件类型识别和完整性校验
- **schema 行**：`INT_LEN (4 bytes, big-endian)` + schema JSON 字节流
- **data 行**：每行 `INT_LEN (4 bytes, big-endian)` + row 字节流
- **row 编码**：行内字段按 schema 顺序拼接，字段间用 `` 分隔（Hive SerDe 风格）
- **存储格式与后端无关**：executor 内存序列化（MAGIC+schema+rows）为 `byte[]` 后一次性 `uploadResult`，格式与存储后端无关（local/aliyun 共用同一格式）

### 3.2 schema JSON 格式

schema 直接写在 result.part-0 文件头，不单独建表：

```json
[
  {"col_index": 0, "col_name": "id", "col_type": "BIGINT"},
  {"col_index": 1, "col_name": "name", "col_type": "STRING"},
  {"col_index": 2, "col_name": "create_time", "col_type": "TIMESTAMP"}
]
```

- **col_index**：从 0 开始的列序号
- **col_name**：列名
- **col_type**：列类型（BIGINT / STRING / TIMESTAMP / DOUBLE / DECIMAL 等，引擎返回的 JDBC 类型映射后统一命名）

### 3.3 task.log / job.log 文件格式

纯文本，每行一条，UTF-8 编码：

```
2026-07-14 10:00:05.123 [INFO] [executor-1] Connecting to Kyuubi jdbc:kyuubi://...
2026-07-14 10:00:10.456 [INFO] [executor-1] Executing SQL: SELECT * FROM ...
2026-07-14 10:00:30.789 [WARN] [executor-1] Slow query detected, elapsed=20s
2026-07-14 10:00:35.012 [INFO] [executor-1] Fetched 1000 rows
2026-07-14 10:00:35.200 [ERROR] [executor-1] Engine error: Permission denied
```

- **行格式**：`yyyy-MM-dd HH:mm:ss.SSS [LEVEL] [thread] message`
- **LEVEL**：INFO / WARN / ERROR / DEBUG
- **thread**：executor 内部线程名（executor-1、executor-2 等）
- **换行符**：`\n`（Linux 风格，便于 tail -f）

## 4. 日志内容

### 4.1 job.log

产生方：executor。

| 阶段 | 日志内容 |
|---|---|
| session 创建 | `Creating JDBC session for engine=KYUUBI, instance=kyuubi-1, params={...}` |
| Job 拆分 | `Splitting Job into N tasks: [task-1, task-2, ...]` |
| Task 调度 | `Scheduling task-1 (segment_index=0)` / `Scheduling task-2 (segment_index=1)` |
| Task 状态变更 | `Task task-1 status: RUNNING -> SUCCESS, elapsed=15s` |
| session 关闭 | `Closing JDBC session for job=job-xxx` |
| Job 终态 | `Job job-xxx finished, status=PARTIAL_FAILED, success=1, failed=1` |

### 4.2 task.log

产生方：executor。

| 阶段 | 日志内容 |
|---|---|
| 引擎连接 | `Connecting to Kyuubi jdbc:kyuubi://kyuubi-1:10009/...` |
| prefix 执行 | `Executing prefix_sql: SET spark.sql.shuffle.partitions=100; USE db_ods;` |
| SQL 执行 | `Executing SQL: SELECT * FROM table_a WHERE dt='2026-07-01'` |
| 结果拉取 | `Fetched 1000 rows, 131072 bytes` |
| 序列化 | `Serializing result: MAGIC+schema+rows, 131072 bytes (in-memory)` |
| 上传存储 | `Uploading result: result/job-xxx/task-1/result.part-0` |
| 引擎报错 | `Engine error: Permission denied: user=alice, table=db_ods.table_a` |

### 4.3 server 端不产生日志文件

server 前置校验失败（SQL 语法错误、段数超限、限流超限、engine_type 非法等）直接返回错误给前端，**不创建 Job 记录、不下发 executor、不产生任何目录或日志文件**。

server 端的读路径（gRPC FetchLog 转发、读存储）只读不写，不在 server 本地落盘任何 Job/Task 文件。

## 5. 写入流程

### 5.1 executor 写入流程

```
executor 接收 dispatchJob
  │
  ▼ 创建 JDBC session，初始化 job.log 内存 buffer
  │
  ▼ 拆分 Job，为每个 Task 初始化 task.log 内存 buffer（List<String> + ReentrantLock）
  │
  ▼ 顺序执行 Task：
  │   for each task:
  │     写 task.log buffer（引擎连接、SQL 执行）
  │     拉结果行（上限 100w 行，LIMIT 强制）
  │     序列化 result.part-0（内存：MAGIC + schema + 数据行 -> byte[]）
  │     写 task.log buffer（结果拉取完成、序列化完成）
  │     storageClient.uploadResult 一次性上传 result.part-0
  │       result/{jobId}/{taskId}/result.part-0
  │
  ├── 上传成功：UPDATE storage_type = PERSISTENT, oss_upload_status = SUCCESS
  └── 上传失败：storage_type 保持 NONE，oss_upload_status = FAILED（重试上限后），Task FAILED（WRITE_ERROR）
  │
  │   定期(2s) storageClient.uploadLog 覆盖 task.log 快照（同 fileName 覆盖）
  │     log/{jobId}/{taskId}/task.log
  │   Task 终态：最后 flush 一次完整 task.log
  │
  ▼ 写 job.log buffer（Task 状态变更、Job 终态、session 关闭）
  │
  ▼ Job 终态后一次性 uploadLog job.log
  │   log/{jobId}/job.log
```

### 5.2 写线程模型

- **执行线程**：执行 SQL、拉结果、序列化 result.part-0（内存）、写 task.log buffer
- **存储上传线程**：结果序列化后一次性 uploadResult，不阻塞执行线程
- **日志 flush 线程**：定期(2s)把 task.log buffer 全量序列化 uploadLog 覆盖快照，不阻塞执行线程
- **FetchLog 读线程池**：处理 gRPC FetchLog，读 task.log 内存 buffer，不影响执行线程
- **WAL 写线程**：直写 DB 失败时写 WAL，不阻塞执行线程

### 5.3 server 端无写入

server 不接触数据，不写日志/结果文件。server 只做：
- 前置校验（不落盘）
- dispatchJob（RPC 转发）
- 读路径：结果 `storageClient.download` 直读存储（不转发 executor）；日志 gRPC FetchLog 转发 executor（读内存）或 `storageClient.download` 读存储快照（executor DOWN/终态）

## 6. 读取流程

### 6.1 结果读取

```
SDK 调 GET /api/adhoc/task/{taskId}/result?pageNo=2&pageSize=100
  │
  ▼ 任意 server A 接收
  │
  ▼ 查 result_summary（storage_type, persistent_path, oss_upload_status）
  │
  ├── storage_type = PERSISTENT 且 oss_upload_status = SUCCESS：
  │     storageClient.download(persistent_path) -> InputStream
  │     AdhocResultReader(InputStream) skip 分页
  │     （不转发 executor，gRPC FetchResult 已删除）
  │
  ├── storage_type = NONE：
  │     ADHOC_RESULT_NO_RESULT（无结果集，DDL/DML）
  │
  ├── oss_upload_status = PENDING：
  │     ADHOC_RESULT_UPLOAD_PENDING（上传中，稍后重试）
  │
  └── oss_upload_status = FAILED：
        ADHOC_RESULT_UPLOAD_FAILED（上传失败，重试上限后）
```

### 6.2 日志读取

```
SDK 调 GET /api/adhoc/task/{taskId}/log?offset=N&limit=100
  │
  ▼ 任意 server A 接收
  │
  ▼ 查 task.executor_instance + persistent_log_path（logKey）
  │
  ├── Task 运行中 + executor.status=UP：
  │     gRPC FetchLog(executor, taskId, offset, limit)
  │     executor 读内存 task.log buffer 返回（实时，~100ms）
  ├── executor.status=DOWN 或 Task 终态：
  │     storageClient.download(logKey) 读存储快照
  │     （executor 宕机丢最后 ≤2s 日志，内存 buffer 未 flush 部分）
  └── 存储快照不存在（从未 flush）：ADHOC_LOG_INCOMPLETE
```

Job 日志读取同理，`log/{jobId}/job.log`（Job 终态一次性 uploadLog，运行中不可读）。

### 6.3 实时读取

日志实时读取走 gRPC `FetchLog`，executor 读内存 task.log buffer（`List<String>` per Task + `ReentrantLock`）返回（从 offset 行起）。

**实时性**：~100ms（executor 写 buffer + server gRPC 转发 + 网络往返），无需页缓存/文件系统。

**日志存储快照**：executor 定期(2s)把 buffer 全量序列化 `storageClient.uploadLog` 覆盖 logKey 快照；Task 终态最后 flush 一次完整日志。executor 宕机时 server 读存储快照（丢最后 ≤2s）。

**结果实时性**：结果写完存储即可读（`storage_type=PERSISTENT` 且 `oss_upload_status=SUCCESS`），不支持写入过程中 streaming 读（一次性上传，无中间态）。读取时若 `oss_upload_status=PENDING`，返回 ADHOC_RESULT_UPLOAD_PENDING。

## 7. TTL 清理

### 7.1 清理策略

| 路径 | TTL | 清理方 | 清理方式 |
|---|---|---|---|
| 存储 `result/{jobId}/{taskId}/result.part-0` | 30 天 | 后端实现 | local：自扫描 baseDir/result/ 过期文件；aliyun：桶生命周期规则 |
| 存储 `log/{jobId}/{taskId}/task.log` | 30 天 | 后端实现 | 同上 |
| executor 本地 WAL `wal/{taskId}.json` | 提交成功后立即删除 | executor 后台线程 | WAL 提交成功后立即 unlink |

### 7.2 存储对象 TTL

存储对象 TTL 由后端实现控制：

- **local 实现**：server 后台线程定期扫描 baseDir 下 result/ 与 log/ 目录，清理 N 天前的文件
- **aliyun 实现**：依赖对象存储桶生命周期规则（lifecycle rule），到期自动删除对象

```
local 实现：
  server 后台线程扫描 baseDir/result/、baseDir/log/
  删除 mtime > retentionDays 的文件

aliyun 实现：
  桶配置 lifecycle rule，prefix=result/ 与 prefix=log/，到期自动删除
```

**注意**：日志定期(2s)覆盖 uploadLog，同 fileName 覆盖写快照；TTL 按首次上传时间计算（覆盖不重置 TTL）。

### 7.3 WAL 清理

```
executor 后台线程（每 30s 重试 WAL）
  │
  ▼ 扫描 wal/
  │
  ▼ 对每个 {taskId}.json：
  │   ├── 重试直写 DB（或 gRPC 接口转发）：成功 -> unlink
  │   └── 重试失败：保留，下次再试
  │
  ▼ executor 重启时优先提交 WAL
```

WAL 不设 TTL（理论上提交成功就删，失败则持续重试）。但 WAL 文件极小（~200 字节），即使积压也占用极小空间。WAL 是写库失败兜底，不是结果/日志（结果/日志在存储后端）。

### 7.4 DB 记录清理（30 天，配合存储 TTL）

存储对象由后端清理，DB 记录由 server 后台线程清理：

```
server 后台线程（每天凌晨 02:00 执行一次）
  │
  ▼ 计算 30 天前的日期：cutoff_day = today - 30d
  │
  ▼ 清理 DB（存储对象已由后端清理）：
  │   DELETE FROM adhoc_result_summary
  │   WHERE create_time < cutoff_day;
  │   DELETE FROM adhoc_query_task
  │   WHERE create_time < cutoff_day AND is_deleted=1;
  │   DELETE FROM adhoc_query_job
  │   WHERE submit_time < cutoff_day AND is_deleted=1;
```

### 7.5 异常清理

- **Task FAILED 且 result_status=INCOMPLETE**：结果未上传存储（序列化未完成或上传失败），无文件需清理
- **Task SUCCESS 但上传失败**：已走 WRITE_ERROR（fail_stage=OSS_UPLOAD），oss_upload_status=FAILED，无文件需清理
- **executor DOWN 后重启**：executor 不本地存储结果/日志，无本地文件扫描清理；仅提交 WAL（§7.3）

## 8. 与其他模块的接口

### 8.1 模块 1（任务调度与执行）

- executor 接收 dispatchJob 后初始化 job.log 内存 buffer
- executor 拆分 Job 后为每个 Task 初始化 task.log 内存 buffer
- executor 顺序执行 Task，序列化 result.part-0 + uploadResult，task.log buffer + 定期 flush 存储快照
- Job 终态后 uploadLog job.log

### 8.2 模块 4（结果存储）

- result.part-0 文件格式见 §3.1
- `result_summary.persistent_path` = `result/{jobId}/{taskId}/result.part-0`（存储 key）
- `storage_type` 2 态（NONE/PERSISTENT）转换由本模块的写入流程驱动（uploadResult 成功 -> PERSISTENT）

### 8.3 模块 5（高可用）

- executor DOWN 后，存储对象不清理（按 30 天 TTL 自然过期）
- executor 重启时不扫描本地文件（不本地存储结果/日志），仅提交 WAL
- server 端无状态，DOWN 后重启不影响存储上的文件

### 8.4 模块 9（执行日志）

- job.log / task.log 文件格式见 §3.3
- 日志内容见 §4
- 实时读取机制见 §6.3
- 日志读取路径见 §6.2

### 8.5 模块 6（可观测性）

- 监控指标：executor 磁盘水位（WAL 用）、存储上传成功率、WAL 积压数
- 审计：uploadResult/uploadLog 成功/失败、日志 flush 记 INFO 日志

## 9. 错误码

| 错误码 | 触发条件 |
|---|---|
| `ADHOC_RESULT_NO_RESULT` | 无结果数据（storage_type=NONE，DDL/DML） |
| `ADHOC_RESULT_INCOMPLETE` | 结果传输中断，不完整（executor 宕机时 result_status=WRITING 被标记为 INCOMPLETE） |
| `ADHOC_RESULT_UPLOAD_PENDING` | 上传进行中（oss_upload_status=PENDING，用户稍后重试） |
| `ADHOC_RESULT_UPLOAD_FAILED` | 上传失败（oss_upload_status=FAILED，重试上限后） |
| `ADHOC_LOG_INCOMPLETE` | 日志不完整（executor DOWN 且 task.log 从未 flush 存储） |
| `ADHOC_EXECUTOR_CRASHED` | executor 宕机，无法 gRPC FetchLog（fallback 读存储快照） |

## 10. 监控指标

| 指标 | 含义 |
|---|---|
| `adhoc_executor_wal_pending_count{executor}` | executor WAL 积压数（gauge） |
| `adhoc_executor_wal_submit_total{result=success/failure}` | WAL 提交次数（counter） |
| `adhoc_executor_disk_usage{executor}` | executor 磁盘水位（gauge，0-1，WAL 用） |
| `adhoc_storage_upload_total{type=result/log,result=success/failure}` | 存储上传次数（counter） |
| `adhoc_storage_upload_duration_seconds{type=result/log}` | 存储上传耗时（histogram） |
| `adhoc_storage_download_total{type=result/log,result=success/failure}` | 存储下载次数（counter） |
| `adhoc_executor_log_flush_total{result=success/failure}` | 日志定期 flush 存储次数（counter） |

## 11. 配置项

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `adhoc.executor.wal-dir` | `./data/wal` | executor WAL 目录（本地） |
| `adhoc.executor.wal-retry-interval-seconds` | `30` | WAL 重试间隔 |
| `adhoc.log.flush-interval-ms` | `2000` | 日志存储快照 flush 间隔（2s） |
| `adhoc.log.flush-batch-lines` | `100` | 日志 flush 行数阈值 |
| `adhoc.storage.type` | `local` | 存储后端（local / aliyun） |
| `adhoc.storage.local.base-dir` | `./data/storage` | local 实现存储根目录 |
| `adhoc.storage.aliyun.endpoint` | （无默认） | s3 兼容端点 |
| `adhoc.storage.aliyun.bucket` | （无默认） | s3 桶名 |
| `adhoc.storage.db-cleanup-cron` | `0 0 2 * * ?` | DB 记录清理 cron（每天凌晨 2 点，配合存储 TTL） |
| `adhoc.result.magic` | `0xADH0C` | result.part-0 文件 MAGIC |
| `adhoc.result.max-rows` | `1000000` | 单 Task 结果最大行数（100w） |

**优先级**：Apollo > 环境变量 > 本地 yml > 代码默认值，无需重启即可生效（Apollo 推送）。

## 12. 验收标准

1. 存储 key 结构：`result/{jobId}/{taskId}/result.part-0` + `log/{jobId}/{taskId}/task.log` + `log/{jobId}/job.log`
2. executor 不写本地文件（结果/日志），存储是唯一持久化层（StorageClient SPI，local/aliyun 内置实现）
3. WAL 目录：`wal/{taskId}.json`，单文件约 200 字节（WAL 是写库失败兜底，非结果/日志）
4. result.part-0 文件格式：MAGIC (4B) + schema 行 + 数据行，长度前缀编码
5. schema JSON 存 result.part-0 文件头，不单独建表
6. job.log / task.log 纯文本格式，每行一条：`yyyy-MM-dd HH:mm:ss.SSS [LEVEL] [thread] message`
7. job.log 记录 session 创建、Job 拆分、Task 调度顺序、Task 状态变更、session 关闭
8. task.log 记录引擎连接、SQL 执行、结果拉取、引擎报错
9. server 端不创建 Job/Task 目录、不产生日志/结果文件
10. 前置校验失败直接返回错误给前端，不创建 Job 记录
11. server 读路径：结果 `storageClient.download` 直读存储（不转发 executor）；日志 gRPC FetchLog 转发 executor 或读存储快照
12. 日志实时读取：gRPC FetchLog 读 executor 内存 buffer，实时性 ~100ms
13. 日志存储快照：定期(2s) flush + 终态最后 flush；executor 宕机丢最后 ≤2s
14. WAL 提交成功后立即删除，executor 后台线程每 30s 重试
15. 存储对象 TTL 30 天：local 自扫描 / aliyun 桶生命周期规则
16. DB 记录清理：server 后台线程按 30 天 cron 清理（配合存储 TTL）
17. 结果 uploadResult 成功后 `storage_type` = `PERSISTENT`（两态 NONE/PERSISTENT）
18. 结果 uploadResult 失败 `storage_type` 保持 `NONE`，`oss_upload_status` = `FAILED`（重试上限后），Task FAILED（WRITE_ERROR，fail_stage=OSS_UPLOAD，无本地兜底）
19. StorageClient SPI：4 方法接口，内置 local/aliyun 两种实现，第三方可注册 Bean 覆盖
20. 无 server 端内存缓存目录、无 LogBuffer
