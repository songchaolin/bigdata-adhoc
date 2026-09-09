# Job / Task API

Job lifecycle and Task detail queries. Common conventions (unified response, auth, error codes) are in [README.md](README.md).

## Status Reference

**Job state transitions**:

```
PENDING → DISPATCHING → RUNNING → SUCCESS / PARTIAL_FAILED / FAILED / CANCELED
```

| Status | Description |
|------|------|
| `PENDING` | Persisted, awaiting scheduling |
| `DISPATCHING` | Claimed by a server via CAS preemption, being dispatched to an executor |
| `RUNNING` | Executor is running it |
| `SUCCESS` | All Tasks succeeded |
| `PARTIAL_FAILED` | Some Tasks succeeded, some failed |
| `FAILED` | All Tasks failed (or overall execution failure) |
| `CANCELED` | Cancelled by the user |

**Task status**: `PENDING` / `RUNNING` / `SUCCESS` / `FAILED` / `CANCELED`. After a segment fails, subsequent Tasks are no longer executed; their `failReasonCategory` is marked `SKIPPED_DUE_TO_PRIOR_FAILURE` (failure propagation, see [FAQ](../faq.md)).

---

## POST /api/job

Submit a Job (asynchronous; acceptance is returned on receipt). The original SQL is submitted and split into multiple Tasks on the executor side, executed sequentially.

**Auth**: user-identity header (`X-Adhoc-User-Id`).

### Request Parameters

| Field | Type | Required | Description |
|------|------|------|------|
| `sqlContent` | String | Yes | Original SQL (may contain multiple segments, semicolon-separated) |
| `engineType` | String | Yes | `KYUUBI` / `STARROCKS` |
| `engineInstance` | String | No | Engine instance name (e.g. `kyuubi-02`); empty = default instance |
| `clientRequestId` | String | No | Idempotency key: a duplicate submission with the same value returns the original Job |
| `fileId` | String | No | Source file node ID (SQL console script source tracing) |
| `userId` / `userName` | String | No | SDK-populated fields; the server overrides them with the request headers, so REST callers need not pass them |

### Response `data` (JobSubmitResponse)

| Field | Type | Description |
|------|------|------|
| `jobId` | String | Job ID; the credential for subsequent queries/cancel |

### curl Example

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

Response:

```json
{ "code": 1, "msg": "操作成功", "data": { "jobId": "Job_3f2a9c..." } }
```

Possible error codes: `ADHOC_ENGINE_TYPE_REQUIRED`, `ADHOC_ENGINE_TYPE_INVALID`, `ADHOC_ENGINE_INSTANCE_NOT_FOUND`, `ADHOC_JOB_NO_EXECUTABLE_SQL`, `ADHOC_JOB_TOO_MANY_TASKS`, `ADHOC_JOB_LIMIT_EXCEEDED`, `ADHOC_SQL_DANGEROUS_STATEMENT` (see the full error-code table in [README.md](README.md)).

---

## POST /api/job/page

Paginated query of the **current user's** Job list.

**Auth**: user-identity header; results are filtered by the current user.

### Request Parameters

Inherits the pagination base:

| Field | Type | Required | Description |
|------|------|------|------|
| `current` | Long | No | Page number, default 1 |
| `size` | Long | No | Page size, default 10, 1–100 |
| `status` | String | No | Status filter: `PENDING`/`DISPATCHING`/`RUNNING`/`SUCCESS`/`PARTIAL_FAILED`/`FAILED`/`CANCELED` |
| `engineType` | String | No | Engine filter: `KYUUBI`/`STARROCKS` |
| `fileNodeId` | String | No | Source script (file node) ID filter |

### Response `data` (MyBatis-Plus IPage structure)

| Field | Type | Description |
|------|------|------|
| `records` | List\<JobVO\> | Current page data |
| `total` | Long | Total count |
| `size` / `current` | Long | Page size / current page number |
| `pages` | Long | Total pages |

**JobVO** fields:

| Field | Type | Description |
|------|------|------|
| `jobId` | String | Job ID |
| `userId` / `userName` | String | Submitter |
| `engineType` | String | Engine type |
| `status` | String | Job status |
| `submitTime` / `startTime` / `finishTime` | Date | Submit / start / finish time |
| `durationMs` | Long | Total duration (ms) |
| `executorInstance` | String | Executor instance that ran this Job |

### curl Example

```bash
curl -X POST http://localhost:8080/api/job/page \
  -H "Content-Type: application/json" \
  -H "X-Adhoc-User-Id: u001" \
  -d '{ "current": 1, "size": 10, "status": "RUNNING" }'
```

