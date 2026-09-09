# Job / Task 接口

Job 生命周期与 Task 详情查询。通用约定（统一响应、鉴权、错误码）见 [README.md](README.md)。

## 状态说明

**Job 状态流转**：

```
PENDING → DISPATCHING → RUNNING → SUCCESS / PARTIAL_FAILED / FAILED / CANCELED
```

| 状态 | 说明 |
|------|------|
| `PENDING` | 已入库，等待调度 |
| `DISPATCHING` | 已被某 server CAS 抢占，正在派发到 executor |
| `RUNNING` | executor 执行中 |
| `SUCCESS` | 全部 Task 成功 |
| `PARTIAL_FAILED` | 部分 Task 成功、部分失败 |
| `FAILED` | 全部 Task 失败（或整体执行失败） |
| `CANCELED` | 用户取消 |

**Task 状态**：`PENDING` / `RUNNING` / `SUCCESS` / `FAILED` / `CANCELED`。当某段失败后，其后的 Task 不再执行，`failReasonCategory` 标记为 `SKIPPED_DUE_TO_PRIOR_FAILURE`（失败传播，见 [FAQ](../faq.md)）。

---

## POST /api/job

提交 Job（异步，返回即受理）。SQL 原文提交后在 executor 侧拆分为多个 Task 顺序执行。

**鉴权**：用户身份头（`X-Adhoc-User-Id`）。

### 请求参数

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `sqlContent` | String | 是 | SQL 原文（可含多段，分号分隔） |
| `engineType` | String | 是 | `KYUUBI` / `STARROCKS` |
| `engineInstance` | String | 否 | 引擎实例名（如 `kyuubi-02`），空则用默认实例 |
| `clientRequestId` | String | 否 | 幂等键：重复提交同值返回原 Job |
| `fileId` | String | 否 | 来源文件节点 ID（SQL 控制台脚本来源追溯） |
| `userId` / `userName` | String | 否 | SDK 填充字段，server 以请求头为准覆盖，REST 调用无需传 |

### 响应 `data`（JobSubmitResponse）

| 字段 | 类型 | 说明 |
|------|------|------|
| `jobId` | String | Job ID，后续查询/取消的凭证 |

### curl 示例

```bash
curl -X POST http://localhost:8080/api/job \
  -H "Content-Type: application/json" \
  -H "X-Adhoc-User-Id: u001" \
  -H "X-Adhoc-User-Name: 张三" \
  -d '{
    "sqlContent": "SELECT order_id, amount FROM dwd_order WHERE dt = '"'"'2026-09-01'"'"' LIMIT 100;",
    "engineType": "KYUUBI",
    "clientRequestId": "demo-req-001"
  }'
```

返回：

```json
{ "code": 1, "msg": "操作成功", "data": { "jobId": "Job_3f2a9c..." } }
```

可能出现的错误码：`ADHOC_ENGINE_TYPE_REQUIRED`、`ADHOC_ENGINE_TYPE_INVALID`、`ADHOC_ENGINE_INSTANCE_NOT_FOUND`、`ADHOC_JOB_NO_EXECUTABLE_SQL`、`ADHOC_JOB_TOO_MANY_TASKS`、`ADHOC_JOB_LIMIT_EXCEEDED`、`ADHOC_SQL_DANGEROUS_STATEMENT`（完整错误码表见 [README.md](README.md)）。

---

## POST /api/job/page

分页查询**本人**的 Job 列表。

**鉴权**：用户身份头；结果按当前用户过滤。

### 请求参数

继承分页基类：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `current` | Long | 否 | 页码，默认 1 |
| `size` | Long | 否 | 每页条数，默认 10，1~100 |
| `status` | String | 否 | 状态过滤：`PENDING`/`DISPATCHING`/`RUNNING`/`SUCCESS`/`PARTIAL_FAILED`/`FAILED`/`CANCELED` |
| `engineType` | String | 否 | 引擎过滤：`KYUUBI`/`STARROCKS` |
| `fileNodeId` | String | 否 | 来源脚本（文件节点）ID 过滤 |

### 响应 `data`（MyBatis-Plus IPage 结构）

| 字段 | 类型 | 说明 |
|------|------|------|
| `records` | List\<JobVO\> | 当前页数据 |
| `total` | Long | 总条数 |
| `size` / `current` | Long | 每页条数 / 当前页码 |
| `pages` | Long | 总页数 |

**JobVO** 字段：

| 字段 | 类型 | 说明 |
|------|------|------|
| `jobId` | String | Job ID |
| `userId` / `userName` | String | 提交人 |
| `engineType` | String | 引擎类型 |
| `status` | String | Job 状态 |
| `submitTime` / `startTime` / `finishTime` | Date | 提交 / 开始 / 结束时间 |
| `durationMs` | Long | 总耗时（ms） |
| `executorInstance` | String | 执行该 Job 的 executor 实例 |

