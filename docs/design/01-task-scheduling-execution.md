# 模块 01：任务调度与执行

> 本文档是 bigdata-adhoc 平台的任务调度与执行模块设计。平台按功能模块拆分独立设计，本模块负责任务调度与执行，其他模块（SQL 解析与治理、引擎路由、结果存储、高可用、可观测等）各自独立成文。

## 1. 概述

### 1.1 子系统职责

负责任务的完整生命周期管理：从用户提交 SQL、Job 调度到 executor、executor 拆分 Job 创建 Task、session 内顺序执行 Task、状态迁移、失败传播、取消超时，到最终终态聚合。

### 1.2 设计原则

- **两层模型**：Job（用户视角的一次提交）+ Task（executor 拆分后的一段 SQL）
- **职责切分**：server 只做前置校验 + Job 调度 + 读路径 + HA 补偿；executor 做拆分 + session 执行 + 存储 + 状态直写
- **session 闭环**：Job 的所有产物（日志、结果、session）都在 executor，生命周期完整；server 宕机不影响 RUNNING Job
- **状态机拆字段**：status（生命周期位置）+ stage（进行中阶段）
- **流控两层**：全局 + per-user，Job 级（提交 + 调度时校验）
- **CAS 终态保护**：executor 直写 DB 与 server HA 补偿都用 CAS，避免并发覆盖

### 1.3 设计约定

- **不引入租户（tenant）概念**：所有限制按 user 维度。
- **取消用标志位而非独立 status**：cancel_requested 标志位 + cancel_requested_time 时间戳，status 保持干净。
- **engine_type 必须显式指定**：用户提交时指定 KYUUBI 或 STARROCKS，不指定则拒绝。无 AUTO 模式。
- **平台不做权限检查**：如引擎侧部署了权限插件，权限拒绝统一作为 ENGINE_ERROR，由 error_message 区分。

## 2. Job / Task 两层模型

### 2.1 模型定义

| 模型 | 是什么 | 谁关心 |
|---|---|---|
| **Job** | 一次窗口提交，包含原始 SQL（可能多段，含 SET/USE） | 业务方（我提交了一次查询） |
| **Task** | executor 拆分 Job 后的一段可执行 SQL（含累积的 SET/USE 前缀），独立状态机和结果 | executor、结果读取 |

### 2.2 生命周期

```
用户提交 SQL
  │
  ▼
server 前置校验：
  - SQL 语法校验（g4 解析整个 sql_content，不通过直接报错，不创建 Job）
  - Task 段数检查（按 ';' 计数，<= max_tasks_per_job，SET/USE 也计入段数）
  - 限流检查（PENDING，全局 + per-user）
  - engine_type 合法性（KYUUBI/STARROCKS）
  - engine_params 合法性（JSON 格式）
  - 引擎语句类型限制（仅 STARROCKS：仅允许 DQL/CTAS/SESSION_CONFIG/AUX，DDL/DML/DCL 拦截）
  - 超限/非法 -> 拒绝提交，返回错误
  - 通过 -> 创建 Job 记录（status=PENDING），存原始 sql_content
  │  （此时不创建 Task 记录，不识别 SET/USE）
  ▼
Job 队列（PENDING）
  │
  ▼
server worker 抢占 Job（CAS：PENDING -> DISPATCHING，受 RUNNING 限流约束）
  │
  ▼
server -> executor：dispatchJob（下发整个 Job 的 sql_content）
  │  executor accepted=true：Job status -> RUNNING，记录 executor_instance
  │  全部候选 executor 失败：Job 回退 PENDING，等待下轮调度
  │
  ▼ executor 接管
  │
  ▼ 创建 JDBC connection（session）
  │   connection params = engineParams（作为初始 params）
  │
  ▼ 拆分 Job（按 ';' 切分，识别 SET/USE 为会话配置前缀）：
  │   - 整个窗口全是 SET/USE：拒绝，Job FAILED（ADHOC_JOB_NO_EXECUTABLE_SQL）
  │   - 最后一段是 SET/USE：丢弃
  │   - SET/USE 合并到下一个真正执行的 Task 作为 prefix_sql
  │   - 创建 N 个 Task 记录（status=PENDING），直写 DB
  │
  ▼ 顺序执行 Task（segment_index ASC）：
  │   for each task:
  │     在 session 里执行 prefix_sql + sql_content
  │     （prefix_sql 重复执行 SET/USE，幂等无副作用）
  │     session 状态保留（临时表、视图、USE db 等）
  │     Task status -> RUNNING, stage=EXECUTING -> FETCHING -> WRITING -> SUCCESS
  │     前 Task 失败 -> 后续 PENDING Task 批量 FAILED（SKIPPED_DUE_TO_PRIOR_FAILURE）
  │
  ▼ Job 终态，关闭 session
  │
  ▼ Job 聚合终态（SUCCESS/PARTIAL_FAILED/FAILED/CANCELED）
```

### 2.3 为什么是两层

- **业务方视角是"一次提交"**：用户在控制台粘一段 SQL 点提交，关心的是"这次查询跑完没、结果怎么样"，不关心里面有几段 SQL。
- **executor 视角是"一段 SQL"**：一段 SQL 是一个独立的执行单位，有自己的状态、自己的结果。多段 SQL 在一个 Job 里按 segment_index 在同一 session 内串行执行，前 Task 的 session 状态（临时表、视图、USE db、SET 参数）对后 Task 可见。
- **结果视角是"一段 SQL 一份结果"**：每段 SQL 的结果 schema 不同、行数不同，必须独立存储、独立分页。

## 3. Task 状态机

### 3.1 status 字段 -- 5 态（生命周期位置）

| status | 含义 | 是否终态 |
|---|---|---|
| PENDING | executor 拆分后已入队，等待顺序执行 | 否 |
| RUNNING | executor 正在 session 里执行 | 否 |
| SUCCESS | 成功 | 是 |
| FAILED | 执行失败（引擎错误/写失败/被前 Task 失败跳过/被 session 丢失跳过/executor 宕机/心跳对账丢失等） | 是 |
| CANCELED | 用户取消 | 是 |

### 3.2 stage 字段 -- 3 阶段（仅 status=RUNNING 时有意义）

| stage | 含义 | 执行方 |
|---|---|---|
| EXECUTING | 在 session 里执行 prefix_sql + sql_content | executor + 引擎 |
| FETCHING | 拉结果行（LIMIT 强制） | executor |
| WRITING | 序列化结果 + 上传对象存储（一次性） | executor |

