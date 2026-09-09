# Monitoring Dashboard API

The `/api/metrics/**` series of endpoints are used by the built-in monitoring dashboard page (`/dashboard`), authenticated via Dashboard Token. Ops view; **no per-user filtering** (drill-through endpoints can query any user's Job/Task across users). External business callers generally do not call this group of endpoints directly.

← Back to [API reference home](README.md)

## Auth: Dashboard Token

| Item | Description |
|------|------|
| Header | `X-Dashboard-Token` (or URL param `?token=`) |
| Config | `adhoc.dashboard.access-token` (Apollo live-read); **empty = auth disabled** |
| Failure | HTTP 401, `{"code": -1, "msg": "dashboard token 无效或缺失"}` |

When accessing via a gateway in production, you need to configure a login-exemption whitelist for `/dashboard/**` and `/api/metrics/**` (see the [deployment guide](../deployment.md)).

## Endpoint Index

All are `GET + Query` params. **Time-window params**: `startMs` / `endMs` (epoch milliseconds, custom range, takes precedence) or `hours` (preset window, default 24) — pass one or the other; `startMs/endMs` takes precedence.

| Path | Params | Return description |
|------|------|---------|
| `/api/metrics/overview` | Time window | Overview: Job/Task status distribution, success rate, avg/max duration, duration buckets, engine distribution, real-time states (PENDING/RUNNING backlog), executor/server summary |
| `/api/metrics/trends` | Time window | Job hourly/daily submit & finish trend point list |
| `/api/metrics/task-trends` | Time window | Task hourly/daily submit & finish trend (same caliber as Job trends, for comparison) |
| `/api/metrics/executors` | `onlineOnly` (default true) | Executor cluster list: status/concurrency/utilization/heartbeat/JVM metrics |
| `/api/metrics/servers` | `onlineOnly` (default true) | Server cluster list: status/claimed count/active_jobs/heartbeat |
| `/api/metrics/topn` | Time window + `limit` (default 10) | TopN slow Jobs + active users |
| `/api/metrics/task-failures` | Time window | Task failure dimension distribution: fail_stage / error_code / sql_type |
| `/api/metrics/instance-load` | Time window | Per-instance load: Jobs and Tasks submitted per server/executor within the time window |
| `/api/metrics/jvm-series` | `instanceIds` + time window / `minutes` | JVM metric time series (see section below) |
| `/api/metrics/jobs` | Time window + filters + pagination | Drill-through: Job pagination detail (cross-user), supports status/engineType/duration-range filtering and sorting |
| `/api/metrics/job-detail` | `jobId` | Drill-through: Job detail (with Task summary, same structure as `/api/job/detail`) |
| `/api/metrics/job-progress` | `jobId` | Drill-through: Job execution progress (stage timeline) |
| `/api/metrics/task-detail` | `taskId` | Drill-through: Task detail (full fields) |
| `/api/metrics/job-log` | `jobId` + `offset` (default 0) + `limit` (default 1000) | Drill-through: Job log incremental fetch (running reads memory/forwards; terminal reads OSS) |
| `/api/metrics/task-log` | `taskId` + `offset` (default 0) + `limit` (default 100) | Drill-through: Task log (running goes via executor gRPC; terminal falls back to reading the job log) |

The drill-through endpoints (`/jobs` through `/task-log`) have the same return structure as the corresponding user-side endpoints ([job.md](job.md) / [log.md](log.md)); the only difference: system-level access (no ownership check, can query any user's Job/Task).

### JVM Metric Time Series (/jvm-series)

Query JVM sampling history per instance (5s sampling, retention 2 hours).

| Param | Type | Required | Description |
|------|------|------|------|
| `instanceIds` | List\<String\> | No | Instance ID list (server/executor instance IDs; obtainable from `/executors`, `/servers`) |
| `startMs` / `endMs` | Long | No | Custom range (epoch milliseconds, takes precedence) |
| `minutes` | int | No | Look-back minutes, default 30, max 120 |

Returns `{instanceId: [sample point list]}`. Each sample point (`JvmSamplePoint`) contains timestamp `ts` + CPU/OS (cpuUsagePct, systemCpuUsagePct, systemLoadAvg, physMemUsedPct) + heap memory (heapUsedMb, heapUsedPct, etc.) + generations (edenUsedMb, oldUsedMb) + GC (youngGcCount, fullGcCount, gcTimeRatioPct, etc.) + threads + direct memory + class loading.

```bash
curl "http://localhost:8080/api/metrics/overview?hours=24" \
  -H "X-Dashboard-Token: your-token"
```

---

## SQL Console

`POST /api/metrics/sql`

Executes a **read-only** query against the platform metadata database (`adhoc_*` tables), used by the built-in SQL console on the dashboard.

**Auth**: Dashboard Token (same as above).

**Request Parameters**

| Field | Type | Required | Description |
|------|------|------|------|
| `sql` | String | Yes | A single read-only SQL statement, max length 16000 |

**Limits**:

- Only accepts `SELECT` / `SHOW` / `DESCRIBE` / `EXPLAIN` (others return `ADHOC_SQL_QUERY_ONLY_SELECT`)
- Query scope is limited to the metadata database (`adhoc_query_job` / `adhoc_query_task` and other platform tables)
- Auto `LIMIT 1000`, execution timeout 30s

**Response**: `data` is `SqlConsoleResult`:

| Field | Type | Description |
|------|------|------|
| `columns` | List\<String\> | Column labels |
| `rows` | List\<List\<String\>> | Row set (cells uniformly stringified; null shown as empty string) |
| `totalRows` | int | Number of rows returned this time (≤ 1000) |
| `truncated` | boolean | true when the actual hit count exceeded the limit and was truncated |

```bash
curl -X POST http://localhost:8080/api/metrics/sql \
  -H "Content-Type: application/json" \
  -H "X-Dashboard-Token: your-token" \
  -d '{ "sql": "SELECT status, count(*) FROM adhoc_query_job GROUP BY status" }'
```

Response:

```json
{
  "code": 1,
  "msg": "操作成功",
  "data": {
    "columns": ["status", "count(*)"],
    "rows": [["SUCCESS", "1532"], ["FAILED", "21"]],
    "totalRows": 2,
    "truncated": false
  }
}
```

---

[English](dashboard.md) | [中文](../../zh/api/dashboard.md)
