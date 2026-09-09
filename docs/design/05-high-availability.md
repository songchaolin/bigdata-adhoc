# 模块 5：高可用与补偿

> 本模块负责对等集群管理、心跳监控、宕机判定、executor 宕机探查与补偿、executor 写库失败处理、server 宕机补偿、过期实例清理、优雅上下线，保障平台在实例宕机时任务不卡死、不悬挂，且 server 宕机不影响 RUNNING Job。

## 1. 概述

### 1.1 模块职责

- **对等集群**：server/executor 多实例无主从，CAS 抢占避免单点
- **心跳监控**：executor 5s 上报 + 30s 超时判定 DOWN；server 5s 上报 + 15s 超时判定 DOWN
- **健康检查**：去中心化全量扫描，CAS 标记 DOWN
- **executor 宕机探查与补偿**：心跳超时 -> CAS 标记 DOWN -> RUNNING/DISPATCHED Task 标记 FAILED -> result_summary 处理 -> session 模式下 PENDING Task 跳过 -> Job 状态重算
- **executor 写库失败处理**：L1 直写 -> L2 重试 -> L3 接口转发 -> L4 本地 WAL + 心跳对账兜底
- **server 宕机补偿**：PENDING Job 接管重新调度；RUNNING Job 不受影响（session 在 executor）
- **过期实例清理**：DOWN 24h 后物理删除
- **优雅上下线**：drain -> grace -> DOWN

### 1.2 设计原则

- **session 绑定 executor（核心）**：session 在 executor 内存维护，server 宕机不影响 RUNNING Job；只有 executor 宕机 session 才丢失，Job 才 FAILED。这是健壮性的核心提升。
- **无主从**：CAS 抢占解决"多实例不重复处理"，心跳超时解决"故障发现"
- **executor 直写 DB**：executor 执行成功后直接 UPDATE task.status + INSERT result_summary，不依赖 server 转发。server 不接触数据。
- **四层写库保障**：L1 直写 -> L2 重试（指数退避：1s/2s/4s，3 次）-> L3 接口转发（gRPC server.reportTaskStatus，server 代写）-> L4 本地 WAL + 心跳对账兜底
- **心跳对账兜底**：executor 心跳带上 running_tasks 列表（task_id + status + stage），server 收到心跳后对账，executor 写库失败时由心跳校正状态
- **Task status 判补偿范围**：executor 宕机时 RUNNING/DISPATCHED Task 标记 EXECUTOR_CRASHED；session 模式下 PENDING Task 标记 SKIPPED_DUE_TO_SESSION_LOSS；终态 Task 不动
- **executor 宕机不重试**：session 丢失、执行上下文丢失，重试无意义，直接 FAILED
- **终态保护**：补偿只处理非终态 Task，终态 Task 不动（避免状态回退）；假 DOWN 恢复后也不恢复已 FAILED 的 Task
- **故障发现靠心跳**：不引入主动探活，依赖心跳超时（executor 30s / server 15s），简单可靠

## 2. 对等集群

### 2.1 无主从设计

- **所有 server 平等**：每个 server 独立运行 worker 抢占 PENDING Job
- **CAS 抢占**：`UPDATE ... WHERE status='PENDING'` 原子操作，多 server 同时拉同一 Job 只有一个成功
- **无本地任务状态**：所有状态在 MySQL，任意 server 可查询、取消、读结果
- **故障接管**：一个 server 宕机，被它处理的 PENDING Job 由其他 server 通过超时补偿接管重新调度；RUNNING Job 已在 executor 上执行，不受影响

### 2.2 为什么不要 leader 选举

- 复杂度高：raft/Paxos 引入共识算法，运维成本大
- 不必要：CAS 抢占已解决"多 server 不重复处理"，心跳超时已解决"故障发现"
- 代价小：多 server 各自全量扫描实例表，表数据量小（实例数有限），QPS 可控

## 3. 实例表

### 3.1 adhoc_server_instance

| 字段 | 类型 | 说明 |
|---|---|---|
| id | bigint PK | 自增 |
| instance_id | varchar(64) | 实例标识 `ip:port`，唯一 |
| host | varchar(64) | IP |
| http_port | int | HTTP 端口 |
| status | varchar(16) | UP / DOWN / UNKNOWN |
| accepting | tinyint | 是否承接新任务（优雅下线用） |
| start_time | datetime | 启动时间 |
| version | varchar(32) | 版本号 |
| heartbeat_time | datetime | 最后心跳时间 |
| last_down_time | datetime | 最近一次标记 DOWN 的时间 |
| active_jobs | int | 当前处理 Job 数 |
| active_tasks | int | 当前处理 Task 数 |
| create_time | datetime | 创建时间 |
| update_time | datetime | 更新时间 |

索引：`uk_instance_id`（instance_id 唯一）

### 3.2 adhoc_executor_instance