**stage 迁移规则**：
1. PENDING -> RUNNING 时 stage=EXECUTING
2. EXECUTING -> FETCHING -> WRITING 顺序迁移
3. 终态时 stage=NULL（SUCCESS）或保留最后值（FAILED，排障看卡在哪）
4. CAS 迁移：`WHERE query_id=? AND status='RUNNING' AND stage=?`

### 3.3 边界规则

- `status=PENDING` 时 `stage=NULL`（executor 拆分后入队，未开始执行）
- `status=RUNNING` 时 `stage` 必须有值，且按 3 阶段顺序迁移
- 终态时 `stage` 保留最后值（SUCCESS/FAILED/CANCELED），便于排障看"卡在哪个阶段失败"
- 例外：`status=SUCCESS` 且为结果复用命中时 `stage=NULL`（没走过任何 stage）
- 结果复用路径：提交时命中复用，直接 `status=SUCCESS`，`stage=NULL`，`reused_from_task_id` 指向原 task

### 3.4 状态迁移规则

合法迁移：

```
PENDING  -> RUNNING     executor 顺序执行抢占（CAS）
PENDING  -> SUCCESS     结果复用命中（提交时直接终态）
PENDING  -> CANCELED    cancel_requested=1 时秒取消（还没开始）
PENDING  -> FAILED      前 Task 失败跳过（SKIPPED_DUE_TO_PRIOR_FAILURE）
                          或 session 丢失跳过（SKIPPED_DUE_TO_SESSION_LOSS）
RUNNING  -> SUCCESS     正常完成（stage 走完 WRITING）
RUNNING  -> FAILED      执行失败（引擎错误/写失败/executor 宕机/心跳对账丢失）
RUNNING  -> CANCELED    cancel_requested=1 且 executor 确认已杀，或超时未确认 server 强制
```

终态保护：SUCCESS / FAILED / CANCELED 三态不可回退。所有迁移通过 `WHERE query_id=? AND status=?` 实现 CAS，避免并发覆盖（executor 直写与 server HA 补偿并发场景）。

### 3.5 状态更新机制

Task 状态更新由两方发起，都用 CAS：

**executor 直写**（主路径，写库失败四层保障 L1）：

```sql
-- executor 开始执行 PENDING Task
UPDATE adhoc_query_task
SET status = 'RUNNING',
    stage = 'EXECUTING',
    start_time = NOW()
WHERE query_id = ?
  AND status = 'PENDING';

-- stage 迁移
UPDATE adhoc_query_task
SET stage = 'FETCHING', fetch_start_time = NOW()
WHERE query_id = ? AND status = 'RUNNING' AND stage = 'EXECUTING';

-- 终态
UPDATE adhoc_query_task
SET status = 'SUCCESS', stage = NULL, finish_time = NOW(), duration_ms = ?
WHERE query_id = ? AND status = 'RUNNING' AND stage = 'WRITING';
```

**server HA 补偿**（兜底路径，详见模块 05）：

```sql
-- server 检测 executor 宕机，标记 RUNNING Task 为 FAILED
UPDATE adhoc_query_task
SET status = 'FAILED',
    fail_reason_category = 'EXECUTOR_CRASHED',
    fail_stage = stage,
    finish_time = NOW()
WHERE query_id = ? AND status = 'RUNNING';

-- server 心跳对账发现 TASK_LOST
UPDATE adhoc_query_task
SET status = 'FAILED',
    fail_reason_category = 'TASK_LOST',
    finish_time = NOW()
WHERE query_id = ? AND status = 'RUNNING';
```

### 3.6 取消请求表达

取消是异步动作（用户点取消 -> server 标志 -> server 发 cancelJob -> executor 杀引擎查询 -> 确认已杀 -> 转 CANCELED）。中间"已请求未确认"阶段用 `cancel_requested TINYINT` 标志位 + `cancel_requested_time` 时间戳表达，status 保持 5 态。

迁移规则：

```
PENDING + cancel_requested=1  -> 直接 CANCELED（还没开始，秒取消）
RUNNING  + cancel_requested=1 -> executor 收到 cancelJob 后杀引擎查询，确认后 -> CANCELED
RUNNING  + cancel_requested=1 且超时未确认 -> server 强制 -> CANCELED
```

cancel 耗时追踪：
- `cancel_requested_time`：cancel_requested 从 0 变 1 的时刻
- `finish_time`：转 CANCELED 终态的时刻
- 取消耗时 = finish_time - cancel_requested_time（监控 executor 杀引擎查询的响应速度）
- 兜底：`cancel_requested_time + 60s < NOW()` 且仍未确认，server 强制转 CANCELED

## 4. Job 状态机

### 4.1 7 态

```
PENDING     -> DISPATCHING    server worker CAS 抢占（claimJob）
DISPATCHING -> RUNNING        dispatchJob 成功，executor accepted=true
            -> PENDING        全部候选 executor 失败，回退重调度
PENDING     -> FAILED         拆分失败（全是 SET/USE，ADHOC_JOB_NO_EXECUTABLE_SQL）
            -> CANCELED       用户取消（Job 还在 PENDING，直接取消）
RUNNING     -> SUCCESS        所有 Task SUCCESS
            -> PARTIAL_FAILED 混合（SUCCESS + FAILED）
            -> FAILED         所有 Task FAILED
            -> CANCELED       用户取消（未终态 Task 走取消流程）
```

**DISPATCHING 是过渡态**：多 server 部署下表达"某 server 已抢占、正在派发"，避免其他 server 重复抢占；派发失败回退 PENDING 重调度。PENDING -> DISPATCHING 由 `claimJob`（CAS `WHERE job_id=? AND status='PENDING'`）触发，DISPATCHING -> RUNNING 在 executor 确认接收后触发（记录 executor_instance + dispatch_time）。

### 4.2 聚合规则

- Job 提交时 `status=PENDING`
- server worker 抢占 Job（DISPATCHING），dispatchJob 成功后 Job 迁移到 RUNNING（设置 dispatch_time、executor_instance）
- executor 拆分 Job 创建所有 Task 后（设置 split_finish_time），开始顺序执行
- 任一 Task 进入终态时触发 Job 状态重算（此时 Job 已是 RUNNING，不会回 PENDING）
- 重算时统计所有 Task 的终态分布：
  - 全 SUCCESS -> SUCCESS
  - 全 FAILED -> FAILED
  - 混合（含 SUCCESS + FAILED） -> PARTIAL_FAILED