---

## POST /api/job/detail

Query a single Job's detail (with all Task summaries).

**Auth**: user-identity header + ownership check (self or admin).

### Request Parameters

| Field | Type | Required | Description |
|------|------|------|------|
| `jobId` | String | Yes | Job ID |

### Response `data` (JobDetailResponse)

| Field | Type | Description |
|------|------|------|
| `jobId` | String | Job ID |
| `status` | String | Job status |
| `sqlContent` | String | Original SQL (before segmentation) |
| `engineType` | String | Engine type |
| `submitTime` | Date | Submit time |
| `tasks` | List\<TaskSummary\> | Task summaries, ordered by segment index |

**TaskSummary** fields:

| Field | Type | Description |
|------|------|------|
| `taskId` | String | Task ID |
| `segmentIndex` | Integer | Segment index (0-based) |
| `status` | String | Task status |
| `failStage` | String | Failure stage: `DISPATCH`/`SPLIT`/`EXECUTING`/`FETCHING`/`WRITING`/`OSS_UPLOAD` |
| `sqlType` | String | SQL type: `DQL`/`DDL`/`DML`/`CTAS`/`SESSION_CONFIG`/`AUX`, etc. |
| `hasResultSet` | Boolean | Whether there is a result set (determines whether `/api/task/result` can be called) |
| `sqlContent` | String | The SQL actually executed for this segment |
| `prefixSql` | String | Accumulated SET/USE prefix |
| `resultRows` | Long | Result row count (DQL; null for DDL/DML) |
| `affectedRows` | Long | Affected row count (DDL/DML; null for DQL) |
| `durationMs` | Long | Execution duration (ms) |
| `errorMessage` | String | Failure reason (when FAILED) |

### curl Example

```bash
curl -X POST http://localhost:8080/api/job/detail \
  -H "Content-Type: application/json" \
  -H "X-Adhoc-User-Id: u001" \
  -d '{ "jobId": "Job_3f2a9c..." }'
```

Possible error codes: `ADHOC_JOB_NOT_FOUND`, `ADHOC_JOB_FORBIDDEN`.

---

## POST /api/job/status

Lightweight query of a Job's current status (for polling; smaller response than detail).

**Auth**: user-identity header + ownership check.

### Request Parameters

| Field | Type | Required | Description |
|------|------|------|------|
| `jobId` | String | Yes | Job ID |

### Response `data` (JobStatusResponse)

| Field | Type | Description |
|------|------|------|
| `jobId` | String | Job ID |
| `status` | String | Job status |

### curl Example

```bash
curl -X POST http://localhost:8080/api/job/status \
  -H "Content-Type: application/json" \
  -H "X-Adhoc-User-Id: u001" \
  -d '{ "jobId": "Job_3f2a9c..." }'
```

Possible error codes: `ADHOC_JOB_NOT_FOUND`, `ADHOC_JOB_FORBIDDEN`.

---

## POST /api/job/progress

Job execution progress: Job-level and Task-level **stage timeline** (DAG + per-stage duration), for the frontend to render progress bars.

**Auth**: user-identity header + ownership check.

### Request Parameters

| Field | Type | Required | Description |
|------|------|------|------|
| `jobId` | String | Yes | Job ID |

### Response `data` (JobProgressResponse)

| Field | Type | Description |
|------|------|------|
| `jobId` | String | Job ID |
| `status` | String | Job status |
| `currentStage` | String | Job's current stage (JobPhase) |
| `stages` | List\<StageTimeline\> | Job-level stage timeline |
| `tasks` | List\<TaskProgress\> | Task-level progress (ordered by segmentIndex) |

**StageTimeline** fields:

| Field | Type | Description |
|------|------|------|
| `stage` | String | Stage code (name() of JobPhase / TaskPhase) |
| `name` | String | Display name (see stage table below) |
| `status` | String | Stage status (StageState) |
| `startTime` / `endTime` | Date | Start/end time (null if not started) |
| `durationMs` | Long | Duration (ms); for in-progress stages this is a real-time "up-to-now" value |
| `duration` | String | Human-readable duration (e.g. `34.77s`, `5m 23s`) |

**TaskProgress** fields: `taskId`, `segmentIndex` (segment index), `status` (Task status), `currentStage` (TaskPhase), `stages` (List\<StageTimeline\>).

**JobPhase stages** (Job-level timeline):