| 字段 | 类型 | 说明 |
|---|---|---|
| id | bigint PK | 自增 |
| instance_id | varchar(64) | 实例标识 `ip:port`，唯一 |
| host | varchar(64) | IP |
| grpc_port | int | gRPC 端口 |
| status | varchar(16) | UP / DOWN / UNKNOWN |
| accepting | tinyint | 是否承接新任务 |
| start_time | datetime | 启动时间 |
| version | varchar(32) | 版本号 |
| heartbeat_time | datetime | 最后心跳时间 |
| last_down_time | datetime | 最近一次标记 DOWN 的时间 |
| current_running | int | 当前运行 Task 数 |
| current_queued | int | 本地队列等待数 |
| cpu_usage | double | JVM 进程 CPU 使用率（心跳上报） |
| mem_usage | double | JVM 堆使用率（心跳上报） |
| disk_usage | double | 数据盘使用率（心跳上报，load_score 依据） |
| running_tasks | text | 当前在执行的 Task 列表（JSON，心跳上报，用于 server 对账） |
| system_cpu_usage_pct | double | 系统整体 CPU |
| memory_used_mb | bigint | JVM 堆使用 MB |
| memory_max_mb | bigint | JVM 堆最大 MB |
| thread_count | int | 线程数 |
| last_gc_pause_ms | bigint | 最近 GC 暂停 |
| load_score | double | 综合负载评分 |
| create_time | datetime | 创建时间 |
| update_time | datetime | 更新时间 |

索引：`uk_instance_id`（instance_id 唯一）

### 3.3 自注册

server/executor 启动时向实例表 UPSERT：

```sql
INSERT INTO adhoc_executor_instance
  (instance_id, host, grpc_port, status, accepting, start_time, version,
   heartbeat_time, create_time, update_time)
VALUES (?, ?, ?, 'UP', 1, NOW(), ?, NOW(), NOW(), NOW())
ON DUPLICATE KEY UPDATE
  host = VALUES(host),
  grpc_port = VALUES(grpc_port),
  status = 'UP',
  accepting = 1,
  start_time = VALUES(start_time),
  version = VALUES(version),
  heartbeat_time = NOW(),
  update_time = NOW();
```

### 3.4 容器化 vs 物理部署

| 部署形态 | instance_id 行为 | 旧记录处理 |
|---|---|---|
| 物理部署 | 重启后 IP 不变，instance_id 不变，UPSERT 覆盖 | 旧记录被刷新 |
| 容器化部署 | 重启后 Pod IP 变化，instance_id 是新值，INSERT 新记录 | 旧记录靠心跳超时 DOWN，24h 后清理 |

容器化和物理部署用同一套代码。

## 4. 心跳机制

### 4.1 心跳参数

| 参数 | 默认值 | 配置项 |
|---|---|---|
| executor 心跳频率 | 5 秒 | `adhoc.instance.executor.heartbeat-interval-seconds=5` |
| executor 超时阈值 | 30 秒（6 次未到） | `adhoc.instance.executor.heartbeat-timeout-seconds=30` |
| server 心跳频率 | 5 秒 | `adhoc.instance.server.heartbeat-interval-seconds=5` |
| server 超时阈值 | 15 秒（3 次未到） | `adhoc.instance.server.heartbeat-timeout-seconds=15` |

executor 超时阈值 30s（6 次心跳未收到），容忍长 GC 和网络抖动，减少"假 DOWN"。

### 4.2 心跳内容

- **server**：heartbeat_time + active_jobs + active_tasks
- **executor**：heartbeat_time + cpu_usage + mem_usage + disk_usage + running_tasks（JSON 数组，含 task_id/status/stage）+ current_running + current_queued

executor 心跳 SQL：

```sql
UPDATE adhoc_executor_instance
SET heartbeat_time = NOW(),
    status = 'UP',
    cpu_usage = ?,
    mem_usage = ?,
    disk_usage = ?,
    running_tasks = ?,  -- 当前在执行的 Task 列表（JSON）
    current_running = ?,
    current_queued = ?,
    update_time = NOW()
WHERE instance_id = ?;
```

心跳带上 running_tasks 列表，用于 server 对账（见 §8.2）。

### 4.3 执行方

server/executor 各自的后台心跳线程，单线程调度，不并发。每 5 秒 update 自己的实例表行。

### 4.4 故障发现延迟

- executor 心跳 5s 一次，超时 30s（6 次未到）；HA 扫描 10s 一次
- 最坏情况：executor 在心跳后立即宕机 -> 30s 后超时 -> 10s 内 HA 扫描发现 -> 总延迟 ≤40s
- 心跳对账补充：executor 短暂重启（< 30s）时，重启后第一次心跳即可被对账发现 TASK_LOST，5s 内校正
- 一期接受此延迟，不引入主动探活

## 5. 健康检查

### 5.1 全量扫描

每个 server 都运行后台 HA 补偿线程，全量扫描两张实例表（不分片）：

```sql
-- executor 表（30s 超时）
SELECT instance_id FROM adhoc_executor_instance
WHERE status = 'UP'
  AND heartbeat_time < NOW() - INTERVAL 30 SECOND;

-- server 表（15s 超时）
SELECT instance_id FROM adhoc_server_instance
WHERE status = 'UP'
  AND heartbeat_time < NOW() - INTERVAL 15 SECOND;
```

扫描频率 10 秒一次。多 server 部署时任一 server 都能跑，CAS 标记 DOWN 保证只有一个成功。

### 5.2 为什么不分片

- 实例表数据量小（server 个位数、executor 几十个），全量扫开销低
- 分片逻辑（hash % N）增加运维复杂度（server 数变化时分片重算）
- 多 server 重复扫描无所谓，CAS 标记 DOWN 保证只有一个成功
- 简单优先