- 用户取消 Job（设置 Job 的 `cancel_requested=1` + `cancel_requested_time=NOW()`）：
  - Job=PENDING 时：直接转 CANCELED（还没 dispatchJob，无 Task 需取消）
  - Job=RUNNING 时：server 发 cancelJob 给 executor，executor 对所有未终态 Task 走取消流程，所有 Task 终态后 Job 转 CANCELED

### 4.3 特殊情况：结果复用

如果 Job 的所有 Task 提交时都命中结果复用（直接 SUCCESS），Job 跳过 PENDING/RUNNING，直接 SUCCESS。罕见但合法。

### 4.4 Job 与 Task 状态对应

| Job status | Task 状态分布 |
|---|---|
| PENDING | Job 已提交，尚未 dispatchJob，Task 尚未创建 |
| DISPATCHING | server 已抢占，正在选择 executor 派发 |
| RUNNING | Task 已创建，至少一个 Task=RUNNING（其余 PENDING 或终态） |
| SUCCESS | 所有 Task=SUCCESS |
| PARTIAL_FAILED | 含 SUCCESS + FAILED |
| FAILED | 所有 Task=FAILED |
| CANCELED | 用户取消，Task 批量 CANCELED 或 Job 未 dispatch 即取消 |

## 5. 超时机制（session 模式）

### 5.1 设计原则

无平台级 TIMEOUT 终态，超时由引擎层 + 心跳对账兜底：

| 来源 | 处理 | 终态 |
|---|---|---|
| **引擎级超时**（如 `spark.network.timeout`、`kyuubi.operation.query.timeout`、StarRocks `query_timeout`） | 引擎返回错误，executor 捕获后上报 | FAILED（ENGINE_ERROR） |
| **session 卡死**（executor 存活但 Task 长时间无进展） | 心跳对账检测（DB=RUNNING 但心跳里没有），标记 TASK_LOST | FAILED（TASK_LOST） |
| **executor 宕机** | 心跳超时（30s）检测 | FAILED（EXECUTOR_CRASHED） + SKIPPED_DUE_TO_SESSION_LOSS |

不设平台级 TIMEOUT 终态的理由：
- session 模式下，引擎内部有自己的超时机制（连接超时、查询超时、网络超时），引擎超时返回错误，executor 捕获后统一作为 ENGINE_ERROR 走 FAILED
- 心跳对账覆盖"executor 存活但 Task 异常"场景（TASK_LOST），不需要平台再扫超时
- executor 宕机由 executor heartbeat 超时检测（30s），所有未终态 Task 走 EXECUTOR_CRASHED / SKIPPED_DUE_TO_SESSION_LOSS
- 平台级 TIMEOUT 终态语义复杂（要不要杀引擎查询？要不要回写 session 状态？），不设则语义干净

### 5.2 引擎超时配置

引擎超时通过 engineParams 透传到 executor，executor 创建 session 时设置，用户的 engineParams 可覆盖默认值。

### 5.3 心跳对账兜底

心跳对账机制（详见 §11.5）覆盖以下场景：

- executor 短暂重启：5s 内检测到 TASK_LOST
- session 卡死（引擎 hang 住但未报错）：心跳对账发现 running_tasks 里 task 状态滞后或缺失，标记 TASK_LOST
- executor 长时间宕机：30s 心跳超时，标记 executor DOWN，RUNNING Task FAILED（EXECUTOR_CRASHED）

### 5.4 与 cancel 的关系

- cancel_requested 表达"用户主动取消，等 executor 确认"
- 引擎超时是引擎自己报错，executor 上报 FAILED，不等用户确认
- 两者都是终态入口，但语义不同（用户取消 vs 引擎报错）

## 6. 失败语义与 session 模式传播

### 6.1 fail_reason_category 分类

fail_reason_category 用于失败原因分类与排障统计，不决定降级/重试策略：

| category | 含义 | 对应 fail_stage | 触发方 |
|---|---|---|---|
| ENGINE_ERROR | 引擎报错（含权限拒绝、SQL 错误、表不存在等，由 error_message 区分） | EXECUTING | executor |
| EXECUTOR_CRASHED | executor 宕机，server HA 补偿标记 | 保留最后 stage | server HA |
| TASK_LOST | 心跳对账发现 Task 丢失（DB=RUNNING 但心跳里没有） | 保留最后 stage | server 心跳对账 |
| SKIPPED_DUE_TO_PRIOR_FAILURE | session 模式前 Task 失败，跳过后续 PENDING Task | EXECUTING | executor |
| SKIPPED_DUE_TO_SESSION_LOSS | executor 宕机导致 session 丢失，跳过 PENDING Task | EXECUTING | server HA |
| WRITE_ERROR | 结果上传失败（含序列化失败，无本地兜底） | WRITING/OSS_UPLOAD | executor |
| FETCH_ERROR | 拉结果失败 | FETCHING | executor |
| SPLIT_ERROR | 拆分失败（如全是 SET/USE，ADHOC_JOB_NO_EXECUTABLE_SQL） | SPLIT | executor |
| CANCELED | 用户取消 | 保留最后 stage | server / executor |

权限拒绝归一化：平台不做权限检查。若引擎侧部署了权限插件，无权限时引擎报错，executor 捕获后统一标记为 ENGINE_ERROR，由 error_message 区分具体原因。

### 6.2 session 模式失败传播

session 模式下，前 Task 失败会跳过后续所有 PENDING Task，避免在状态异常的 session 上继续执行：

```
Task 1 FAILED
  │
  ▼ executor 对同 Job 内每个 PENDING Task：
      UPDATE adhoc_query_task
      SET status = 'FAILED',
          fail_reason_category = 'SKIPPED_DUE_TO_PRIOR_FAILURE',
          fail_stage = 'EXECUTING',
          finish_time = NOW()
      WHERE query_id = ? AND status = 'PENDING';
  │
  ▼ Job 聚合：含 SUCCESS + FAILED -> PARTIAL_FAILED
              全 FAILED -> FAILED
```

**理由**：session 模式下前 Task 失败可能使 session 状态异常（未提交事务、临时表残留、USE db 失效），继续执行后续 Task 风险高。失败传播让 Job 快速进入终态，由用户决定是否重试整个 Job。

**例外**：用户取消（cancel_requested=1）走 CANCELED 而非 FAILED，不触发失败传播，由 cancel 流程统一处理未终态 Task。

### 6.3 engineParams 与 SET 共存

