# Result API

Query the result sets of a Task / Job. Common conventions (unified response, auth, error codes) are in [README.md](README.md).

Results are serialized and uploaded to storage (local / OSS) by the executor after execution completes; the server reads them on demand and returns pages. DDL/DML Tasks have no result set (`hasResultSet=false`); only `affectedRows` is available.

---

## POST /api/task/result

Paginated query of a single Task's result set.

**Auth**: user-identity header + ownership check (by the Task's parent Job).

### Request Parameters

Inherits the pagination base:

| Field | Type | Required | Description |
|------|------|------|------|
| `taskId` | String | Yes | Task ID |
| `current` | Long | No | Page number (1-based), default 1 |
| `size` | Long | No | Rows per page, default 10, 1–100 |

### Response `data` (ResultResponse)

| Field | Type | Description |
|------|------|------|
| `schema` | List\<ColumnDto\> | Column definitions |
| `rows` | List\<String\> | Data rows; **each row is a JSON-encoded array string** (see example below) |
| `current` / `size` | Long | Current page number / rows per page (echoes the request) |
| `totalRows` | long | Total row count |
| `hasMore` | boolean | Whether there is a next page |

**ColumnDto** fields:

| Field | Type | Description |
|------|------|------|
| `colIndex` | int | Column index (0-based) |
| `colName` | String | Column name |
| `colType` | String | Column type (type name returned by the engine) |

### Response Example

```json
{
  "code": 1,
  "msg": "操作成功",
  "data": {
    "schema": [
      { "colIndex": 0, "colName": "order_id", "colType": "string" },
      { "colIndex": 1, "colName": "amount", "colType": "decimal" }
    ],
    "rows": [
      "[\"ORD20260901000001\", 129.90]",
      "[\"ORD20260901000002\", 89.00]"
    ],
    "current": 1,
    "size": 10,
    "totalRows": 2,
    "hasMore": false
  }
}
```

When parsing `rows`, the client must JSON-deserialize each element again to obtain the row array.

### curl Example

```bash
curl -X POST http://localhost:8080/api/task/result \
  -H "Content-Type: application/json" \
  -H "X-Adhoc-User-Id: u001" \
  -d '{ "taskId": "Task_8c1d5e...", "current": 1, "size": 100 }'
```

Possible error codes: `ADHOC_JOB_NOT_FOUND`, `ADHOC_JOB_FORBIDDEN`, `ADHOC_RESULT_NO_RESULT`, `ADHOC_RESULT_INCOMPLETE`, `ADHOC_RESULT_LOST` (see the full error-code table in [README.md](README.md)).

---

## POST /api/job/result

Job result aggregation: returns the **first page of results for all Tasks** under the Job at once (ordered by segment index), suitable for the frontend to render a multi-segment query result preview in one pass. To view a Task's full results, call `/api/task/result` to paginate.

**Auth**: user-identity header + ownership check.

### Request Parameters

| Field | Type | Required | Description |
|------|------|------|------|
| `jobId` | String | Yes | Job ID |
| `size` | Integer | No | Result rows returned per Task (first page), default 20, 1–100 |

### Response `data` (JobResultResponse)

| Field | Type | Description |
|------|------|------|
| `jobId` | String | Job ID |
| `status` | String | Job status |
| `tasks` | List\<TaskResultItem\> | Per-Task result items, ordered by segmentIndex |

**TaskResultItem** fields:

| Group | Field | Type | Description |
|------|------|------|------|
| Task meta | `taskId` / `segmentIndex` / `status` / `failStage` / `sqlType` | String/Integer | Same as [TaskSummary](job.md) |
| | `hasResultSet` / `sqlContent` / `prefixSql` | Boolean/String | Same as above |
| | `affectedRows` / `durationMs` / `errorMessage` | Long/String | Same as above |
| First result page | `schema` | List\<ColumnDto\> | Column definitions (only when `hasResultSet=true`) |
| | `rows` | List\<String\> | Result rows (JSON-encoded, only when `hasResultSet=true`) |
| | `totalRows` | Long | Total row count |
| | `hasMore` | Boolean | Whether there are more rows |

### curl Example

```bash
curl -X POST http://localhost:8080/api/job/result \
  -H "Content-Type: application/json" \
  -H "X-Adhoc-User-Id: u001" \
  -d '{ "jobId": "Job_3f2a9c...", "size": 20 }'
```

Possible error codes: same as `/api/task/result`.

---

[English](result.md) | [中文](../../zh/api/result.md)
