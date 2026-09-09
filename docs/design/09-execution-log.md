# 模块 9：执行日志

> 本模块负责 Job/Task 执行日志的采集、存储、查询，支持运行中实时查看和完成后历史查询。日志全在 executor 内存 buffer（实时读）+ 定期覆盖上传存储快照（近实时备份）+ Task 终态最后 flush 完整版，server 只做读路径转发。

## 1. 概述

### 1.1 模块职责

- **Job 执行日志（job.log）**：executor 产生，记录 session 创建、Job 拆分、Task 调度顺序、Task 状态变更、session 关闭
- **Task 执行日志（task.log）**：executor 产生，记录引擎连接、SQL 执行、结果拉取、引擎报错
- **日志存储**：executor 内存 buffer（实时读）+ 定期覆盖上传存储快照（近实时备份）+ Task 终态最后 flush 完整版，**不**写本地文件、**不**依赖 server 写文件
- **日志查询**：server 路由 FetchLog RPC 到 executor 读内存（运行中 + executor UP），executor DOWN 或终态时 fallback 存储快照
- **实时读取**：基于 executor 内存 buffer 读取（gRPC FetchLog），实时性 ~100ms

### 1.2 设计原则

- **内存 buffer 为主 + 定期存储快照为辅**：executor 写内存 buffer（`List<String>` per Task，`ReentrantLock` 保护）的同时，定时任务每 2s 或满 100 行把 buffer 全量序列化覆盖上传到存储（内置实现无 append API，用"定期覆盖上传"实现近实时快照）。若 executor 宕机，存储至少有最后一次 flush 的快照（丢最后 ≤2s），避免日志全丢
- **Task 终态最后 flush**：Task 终态后，executor 把内存 buffer 全量序列化最后 flush 一次到存储，确保存储是完整版（替换执行期间的定期快照）
- **日志全在 executor 内存**：实时读走内存 buffer（~100ms），存储仅作宕机 fallback + 终态归档
- **server 不产生日志文件**：前置校验失败直接返回错误给前端，不创建 Job 记录；Job 一旦创建，所有日志都在 executor 内存 + 存储
- **job.log / task.log 分离**：Job 级事件和 Task 级事件分别记录到不同 buffer，便于按需查询
- **内存 buffer 读**：写线程 `lock()` + `buffer.add(line)` + `unlock()`，读线程 `lock()` + 从 offset 行读取 + `unlock()`，ReentrantLock 保证线程安全，写后立即可见
- **offset 全局行号**：前端传 offset 增量拉取，支持多次刷新
- **executor DOWN fallback 存储快照**：日志已定期覆盖上传到存储则 fallback 读存储（可能丢最后 ≤2s），终态后最后 flush 则读完整版
- **不从 YARN/Spark History Server 聚合**：只走 JDBC 采集 + executor 自身事件

## 2. 数据模型

### 2.1 Task 表日志路径字段

Task 表日志路径字段：

| 字段 | 类型 | 设置方 | 设置时机 | 用途 |
|---|---|---|---|---|
| `persistent_log_path` | VARCHAR(256) | executor | 拆分 Task 时（与 enqueue_time 同时刻） | 存储上的 task.log key，**执行期间定期覆盖上传（近实时快照），终态后最后 flush 完整版**。executor 宕机后 server 按此 key 读存储快照（可能丢最后 ≤2s） |

**Job 表不新增日志路径字段**：job.log 在 Job 终态时一次性 uploadLog 到存储，key 从 `job_id` 推导：`log/{jobId}/job.log`。

### 2.2 日志存储结构（全部在存储后端，executor 不落本地）

**存储（StorageClient SPI，log/ 前缀）**：

```
log/
  └── {jobId}/
      ├── job.log                         ← Job 日志（Job 终态一次性 uploadLog）
      ├── {taskId1}/
      │   └── task.log                    ← Task 日志（定期覆盖 uploadLog + 终态 flush）
      ├── {taskId2}/
      │   └── task.log
      └── {taskId3}/
          └── task.log
```

- `{jobId}` = Job ID，按 Job 分子目录，一个 Job 的 job.log 和所有 Task 的 task.log 聚在一起
- `{taskIdN}` = Task ID，每个 Task 一个子目录，task.log 在其中（与 result.part-0 同目录，结果文件由模块 4 维护）
- job.log 仅 Job 终态时一次性 uploadLog（无定期 flush），task.log 执行期间定期覆盖 uploadLog（近实时快照）+ 终态最后 flush 完整版
- 存储对象 TTL 由后端实现控制（local 自扫描 / aliyun 桶生命周期规则，见模块 4 §14）

**executor 内存（不落盘）**：

```
executor 内存
  └── JobExecutionContext
      ├── jobLogBuffer: List<String>      ← Job 日志 buffer（ReentrantLock 保护）
      └── taskLogBuffers: Map<taskId, List<String>>  ← 每个 Task 一个 buffer（ReentrantLock 保护）
```

### 2.3 日志内容

| 文件 | 产生方 | 内容 |
|---|---|---|
| job.log | executor | session 创建、Job 拆分、Task 调度顺序、Task 状态变更、session 关闭 |
| task.log | executor | 引擎连接、SQL 执行、结果拉取、引擎报错 |

**server 端不产生日志文件**：前置校验失败直接返回错误给前端，不创建 Job 记录。

### 2.4 日志文件格式