engineParams 接口保留，与 SET 语句共存：

- engineParams 在 connection 创建时作为初始 params 设置（连接 URL 或连接属性）
- SET 语句在 session 里执行，可以覆盖/追加 engineParams
- 两者共存，SET 优先级更高（后执行，覆盖初始 params）
- prefix_sql 里累积的 SET 在每个 Task 执行前重复跑一遍，幂等无副作用

```
executor 创建 session：
  connection = DriverManager.getConnection(url, props)
  props 来自 engineParams（JSON 解析为 key-value）

executor 执行 Task：
  connection.execute(prefix_sql)   -- SET spark.xxx=100; USE db_ods;
  connection.execute(sql_content)  -- SELECT * FROM ...
```

## 7. Job 内 Task 串行执行

### 7.1 串行规则

- executor 拆分 Job 时所有 Task 入队（status=PENDING，直写 DB）
- 同一 Job 内 Task 严格按 `segment_index ASC` 顺序在 session 里执行
- Task[k] 未进入终态时，Task[k+1] 不开始执行
- Task[k] 终态后，Task[k+1] 立即开始执行（同 session）
- 整个 Job 在同一个 JDBC connection（session）里执行，session 状态保留

### 7.2 失败传播

session 模式下前 Task 失败跳过后续 Task（详见 §6.2）：

- Task[k] FAILED -> Task[k+1..N] 批量标记 FAILED（SKIPPED_DUE_TO_PRIOR_FAILURE）
- 例外：Job 被用户取消（cancel_requested=1 on Job）-> 所有未终态 Task 走 CANCELED 流程

### 7.3 DDL/DML 隐式依赖

session 模式下 DDL/DML 的隐式依赖天然得到保障：前 Task 的 DDL/DML 在 session 里生效（临时表、视图、USE db），后 Task 能引用。前 Task 失败跳过后续 Task，避免在残留状态上执行。

### 7.4 串行理由

DDL/DML 有隐式依赖（先建表后写入），并行会导致语义错乱。session 模式下串行保证 Task[k] 的 DDL/DML 先生效，Task[k+1] 能引用前序创建的表。前 Task 失败跳过后续是 session 模式的容错——session 状态异常时快速终止，避免连锁错误。

## 8. executor session 生命周期与 RPC

### 8.1 session 生命周期

```
executor 接收 dispatchJob
  │
  ▼ 创建 JDBC connection（session）
  │   connection params = engineParams（接口保留，作为初始 params）
  │   connection 失败 -> Job FAILED（ENGINE_ERROR，含网络错误，由 error_message 区分）
  │
  ▼ 拆分 Job，创建 Task 记录（直写 DB，status=PENDING）
  │   拆分失败（全是 SET/USE）-> Job FAILED（ADHOC_JOB_NO_EXECUTABLE_SQL）
  │
  ▼ 顺序执行 Task（segment_index ASC）：
  │   for each task:
  │     UPDATE task SET status='RUNNING', stage='EXECUTING', start_time=NOW()
  │     在 session 里执行 prefix_sql + sql_content
  │     │
  │     ├── 引擎执行成功：
  │     │     UPDATE task SET stage='FETCHING', fetch_start_time=NOW()
  │     │     拉结果（LIMIT 强制）
  │     │     UPDATE task SET stage='WRITING', write_start_time=NOW()
  │     │     序列化结果（MAGIC+schema+rows，内存）
  │     │     UPDATE task SET local_write_finish_time=NOW()  -- 结果序列化完成（不写本地）
  │     │     StorageClient.uploadResult 一次性上传（storage_type=PERSISTENT，无本地兜底）
  │     │     UPDATE task SET oss_upload_time=NOW()  -- 上传成功时；失败时为 NULL
  │     │     UPDATE task SET status='SUCCESS', stage=NULL, finish_time=NOW()
  │     │
  │     ├── 引擎执行失败（含权限拒绝）：
  │     │     UPDATE task SET status='FAILED',
  │     │       fail_reason_category='ENGINE_ERROR', fail_stage='EXECUTING',
  │     │       error_message=..., finish_time=NOW()
  │     │     跳过后续 PENDING Task（SKIPPED_DUE_TO_PRIOR_FAILURE）
  │     │     break
  │     │
  │     └── 收到 cancelJob：
  │           杀引擎查询
  │           UPDATE task SET status='CANCELED', finish_time=NOW()
  │           跳过后续 PENDING Task（CANCELED）
  │           break
  │
  ▼ Job 终态，关闭 session
  │   connection.close()
  │   从内存 Map 移除
  │
  ▼ Job 聚合终态（直写 DB）
```

### 8.2 session 存储与清理

- executor 内存里维护 `Map<jobId, JDBC.Connection>`，Job 终态后清理
- session 仅存在 executor 内存，不持久化（executor 重启 = session 丢失 = Job FAILED）
- session 绑定 executor 是健壮性的核心：server 宕机不影响 session，只有 executor 宕机才影响

### 8.3 RPC 接口

#### 8.3.1 server -> executor

| RPC | 用途 | 时机 | 关键字段 |
|---|---|---|---|
| dispatchJob | 下发整个 Job | server 抢占 Job 后 | job_id, user_id, user_name, sql_content, engine_type, engine_instance, engine_params, client_ip, client_request_id |
| cancelJob | 取消整个 Job | cancel_requested=1 时 | job_id |
| FetchLog | 拉日志分页（读 executor 内存 buffer） | 用户读日志时 | job_id, offset, limit, trace_id |
| getRunningJobs | 探查 executor 当前在跑 Job | 孤儿 RUNNING Job 兜底检测 | - |
| clearJobLog | 通知 executor 清理日志 buffer | 日志已落盘 | job_id |

#### 8.3.2 executor -> server

| RPC | 用途 | 时机 | 关键字段 |
|---|---|---|---|
| heartbeat | 心跳 + 对账 | 定期 | instance_id, heartbeat_time, cpu_usage, mem_usage, disk_usage, running_tasks[] |
| reportTaskStatus | 接口转发 Task 状态 | executor 直写 DB 失败时（L3） | task_id, status, stage, fail_reason_category, error_code, error_message, finish_time, result_summary |
| reportJobStatus | 接口转发 Job 状态 | executor 直写 DB 失败时（L3） | job_id, status, finish_time |

详细 protobuf 定义见模块 12（12-sdk.md）。

### 8.4 状态上报与 CAS 校验