### 5.3 CAS 标记 DOWN

```sql
-- executor
UPDATE adhoc_executor_instance
SET status = 'DOWN',
    last_down_time = NOW(),
    update_time = NOW()
WHERE instance_id = ?
  AND status = 'UP';

-- server
UPDATE adhoc_server_instance
SET status = 'DOWN',
    last_down_time = NOW(),
    update_time = NOW()
WHERE instance_id = ?
  AND status = 'UP';
```

`WHERE status='UP'` 避免多 server 重复标记。标记 DOWN 后触发 §7（executor 宕机补偿）或 §9（server 宕机补偿）。

## 6. 宕机判定

### 6.1 状态语义

| status | 含义 |
|---|---|
| UP | 心跳正常，可承接任务 |
| DOWN | 心跳超时或显式停机，不承接新任务，已有任务按补偿流程处理 |
| UNKNOWN | 初始状态或重启中，不承接任务 |

### 6.2 网络分区处理

server/executor 与 MySQL 网络分区时心跳写不进去，会被其他实例健康检查线程误判 DOWN。

- **一期简化**：直接判定 DOWN，依赖任务补偿逻辑兜底
- **心跳对账兜底**：executor 写库失败时，L4 WAL + 心跳对账机制（见 §8）确保状态最终一致
- **二期增强**：心跳线程写失败时本地缓存最近 3 次心跳，网络恢复后批量补写；健康检查判定 DOWN 前可选 gRPC 探活确认

## 7. executor 宕机探查与处理

### 7.1 存活检测：心跳

executor 每 5s 更新心跳，心跳带上当前在执行的 Task 列表（running_tasks，JSON 数组），用于 server 对账（见 §8.2）。

```sql
UPDATE adhoc_executor_instance
SET heartbeat_time = NOW(),
    status = 'UP',
    cpu_usage = ?,
    mem_usage = ?,
    disk_usage = ?,
    running_tasks = ?,
    current_running = ?,
    current_queued = ?,
    update_time = NOW()
WHERE instance_id = ?;
```

### 7.2 server HA 补偿线程

server 后台线程（每 10s 扫描，多 server 部署时任一 server 都能跑）：

```
server HA 补偿线程（每 10s 扫描）
  │
  ▼ 1. 检测心跳超时的 executor
       SELECT instance_id FROM adhoc_executor_instance
       WHERE status = 'UP'
         AND heartbeat_time < NOW() - INTERVAL 30 SECOND;
  │
  ▼ 2. CAS 标记 executor DOWN（避免多 server 重复处理）
       UPDATE adhoc_executor_instance
       SET status = 'DOWN', last_down_time = NOW(), update_time = NOW()
       WHERE instance_id = ? AND status = 'UP';
  │
  ▼ 3. 进入 Task 补偿流程（§7.3、§7.4、§7.5）
```

**心跳超时阈值 30s**（6 次心跳未收到），容忍长 GC 和网络抖动，减少"假 DOWN"。

### 7.3 Task 状态处理

对每个该 executor 上的 RUNNING/DISPATCHED Task：

```
对每个该 executor 上的 RUNNING/DISPATCHED Task：
  │
  ▼ CAS 标记 FAILED（保留最后 stage 到 fail_stage）
       UPDATE adhoc_query_task
       SET status = 'FAILED',
           fail_reason_category = 'EXECUTOR_CRASHED',
           fail_stage = stage,
           error_code = 'ADHOC_EXECUTOR_CRASHED',
           error_message = 'executor instance down during execution',
           finish_time = NOW(),
           update_time = NOW()
       WHERE query_id = ?
         AND status IN ('RUNNING', 'DISPATCHED');
```

CAS 条件 `status IN ('RUNNING', 'DISPATCHED')` 保证只更新非终态 Task，避免误改已 SUCCESS 的 Task。

### 7.4 result_summary 处理

对每个被标记 FAILED 的 Task，查 result_summary：

```
对每个被标记 FAILED 的 Task，查 result_summary：
  │
  ├── 无 summary（executor 还没来得及写元信息）：
  │     不创建 summary，用户读取返回 ADHOC_RESULT_NO_RESULT
  │
  ├── result_status = WRITING（结果不完整，存储未上传）：
  │     UPDATE result_summary
  │     SET result_status = 'INCOMPLETE'
  │     WHERE query_id = ?;
  │     用户读取返回 ADHOC_RESULT_INCOMPLETE
  │
  └── result_status = COMPLETE（结果序列化完成）：
        ├── 已上传存储（storage_type = PERSISTENT）：
        │     保持，用户读取直读存储（server 直读，不依赖 executor）
        └── 未上传存储（storage_type = NONE，上传未成功）：
              result_status -> INCOMPLETE，用户读取返回 ADHOC_RESULT_INCOMPLETE
```

### 7.5 session 模式下的 Job 级处理

session 绑定 executor，executor DOWN 意味着 session 丢失。session 模式下未开始的 PENDING Task 无法再执行（session 上下文无法重建），统一标记 SKIPPED_DUE_TO_SESSION_LOSS。