纯文本，每行一条：

```
2026-07-20 10:00:05.123 [INFO] [executor-1] Connecting to Kyuubi jdbc:kyuubi://...
2026-07-20 10:00:10.456 [INFO] [executor-1] Executing SQL: SELECT * FROM ...
2026-07-20 10:00:30.789 [WARN] [executor-1] Slow query detected, elapsed=20s
2026-07-20 10:00:35.012 [INFO] [executor-1] Fetched 1000 rows
```

字段：`yyyy-MM-dd HH:mm:ss.SSS [LEVEL] [thread] message`

- `LEVEL`：INFO / WARN / ERROR / DEBUG（见 §8.2）
- `thread`：executor 内部线程名（如 `executor-1`、`log-collector-taskId`）
- `message`：自由文本，含事件类型 + 关键参数

## 3. Task 日志流程

### 3.1 日志产生（内存 buffer + 定期存储快照流程）

```
executor 收到 dispatchJob
  │
  ▼ 创建 Job 内存 buffer（List<String>）+ ReentrantLock
  │
  ▼ 拆分 Job，创建 Task 记录（直写 DB）
  │   每个 Task 创建内存 buffer（List<String>）+ ReentrantLock
  │   写 DB：task.persistent_log_path = logKey（此时即写入，定期覆盖上传启动即确定 key）
  │   logKey = log/{jobId}/{taskId}/task.log
  │
  ▼ 启动 Task 日志采集：
  │   for each task:
  │     【执行线程】写内存 buffer（lock + add + unlock）：
  │       [INFO] Connecting to Kyuubi jdbc:kyuubi://...
  │       [INFO] Executing SQL: SELECT * FROM ...
  │       [INFO] Fetched 1000 rows
  │       （同时 JDBC 日志采集线程并行写 buffer，见 §7）
  │
  │     【定期 flush 线程】每 adhoc.log.flush-interval-ms（默认 2000ms）或 buffer 行数阈值（adhoc.log.flush-batch-lines=100）：
  │       全量序列化 buffer（lock + 读全部 + unlock）-> byte[]
  │       storageClient.uploadLog(logKey, data)
  │       （内置实现同 fileName 覆盖上传，实现近实时快照，不是真 append）
  │       -> 存储始终有"近实时快照"的日志副本（延迟 ≤2s）
  │
  │     Task 终态时：
  │       1. 停止定期 flush 线程
  │       2. 最后 flush 一次（完整日志）-> storageClient.uploadLog 覆盖 logKey
  │       3. 上传成功后存储为完整版（与内存 buffer 一致）
  │
  ▼ Job 终态，关闭 session，清理 executor 内存里的 session 句柄 + job/task buffer
  │   （job.log 终态一次性 uploadLog 存储，无定期 flush；upload 后 job buffer 可清理）
```

executor 内部使用 SLF4J + 自定义 Appender，将 Task 执行相关日志写到 task 内存 buffer，将 Job 级事件写到 job 内存 buffer。Appender 按当前执行上下文的 taskId/jobId 路由到对应 buffer。

**内存 buffer + 存储快照关键点**：
- 内存 buffer 是主路径（实时读走内存），定期覆盖上传存储是备份路径（宕机 fallback）
- 定期 flush 线程独立于执行线程，不阻塞 SQL 执行
- 内置实现无 append API，用"定期覆盖上传同名 fileName"实现近实时快照（每次上传全量 buffer）
- 终态最后 flush 确保存储最终是完整版（替换执行期间的定期快照）

### 3.2 内存 buffer 读取（实时读取原理）

executor 写线程和 server 转发的 FetchLog 读线程并发访问同一个 `List<String>` buffer：

```
executor 写线程                              server 转发的 FetchLog 读线程
   │                                            │
   │  lock()                                    │  lock()
   │  buffer.add(line)                          │  read from offset（buffer.size()）
   │  unlock()                                  │  return lines + total_lines
   │                                            │  unlock()
   │                                            │
   │  写后立即可见（内存可见性由 lock 保证）     │  读到刚 add 的行
```

- `ReentrantLock` 保证写线程与读线程互斥访问 buffer
- 写线程 `add()` 后 `unlock()`，读线程下次 `lock()` 后立即可见（happens-before）
- 不依赖 OS 页缓存（无文件 IO），纯内存操作

**实时性**：~100ms（executor 写内存 + gRPC 转发读 + 网络往返）。

### 3.3 无本地文件

executor 不写本地结果/日志文件，持久化由存储后端承担（定期覆盖上传 + 终态 flush）：

- executor 写内存 buffer（无文件 IO，写性能可忽略）
- server 转发 FetchLog 直接读 executor 内存 buffer（无文件 IO，读性能可忽略）
- 不在 executor 本地维护 task.log / job.log 文件，不依赖 OS 页缓存

### 3.4 定期存储快照与终态 flush

#### 3.4.1 执行期间定期覆盖上传（近实时快照）

Task 执行期间，executor 启动独立的定期 flush 线程，每 2s 或满 100 行把内存 buffer 全量序列化覆盖上传到存储：