executor 直写 DB 为主要路径（L1），写失败时走 L2/L3/L4 + 心跳对账（详见 §11.4）。executor 直写 DB 时用 CAS 防并发：

```sql
-- executor 上报 stage 迁移
UPDATE adhoc_query_task
SET stage = ?,  -- new_stage
    <对应时间戳字段> = NOW()
WHERE query_id = ?
  AND status = 'RUNNING'
  AND stage = ?;  -- expected_stage
-- 更新 0 行说明状态已被其他方修改（如 server 取消/HA 补偿/心跳对账），executor 需重新拉取最新状态
```

如果 CAS 失败（更新 0 行），executor 查询当前 task 状态：
- 如果 status 已是终态（CANCELED/FAILED），executor 停止执行，杀引擎查询，关闭 session
- 如果 cancel_requested=1，executor 杀引擎查询，回报 CANCELED
- 其他情况记日志告警

### 8.5 engineParams 接口

engineParams 通过 dispatchJob 透传到 executor：

- server 前置校验时检查 JSON 格式合法性
- dispatchJob 请求里携带 engine_params（JSON）
- executor 创建 session 时解析 engineParams 为连接属性
- SET 语句在 session 里执行，覆盖/追加 engineParams
- 两者共存，SET 优先级更高

## 9. 结果复用

### 9.1 复用判定条件

新 task 提交时，检查是否命中复用：

```sql
SELECT query_id FROM adhoc_query_task
WHERE user_id = ?
  AND sql_hash = ?              -- SQL 文本 hash（含 prefix_sql）
  AND sql_type IN ('DQL', 'CTAS')  -- 仅查询类可复用
  AND status = 'SUCCESS'
  AND finish_time >= NOW() - INTERVAL ? SECOND  -- TTL 内
ORDER BY finish_time DESC
LIMIT 1;
```

命中后：新 task 直接 `status=SUCCESS`，`stage=NULL`，`reused_from_task_id` 指向原 task，不重复执行。

**sql_hash 计算**：`sql_hash = SHA256(prefix_sql + sql_content)`（前缀参与 hash，因为前缀 SET/USE 影响结果）。

### 9.2 复用边界

| 维度 | 是否参与判定 | 理由 |
|---|---|---|
| user_id | 是（同 user） | 跨 user 复用涉及权限语义，简化不做 |
| sql_hash | 是 | SQL 文本不同不算复用（含 prefix_sql） |
| sql_type | 是（仅 DQL/CTAS） | DDL/DML 有副作用，不能复用 |
| engine_params | 是（参与 hash） | 某些参数影响结果（如时区策略），保守判定 |
| engine_type | 否 | 同 SQL 不同引擎结果应一致，不区分 |
| engine_instance | 否 | 同引擎类型不同实例结果应一致 |
| TTL | 默认 300s（可配） | 平衡新鲜度与复用收益 |

### 9.3 复用时的结果引用

- 命中复用，新 task 不写对象存储，不占 executor 资源
- 结果读取时：`SELECT reused_from_task_id FROM adhoc_query_task WHERE query_id=?`
- 如果 `reused_from_task_id IS NOT NULL`，跳转到原 task 的结果
- 原 task 的结果 TTL 由原 task 的 finish_time 决定，不是新 task 的
- 复用结果直接读对象存储（原 task 必须 storage_type=PERSISTENT，已上传），不指向原 executor

## 10. 阶段时间戳

### 10.1 时间戳说明

时间戳分 Job 级和 Task 级两层。

#### 10.1.1 Job 时间戳（6 个）

| 时间戳 | 设置方 | 设置时机 | 说明 |
|---|---|---|---|
| submit_time | server | 创建 Job 记录时 | Job 提交时间 |
| validate_finish_time | server | 前置校验通过后 | 前置校验完成时间（校验失败不创建 Job，此字段不设置） |
| dispatch_time | server | dispatchJob RPC 发起时 | Job 下发到 executor 的时间 |
| split_finish_time | executor | Job 拆分完成，Task 记录创建后 | executor 拆分完成时间 |
| start_time | executor | 第一个 Task 开始执行时 | Job 实际开始执行时间 |
| finish_time | executor | Job 终态时 | Job 终态时间 |

**duration_ms 计算**：`finish_time - submit_time`（含校验、调度、拆分、执行全流程）

#### 10.1.2 Task 时间戳（7 个）

| 时间戳 | 设置方 | 设置时机 | 对应 stage 迁移 |
|---|---|---|---|
| enqueue_time | executor | 创建 Task 记录时（executor 拆分时） | -（status=PENDING） |
| start_time | executor | stage=EXECUTING 时 | PENDING -> RUNNING, stage=EXECUTING |
| fetch_start_time | executor | stage=FETCHING 时 | stage: EXECUTING -> FETCHING |
| write_start_time | executor | stage=WRITING 时 | stage: FETCHING -> WRITING |
| local_write_finish_time | executor | 结果序列化完成时（内存序列化，不写本地） | -（stage 仍为 WRITING） |
| oss_upload_time | executor | 结果上传成功时 | -（上传失败为 NULL） |
| finish_time | executor | Task 终态时 | stage: WRITING -> SUCCESS（或其他终态） |

**duration_ms 计算**：`finish_time - start_time`（不含 Task 创建到开始执行的等待，session 模式下通常为 0）

### 10.2 耗时分析

#### 10.2.1 Job 耗时分布

```
submit_time ─── validate_finish_time ─── dispatch_time ─── split_finish_time ─── start_time ─── finish_time
              │                          │                  │                      │              │
              ├─ 前置校验耗时             ├─ 调度选 executor  ├─ dispatchJob RPC     ├─ 第一个 Task  ├─ Job 执行
              │  （应 < 100ms）           │  （应 < 50ms）    │  + 创建 session      │  排队等待     │  总耗时
              │                          │                  │  + 拆分 Job           │  （通常 0）   │
              │                          │                  │  （应 < 500ms）       │              │
```

计算公式：
```
前置校验耗时 = validate_finish_time - submit_time
调度耗时     = dispatch_time        - validate_finish_time
拆分耗时     = split_finish_time    - dispatch_time
排队等待     = start_time           - split_finish_time
Job 执行耗时 = finish_time          - start_time
Job 总耗时   = finish_time          - submit_time   (= duration_ms)
```

#### 10.2.2 Task 耗时分布