```
executor DOWN
  │
  ▼ 该 executor 上所有 RUNNING/DISPATCHED Task 处理完（-> FAILED，§7.3）
  │
  ▼ 扫描该 executor 关联的 Job（通过 executor_instance 字段）
       SELECT job_id FROM adhoc_query_job
       WHERE executor_instance = ?
         AND status = 'RUNNING';
  │
  ▼ 对每个 Job 的 PENDING Task（session 模式下未开始的 Task）：
       UPDATE adhoc_query_task
       SET status = 'FAILED',
           fail_reason_category = 'SKIPPED_DUE_TO_SESSION_LOSS',
           fail_stage = 'EXECUTING',
           error_code = 'ADHOC_SESSION_LOST',
           error_message = 'session lost due to executor down, subsequent tasks skipped',
           finish_time = NOW(),
           update_time = NOW()
       WHERE job_id = ?
         AND status = 'PENDING';
  │
  ▼ Job 状态重算
       ├── 有 Task SUCCESS：PARTIAL_FAILED
       └── 全部 FAILED：FAILED
```

### 7.6 executor 假 DOWN 的处理

executor 心跳超时（30s）但实际存活（如长 GC、瞬时网络抖动）：

```
executor 心跳超时（30s）但实际存活
  │
  ▼ server 标记 executor DOWN，Task FAILED（§7.3、§7.5）
  │
  ▼ executor 心跳恢复（下一次心跳 UPDATE 包含 status='UP'）
  │   自动将自身 status 设回 UP
  │
  ▼ executor 检测到自身从 DOWN 恢复（SELECT status 前后对比）：
  │
  ▼ 不恢复已 FAILED 的 Task（避免状态回退，用户可能已重试）
  │
  ▼ 重试未成功的存储上传（如果结果已序列化但 upload 失败，结果仍在内存）：
       重试 storageClient.uploadResult（实现内部重试 + executor 外层重试）
       成功后 UPDATE result_summary
       SET storage_type = 'PERSISTENT',
           oss_upload_status = 'SUCCESS',
           persistent_path = ?
       WHERE query_id = ?
         AND storage_type = 'NONE';
       （Task 状态不回退仍 FAILED，但结果可读直读存储）
  │
  ▼ 重新加入调度池，接收新 Task
```

**为什么假 DOWN 已 FAILED 的 Task 不恢复**：
- 用户可能已基于 FAILED 状态做了重试决策，恢复会造成状态混乱
- 终态保护原则：补偿只处理非终态 Task，终态 Task 不回退
- 假 DOWN 期间造成的"误 FAILED"由用户重试解决，不自动恢复

### 7.7 executor 重启后的处理

```
executor 启动
  │
  ▼ 恢复心跳（UPDATE status='UP', heartbeat_time=NOW()）
  │
  ▼ 不恢复已 FAILED 的 Task（用户可能已重试）
  │
  ▼ 提交本地 WAL（见 §8）
  │   扫描 wal/ 目录
  │   对每个 WAL 文件，按 §8.1 L1/L2/L3 流程重试
  │   （executor 不本地存储结果/日志，无本地文件扫描清理；结果/日志在内存 + 存储，
  │    重启后内存丢失 = 未上传的结果/日志丢失，由 server HA 标记 INCOMPLETE）
  │
  ▼ 接收新 Task（重新加入调度池）
```

### 7.8 为什么直接 FAILED 不重试

- executor 持有 Task 执行上下文（JDBC connection、引擎 session）
- executor 宕机 -> session 丢失，上下文无法恢复
- 可能有部分结果已上传存储，重试会重复执行
- Task -> FAILED，用户决定是否重跑

### 7.9 与 EXECUTOR_UNAVAILABLE 的区别

| 项 | EXECUTOR_CRASHED | EXECUTOR_UNAVAILABLE |
|---|---|---|
| 触发时机 | executor 跑到一半宕机 | server 调度 dispatchJob 时选不到可用 executor |
| Task 是否已开始 | 已开始（RUNNING/DISPATCHED） | 未开始（Job 还未 dispatch） |
| 处理方式 | Task 直接 FAILED | Job 走调度重试（换 executor） |
| 理由 | session 丢失、执行上下文丢失，重试无意义 | 还没执行，换 executor 重试可行 |

## 8. executor 写库失败处理

executor 执行成功后需要更新 task.status + result_summary。由于 executor 直写 DB（不依赖 server），需要兜底机制保证状态最终一致。

### 8.1 四层保障 + 心跳对账

```
executor 执行成功，需要更新 task.status + result_summary
  │
  ▼ L1：executor 直写 DB（UPDATE task.status + INSERT result_summary）
  │
  ├── 成功：完成
  │
  └── 失败（连不上 DB）：
       │
       ▼ L2：重试（指数退避：1s, 2s, 4s，3 次，总等待 ~7s）
       │
       ├── 重试成功：完成
       │
       └── 重试失败：
            │
            ▼ L3：接口转发，gRPC 调用 server.reportTaskStatus
            │     server 代为更新 DB
            │
            ├── 成功：executor 完成
            │
            └── 失败（server 也连不上 DB，或 gRPC 失败）：
                 │
                 ▼ L4：写本地 WAL
                 │   wal/{taskId}.json
                 │
                 ▼ executor 继续心跳（不停止）
                 │   心跳带 task 状态，server 对账兜底（§8.2）
                 │
                 ▼ 后台线程定期重试 WAL（每 30s）
                 │   ├── 成功：删除 WAL
                 │   └── 失败：保留 WAL
                 │
                 ▼ executor 重启时优先提交 WAL（见 §7.7）
```