```java
// Task 开始时确定 logKey（写 DB：task.persistent_log_path）
String logKey = "log/" + jobId + "/" + taskId + "/task.log";

// 定期 flush 线程：全量序列化 buffer，覆盖上传到存储
while (!taskFinished) {
    Thread.sleep(flushIntervalMs);  // 默认 2000ms
    byte[] data;
    lock.lock();
    try {
        if (buffer.size() < flushBatchLines && !shouldFlush()) {
            continue;  // 未达阈值且未到间隔，跳过
        }
        data = serializeToBytes(new ArrayList<>(buffer));  // 全量快照
    } finally {
        lock.unlock();
    }
    // 覆盖上传（同 fileName 覆盖，实现近实时快照）
    storageClient.uploadLog(logKey, data);
}
```

**定期覆盖上传的意义**：
- executor 宕机时，存储有到宕机前 ≤2s 的日志快照（最后一次 flush）
- server 可直接读存储获取部分日志，避免全丢
- flush 线程不阻塞执行线程，不影响 SQL 执行性能
- 内置实现无 append API，用"覆盖上传同名 fileName"实现近实时快照（每次上传全量 buffer，不是增量 append）

#### 3.4.2 Task 终态最后 flush（完整版）

```
Task 终态（SUCCESS/FAILED/CANCELED）：
  │
  ▼ 1. 停止定期 flush 线程
  │
  ▼ 2. 最后 flush 一次（完整日志）-> storageClient.uploadLog 覆盖 logKey
  │   （覆盖 §3.4.1 的定期快照，确保存储是完整版）
  │
  ├── 上传成功：存储为完整版（与内存 buffer 一致）
  │   用户读取可 fallback 存储（executor DOWN 时），获得完整日志
  │
  └── 上传失败：存储仍是 §3.4.1 的定期快照（丢最后 ≤2s）
       内存 buffer 随 Task 上下文清理，用户读取返回部分日志 + ADHOC_LOG_PARTIAL 提示
       （executor 重启时无法补传--内存 buffer 已丢，仅存储快照可用）

Job 终态：
  │
  ▼ job.log 终态一次性 uploadLog 存储（无定期 flush）
       logKey = log/{jobId}/job.log
       （Job 级事件少，无需定期 flush；upload 后 job buffer 可清理）
```

**最后 flush 策略**：
- Task 终态时最后 flush 一次 task.log（替换定期快照）
- 上传失败重试（指数退避：1s, 2s, 4s, 8s, 16s, 30s，最多 6 次，总等待 ~60s）
- 重试上限后放弃，存储保持定期快照（丢最后 ≤2s），内存 buffer 随 Task 上下文清理
- job.log 终态一次性 uploadLog（无定期 flush）

#### 3.4.3 宕机场景日志可用性

| 场景 | 内存 buffer | 存储快照 | 用户可读性 |
|---|---|---|---|
| 执行中 executor 正常 | 实时完整 | 定期快照（延迟 ≤2s） | 读内存（实时，gRPC FetchLog） |
| 执行中 executor 宕机 | 丢失（内存不可达） | 定期快照（到宕机前 ≤2s） | 读存储（部分日志，返回 ADHOC_LOG_PARTIAL） |
| 终态后 executor 正常 | 完整（buffer 未清理前） | 完整（最后 flush 成功） | 读内存（优先，gRPC FetchLog） |
| 终态后 executor 宕机 | 丢失 | 完整（最后 flush 成功）或定期快照（flush 失败） | 读存储（完整或部分） |

## 4. Task 日志查询

### 4.1 查询 API

```
GET /api/adhoc/task/{taskId}/log?offset=N&limit=100
```

参数：
- `offset`：已消费的最后行号（全局行号），首次传 0
- `limit`：单次拉取行数上限，默认 100

返回：

```json
{
  "taskId": "abc123",
  "status": "RUNNING",
  "offset": 150,
  "lines": [
    "2026-07-20 10:00:05.123 [INFO] [executor-1] Connecting to Kyuubi jdbc:kyuubi://...",
    "2026-07-20 10:00:10.456 [INFO] [executor-1] Executing SQL: SELECT * FROM ...",
    "..."
  ],
  "finished": false,
  "source": "executor"
}
```

- `offset`：当前最新行号，前端下次传此值拉增量
- `finished`：Task 是否终态
- `source`：数据来源（`executor` = 转发 executor 读内存 buffer，`storage` = server 直接读存储快照）

### 4.2 读路径

```
SDK 调 GET /api/adhoc/task/{taskId}/log?offset=N&limit=100
  │
  ▼ 任意 server A 接收
  │
  ▼ 查 task.executor_instance + task.status + task.persistent_log_path
  │
  ├── task.status ∈ 运行态（PENDING/RUNNING）：
  │     ├── executor.status=UP：
  │     │     gRPC FetchLog(executor, taskId, offset, limit, is_job_log=false)
  │     │     executor 读内存 buffer 返回（lock + 从 offset 行开始读 + unlock）
  │     │     └── 返回 offset+1 ~ buffer 末尾的行（最多 limit 行）
  │     │
  │     └── executor.status=DOWN：
  │           ├── task.persistent_log_path 非空（定期 flush 已启动）：
  │           │     server 直接读存储快照 storageClient.download(persistent_log_path)
  │           │     └── 返回定期快照日志（到宕机前 ≤2s），附 source=storage + finished=false
  │           │         （日志可能不完整，前端提示"executor 宕机，日志为存储快照可能丢最后 2s"）
  │           └── task.persistent_log_path 为空（极少见，Task 刚创建未启动定期 flush）：
  │                 返回 ADHOC_LOG_INCOMPLETE
  │
  └── task.status ∈ 终态（SUCCESS/FAILED/CANCELED）：
        ├── executor.status=UP：
        │     gRPC FetchLog(executor, taskId, offset, limit)
        │     executor 读内存 buffer 返回（buffer 未清理前优先读内存，减少存储 IO）
        └── executor.status=DOWN：
              └── server 直接读存储（终态时 task.log 已最后 flush 完整版）
                    storageClient.download(persistent_log_path)
                    log/{jobId}/{taskId}/task.log
                    （若最后 flush 失败，存储为定期快照，丢最后 ≤2s）
```