```
enqueue_time ─── start_time ─── fetch_start_time ─── write_start_time ─── local_write_finish_time ─── oss_upload_time ─── finish_time
              │                │                    │                      │                              │                      │
              ├─ 排队等待       ├─ 引擎执行 SQL       ├─ 拉结果               ├─ 结果序列化                  ├─ 结果上传            ├─ 终态处理
              │  （session     │  （主要耗时）        │                      │  （内存，不落本地）           │                      │
              │   模式下 =     │                    │                      │                              │                      │
              │   前序 Task    │                    │                      │                              │                      │
              │   执行时间）   │                    │                      │                              │                      │
```

计算公式：
```
排队等待       = start_time              - enqueue_time
引擎执行       = fetch_start_time        - start_time
拉取结果       = write_start_time        - fetch_start_time
结果序列化     = local_write_finish_time - write_start_time
结果上传       = oss_upload_time         - local_write_finish_time   (上传失败时不计算)
Task 总耗时    = finish_time             - start_time                (= duration_ms)
```

#### 10.2.3 关键 SLA 指标

| 指标 | 计算 | SLA |
|---|---|---|
| 前置校验耗时 | validate_finish_time - submit_time | P99 < 100ms |
| 调度耗时 | dispatch_time - validate_finish_time | P99 < 50ms |
| 拆分耗时 | split_finish_time - dispatch_time | P99 < 500ms |
| 引擎执行耗时 | fetch_start_time - start_time | P99 < 30s |
| 拉结果耗时 | write_start_time - fetch_start_time | P99 < 10s |
| 结果序列化耗时 | local_write_finish_time - write_start_time | P99 < 5s |
| 结果上传耗时 | oss_upload_time - local_write_finish_time | P99 < 30s |
| Task 总耗时 | finish_time - start_time | P99 < 60s |
| Job 总耗时 | finish_time - submit_time | P99 < 120s |
| 心跳对账检测延迟 | executor 重启到 TASK_LOST 标记 | < 10s（两个心跳周期） |
| executor 宕机检测延迟 | executor DOWN 到 EXECUTOR_CRASHED 标记 | < 35s（心跳超时 30s + 一个扫描周期） |

### 10.3 异常路径的时间戳

- **FAILED（ENGINE_ERROR）**：finish_time = 失败时刻，fail_stage=EXECUTING，未走到的 stage 时间戳（fetch_start_time 等）为 NULL
- **FAILED（FETCH_ERROR）**：finish_time = 失败时刻，fail_stage=FETCHING，write_start_time 及之后为 NULL
- **FAILED（WRITE_ERROR，fail_stage=WRITING）**：结果序列化失败，finish_time = 失败时刻，local_write_finish_time 及之后为 NULL
- **FAILED（WRITE_ERROR，fail_stage=OSS_UPLOAD）**：结果上传失败（无本地兜底，结果丢失），finish_time = 失败时刻，oss_upload_time = NULL，storage_type 保持 NONE
- **FAILED（SKIPPED_DUE_TO_PRIOR_FAILURE）**：finish_time = 被跳过时刻，fail_stage=EXECUTING，start_time 及之后时间戳为 NULL（没开始执行）
- **FAILED（SKIPPED_DUE_TO_SESSION_LOSS）**：同上，finish_time = server HA 标记时刻
- **FAILED（EXECUTOR_CRASHED）**：finish_time = server HA 标记时刻，当前 stage 的时间戳有值，后续为 NULL
- **FAILED（TASK_LOST）**：finish_time = 心跳对账标记时刻
- **FAILED（SPLIT_ERROR）**：Job 级失败，Task 未创建，无 Task 时间戳
- **CANCELED**：finish_time = 取消确认时刻
- **复用命中**：所有时间戳为 NULL（没走过任何 stage），finish_time = submit_time

### 10.4 fail_stage 字段

除了 `stage`（当前阶段），失败时还要记 `fail_stage` 精确定位。Task 表 `fail_stage`：

| fail_stage | 触发 | 终态 | 设置方 |
|---|---|---|---|
| SPLIT | executor 拆分失败（如全是 SET/USE） | Job FAILED | executor |
| EXECUTING | 引擎执行错误 | Task FAILED | executor |
| FETCHING | 拉结果失败 | Task FAILED | executor |
| WRITING | 结果序列化失败（内存） | Task FAILED | executor |
| OSS_UPLOAD | 结果上传失败（无本地兜底，结果丢失） | Task FAILED（WRITE_ERROR） | executor |

**注意**：
- OSS_UPLOAD 导致 Task 失败（对象存储是唯一持久化层，无本地兜底；upload 失败 = 结果丢失，走 WRITE_ERROR）
- SPLIT 是 Job 级失败（如全是 SET/USE 无可执行 SQL，ADHOC_JOB_NO_EXECUTABLE_SQL），Task 表的 fail_stage 字段此时为 NULL（无 Task 记录）

`fail_stage` 与 `stage` 的区别：`stage` 是当前阶段（RUNNING 时），`fail_stage` 是失败时的精确定位（终态时）。例如 SKIPPED_DUE_TO_PRIOR_FAILURE 跳过的 Task，`stage` 为 NULL（PENDING 时被跳过），但 `fail_stage=EXECUTING`（表示"本该在 EXECUTING 阶段执行，因前 Task 失败未执行"）。

## 11. 宕机处理与写库失败

### 11.1 提供的字段

| 字段 | 用途 |
|---|---|
| processing_server_instance | Job 级：哪个 server 抢占并 dispatchJob（server HA 检测用） |
| executor_instance | Job/Task 级：哪个 executor 在执行（executor HA 检测用） |
| stage + 时间戳 | 隐式 heartbeat（长时间未更新 = 异常） |
| cancel_requested + cancel_requested_time | 取消请求追踪 |

### 11.2 server 宕机处理

server 宕机不影响 RUNNING Job（session 在 executor），具体机制归高可用子系统。本子系统定义接口：

- Job.processing_server_instance 记录哪个 server 抢占了 Job
- server DOWN 后，其他 server 扫描 `processing_server_instance = dead server AND status IN ('PENDING', 'DISPATCHING', 'RUNNING')` 的 Job
- Job=PENDING/DISPATCHING：未 dispatchJob 或派发未完成，UPDATE processing_server_instance 为新 server，重新调度
- Job=RUNNING：已 dispatchJob，session 在 executor，UPDATE processing_server_instance = NULL，executor 继续执行直写 DB，Job 终态后由 executor 直写
- 关键：server 宕机不影响 RUNNING Job，只有 executor 宕机才影响

### 11.3 executor 宕机处理（session 模式）