### curl 示例

```bash
curl -X POST http://localhost:8080/api/job/page \
  -H "Content-Type: application/json" \
  -H "X-Adhoc-User-Id: u001" \
  -d '{ "current": 1, "size": 10, "status": "RUNNING" }'
```

---

## POST /api/job/detail

查询单个 Job 详情（含全部 Task 摘要）。

**鉴权**：用户身份头 + 归属校验（本人或管理员）。

### 请求参数

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `jobId` | String | 是 | Job ID |

### 响应 `data`（JobDetailResponse）

| 字段 | 类型 | 说明 |
|------|------|------|
| `jobId` | String | Job ID |
| `status` | String | Job 状态 |
| `sqlContent` | String | SQL 原文（拆分前） |
| `engineType` | String | 引擎类型 |
| `submitTime` | Date | 提交时间 |
| `tasks` | List\<TaskSummary\> | Task 摘要，按段序排列 |

**TaskSummary** 字段：

| 字段 | 类型 | 说明 |
|------|------|------|
| `taskId` | String | Task ID |
| `segmentIndex` | Integer | 段序号（0 起） |
| `status` | String | Task 状态 |
| `failStage` | String | 失败阶段：`DISPATCH`/`SPLIT`/`EXECUTING`/`FETCHING`/`WRITING`/`OSS_UPLOAD` |
| `sqlType` | String | SQL 类型：`DQL`/`DDL`/`DML`/`CTAS`/`SESSION_CONFIG`/`AUX` 等 |
| `hasResultSet` | Boolean | 是否有结果集（决定能否调 `/api/task/result`） |
| `sqlContent` | String | 该段实际执行的 SQL |
| `prefixSql` | String | 累积的 SET/USE 前缀 |
| `resultRows` | Long | 结果行数（DQL；DDL/DML 为 null） |
| `affectedRows` | Long | 影响行数（DDL/DML；DQL 为 null） |
| `durationMs` | Long | 执行耗时（ms） |
| `errorMessage` | String | 失败原因（FAILED 时） |

### curl 示例

```bash
curl -X POST http://localhost:8080/api/job/detail \
  -H "Content-Type: application/json" \
  -H "X-Adhoc-User-Id: u001" \
  -d '{ "jobId": "Job_3f2a9c..." }'
```

可能出现的错误码：`ADHOC_JOB_NOT_FOUND`、`ADHOC_JOB_FORBIDDEN`。

---

## POST /api/job/status

轻量查询 Job 当前状态（轮询用，比 detail 响应小）。

**鉴权**：用户身份头 + 归属校验。

### 请求参数

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `jobId` | String | 是 | Job ID |

### 响应 `data`（JobStatusResponse）

| 字段 | 类型 | 说明 |
|------|------|------|
| `jobId` | String | Job ID |
| `status` | String | Job 状态 |

### curl 示例

```bash
curl -X POST http://localhost:8080/api/job/status \
  -H "Content-Type: application/json" \
  -H "X-Adhoc-User-Id: u001" \
  -d '{ "jobId": "Job_3f2a9c..." }'
```

可能出现的错误码：`ADHOC_JOB_NOT_FOUND`、`ADHOC_JOB_FORBIDDEN`。

---

## POST /api/job/progress

Job 执行进度：Job 级与 Task 级**阶段时间线**（DAG + 各阶段耗时），供前端渲染进度条。

**鉴权**：用户身份头 + 归属校验。

### 请求参数

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `jobId` | String | 是 | Job ID |

### 响应 `data`（JobProgressResponse）

| 字段 | 类型 | 说明 |
|------|------|------|
| `jobId` | String | Job ID |
| `status` | String | Job 状态 |
| `currentStage` | String | Job 当前阶段（JobPhase） |
| `stages` | List\<StageTimeline\> | Job 级阶段时间线 |
| `tasks` | List\<TaskProgress\> | Task 级进度（按 segmentIndex 排列） |

**StageTimeline** 字段：

| 字段 | 类型 | 说明 |
|------|------|------|
| `stage` | String | 阶段码（JobPhase / TaskPhase 的 name()） |
| `name` | String | 中文展示名（见下方阶段表） |
| `status` | String | 阶段状态（StageState） |
| `startTime` / `endTime` | Date | 起止时间（未开始为 null） |
| `durationMs` | Long | 耗时（ms）；进行中阶段为"截至当前"的实时值 |
| `duration` | String | 人类可读时长（如 `34.77s`、`5m 23s`） |

**TaskProgress** 字段：`taskId`、`segmentIndex`（段序号）、`status`（Task 状态）、`currentStage`（TaskPhase）、`stages`（List\<StageTimeline\>）。