**转发跳数限 1**：server A 转发到 executor 后，executor 直接读内存 buffer 返回，不再二次转发。

**gRPC 短超时**：转发调用超时 200ms，超时后 fallback 存储快照（如果 persistent_log_path 非空）。

### 4.3 运行中查询（转发 executor 或 fallback 存储）

Task 状态为 PENDING/RUNNING 时：

1. server 收到查询请求
2. 查 task.executor_instance，gRPC 调对应 executor 的 FetchLog
3. executor `lock()` 读内存 buffer，从 offset 行起读最多 limit 行，`unlock()` 返回
4. 返回行 + 当前 buffer 总行数作为新 offset

**executor DOWN 时**（存储快照保底）：
- task.persistent_log_path 非空（定期 flush 已启动）：server 直接读存储快照 `storageClient.download(persistent_log_path)`，返回定期快照日志（到宕机前 ≤2s），`source=storage`，前端提示"executor 宕机，日志为存储快照可能丢最后 2s"
- task.persistent_log_path 为空（极少见，Task 刚创建未启动定期 flush）：返回 `ADHOC_LOG_INCOMPLETE`

**offset 语义**：
- 前端传 offset=100
- executor 读 buffer 第 101 行起到末尾，最多 limit 行
- 返回新行 + 新 offset（已读到的最后行号）
- 若 offset >= buffer 总行数，返回空列表 + 原 offset

### 4.4 已完成查询（优先内存，fallback 存储）

Task 状态为 SUCCESS/FAILED/CANCELED 时：

1. server 优先尝试转发 executor（buffer 未清理前优先读内存，减少存储 IO）
2. executor DOWN 或读失败：server 直接读存储 `storageClient.download(persistent_log_path)`（终态时已最后 flush 完整版）
3. 跳过前 offset 行，返回剩余行（最多 limit 行）

**offset 语义**：
- 前端传 offset=100
- 存储快照共 500 行
- 返回 101-500 行（最多 limit 行）+ 新 offset
- 支持任意时刻补查全量（offset=0 返回从头开始）

### 4.5 executor 与存储切换判断

server 根据 task.status + executor.status + task.persistent_log_path 判断数据来源：

| task.status | executor.status | persistent_log_path | 数据来源 |
|---|---|---|---|
| 运行态 | UP | （不关心） | 转发 executor 读内存 buffer（实时） |
| 运行态 | DOWN | 非空 | server 读存储快照（定期快照，丢最后 ≤2s） |
| 运行态 | DOWN | 空 | ADHOC_LOG_INCOMPLETE（定期 flush 未启动） |
| 终态 | UP | （不关心） | 转发 executor 读内存 buffer（未清理前优先） |
| 终态 | DOWN | 非空 | server 读存储（最后 flush 完整版，或定期快照） |
| 终态 | DOWN | 空 | ADHOC_LOG_INCOMPLETE（未启动 flush，极少见） |

## 5. Job 日志流程

### 5.1 Job 事件类型

Job 级事件少（每个 Job 几十条），executor 写到内存 job buffer。

| 事件 | 日志行示例 |
|---|---|
| SESSION_CREATE | `2026-07-20 10:00:00.000 [INFO] [executor-1] Session created for job=job_abc, engine=KYUUBI` |
| SPLIT | `2026-07-20 10:00:00.050 [INFO] [executor-1] Job split into 1 task, task_ids=[abc123]` |
| TASK_DISPATCHED | `2026-07-20 10:00:01.012 [INFO] [executor-1] Task abc123 started (segment_index=0)` |
| TASK_COMPLETE | `2026-07-20 10:00:10.000 [INFO] [executor-1] Task abc123 completed, duration=9000ms` |
| TASK_FAIL | `2026-07-20 10:00:10.000 [ERROR] [executor-1] Task abc123 failed, fail_stage=RUNNING` |
| COMPLETE | `2026-07-20 10:00:10.500 [INFO] [executor-1] Job completed, total_duration=10500ms` |
| FAIL | `2026-07-20 10:00:10.500 [ERROR] [executor-1] Job failed, reason=task_abc_failed` |
| CANCEL | `2026-07-20 10:00:10.500 [INFO] [executor-1] Job canceled, by=zhangsan` |
| SESSION_CLOSE | `2026-07-20 10:00:10.600 [INFO] [executor-1] Session closed for job=job_abc` |

### 5.2 Job 日志查询

```
GET /api/adhoc/job/{jobId}/log?offset=N&limit=100
```

读路径同 Task 日志（§4.2），路由到 `job.executor_instance`：

