# Log API

Incremental fetching of Task / Job execution logs. Common conventions (unified response, auth, error codes) are in [README.md](README.md).

Log sources:

- **Task log**: fetched in real time from the executor's in-memory buffer via gRPC while the Job is running; after terminal state, falls back to reading storage (OSS).
- **Job log**: read from storage (`persistent_log_path`, uploaded by the server after the Job reaches terminal state).

### Polling Contract (important)

`LogResponse` has two signal fields with different semantics:

| Field | Meaning |
|------|------|
| `hasMore` | Pure pagination signal: whether there are unread lines after `offset` in the current data source (this batch didn't read everything) |
| `complete` | Whether the log is **complete** (the Job has reached terminal state, the log is finalized and will not grow further) |

Client polling rules:

| Status combination | Action |
|----------|------|
| `hasMore=true` | Keep paginating (`offset += lines.length`) |
| `hasMore=false` and `complete=false` | This batch is read but the log is not finished; **keep polling** (call again later, offset unchanged) |
| `hasMore=false` and `complete=true` | Log is complete; **stop polling** |

> In abnormal cases (log collection not finalized) `complete` may remain `false` forever; the client should set a polling-timeout safety net to avoid infinite polling.

---

## POST /api/task/log

Fetch a single Task's execution log (engine execution detail, including SQL, fetch progress, error stacks).

**Auth**: user-identity header + ownership check (by the Task's parent Job).

### Request Parameters

| Field | Type | Required | Description |
|------|------|------|------|
| `taskId` | String | Yes | Task ID |
| `offset` | long | No | Line offset (0-based), default 0 |
| `limit` | int | No | Lines to fetch this time, default 100 |

### Response `data` (LogResponse)

| Field | Type | Description |
|------|------|------|
| `lines` | List\<String\> | Log lines |
| `offset` | long | Line offset for this request (echoed) |
| `limit` | int | Lines fetched this time (echoed) |
| `hasMore` | boolean | Pagination signal (see polling contract) |
| `complete` | boolean | Whether the log is complete |

### curl Example

```bash
curl -X POST http://localhost:8080/api/task/log \
  -H "Content-Type: application/json" \
  -H "X-Adhoc-User-Id: u001" \
  -d '{ "taskId": "Task_8c1d5e...", "offset": 0, "limit": 100 }'
```

Response (excerpt):

```json
{
  "code": 1,
  "msg": "操作成功",
  "data": {
    "lines": [
      "[executor] [INFO] [job=Job_3f2a9c][task=Task_8c1d5e] submitted sql: SELECT order_id, amount FROM dwd_order ...",
      "[executor] [INFO] [job=Job_3f2a9c][task=Task_8c1d5e] fetched 100 rows"
    ],
    "offset": 0,
    "limit": 100,
    "hasMore": false,
    "complete": true
  }
}
```

Possible error codes: `ADHOC_JOB_NOT_FOUND`, `ADHOC_JOB_FORBIDDEN`, `ADHOC_LOG_INCOMPLETE`, `ADHOC_EXECUTOR_READ_BUSY` (see the full error-code table in [README.md](README.md)).

---

## POST /api/job/log

Fetch the entire Job's log (Task-level logs aggregated by segment index; the run view across the Job's full lifecycle).

**Auth**: user-identity header + ownership check.

### Request Parameters

| Field | Type | Required | Description |
|------|------|------|------|
| `jobId` | String | Yes | Job ID |
| `offset` | long | No | Line offset (0-based), default 0 |
| `limit` | int | No | Lines to fetch this time, default 1000 |

### Response `data` (LogResponse)

Same structure as `/api/task/log` (lines / offset / limit / hasMore / complete); same polling contract.

### curl Example

```bash
curl -X POST http://localhost:8080/api/job/log \
  -H "Content-Type: application/json" \
  -H "X-Adhoc-User-Id: u001" \
  -d '{ "jobId": "Job_3f2a9c...", "offset": 0, "limit": 1000 }'
```

Possible error codes: same as `/api/task/log`.

---

[English](log.md) | [中文](../../zh/api/log.md)
