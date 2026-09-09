# 日志接口

Task / Job 执行日志的增量拉取。通用约定（统一响应、鉴权、错误码）见 [README.md](README.md)。

日志来源：

- **Task 日志**：Job 运行中走 gRPC 实时拉取 executor 内存缓冲；终态后兜底读存储（OSS）。
- **Job 日志**：读存储（`persistent_log_path`，server 在 Job 终态后上传）。

### 轮询契约（重要）

`LogResponse` 有两个信号字段，语义不同：

| 字段 | 含义 |
|------|------|
| `hasMore` | 纯分页信号：当前数据源中 `offset` 之后是否还有未读行（本次没读完） |
| `complete` | 日志是否已**完整**（Job 已终态，日志已 finalize，不会再增长） |

客户端轮询规则：

| 状态组合 | 动作 |
|----------|------|
| `hasMore=true` | 继续翻页（`offset += lines.length`） |
| `hasMore=false` 且 `complete=false` | 本次读完但日志尚未结束，**继续轮询**（稍后再调，offset 不变） |
| `hasMore=false` 且 `complete=true` | 日志完整，**停止轮询** |

> 异常情况下（日志采集未 finalize）`complete` 可能始终为 `false`，客户端需设轮询超时兜底，避免无限轮询。

---

## POST /api/task/log

拉取单个 Task 的执行日志（引擎执行明细，含 SQL、fetch 进度、错误堆栈）。

**鉴权**：用户身份头 + 归属校验（按 Task 所属 Job 判定）。

### 请求参数

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `taskId` | String | 是 | Task ID |
| `offset` | long | 否 | 行偏移（0 起），默认 0 |
| `limit` | int | 否 | 本次拉取行数，默认 100 |

### 响应 `data`（LogResponse）

| 字段 | 类型 | 说明 |
|------|------|------|
| `lines` | List\<String\> | 日志行 |
| `offset` | long | 本次请求的行偏移（回显） |
| `limit` | int | 本次拉取行数（回显） |
| `hasMore` | boolean | 分页信号（见轮询契约） |
| `complete` | boolean | 日志是否已完整 |

### curl 示例

```bash
curl -X POST http://localhost:8080/api/task/log \
  -H "Content-Type: application/json" \
  -H "X-Adhoc-User-Id: u001" \
  -d '{ "taskId": "Task_8c1d5e...", "offset": 0, "limit": 100 }'
```

返回（节选）：

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

可能出现的错误码：`ADHOC_JOB_NOT_FOUND`、`ADHOC_JOB_FORBIDDEN`、`ADHOC_LOG_INCOMPLETE`、`ADHOC_EXECUTOR_READ_BUSY`（完整错误码表见 [README.md](README.md)）。

---

## POST /api/job/log

拉取整个 Job 的日志（Task 级日志按段序聚合，Job 全生命周期的运行视图）。

**鉴权**：用户身份头 + 归属校验。

### 请求参数

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `jobId` | String | 是 | Job ID |
| `offset` | long | 否 | 行偏移（0 起），默认 0 |
| `limit` | int | 否 | 本次拉取行数，默认 1000 |

### 响应 `data`（LogResponse）

结构同 `/api/task/log`（lines / offset / limit / hasMore / complete），轮询契约相同。

### curl 示例

```bash
curl -X POST http://localhost:8080/api/job/log \
  -H "Content-Type: application/json" \
  -H "X-Adhoc-User-Id: u001" \
  -d '{ "jobId": "Job_3f2a9c...", "offset": 0, "limit": 1000 }'
```

可能出现的错误码：同 `/api/task/log`。