executor 宕机后，session 丢失，所有未终态 Task 需要处理。具体机制归高可用子系统，本子系统定义接口：

```
executor DOWN（心跳超时 30s）
  │
  ▼ server HA 线程 CAS 标记 executor DOWN
  │
  ▼ 该 executor 上所有 RUNNING Task 处理：
       UPDATE adhoc_query_task
       SET status = 'FAILED',
           fail_reason_category = 'EXECUTOR_CRASHED',
           fail_stage = stage,
           finish_time = NOW()
       WHERE executor_instance = ? AND status = 'RUNNING';
  │
  ▼ 扫描该 executor 关联的 Job（通过 Job.executor_instance）
  │
  ▼ 对每个 Job 的 PENDING Task（session 模式下未开始的 Task）：
       UPDATE adhoc_query_task
       SET status = 'FAILED',
           fail_reason_category = 'SKIPPED_DUE_TO_SESSION_LOSS',
           fail_stage = 'EXECUTING',
           finish_time = NOW()
       WHERE job_id = ? AND status = 'PENDING';
  │
  ▼ Job 状态重算
       ├── 有 Task SUCCESS：PARTIAL_FAILED
       └── 全部 FAILED：FAILED
```

**session 丢失的语义**：executor 宕机 = session 丢失 = Job 内所有未完成 Task 都无法继续。RUNNING Task 标记 EXECUTOR_CRASHED，PENDING Task 标记 SKIPPED_DUE_TO_SESSION_LOSS（即使有其他 executor 可用，session 状态无法迁移，只能整体失败）。

**executor 假 DOWN 的处理**：executor 心跳超时但实际存活，恢复后不回退已 FAILED 的 Task（避免状态回退），仅重试未成功的结果上传（如果结果已序列化但上传失败）。

**executor 重启后的处理**：恢复心跳，不恢复已 FAILED 的 Task（用户可能已重试），提交本地 WAL（详见高可用子系统）。

### 11.4 executor 写库失败四层保障

executor 直写 DB 是主路径，写失败时按四层保障 + 心跳对账兜底：

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
                 ▼ L4：写本地 WAL（wal/{taskId}.json）
                 │
                 ▼ executor 继续心跳（不停止）
                 │   心跳带 task 状态，server 对账兜底
                 │
                 ▼ 后台线程定期重试 WAL
                 │   ├── 成功：删除 WAL
                 │   └── 失败：保留 WAL
                 │
                 ▼ executor 重启时优先提交 WAL
```

**WAL 数据量**：WAL 只存"状态变更事件"，不存结果数据（结果上传对象存储，不在本地）。单条 WAL 约 200 字节，本地 WAL 目录容量可忽略。

### 11.5 心跳对账

executor 心跳带上当前在运行的 Task 状态，server 收到后对账：

```
server 收到 executor 心跳
  │
  ▼ 1. 更新 executor.heartbeat_time + cpu/mem/disk_usage + running_tasks
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
              UPDATE task.status = 'FAILED',
                     fail_reason_category = 'TASK_LOST',
                     finish_time = NOW()
              WHERE query_id = ? AND status = 'RUNNING';