**JobPhase 阶段**（Job 级时间线）：

| 阶段码 | 中文名 | 含义 |
|--------|--------|------|
| `SUBMIT` | 提交 | 入库完成 |
| `QUEUE` | 排队 | submit → dispatch 的等待 |
| `DISPATCH` | 调度 | server 派发 → executor 拆分完成 |
| `RUNNING` | 运行 | 执行中 |
| `FINISH` | 完成 | 终态 |

**TaskPhase 阶段**（Task 级时间线）：

| 阶段码 | 中文名 | 含义 |
|--------|--------|------|
| `ENQUEUE` | 入队 | Task 创建 |
| `EXECUTING` | 执行 | 引擎执行主查询 |
| `FETCHING` | 拉取 | 拉取结果集 |
| `WRITING` | 写入 | 结果序列化与上传 |
| `FINISH` | 完成 | 终态 |

**StageState**：`DONE`（已完成）/ `RUNNING`（进行中）/ `PENDING`（未开始）/ `SKIPPED`（跳过，如无结果集 Task 的 FETCHING/WRITING）/ `FAILED`（失败点）/ `CANCELED`（取消点）。

### curl 示例

```bash
curl -X POST http://localhost:8080/api/job/progress \
  -H "Content-Type: application/json" \
  -H "X-Adhoc-User-Id: u001" \
  -d '{ "jobId": "Job_3f2a9c..." }'
```

可能出现的错误码：`ADHOC_JOB_NOT_FOUND`、`ADHOC_JOB_FORBIDDEN`。

---

## POST /api/job/cancel

请求取消 Job。置取消标志位：执行中的 Task 由 executor 中断（Kyuubi 关底层连接，StarRocks 调 Statement.cancel），未开始的段不再执行。

**鉴权**：用户身份头 + 归属校验。

### 请求参数

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `jobId` | String | 是 | Job ID |

### 响应 `data`

Boolean：`true` 表示取消请求已受理（异步生效，以 `job/status` 轮询到的 `CANCELED` 为准）。

### curl 示例

```bash
curl -X POST http://localhost:8080/api/job/cancel \
  -H "Content-Type: application/json" \
  -H "X-Adhoc-User-Id: u001" \
  -d '{ "jobId": "Job_3f2a9c..." }'
```

可能出现的错误码：`ADHOC_JOB_NOT_FOUND`、`ADHOC_JOB_FORBIDDEN`。

---

## POST /api/task/detail

查询单个 Task 的完整信息（比 Job 详情中的 TaskSummary 多执行统计、失败分类、时间明细）。

**鉴权**：用户身份头 + 归属校验（按 Task 所属 Job 判定）。

### 请求参数

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `taskId` | String | 是 | Task ID |

### 响应 `data`（TaskDetailResponse）

| 分类 | 字段 | 类型 | 说明 |
|------|------|------|------|
| 基本 | `taskId` / `jobId` | String | Task / 所属 Job ID |
| | `segmentIndex` | Integer | 段序号 |
| | `status` | String | Task 状态 |
| | `sqlType` | String | SQL 类型 |
| | `hasResultSet` | Boolean | 是否有结果集 |
| | `sqlContent` / `prefixSql` | String | 该段 SQL / 累积 SET/USE 前缀 |
| 执行统计 | `resultRows` / `affectedRows` | Long | 结果行数（DQL）/ 影响行数（DDL/DML） |
| | `scanRows` / `scanBytes` | Long | 扫描行数 / 扫描字节数（引擎上报时才有） |
| | `durationMs` | Long | 执行耗时（ms） |
| 失败 | `failStage` | String | 失败阶段 |
| | `failReasonCategory` | String | 失败原因分类（如 `ENGINE_ERROR`、`SKIPPED_DUE_TO_PRIOR_FAILURE`） |
| | `errorCode` | String | 错误码（`ADHOC_*`） |
| | `errorMessage` | String | 失败详情 |
| 复用 | `reusedFromTaskId` | String | 命中结果复用时，被复用的源 Task ID |
| 引擎 | `engineType` / `engineInstance` / `executorInstance` | String | 引擎类型 / 引擎实例 / 执行 executor |
| 时间 | `enqueueTime` / `startTime` / `fetchStartTime` / `writeStartTime` / `finishTime` | Date | 入队 / 开始执行 / 开始拉取 / 开始写入 / 完成时间 |

### curl 示例

```bash
curl -X POST http://localhost:8080/api/task/detail \
  -H "Content-Type: application/json" \
  -H "X-Adhoc-User-Id: u001" \
  -d '{ "taskId": "Task_8c1d5e..." }'
```

可能出现的错误码：`ADHOC_JOB_NOT_FOUND`、`ADHOC_JOB_FORBIDDEN`。