### 8.2 心跳对账（关键机制）

executor 心跳带上当前在运行的 Task 状态：

```protobuf
message HeartbeatRequest {
  string instance_id = 1;
  int64 heartbeat_time = 2;
  double cpu_usage = 3;
  double mem_usage = 4;
  double disk_usage = 5;
  repeated TaskStatusReport running_tasks = 6;
}

message TaskStatusReport {
  string task_id = 1;
  string status = 2;   // RUNNING/SUCCESS/FAILED
  string stage = 3;    // EXECUTING/FETCHING/WRITING
}
```

server 收到心跳后对账：

```
server 收到 executor 心跳
  │
  ▼ 1. 更新 executor.heartbeat_time + 资源指标 + running_tasks
  │
  ▼ 2. 对账 Task 状态：
       db_running = SELECT * FROM adhoc_query_task
                    WHERE executor_instance = ? AND status = 'RUNNING'
       │
       for db_task in db_running:
         │
         ├── db_task.task_id 在 heartbeat.running_tasks 里：
         │   ├── 状态一致：无操作
         │   └── 状态不一致（DB=RUNNING，心跳=SUCCESS/FAILED）：
         │        UPDATE task.status = heartbeat 的状态
         │        （executor 写库失败，心跳对账兜底）
         │
         └── db_task.task_id 不在 heartbeat.running_tasks 里：
              # DB 认为在跑，但 executor 心跳里没有
              # Task 已不在 executor（重启/崩溃/异常退出）
              UPDATE adhoc_query_task
              SET status = 'FAILED',
                  fail_reason_category = 'TASK_LOST',
                  fail_stage = stage,
                  error_code = 'ADHOC_SESSION_LOST',
                  error_message = 'task lost from executor heartbeat',
                  finish_time = NOW(),
                  update_time = NOW()
              WHERE query_id = ?
                AND status = 'RUNNING';
```

### 8.3 心跳对账覆盖的场景

| 场景 | 对账行为 | 效果 |
|---|---|---|
| executor 短暂重启 | 重启后 running_tasks 为空，DB 的 RUNNING Task 不在心跳 | 标记 FAILED（TASK_LOST），5s 内发现 |
| executor 写库失败 | DB 状态停在 RUNNING，心跳带 SUCCESS | 心跳对账更新为 SUCCESS，5s 内校正 |
| executor 长时间宕机 | 心跳超时（30s） | 标记 executor DOWN，RUNNING Task FAILED（§7.3） |
| executor 假 DOWN | 心跳恢复，running_tasks 带 Task | 对账校正状态（不回退终态，已 FAILED 不恢复） |

### 8.4 L3 接口转发（reportTaskStatus RPC）

```protobuf
service TaskStatusService {
  rpc reportTaskStatus(ReportTaskStatusRequest) returns (ReportTaskStatusResponse);
}

message ReportTaskStatusRequest {
  string task_id = 1;
  string status = 2;
  string stage = 3;
  string fail_reason_category = 4;
  string error_code = 5;
  string error_message = 6;
  int64 finish_time = 7;
  ResultSummary result_summary = 8;
}

message ReportTaskStatusResponse {
  bool success = 1;
  string error_message = 2;
}

message ResultSummary {
  string query_id = 1;
  int64 result_rows = 2;
  int64 result_bytes = 3;
  string persistent_path = 4;    // 存储 key
  string storage_type = 5;       // NONE/PERSISTENT（两态）
  string result_status = 6;      // WRITING/COMPLETE/INCOMPLETE
  string oss_upload_status = 7;  // PENDING/SUCCESS/FAILED
}
```

**场景**：executor 能连 server（gRPC），但连不上 DB（MySQL 网络隔离）。executor 直写 DB 失败，但是 server 能写 DB。executor 通过 gRPC 把状态交给 server，server 代为更新。

### 8.5 WAL 的数据量

WAL 只存"状态变更事件"，不存结果数据（结果上传存储，不在本地）：

```json
{
  "task_id": "q-20260714-001",
  "status": "SUCCESS",
  "stage": "WRITING",
  "fail_reason_category": null,
  "error_code": null,
  "error_message": null,
  "finish_time": 1720934405000,
  "result_summary": {
    "query_id": "q-20260714-001",
    "result_rows": 1000,
    "result_bytes": 2048576,
    "persistent_path": "job-001/q-20260714-001/result.part-0",
    "storage_type": "PERSISTENT",
    "result_status": "COMPLETE",
    "oss_upload_status": "SUCCESS"
  }
}
```

单条 WAL 约 200 字节。executor 本地 WAL 目录最大容量可忽略（按 100 个并发 Task × 200B = 20KB 估算）。

## 9. server 宕机处理

### 9.1 server 之间互相心跳

```
每个 server 每 5s 更新自己的心跳：
UPDATE adhoc_server_instance
SET heartbeat_time = NOW(), status = 'UP', update_time = NOW()
WHERE instance_id = ?;

其他 server 的 HA 线程扫描（每 10s）：
SELECT instance_id FROM adhoc_server_instance
WHERE status = 'UP'
  AND heartbeat_time < NOW() - INTERVAL 15 SECOND;
```