| Stage code | Name | Meaning |
|--------|--------|------|
| `SUBMIT` | Submit | Persisted |
| `QUEUE` | Queue | Wait from submit to dispatch |
| `DISPATCH` | Dispatch | Server dispatch → executor segmentation complete |
| `RUNNING` | Running | Executing |
| `FINISH` | Finish | Terminal state |

**TaskPhase stages** (Task-level timeline):

| Stage code | Name | Meaning |
|--------|--------|------|
| `ENQUEUE` | Enqueue | Task created |
| `EXECUTING` | Execute | Engine executing the main query |
| `FETCHING` | Fetch | Fetching result set |
| `WRITING` | Write | Result serialization & upload |
| `FINISH` | Finish | Terminal state |

**StageState**: `DONE` (completed) / `RUNNING` (in progress) / `PENDING` (not started) / `SKIPPED` (skipped, e.g. FETCHING/WRITING for a Task with no result set) / `FAILED` (failure point) / `CANCELED` (cancel point).

### curl Example

```bash
curl -X POST http://localhost:8080/api/job/progress \
  -H "Content-Type: application/json" \
  -H "X-Adhoc-User-Id: u001" \
  -d '{ "jobId": "Job_3f2a9c..." }'
```

Possible error codes: `ADHOC_JOB_NOT_FOUND`, `ADHOC_JOB_FORBIDDEN`.

---

## POST /api/job/cancel

Request to cancel a Job. Sets the cancel flag: the running Task is interrupted by the executor (Kyuubi closes the underlying connection; StarRocks calls `Statement.cancel`), and segments not yet started are no longer executed.

**Auth**: user-identity header + ownership check.

### Request Parameters

| Field | Type | Required | Description |
|------|------|------|------|
| `jobId` | String | Yes | Job ID |

### Response `data`

Boolean: `true` means the cancel request was accepted (takes effect asynchronously; the `CANCELED` status polled via `job/status` is authoritative).

### curl Example

```bash
curl -X POST http://localhost:8080/api/job/cancel \
  -H "Content-Type: application/json" \
  -H "X-Adhoc-User-Id: u001" \
  -d '{ "jobId": "Job_3f2a9c..." }'
```

Possible error codes: `ADHOC_JOB_NOT_FOUND`, `ADHOC_JOB_FORBIDDEN`.

---

## POST /api/task/detail

Query a single Task's full information (more execution stats, failure classification, and time detail than the TaskSummary in Job detail).

**Auth**: user-identity header + ownership check (by the Task's parent Job).

### Request Parameters

| Field | Type | Required | Description |
|------|------|------|------|
| `taskId` | String | Yes | Task ID |

### Response `data` (TaskDetailResponse)

| Group | Field | Type | Description |
|------|------|------|------|
| Basic | `taskId` / `jobId` | String | Task / parent Job ID |
| | `segmentIndex` | Integer | Segment index |
| | `status` | String | Task status |
| | `sqlType` | String | SQL type |
| | `hasResultSet` | Boolean | Whether there is a result set |
| | `sqlContent` / `prefixSql` | String | This segment's SQL / accumulated SET/USE prefix |
| Execution stats | `resultRows` / `affectedRows` | Long | Result rows (DQL) / affected rows (DDL/DML) |
| | `scanRows` / `scanBytes` | Long | Scanned rows / scanned bytes (only when reported by the engine) |
| | `durationMs` | Long | Execution duration (ms) |
| Failure | `failStage` | String | Failure stage |
| | `failReasonCategory` | String | Failure reason category (e.g. `ENGINE_ERROR`, `SKIPPED_DUE_TO_PRIOR_FAILURE`) |
| | `errorCode` | String | Error code (`ADHOC_*`) |
| | `errorMessage` | String | Failure detail |
| Reuse | `reusedFromTaskId` | String | When result reuse hits, the source Task ID reused |
| Engine | `engineType` / `engineInstance` / `executorInstance` | String | Engine type / engine instance / executing executor |
| Time | `enqueueTime` / `startTime` / `fetchStartTime` / `writeStartTime` / `finishTime` | Date | Enqueue / start executing / start fetching / start writing / finish time |

### curl Example

```bash
curl -X POST http://localhost:8080/api/task/detail \
  -H "Content-Type: application/json" \
  -H "X-Adhoc-User-Id: u001" \
  -d '{ "taskId": "Task_8c1d5e..." }'
```

Possible error codes: `ADHOC_JOB_NOT_FOUND`, `ADHOC_JOB_FORBIDDEN`.

---

[English](job.md) | [中文](../../zh/api/job.md)
