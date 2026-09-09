# API Reference

bigdata-adhoc external REST API reference. All endpoints are served by **adhoc-server** (default port `8080`).

> A running service exposes online Swagger docs at `http://<server-host>:8080/doc.html` (Knife4j).
> This static doc shares the same source (Controller annotations); for offline reading, this document is authoritative.

[English](README.md) | [中文](../zh/api/README.md)

## Contents

| Doc | Content |
|------|------|
| [job.md](job.md) | Job submit / paginate / detail / status / progress / cancel, Task detail |
| [result.md](result.md) | Task result pagination, Job result aggregation |
| [log.md](log.md) | Task / Job execution log (incremental polling) |
| [file.md](file.md) | File nodes (SQL script directory tree): create/delete/update / move / search / submit-by-file |
| [metadata.md](metadata.md) | Metadata autocomplete (database / table / column), runtime config query |
| [dashboard.md](dashboard.md) | Monitoring dashboard & SQL console endpoints (used by the built-in dashboard page) |

## Common Conventions

### Unified Response Structure

All endpoints return a unified `Result` wrapper:

```json
{
  "code": 1,
  "msg": "操作成功",
  "data": { }
}
```

| Field | Type | Description |
|------|------|------|
| `code` | Integer | `1` = success; any other value = failure |
| `msg` | String | Message text; on failure formatted as `"ADHOC_XXX: message"` |
| `data` | Object | Business data; type per endpoint; `null` on failure |

Common response codes:

| code | Meaning |
|------|------|
| `1` | Success |
| `0` | Business failure (`msg` carries an error-code prefix) |
| `300004` | Parameter validation failure (`@Valid`, msg includes field error details) |
| `600007` | Internal system error |

### Authentication

Endpoints fall into two auth categories:

**1. User-identity endpoints (`/api/**`, excluding `/api/metrics/**`)**

| Header | Required | Description |
|--------|------|------|
| `X-Adhoc-User-Id` | Yes | User identifier; missing returns `ADHOC_USER_CONTEXT_MISSING` |
| `X-Adhoc-User-Name` | No | User display name, stored redundantly for display |