```
SDK 调 GET /api/adhoc/job/{jobId}/log?offset=N&limit=100
  │
  ▼ 任意 server A 接收
  │
  ▼ 查 job.executor_instance + job.status
  │
  ├── job.status ∈ 运行态：
  │     ├── executor.status=UP：gRPC FetchLog(executor, jobId, offset, limit, is_job_log=true)
  │     │     executor 读内存 job buffer 返回
  │     └── executor.status=DOWN：返回 ADHOC_LOG_INCOMPLETE（job.log 终态前未 upload 存储）
  │
  └── job.status ∈ 终态：
        ├── executor.status=UP：转发 executor 读内存 job buffer
        └── executor.status=DOWN：server 读存储 log/{jobId}/job.log
              （Job 终态时一次性 uploadLog 存储）
```

返回结构同 Task 日志查询（§4.1）。

## 6. gRPC 接口

### 6.1 FetchLog（server -> executor）

server 转发 SDK 的日志查询请求到 executor，executor 读内存 buffer 返回：

```protobuf
service DataFetcher {
  rpc fetchLog(FetchLogRequest) returns (FetchLogResponse);
}

message FetchLogRequest {
  string target_id = 1;       // task_id 或 job_id
  int32 offset = 2;           // 已读的最后行号，从 offset+1 行开始返回
  int32 limit = 3;            // 单次拉取行数上限
  bool is_job_log = 4;        // true=读 job buffer, false=读 task buffer
  string trace_id = 5;        // 链路追踪 ID
}

message FetchLogResponse {
  bool hit = 1;               // 内存 buffer 是否命中（false=buffer 不存在/已清理）
  repeated string lines = 2;  // 日志行
  int64 total_lines = 3;      // buffer 总行数（用于推算新 offset）
  bool finished = 4;          // Task/Job 是否终态
}
```

**executor 端处理**：

```java
public FetchLogResponse fetchLog(FetchLogRequest req) {
    List<String> buffer = getLogBuffer(req.getTargetId(), req.getIsJobLog());
    // buffer = JobExecutionContext 里的 jobLogBuffer 或 taskLogBuffers.get(taskId)

    if (buffer == null) {
        // buffer 不存在（Task/Job 未在本 executor）或已清理（终态后清理）
        return FetchLogResponse.newBuilder().setHit(false).build();
    }

    lock.lock();
    try {
        // 从 offset 行开始读（按行计数），最多 limit 行
        List<String> lines = readLines(buffer, req.getOffset(), req.getLimit());
        return FetchLogResponse.newBuilder()
            .setHit(true)
            .addAllLines(lines)
            .setTotalLines(buffer.size())
            .setFinished(isTerminal(req.getTargetId(), req.getIsJobLog()))
            .build();
    } finally {
        lock.unlock();
    }
}
```

### 6.2 去掉的 RPC

原设计的 `reportLog`（executor -> server 推送日志行）**移除**。日志不再上报，server 通过 FetchLog 拉取。

原设计的 `FetchResult`（server -> executor 转发读结果）**移除**（见模块 4，server 直读存储结果，不再转发 executor）。

## 7. JDBC 日志采集

### 7.1 QueryLogIterator SPI

```java
public interface QueryLogIterator {
    boolean hasNext();
    String next();
    List<String> fetchLastLogs();
}
```

### 7.2 引擎实现

| 引擎 | 实现 | 采集方式 |
|---|---|---|
| KYUUBI | KyuubiQueryLogIterator | `Connection.unwrap(HiveConnection.class).getOperationLog()`（Kyuubi JDBC 兼容 Hive 接口，需 session 设 `hive.server2.logging.operation.level=EXECUTION`） |
| STARROCKS | StarRocksQueryLogIterator | `Statement.getWarnings()` 轮询（无操作日志流） |

### 7.3 采集流程

executor 收到 Task 后，`Statement.execute(sql)` 之前启动独立线程 `LogCollectorRunner`，采集 JDBC 引擎日志**直接写入内存 task buffer**（不再通过 gRPC 上报 server，不再写本地文件）：

```java
private class LogCollectorRunner implements Runnable {
    private final Statement statement;
    private final String taskId;
    private final QueryLogIterator iterator;
    private final TaskLogBuffer buffer;  // 写入内存 task buffer（List<String> + ReentrantLock）

    public void run() {
        try {
            while (iterator.hasNext()) {
                String line = iterator.next();
                if (line != null) {
                    buffer.append("[JDBC_LOG] " + line);
                    // buffer 内部 lock() + add() + unlock()
                }
            }
            for (String line : iterator.fetchLastLogs()) {
                buffer.append("[JDBC_LOG] " + line);
            }
        } catch (Exception e) {
            buffer.append("[ERROR] [log-collector] collect log error: " + e.getMessage());
        }
    }
}
```

JDBC 日志与 executor 自身事件日志合并到同一个 task 内存 buffer，按时间排序（写线程串行 add，时间顺序天然一致）。定期 flush 线程会把合并后的 buffer 全量覆盖上传到存储（见 §3.4.1）。

**不从 YARN / Spark History Server 聚合**：只走 JDBC 采集。

## 8. 日志格式

### 8.1 统一行格式

```
yyyy-MM-dd HH:mm:ss.SSS [LEVEL] [thread] message
```

示例：
```
2026-07-20 10:00:05.123 [INFO] [executor-1] Connecting to Kyuubi jdbc:kyuubi://...
2026-07-20 10:00:10.456 [INFO] [executor-1] Executing SQL: SELECT * FROM ...
2026-07-20 10:00:30.789 [WARN] [executor-1] Slow query detected, elapsed=20s
2026-07-20 10:00:35.012 [INFO] [executor-1] Fetched 1000 rows
2026-07-20 10:00:35.500 [ERROR] [executor-1] storage flush failed, retry 1/6
```