```

**心跳对账覆盖的场景**：

| 场景 | 对账行为 | 效果 |
|---|---|---|
| executor 短暂重启 | 重启后 running_tasks 为空，DB 的 RUNNING Task 不在心跳 | 标记 FAILED（TASK_LOST），5s 内发现 |
| executor 写库失败 | DB 状态停在 RUNNING，心跳带 SUCCESS | 心跳对账更新为 SUCCESS，5s 内校正 |
| executor 长时间宕机 | 心跳超时（30s） | 标记 executor DOWN，RUNNING Task FAILED（EXECUTOR_CRASHED） |
| executor 假 DOWN | 心跳恢复，running_tasks 带 Task | 对账恢复状态（不回退终态） |

## 12. 流控配置

### 12.1 Job 级（提交 + 调度校验）

| 配置项 | 作用点 | 维度 | 默认值 |
|---|---|---|---|
| max_tasks_per_job | Job 提交校验（按 ';' 拆分计数，含 SET/USE） | 单 Job | 20 |
| max_pending_jobs_global | Job 提交校验（全局 PENDING Job 总数） | 全局 | 100 |
| max_pending_jobs_per_user | Job 提交校验（单用户 PENDING Job 数） | per-user | 5 |
| max_running_jobs_global | server 调度 Job 时（全局 RUNNING Job 总数） | 全局 | 50 |
| max_running_jobs_per_user | server 调度 Job 时（单用户 RUNNING Job 数） | per-user | 3 |

### 12.2 Task 级

Task 由 executor 内部拆分产生并在 session 内顺序执行，无 server 端 Task 队列抢占，不单独配置 Task 级流控。Task 并发由 Job 级流控（每个 Job 在一个 executor 上串行执行）+ executor 内部线程池上限自然约束。

| 配置项 | 作用点 | 默认值 |
|---|---|---|
| max_concurrent_tasks | executor 同时执行的 Job 数 | 见模块 13 |

### 12.3 配置存储

- 全局配置：Apollo（动态调整，无需重启），含 `max_pending_jobs_per_user` / `max_running_jobs_per_user` 等 per-user 维度默认值
- per-user 流控统一用 Apollo 全局配置，不建 per-user 配置表

### 12.4 Job 调度 SQL（带流控检查）

```sql
-- server worker 查询可调度的 PENDING Job（带 running 限流子查询）
SELECT j.job_id
FROM adhoc_query_job j
WHERE j.status = 'PENDING'
  AND j.is_deleted = 0
  AND j.submit_time > DATE_SUB(NOW(), INTERVAL #{recentWindowHours} HOUR)
  AND (SELECT COUNT(*) FROM adhoc_query_job WHERE status='RUNNING') < ?   -- max_running_jobs_global
  AND (SELECT COUNT(*) FROM adhoc_query_job WHERE status='RUNNING' AND user_id=j.user_id) < ?  -- per-user
ORDER BY j.submit_time ASC
LIMIT 1;

-- 抢占 CAS：PENDING -> DISPATCHING（记录 processing_server_instance）
UPDATE adhoc_query_job
SET processing_server_instance = ?,
    status = 'DISPATCHING'
WHERE job_id = ? AND status = 'PENDING';

-- 派发成功：DISPATCHING -> RUNNING（记录 executor_instance + dispatch_time）
UPDATE adhoc_query_job
SET executor_instance = ?,
    dispatch_time = NOW(),
    status = 'RUNNING'
WHERE job_id = ? AND status = 'DISPATCHING';

-- 派发全失败：回退 PENDING，等待下轮调度
UPDATE adhoc_query_job
SET status = 'PENDING'
WHERE job_id = ? AND status = 'DISPATCHING';

-- 查询可用 executor（UP + accepting + 支持目标引擎）
SELECT instance_id FROM adhoc_executor_instance
WHERE status = 'UP'
  AND accepting = 1
  AND FIND_IN_SET(?, engine_types)  -- engine_type
LIMIT 1;
```

### 12.5 性能说明

流控检查用子查询 COUNT，高并发下有性能开销。生产环境可考虑用 Redis 维护实时计数器，DB 校验作为兜底。

## 13. 数据模型

完整字段定义见模块 11（11-schema.md，权威）。本模块关键表：

### 13.1 adhoc_query_job（Job 主表）关键字段

| 字段 | 说明 |
|---|---|
| job_id | Job ID（PK） |
| user_id / user_name | 提交人 ID / 中文名（冗余） |
| sql_content | 原始 SQL（拆分前，含 SET/USE） |
| engine_type / engine_instance | KYUUBI/STARROCKS（显式指定）/ 实例（未指定用默认） |
| engine_params | JSON，session 初始参数 |
| status | PENDING/DISPATCHING/RUNNING/SUCCESS/PARTIAL_FAILED/FAILED/CANCELED |
| cancel_requested / cancel_requested_time | 取消请求标志位 / 时刻 |
| processing_server_instance | 抢占的 server |
| executor_instance | 执行 Job 的 executor |
| submit_time / validate_finish_time / dispatch_time / split_finish_time / start_time / finish_time / duration_ms | 阶段时间戳 |
| persistent_log_path | job log 存储 key |

**关键索引**：
- `idx_user_status`（user_id, status）
- `idx_status_submit`（status, submit_time）-- Job 抢占
- `idx_executor_status`（executor_instance, status）-- executor 宕机补偿
- `idx_processing_server_status`（processing_server_instance, status）-- server 宕机补偿
- `uk_client_request_id`（client_request_id）-- 幂等

### 13.2 adhoc_query_task（Task 主表）关键字段

| 字段 | 说明 |
|---|---|
| query_id | Task ID（PK） |
| job_id / segment_index | 所属 Job / Job 内序号 |
| prefix_sql | SET/USE 前缀语句（Job 内累积，不含 sql_content 本身） |
| sql_content / sql_hash | 单段 SQL（不含 prefix）/ SHA256(prefix_sql + sql_content)，结果复用用 |
| sql_type | DQL/DDL/DML/CTAS/AUX/SESSION_CONFIG/UNKNOWN |
| status / stage | 5 态 / 3 阶段（见 §3） |
| fail_stage / fail_reason_category / error_code / error_message | 失败定位（见 §6.1 / §10.4） |
| executor_instance / processing_server_instance | 执行的 executor / 派发的 server |
| reused_from_task_id | 命中复用时指向原 Task |
| enqueue_time / start_time / fetch_start_time / write_start_time / local_write_finish_time / oss_upload_time / finish_time / duration_ms | 阶段时间戳（见 §10.1.2） |

**关键索引**：
- `idx_job_segment`（job_id, segment_index）-- Job 内串行
- `idx_user_sql_hash_status_finish`（user_id, sql_hash, status, finish_time）-- 结果复用
- `idx_executor_status`（executor_instance, status）-- 宕机补偿

## 14. 与其他子系统的接口

### 14.1 SQL 解析与治理子系统

- server 前置校验时调用解析子系统做 g4 语法校验（整个 sql_content）
- server 前置校验时调用治理子系统做限流检查
- 解析失败 -> 返回 ADHOC_SQL_SYNTAX_ERROR 给前端，不创建 Job
- 限流超限 -> 返回 ADHOC_JOB_LIMIT_EXCEEDED 给前端，不创建 Job
- executor 拆分时调用解析子系统识别 SET/USE（SqlType=SESSION_CONFIG），合并为 prefix_sql

### 14.2 引擎路由子系统

- server 调度 Job 时调用引擎路由选 executor
- 用户指定 engine_instance：直接用
- 用户未指定 engine_instance：用该 engine_type 的默认实例（Apollo 配置）
- engine_type 只支持 KYUUBI 和 STARROCKS，用户提交时必须指定

### 14.3 结果存储子系统

- executor 在 stage=WRITING 时序列化结果（MAGIC+schema+rows，内存）后经 `StorageClient.uploadResult` 一次性上传，不写本地文件
- 对象存储是唯一持久化层（StorageClient SPI：local/aliyun 内置，可扩展）
- executor 直写 adhoc_result_summary 表（persistent_path=存储 key, storage_type 等）
- storage_type 2 态：NONE/PERSISTENT（结果上传成功即 PERSISTENT）
- 读路径：server 直读（`StorageClient.download` -> `AdhocResultReader` 分页），不转发 executor
- 序列化失败 -> fail_reason_category=WRITE_ERROR，fail_stage=WRITING，status=FAILED
- 上传失败 -> fail_reason_category=WRITE_ERROR，fail_stage=OSS_UPLOAD，status=FAILED（无本地兜底，结果丢失）
- 结果存储的详细策略详见模块 04

### 14.4 高可用子系统

- 本子系统提供 processing_server_instance / executor_instance / stage 时间戳字段
- 高可用子系统负责：
  - server 之间互相心跳，检测 server DOWN，扫描失联 server 的 Job（PENDING/DISPATCHING 重新调度，RUNNING 不影响）
  - executor 心跳超时检测，标记 executor DOWN，扫描关联 Task/Job 做失败传播（EXECUTOR_CRASHED + SKIPPED_DUE_TO_SESSION_LOSS）
  - 心跳对账（TASK_LOST 检测、写库失败兜底）
- 通过 CAS 接口操作 Task/Job 状态，本子系统提供字段定义

### 14.5 可观测子系统

- 本子系统在状态迁移时更新 task.status / stage / fail_stage + 时间戳
- executor 直写 DB，server 通过 DB 读取状态变化
- 不建 adhoc_query_event 表，事件流由 task 表字段 + job.log（executor 产生）覆盖
- 阶段时间戳供可观测子系统计算各阶段耗时（Job 进度时间线 API）
- 心跳带上 cpu/mem/disk_usage + running_tasks，供可观测子系统计算 executor 健康度