server 超时阈值 15s（3 次未到），比 executor 严格（server 个数少，且 PENDING Job 接管需要尽快触发）。

### 9.2 Job 状态处理（核心）

server B 检测到 server A DOWN 后，扫描 server A 上的 Job：

```
server B 的 HA 线程：
  │
  ▼ 1. CAS 标记 server A DOWN
       UPDATE adhoc_server_instance
       SET status = 'DOWN', last_down_time = NOW(), update_time = NOW()
       WHERE instance_id = 'serverA' AND status = 'UP';
  │
  ▼ 2. 扫描 server A 上的 Job
       SELECT * FROM adhoc_query_job
       WHERE processing_server_instance = 'serverA'
         AND status IN ('PENDING', 'RUNNING');
  │
  ▼ 3. 对每个 Job 按 status 分流：
       │
       ├── Job status = PENDING（未下发 executor）：
       │     session 尚未创建，无状态丢失
       │     UPDATE adhoc_query_job
       │     SET processing_server_instance = 'serverB',
       │         update_time = NOW()
       │     WHERE job_id = ? AND processing_server_instance = 'serverA';
       │     server B 的 worker 抢占该 Job，正常调度 + dispatchJob
       │
       └── Job status = RUNNING（已下发 executor）：
             │   session 在 executor，不受 server 宕机影响
             │   executor 继续执行
             │
             ▼ UPDATE adhoc_query_job
             │   SET processing_server_instance = NULL,
             │       update_time = NOW()
             │   WHERE job_id = ? AND processing_server_instance = 'serverA';
             │   （原 server DOWN，新 server 暂不接管，等 executor 直写状态）
             │
             ▼ executor 继续执行，直写 DB 更新 Task/Job 状态（§9.4）
             │
             ▼ Job 终态后，executor 直写 job.status
             │
             ▼ server B 对账：
             │   心跳发现 executor 上的 Job 已终态，无操作
             │
             ▼ 用户查询：任意 server 查 DB + 读存储 / gRPC FetchLog 转发 executor
```

**关键**：server 宕机不影响 RUNNING Job，只有 executor 宕机才影响。这是 session 绑定 executor 的核心保证。

### 9.3 executor 找其他 server

```
executor 连不上原 server（gRPC 失败）
  │
  ▼ 查 adhoc_server_instance 表，找可用 server
  │   SELECT instance_id, host, http_port FROM adhoc_server_instance
  │   WHERE status = 'UP'
  │   ORDER BY heartbeat_time DESC;
  │
  ▼ 向新 server 汇报心跳
  │
  ▼ 新 server 对账，正常处理
```

executor 本地缓存 server 列表 + 每 30s 从 DB 刷新，避免每次心跳都查 DB。

### 9.4 server 宕机期间 executor 的行为

```
server 宕机（单 server 或所有 server）
  │
  ▼ executor 正在执行的 Task：
  │   ├── 继续执行 SQL（不中断，JDBC connection 独立）
  │   ├── 继续上传存储（存储独立于 server，结果序列化后上传）
  │   └── 继续直写 DB（如果 DB 正常，executor 不依赖 server 写库）
  │
  ▼ executor 状态同步：
  │   ├── 有其他 server 可用：连接其他 server，心跳 + L3 接口转发正常
  │   └── 所有 server 不可达：
  │         状态变更走 L4 本地 WAL（§8.1）
  │         后台线程每 5s 重试连接 server
  │         心跳直写 DB（executor 心跳不依赖 server）
  │
  ▼ Task 完成后：
  │   ├── L1 直写 DB（如果 DB 正常）：状态更新成功
  │   └── L1 直写 DB 失败：走 L2/L3/L4（§8.1），等 server 恢复后接口转发或 WAL 重试
```

**executor 不因 server 宕机中断 Task**，只影响状态同步的实时性。

### 9.5 为什么 server 宕机不影响 RUNNING Job

| 维度 | 说明 |
|---|---|
| session 位置 | session 在 executor 内存（`Map<jobId, JDBC.Connection>`），不在 server |
| 数据写入 | 结果/日志由 executor 上传存储，server 不参与 |
| 状态写入 | executor 直写 DB（L1-L4 保障），不依赖 server 转发 |
| 用户查询 | 任意 server 查 DB + 读存储 / gRPC FetchLog 转发 executor，无 server 亲和性 |
| 调度依赖 | Job 已 dispatch 到 executor，不再需要 server 调度 |

server 宕机只影响：
- 尚未 dispatch 的 PENDING Job（由其他 server 接管）
- 实时日志/结果读取的可用性（用户查询时若原 server DOWN，路由到其他 server 即可）

## 10. Job 状态重算

executor 宕机补偿后（§7.3、§7.5），受影响 Task 进入终态 FAILED，触发 Job 状态重算：

- Task 标记 FAILED（EXECUTOR_CRASHED 或 SKIPPED_DUE_TO_SESSION_LOSS）：Job 状态按聚合规则重算
- 聚合规则：
  - 所有 Task SUCCESS：Job SUCCESS
  - 含 SUCCESS + FAILED：Job PARTIAL_FAILED
  - 全部 FAILED：Job FAILED
  - 含 RUNNING/PENDING：Job 仍 RUNNING（等剩余 Task 完成）