### 8.2 日志级别

| 级别 | 用途 | 示例 |
|---|---|---|
| INFO | 正常流程（session 创建、SQL 执行、结果拉取） | `[INFO] [executor-1] Executing SQL: SELECT ...` |
| WARN | 慢查询、重试、资源紧张 | `[WARN] [executor-1] Slow query detected, elapsed=20s` |
| ERROR | 引擎报错、上传失败、写库失败 | `[ERROR] [executor-1] storage upload failed: Connection refused` |
| DEBUG | 调试信息（默认关闭） | `[DEBUG] [executor-1] Buffer size=100, triggering flush` |

### 8.3 日志采样

- **慢查询日志**：elapsed > 10s 单独标记 `[WARN] Slow query detected, elapsed=Xs`
- **错误日志**：ERROR 级别日志上报到集中式日志系统（ELK/Loki），便于运维监控
- **正常 INFO 日志**：只在 executor 内存 buffer + 存储，不上报集中式日志系统（避免日志量爆炸）

### 8.4 事件类型

executor 写入日志的 message 字段包含事件类型 + 关键参数，常见事件类型：

**Job 级事件**（写入 job 内存 buffer）：

| 事件 | 触发点 | 示例 |
|---|---|---|
| SESSION_CREATE | session 创建 | `[INFO] Session created for job=job_abc, engine=KYUUBI` |
| SPLIT | Job 拆分完成 | `[INFO] Job split into 2 tasks, task_ids=[abc123, def456]` |
| TASK_DISPATCHED | Task 开始执行 | `[INFO] Task abc123 started (segment_index=0)` |
| TASK_COMPLETE | Task 成功 | `[INFO] Task abc123 completed, duration=9000ms` |
| TASK_FAIL | Task 失败 | `[ERROR] Task abc123 failed, fail_stage=RUNNING` |
| COMPLETE | Job 成功 | `[INFO] Job completed, total_duration=10500ms` |
| FAIL | Job 失败 | `[ERROR] Job failed, reason=task_abc_failed` |
| CANCEL | Job 取消 | `[INFO] Job canceled, by=zhangsan` |
| SESSION_CLOSE | session 关闭 | `[INFO] Session closed for job=job_abc` |

**Task 级事件**（写入 task 内存 buffer）：

| 事件 | 触发点 | 示例 |
|---|---|---|
| CONNECT | 引擎连接 | `[INFO] Connecting to Kyuubi jdbc:kyuubi://...` |
| EXECUTE_SQL | SQL 执行 | `[INFO] Executing SQL: SELECT * FROM ...` |
| JDBC_LOG | 引擎日志（采集） | `[INFO] [JDBC_LOG] Starting SparkContext...` |
| FETCH_START | 开始拉取 | `[INFO] Fetching results...` |
| FETCH_DONE | 拉取完成 | `[INFO] Fetched 1000 rows, duration=1000ms` |
| SLOW_QUERY | 慢查询 | `[WARN] Slow query detected, elapsed=20s` |
| FLUSH_STORAGE | 定期 flush 存储 | `[INFO] Flushed task.log snapshot to storage` |
| UPLOAD_STORAGE | 终态 upload 存储 | `[INFO] Uploaded task.log to storage (final flush)` |
| ENGINE_ERROR | 引擎报错 | `[ERROR] Engine error: Permission denied: user=zhangsan` |
| UPLOAD_FAIL | 上传失败 | `[ERROR] storage upload failed, retry 1/6` |
| DB_WRITE_FAIL | 写库失败 | `[ERROR] DB write failed, falling back to WAL` |
| COMPLETE | Task 成功 | `[INFO] Task completed, total_duration=6500ms` |
| FAIL | Task 失败 | `[ERROR] Task failed, fail_stage=EXECUTING` |
| CANCEL | Task 取消 | `[INFO] Task canceled` |
| TIMEOUT | Task 超时 | `[WARN] Task timeout, timeout_seconds=300` |

### 8.5 完整 Task 日志示例

```
2026-07-20 10:00:01.012 [INFO] [executor-1] Task abc123 started (segment_index=0)
2026-07-20 10:00:01.100 [INFO] [executor-1] Connecting to Kyuubi jdbc:kyuubi://kyuubi-1:10009/...
2026-07-20 10:00:01.300 [INFO] [executor-1] Engine session ready, app_id=application_123
2026-07-20 10:00:01.500 [INFO] [executor-1] Executing SQL: SELECT * FROM db_ods.table_a WHERE dt='2026-07-01'
2026-07-20 10:00:02.500 [INFO] [log-collector-abc123] [JDBC_LOG] Starting SparkContext...
2026-07-20 10:00:03.100 [INFO] [log-collector-abc123] [JDBC_LOG] Submitting Tez DAG...
2026-07-20 10:00:05.000 [INFO] [executor-1] SQL executed, duration=3500ms
2026-07-20 10:00:05.100 [INFO] [executor-1] Fetching results...
2026-07-20 10:00:06.000 [INFO] [executor-1] Fetched 1000 rows, duration=900ms
2026-07-20 10:00:06.100 [INFO] [executor-1] Wrote result to storage result/job_abc/abc123/result.part-0
2026-07-20 10:00:06.500 [INFO] [log-flusher-abc123] Flushed task.log snapshot to storage log/job_abc/abc123/task.log
2026-07-20 10:00:06.600 [INFO] [executor-1] Uploaded task.log to storage (final flush)
2026-07-20 10:00:06.700 [INFO] [executor-1] Task completed, total_duration=5688ms
```