Headers are normally injected by a gateway / reverse proxy rather than passed directly by the caller (see [Deployment guide - nginx gateway](../deployment.md#nginx-gateway-and-user-header-injection)).

Read and cancel endpoints (job/detail, job/status, job/progress, job/cancel, task/detail, task/result, job/result, task/log, job/log) apply an **ownership check** on top of user identity: only the **owner** or an **admin** (configured via `adhoc.admin.user-ids`) may access another user's Job; otherwise `ADHOC_JOB_FORBIDDEN` is returned.

**2. Dashboard Token endpoints (`/dashboard/**`, `/api/metrics/**`)**

The header `X-Dashboard-Token` (or URL param `?token=`) is compared against the configured `adhoc.dashboard.access-token`; if the config is empty, auth is disabled. Failure returns HTTP 401. See [dashboard.md](dashboard.md).

### Request Method & Pagination

- Most endpoints use `POST + @RequestBody` (JSON); metadata and dashboard endpoints use `GET + Query params`.
- Paginated requests inherit a common base:

| Field | Type | Default | Description |
|------|------|------|------|
| `current` | Long | 1 | Page number |
| `size` | Long | 10 | Page size (max 100) |

### Status Enums

**JobStatus**: `PENDING` → `DISPATCHING` → `RUNNING` → `SUCCESS` / `PARTIAL_FAILED` / `FAILED` / `CANCELED`

**TaskStatus**: `PENDING` / `RUNNING` / `SUCCESS` / `FAILED` / `CANCELED` (includes `SKIPPED` semantics for failure-propagation skips; see each endpoint's `failReasonCategory`)

**EngineType**: `KYUUBI` (Hive ecosystem), `STARROCKS` (MySQL protocol)

## Error Codes

The error code in `msg` is the `AdhocErrorCode` enum name, usable as a machine-readable identifier for branching:

| Error code | Default message |
|--------|---------|
| `ADHOC_JOB_TOO_MANY_TASKS` | SQL segment count exceeds the per-submission limit |
| `ADHOC_JOB_LIMIT_EXCEEDED` | Job submission count exceeds the limit, please retry later |
| `ADHOC_JOB_NO_EXECUTABLE_SQL` | No executable SQL statement detected |
| `ADHOC_ENGINE_TYPE_REQUIRED` | Engine type must not be empty |
| `ADHOC_ENGINE_TYPE_INVALID` | Engine type is invalid |
| `ADHOC_ENGINE_PARAMS_INVALID` | Engine parameters are invalid |
| `ADHOC_ENGINE_INSTANCE_NOT_FOUND` | Engine instance not found or not configured, please check the instance name |
| `ADHOC_SQL_SYNTAX_ERROR` | SQL syntax error |
| `ADHOC_RESULT_NO_RESULT` | No result data found |
| `ADHOC_RESULT_INCOMPLETE` | Result not fully generated yet, please retry later |
| `ADHOC_RESULT_LOST` | Result data has been lost |
| `ADHOC_LOG_INCOMPLETE` | Log not fully generated yet, please retry later |
| `ADHOC_EXECUTOR_READ_BUSY` | Executor is busy reading, please retry later |
| `ADHOC_EXECUTOR_CRASHED` | Executor is down, task execution interrupted |
| `ADHOC_QUERY_TIMEOUT` | Query execution timed out |
| `ADHOC_SESSION_LOST` | Engine session has expired |
| `ADHOC_SERVER_CRASHED` | Scheduling service error, please retry later |
| `ADHOC_QUEUE_WAIT_TIMEOUT` | Queue wait timed out, please retry later |
| `ADHOC_SCHEDULE_NO_EXEC_AVAILABLE` | No executor available, please retry later |
| `ADHOC_ENGINE_SQL_TYPE_NOT_SUPPORTED` | The current engine does not support this SQL statement type |
| `ADHOC_SQL_DANGEROUS_STATEMENT` | Dangerous SQL statement detected and intercepted |
| `ADHOC_JOB_NOT_FOUND` | Job does not exist or has expired |
| `ADHOC_JOB_FORBIDDEN` | No permission to access this Job |
| `ADHOC_NODE_NOT_FOUND` | Node does not exist |
| `ADHOC_NODE_NAME_DUPLICATE` | Node name already exists |
| `ADHOC_NODE_NAME_INVALID` | Node name contains illegal characters |
| `ADHOC_DIRECTORY_NOT_EMPTY` | Directory is not empty, cannot delete |
| `ADHOC_ROOT_NODE_IMMUTABLE` | Root node is immutable |
| `ADHOC_PARENT_NOT_DIRECTORY` | Parent node is not a directory |
| `ADHOC_MOVE_TO_SELF_OR_CHILD` | Cannot move a node into itself or its descendant |
| `ADHOC_USER_CONTEXT_MISSING` | Missing user identity, please access via the gateway |
| `ADHOC_METADATA_QUERY_FAILED` | Metadata query failed |
| `ADHOC_METADATA_NOT_CONFIGURED` | Metadata service is not configured |
| `ADHOC_SQL_QUERY_ONLY_SELECT` | Only SELECT/SHOW/DESCRIBE query statements are supported |
| `ADHOC_SQL_QUERY_FAILED` | Query execution failed |

## Endpoint Index

### Job / Task (see [job.md](job.md))

| Method | Path | Description |
|------|------|------|
| POST | `/api/job` | Submit Job |
| POST | `/api/job/page` | Job pagination query |
| POST | `/api/job/detail` | Job detail (with Task summary) |
| POST | `/api/job/status` | Job status |
| POST | `/api/job/progress` | Job execution progress (stage timeline) |
| POST | `/api/job/cancel` | Cancel Job |
| POST | `/api/task/detail` | Task detail |

### Result (see [result.md](result.md))

| Method | Path | Description |
|------|------|------|
| POST | `/api/task/result` | Task result pagination |
| POST | `/api/job/result` | Job result aggregation (first page of each Task) |

### Log (see [log.md](log.md))

| Method | Path | Description |
|------|------|------|
| POST | `/api/task/log` | Task log incremental fetch |
| POST | `/api/job/log` | Job log incremental fetch |

### File Nodes (see [file.md](file.md))

| Method | Path | Description |
|------|------|------|
| POST | `/api/file/node/create` | Create node (directory/file) |
| POST | `/api/file/tree` | Query user directory tree |
| POST | `/api/file/nodes` | List child nodes |
| POST | `/api/file/node/get` | Query single node |
| POST | `/api/file/node/rename` | Rename |
| POST | `/api/file/node/move` | Move |
| POST | `/api/file/node/delete` | Delete (recycle bin) |
| POST | `/api/file/node/restore` | Restore from recycle bin |
| POST | `/api/file/nodes/search` | Search |
| POST | `/api/file/node/update` | Update file content/description |
| POST | `/api/file/submit` | Submit a Job using a file node's content |

### Metadata & Config (see [metadata.md](metadata.md))

| Method | Path | Description |
|------|------|------|
| GET | `/api/metadata/databases` | Database list |
| GET | `/api/metadata/tables` | Table list |
| GET | `/api/metadata/columns` | Column list |
| GET | `/api/config` | Runtime config query (masked) |

### Monitoring Dashboard (see [dashboard.md](dashboard.md))

`/api/metrics/**` provides 15 endpoints plus a SQL console, used by the built-in dashboard page, authenticated via Dashboard Token.

## Quick-start Example

```bash
# Submit a Kyuubi query Job
curl -X POST http://localhost:8080/api/job \
  -H "Content-Type: application/json" \
  -H "X-Adhoc-User-Id: u001" \
  -H "X-Adhoc-User-Name: 张三" \
  -d '{
    "sqlContent": "SELECT * FROM dwd_order LIMIT 10",
    "engineType": "KYUUBI"
  }'
```

Response:

```json
{
  "code": 1,
  "msg": "操作成功",
  "data": { "jobId": "Job_3f2a..." }
}
```

For more examples see each section doc. For Java calls, using the [SDK](../sdk.md) directly is recommended — no need to handle auth headers and response wrapping manually.