- executor 宕机场景下，所有 Task 都进入终态，Job 直接进入 PARTIAL_FAILED 或 FAILED

server 宕机不触发 Job 状态重算（RUNNING Job 不受影响，PENDING Job 仅迁移 processing_server_instance）。

## 11. 过期实例清理

### 11.1 为什么需要清理

容器化部署下 Pod 重启频繁，DOWN 记录会积累：

- 实例表越来越大，扫描变慢
- 调度 SQL `WHERE status='UP'` 需跳过大量 DOWN 行
- 看板展示一堆 DOWN 噪声

### 11.2 清理机制

DOWN 24h 后直接物理删除，不归档历史表：

```sql
-- 1. 查过期 DOWN 实例
SELECT instance_id FROM adhoc_executor_instance
WHERE status = 'DOWN'
  AND update_time < DATE_SUB(NOW(), INTERVAL 24 HOUR)
LIMIT 1000;

-- 2. 物理删除
DELETE FROM adhoc_executor_instance
WHERE instance_id IN (?);
```

server_instance 表同理。24h 内保留 DOWN 记录给运维排查时间。

### 11.3 清理任务

后台 `@Scheduled` 任务每 30 分钟扫描一次，单次最大 1000 条避免长事务。

### 11.4 配置

```properties
adhoc.instance.retention-hours=24          # DOWN 后保留时长
adhoc.instance.cleanup-interval-minutes=30 # 清理任务执行间隔
adhoc.instance.cleanup-batch-size=1000     # 单次清理最大条数
```

## 12. 优雅上下线

### 12.1 触发方式

- **手动**：`POST /cluster/{type}/{instanceId}/drain`
- **容器化**：K8s preStop hook 调 drain API

### 12.2 状态流转

```
UP (accepting=1)
  │
  ▼ drain 触发
accepting=0（status 仍 UP，等存量 Task 跑完）
  │
  ▼ current_running=0 或超过 grace period
DOWN（status=DOWN，触发宕机补偿）
```

注意：drain 后 status 仍是 UP（心跳正常），只是 accepting=0 不接新任务。等存量 Task 跑完或 grace 到期，才真正标 DOWN。

### 12.3 drain 行为

```sql
UPDATE adhoc_executor_instance
SET accepting = 0,
    update_time = NOW()
WHERE instance_id = ?;
```

- `accepting=0` 后调度器跳过该实例
- 已承接的 Task 继续跑完
- 网关不再路由新请求（靠前置网关健康检查）

### 12.4 grace period

- 默认 5 分钟（`adhoc.instance.grace-period-seconds=300`）
- 超过 grace 仍有 Task -> 标记 status=DOWN，触发宕机补偿
- 一期 grace period 固定，二期可按实例当前 Task 数动态调整

### 12.5 JVM shutdown hook

executor 注册 JVM shutdown hook，容器 SIGTERM 时：

1. 标记 accepting=0
2. 通知 server 自己要下线（gRPC HealthCheck）
3. 等待运行中 Task 完成（最多 grace period）
4. 提交本地 WAL（见 §8.1 L4 -> L1/L2/L3）
5. 强制退出（未完成 Task 由 server 补偿标 FAILED）

## 13. 集群健康看板

通过 REST API 暴露集群健康状态（`GET /api/adhoc/cluster/health`）：

```text
集群总览:
  server: UP=3, DOWN=0, UNKNOWN=0
  executor: UP=5, DOWN=1, UNKNOWN=0

server 实例明细:
  server-1:8080  UP  accepting=1  active_jobs=2  active_tasks=4
  server-2:8080  UP  accepting=1  active_jobs=1  active_tasks=2

executor 实例明细:
  executor-1:9090  UP  accepting=1  running=8  queued=2  cpu=45.5%  mem=60.2%  disk=35.1%  load_score=45.5
  executor-3:9090  DOWN  (last_heartbeat=2026-07-14 15:30:00, marked down at 15:30:30, false_down_recover=0)
```

## 14. 与其他模块的接口

### 14.1 模块 1（任务调度与执行）

- 模块 1 的 task 表有 `executor_instance` 和 `processing_server_instance` 字段，本模块用这两个字段定位宕机实例关联的 Task/Job
- 模块 1 的 job 表有 `executor_instance` 字段（session 绑定），本模块用它扫描宕机 executor 关联的 Job
- 本模块的补偿逻辑更新 task 表的 status / fail_reason_category / fail_stage / finish_time
- executor 直写 DB 更新 task 状态（L1-L4 保障），不依赖 server 转发
- 本模块的 EXECUTOR_CRASHED 不走调度重试（session 丢失，重试无意义）

### 14.2 模块 3（引擎路由）

- executor 宕机的 fail_reason_category=EXECUTOR_CRASHED 不触发引擎降级
- 本模块标记 FAILED 后，模块 3 不参与
- executor 假 DOWN 恢复后重试存储上传不调用模块 3（上传是 executor 内部行为）

### 14.3 模块 6（可观测性）

- 本模块的监控指标由模块 6 采集
- 集群健康看板由模块 6 展示
- 心跳对账、WAL 重试、假 DOWN 恢复等事件由模块 6 记录

