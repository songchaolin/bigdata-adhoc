# 监控大盘接口

`/api/metrics/**` 系列接口供内置监控大盘页面（`/dashboard`）使用，Dashboard Token 鉴权。运维视图，**不做按用户过滤**（穿透下钻接口可跨用户查任意 Job/Task）。外部业务方一般不直接调用本组接口。

← 返回 [接口文档首页](README.md)

## 鉴权：Dashboard Token

| 项目 | 说明 |
|------|------|
| 请求头 | `X-Dashboard-Token`（或 URL 参数 `?token=`） |
| 配置 | `adhoc.dashboard.access-token`（Apollo 活读），**为空则不启用鉴权** |
| 失败 | HTTP 401，`{"code": -1, "msg": "dashboard token 无效或缺失"}` |

生产环境经网关访问时需为 `/dashboard/**` 与 `/api/metrics/**` 配置免登录白名单（见[部署指南](../deployment.md)）。

## 接口总表

均为 `GET + Query` 参数。**时间窗参数**：`startMs` / `endMs`（epoch 毫秒，自定义区间，优先）或 `hours`（预设窗口，默认 24）——二者传其一即可，`startMs/endMs` 优先。

| 路径 | 参数 | 返回说明 |
|------|------|---------|
| `/api/metrics/overview` | 时间窗 | 概览：Job/Task 状态分布、成功率、平均/最大耗时、时长分桶、引擎分布、实时态（PENDING/RUNNING 积压）、executor/server 汇总 |
| `/api/metrics/trends` | 时间窗 | Job 小时/天提交完成趋势点列表 |
| `/api/metrics/task-trends` | 时间窗 | Task 小时/天提交完成趋势（与 Job 趋势同口径，便于对照） |
| `/api/metrics/executors` | `onlineOnly`（默认 true） | Executor 集群列表：状态/并发/利用率/心跳/JVM 指标 |
| `/api/metrics/servers` | `onlineOnly`（默认 true） | Server 集群列表：状态/承接数/active_jobs/心跳 |
| `/api/metrics/topn` | 时间窗 + `limit`（默认 10） | TopN 慢 Job + 活跃用户 |
| `/api/metrics/task-failures` | 时间窗 | Task 失败维度分布：fail_stage / error_code / sql_type |
| `/api/metrics/instance-load` | 时间窗 | 每实例负载：时间窗内每 server/executor 上提交的 Job 数与 Task 数 |
| `/api/metrics/jvm-series` | `instanceIds` + 时间窗/`minutes` | JVM 指标时间曲线（详见下节） |
| `/api/metrics/jobs` | 时间窗 + 过滤 + 分页 | 穿透：Job 分页明细（跨用户），支持 status/engineType/耗时区间过滤与排序 |
| `/api/metrics/job-detail` | `jobId` | 穿透：Job 详情（含 Task 摘要，同 `/api/job/detail` 结构） |
| `/api/metrics/job-progress` | `jobId` | 穿透：Job 执行进度（阶段时间线） |
| `/api/metrics/task-detail` | `taskId` | 穿透：Task 详情（全量字段） |
| `/api/metrics/job-log` | `jobId` + `offset`（默认 0）+ `limit`（默认 1000） | 穿透：Job 日志增量拉取（running 读内存/转发，terminal 读 OSS） |
| `/api/metrics/task-log` | `taskId` + `offset`（默认 0）+ `limit`（默认 100） | 穿透：Task 日志（running 走 executor gRPC，terminal 兜底读 job 日志） |

穿透下钻接口（`/jobs` 至 `/task-log`）的返回结构同用户侧对应接口（[job.md](job.md) / [log.md](log.md)），区别仅在于：系统级访问（无归属校验，可查任意用户的 Job/Task）。

### JVM 指标时间曲线（/jvm-series）

按实例查 JVM 采样历史（5s 采样，保留期 2 小时）。

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `instanceIds` | List\<String\> | 否 | 实例 ID 列表（server/executor 实例 ID，可从 `/executors`、`/servers` 获取） |
| `startMs` / `endMs` | Long | 否 | 自定义区间（epoch 毫秒，优先） |
| `minutes` | int | 否 | 回看分钟数，默认 30，上限 120 |

返回 `{instanceId: [采样点列表]}`。每个采样点（`JvmSamplePoint`）含时间戳 `ts` + CPU/OS（cpuUsagePct、systemCpuUsagePct、systemLoadAvg、physMemUsedPct）+ 堆内存（heapUsedMb、heapUsedPct 等）+ 分代（edenUsedMb、oldUsedMb）+ GC（youngGcCount、fullGcCount、gcTimeRatioPct 等）+ 线程 + 直接内存 + 类加载。

```bash
curl "http://localhost:8080/api/metrics/overview?hours=24" \
  -H "X-Dashboard-Token: your-token"
```

---

## SQL 控制台

`POST /api/metrics/sql`

对平台元数据库（`adhoc_*` 表）执行**只读**查询，供大盘内置 SQL 控制台使用。

**鉴权**：Dashboard Token（同上）。

**请求参数**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `sql` | String | 是 | 单条只读 SQL，最大长度 16000 |

**限制**：

- 仅接受 `SELECT` / `SHOW` / `DESCRIBE` / `EXPLAIN`（其余返回 `ADHOC_SQL_QUERY_ONLY_SELECT`）
- 查询范围限元数据库（`adhoc_query_job` / `adhoc_query_task` 等平台表）
- 自动 `LIMIT 1000`、执行超时 30s

**响应**：`data` 为 `SqlConsoleResult`：

| 字段 | 类型 | 说明 |
|------|------|------|
| `columns` | List\<String\> | 列标签 |
| `rows` | List\<List\<String\>> | 行集（单元格统一字符串化，null 展示为空串） |
| `totalRows` | int | 本次返回行数（≤ 1000） |
| `truncated` | boolean | 实际命中行数超上限被截断时为 true |

```bash
curl -X POST http://localhost:8080/api/metrics/sql \
  -H "Content-Type: application/json" \
  -H "X-Dashboard-Token: your-token" \
  -d '{ "sql": "SELECT status, count(*) FROM adhoc_query_job GROUP BY status" }'
```

返回：

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