## 9. 清理与上限

| 项 | 限制 | 配置项 |
|---|---|---|
| 存储日志文件 TTL | 30 天（local 自扫描 / aliyun 桶生命周期规则） | `adhoc.result.retention-days=30` |
| 存储日志单文件大小 | 无硬上限（受 Task 执行时长限制） | - |
| 单次 FetchLog 行数上限 | 100 行 | `adhoc.log.fetch-limit=100` |
| FetchLog gRPC 超时 | 200ms | `adhoc.log.grpc-timeout-ms=200` |
| 终态最后 flush 重试次数 | 6 次（指数退避 1s/2s/4s/8s/16s/30s） | `adhoc.log.upload-retry=6` |
| 慢查询阈值 | 10s | `adhoc.log.slow-query-seconds=10` |
| 定期 flush 间隔 | 2s | `adhoc.log.flush-interval-ms=2000` |
| 定期 flush 批量行数阈值 | 100 行（攒够即 flush） | `adhoc.log.flush-batch-lines=100` |
| 定期 flush 线程池大小 | 每 executor 2 线程（避免阻塞执行） | `adhoc.log.flush-threads=2` |

**优先级**：Apollo > 环境变量 > 本地 yml > 代码默认值，无需重启即可生效（Apollo 推送）。

## 10. 与其他模块的接口

### 10.1 模块 1（任务调度与执行）

- executor 接收 dispatchJob 后创建 Job 内存 buffer（`List<String>` + `ReentrantLock`）
- executor 拆分 Job 创建 Task 时，为每个 Task 创建内存 buffer + 写 DB `task.persistent_log_path = logKey`（拆分时即写入，定期 flush 启动即确定 key）
- executor 执行 Task 时写 task 内存 buffer（INFO/WARN/ERROR）
- Task 终态时 executor 最后 flush 一次 task.log 到存储（替换定期快照）
- Job 终态时 executor 一次性 uploadLog job.log 到存储（无定期 flush）
- **task.executor_instance / job.executor_instance 字段是 FetchLog 路由键**

### 10.2 模块 5（高可用与补偿）

- **server 宕机不影响日志**：日志全在 executor 内存 buffer + 定期覆盖上传存储，server DOWN 期间 executor 继续写 buffer + 定期 flush + 终态最后 flush；server 恢复后正常响应 FetchLog 转发
- **executor 宕机**（存储快照保底）：
  - 内存 buffer 丢失（executor DOWN）
  - task.persistent_log_path 非空（定期 flush 已启动）：server 直接读存储快照 `storageClient.download(persistent_log_path)`，获得定期快照日志（到宕机前 ≤2s），返回 `ADHOC_LOG_PARTIAL` 提示
  - task.persistent_log_path 为空（极少见，Task 刚创建未启动定期 flush）：返回 `ADHOC_LOG_INCOMPLETE`
  - Task 终态后最后 flush 成功的：存储为完整版
- **executor 假 DOWN 恢复**：内存 buffer 仍在（未重启），恢复后继续定期 flush + 终态最后 flush（如果未完成）
- **executor 重启**：内存 buffer 已丢，仅存储快照可用（无法补传--内存 buffer 无法恢复）；对终态但最后 flush 未成功的 Task，存储保持定期快照（丢最后 ≤2s）

### 10.3 模块 6（可观测性）

- 执行日志存 executor 内存 buffer + 定期覆盖上传存储快照 + 终态最后 flush
- task 表保留时间戳 + fail_stage（终态摘要，快速定位）
- 日志详情通过 FetchLog RPC 读 executor 内存 buffer 或 fallback 存储快照（存储快照保底）
- 监控指标见 §12

### 10.4 模块 12（SDK / 内部 proto）

- **FetchLog RPC**（server -> executor，定义在 `adhoc-protocol/src/main/proto/data_fetcher.proto`）：
  - `fetchLog(FetchLogRequest)`：从 executor 内存 buffer 拉日志行
  - 详见模块 12
- **去掉的 RPC**：
  - `reportLog`（executor -> server 推送日志行）：移除，改为 server 拉取
  - `FetchResult`（server -> executor 转发读结果）：移除（见模块 4，server 直读存储结果）
- 转发调用携带 `trace_id` 透传，串联跨 server 链路

## 11. 错误码

| 错误码 | 触发条件 |
|---|---|
| `ADHOC_LOG_PERSISTENT_WRITE_FAILED` | executor 最后 flush 日志到存储失败（重试上限后） |
| `ADHOC_LOG_PERSISTENT_READ_FAILED` | server 读存储日志快照失败 |
| `ADHOC_LOG_FILE_NOT_FOUND` | Task/Job 终态但日志不可读（存储快照不存在 + executor 内存 buffer 已清理） |
| `ADHOC_LOG_INCOMPLETE` | 日志不可读：executor DOWN 且 task.persistent_log_path 为空（定期 flush 未启动） |
| `ADHOC_LOG_PARTIAL` | 日志为部分快照：executor DOWN，存储只有定期 flush 的快照（丢最后 ≤2s），非完整日志 |