## 15. 监控指标

| 指标 | 含义 |
|---|---|
| `adhoc_server_instance_up{instance_id}` | server 实例存活状态 0/1（gauge） |
| `adhoc_executor_instance_up{instance_id}` | executor 实例存活状态 0/1（gauge） |
| `adhoc_server_heartbeat_total` | server 心跳次数（counter） |
| `adhoc_server_heartbeat_failed_total` | server 心跳写库失败次数（counter） |
| `adhoc_executor_heartbeat_total` | executor 心跳次数（counter） |
| `adhoc_executor_heartbeat_failed_total` | executor 心跳写库失败次数（counter） |
| `adhoc_cluster_health_check_total` | HA 补偿线程扫描次数（counter） |
| `adhoc_cluster_instance_down_detected_total{type=server/executor}` | 发现 DOWN 次数（counter） |
| `adhoc_cluster_task_compensate_total{reason=executor_crashed/session_loss/task_lost}` | 宕机补偿的 Task 数（counter） |
| `adhoc_instance_cleanup_total{type=server/executor}` | 过期实例清理数（counter） |
| `adhoc_instance_down_pending_cleanup{type}` | 待清理的 DOWN 实例数（gauge） |
| `adhoc_executor_drain_total` | executor 优雅下线触发次数（counter） |
| `adhoc_executor_write_db_l1_success_total` | L1 直写 DB 成功次数（counter） |
| `adhoc_executor_write_db_l2_retry_success_total` | L2 重试后写 DB 成功次数（counter） |
| `adhoc_executor_write_db_l3_rpc_success_total` | L3 接口转发写 DB 成功次数（counter） |
| `adhoc_executor_write_db_l4_wal_written_total` | L4 写 WAL 次数（counter） |
| `adhoc_executor_wal_retry_total` | WAL 重试次数（counter） |
| `adhoc_executor_wal_pending_count` | 待提交的 WAL 文件数（gauge） |
| `adhoc_executor_heartbeat_reconcile_total` | 心跳对账执行次数（counter） |
| `adhoc_executor_heartbeat_reconcile_corrected_total` | 心跳对账纠正状态次数（counter） |
| `adhoc_executor_false_down_recover_total` | 假 DOWN 恢复次数（counter） |
| `adhoc_executor_storage_reupload_total` | 存储上传重试次数（假 DOWN 恢复后重试，counter） |
| `adhoc_server_job_takeover_total` | server 宕机后接管 PENDING Job 数（counter） |

## 16. 验收标准

1. server/executor 多实例对等，CAS 抢占避免单点
2. **session 绑定 executor**：server 宕机不影响 RUNNING Job，只有 executor 宕机才影响
3. executor 心跳 5s 上报，30s 超时（6 次未到）判定 DOWN
4. server 心跳 5s 上报，15s 超时（3 次未到）判定 DOWN
5. 健康检查全量扫描（不分片，10s 间隔），CAS 标记 DOWN
6. executor 宕机补偿：RUNNING/DISPATCHED Task 直接 FAILED，fail_reason_category=EXECUTOR_CRASHED，保留 fail_stage
7. executor 宕机 result_summary 处理：无 summary / WRITING / COMPLETE + 已上传存储 / COMPLETE + 未上传四种情况分别处理（storage_type 两态 NONE/PERSISTENT，未上传的 -> INCOMPLETE）
8. executor 宕机时 session 模式下 PENDING Task 标记 SKIPPED_DUE_TO_SESSION_LOSS
9. executor 宕机后 Job 状态重算：有 SUCCESS -> PARTIAL_FAILED，全 FAILED -> FAILED
10. executor 假 DOWN：心跳恢复后 status=UP，不恢复已 FAILED Task，重试未成功的存储上传（storage_type NONE -> PERSISTENT，结果仍在内存可重试）
11. executor 重启后：恢复心跳，提交 WAL（executor 不本地存储结果/日志，无本地文件扫描清理），接收新 Task
12. executor 写库失败四层保障：L1 直写 -> L2 重试（1s/2s/4s，3 次）-> L3 接口转发（gRPC server.reportTaskStatus）-> L4 本地 WAL + 心跳对账
13. 心跳对账：executor 心跳带 running_tasks，server 收到后对账，DB=RUNNING + 心跳=SUCCESS 时校正，DB=RUNNING + 心跳无该 Task 时标记 TASK_LOST
14. server 宕机：PENDING Job 接管重新调度（processing_server_instance 改为新 server），RUNNING Job 不接管（processing_server_instance 置 NULL，等 executor 直写状态）
15. server 宕机期间 executor 继续 SQL 执行 + 存储上传 + 直写 DB，不中断 Task
16. executor 连不上原 server 时查 adhoc_server_instance 表找可用 server，本地缓存 + 每 30s 刷新
17. DOWN 实例 24h 后物理删除，不归档历史表
18. 优雅下线：drain -> accepting=0 -> grace 5min -> status=DOWN
19. JVM shutdown hook 处理 SIGTERM，退出前提交 WAL
20. 集群健康看板暴露实例状态（含 cpu/mem/disk/load_score）
21. 容器化和物理部署用同一套代码
22. 故障发现靠心跳超时，不引入主动探活