## 12. 监控指标

| 指标 | 含义 |
|---|---|
| `adhoc_log_write_total{type=task/job}` | executor 写日志行数到内存 buffer（counter） |
| `adhoc_log_storage_flush_total{result=success/failed, kind=periodic/final}` | 存储覆盖上传次数（counter，定期快照 + 终态 flush） |
| `adhoc_log_storage_flush_duration_seconds` | 存储覆盖上传耗时（histogram） |
| `adhoc_log_storage_flush_lag_seconds` | 存储快照延迟（内存写 -> 存储上传的时间差，histogram） |
| `adhoc_log_storage_read_duration_seconds` | 存储快照读取耗时（histogram） |
| `adhoc_log_fetch_grpc_total` | gRPC FetchLog 调用次数（counter） |
| `adhoc_log_query_total{source=executor/storage}` | 日志查询次数按来源（counter） |
| `adhoc_log_partial_read_total` | 读到部分日志（ADHOC_LOG_PARTIAL）的次数（counter，executor 宕机触发） |
| `adhoc_log_slow_query_total` | 慢查询日志次数（counter，elapsed > 10s） |
| `adhoc_log_error_total` | ERROR 级别日志次数（counter） |

## 13. REST API 汇总

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/adhoc/task/{taskId}/log?offset=&limit=` | 查 Task 日志（运行中转发 executor 读内存，已完成读存储） |
| GET | `/api/adhoc/job/{jobId}/log?offset=&limit=` | 查 Job 日志（运行中转发 executor 读内存，已完成读存储） |

## 14. 验收标准

1. 日志内存 buffer + 存储快照：executor 内存 task buffer 实时写（`lock + add + unlock`）+ 定期覆盖上传到存储（近实时快照，2s 间隔 / 100 行批量），logKey = `log/{jobId}/{taskId}/task.log`
2. task 表含 `persistent_log_path` 单字段（存 log key），拆分 Task 时即写入（定期 flush 启动即确定 key），不等上传成功
3. 存储定期覆盖上传用 `storageClient.uploadLog`（内置实现同 fileName 覆盖，2s 间隔 / 100 行批量），存储始终有近实时快照副本
4. server 端**不**产生任何日志文件（前置校验失败直接返回错误给前端）
5. server 端**不**维护内存 LogBuffer（直接转发 executor 读内存 buffer 或 fallback 存储快照）
6. job.log 记录 session 创建/Job 拆分/Task 调度顺序/Task 状态变更/session 关闭
7. task.log 记录引擎连接/SQL 执行/结果拉取/引擎报错
8. 日志格式 `yyyy-MM-dd HH:mm:ss.SSS [LEVEL] [thread] message`，纯文本每行一条
9. 内存 buffer 读：executor 写线程 `lock() + buffer.add(line) + unlock()`，server 转发的 FetchLog 读线程 `lock() + 从 offset 行读 + unlock()`，ReentrantLock 保证 happens-before
10. 实时性 ~100ms（executor 写内存 + gRPC 转发读 + 网络往返）
11. Task 终态时 executor 最后 flush 一次完整 task.log 到存储（替换定期快照）；job.log 终态一次性 uploadLog 存储（无定期 flush）
12. 最后 flush 失败重试 6 次（指数退避），重试上限后存储保持定期快照（丢最后 ≤2s），内存 buffer 随 Task 上下文清理
13. 日志查询路径：SDK -> 任意 server -> 查 task.executor_instance/job.executor_instance + persistent_log_path -> gRPC FetchLog 转发 executor 读内存或直接读存储快照
14. executor DOWN 时：persistent_log_path 非空则读存储快照（运行态返回 ADHOC_LOG_PARTIAL 定期快照丢最后 ≤2s，终态返回完整版或定期快照）；persistent_log_path 为空返回 ADHOC_LOG_INCOMPLETE
15. FetchLog RPC 定义 `FetchLogRequest{target_id, offset, limit, is_job_log, trace_id}` 和 `FetchLogResponse{hit, lines, total_lines, finished}`
16. 原设计的 `reportLog` RPC（executor -> server 推送日志）**移除**，改为 server 拉取
17. 原设计的 `FetchResult` RPC（server -> executor 转发读结果）**移除**（见模块 4，server 直读存储结果）
18. JDBC 日志采集直接写入 executor 内存 task buffer（不再通过 gRPC 上报 server，不再写本地文件）
19. JDBC 日志与 executor 自身事件合并到同一个 task 内存 buffer，按时间排序
20. 日志级别：INFO（正常）/ WARN（慢查询、重试）/ ERROR（引擎报错、上传失败）/ DEBUG（默认关闭）
21. 慢查询日志（elapsed > 10s）单独标记 WARN 级别
22. ERROR 级别日志上报到集中式日志系统（ELK/Loki），正常 INFO 日志只在 executor 内存 + 存储
23. JDBC 日志只从 JDBC 采集（getOperationLog / getWarnings），不从 YARN/Spark History Server 聚合
24. 存储日志对象 TTL 30 天（local 自扫描 / aliyun 桶生命周期规则）
25. 定期 flush 线程独立于执行线程（2 线程/executor），不阻塞 SQL 执行
26. executor 宕机后存储至少有到宕机前 ≤2s 的日志（定期快照），避免日志全丢
