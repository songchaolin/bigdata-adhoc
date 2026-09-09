# 结果接口

查询 Task / Job 的执行结果集。通用约定（统一响应、鉴权、错误码）见 [README.md](README.md)。

结果由 executor 在执行完成后序列化上传存储（local / OSS），server 按需读取分页返回。DDL/DML 类 Task 无结果集（`hasResultSet=false`），只能看 `affectedRows`。

---

## POST /api/task/result

单个 Task 的结果集分页查询。

**鉴权**：用户身份头 + 归属校验（按 Task 所属 Job 判定）。

### 请求参数

继承分页基类：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `taskId` | String | 是 | Task ID |
| `current` | Long | 否 | 页码（1 起），默认 1 |
| `size` | Long | 否 | 每页行数，默认 10，1~100 |

### 响应 `data`（ResultResponse）

| 字段 | 类型 | 说明 |
|------|------|------|
| `schema` | List\<ColumnDto\> | 列定义 |
| `rows` | List\<String\> | 数据行，**每行为一个 JSON 编码的数组字符串**（见下方示例） |
| `current` / `size` | Long | 当前页码 / 每页行数（与请求一致） |
| `totalRows` | long | 总行数 |
| `hasMore` | boolean | 是否还有下一页 |

**ColumnDto** 字段：

| 字段 | 类型 | 说明 |
|------|------|------|
| `colIndex` | int | 列序号（0 起） |
| `colName` | String | 列名 |
| `colType` | String | 列类型（引擎返回的类型名） |

### 响应示例

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

客户端解析 `rows` 时需对每个元素再做一次 JSON 反序列化得到行数组。

### curl 示例

```bash
curl -X POST http://localhost:8080/api/task/result \
  -H "Content-Type: application/json" \
  -H "X-Adhoc-User-Id: u001" \
  -d '{ "taskId": "Task_8c1d5e...", "current": 1, "size": 100 }'
```

可能出现的错误码：`ADHOC_JOB_NOT_FOUND`、`ADHOC_JOB_FORBIDDEN`、`ADHOC_RESULT_NO_RESULT`、`ADHOC_RESULT_INCOMPLETE`、`ADHOC_RESULT_LOST`（完整错误码表见 [README.md](README.md)）。

---

## POST /api/job/result

Job 结果聚合：一次返回 Job 下**所有 Task 的结果第一页**（按段序排列），适合前端一次渲染多段查询结果预览。要看某 Task 完整结果再调 `/api/task/result` 翻页。

**鉴权**：用户身份头 + 归属校验。

### 请求参数

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `jobId` | String | 是 | Job ID |
| `size` | Integer | 否 | 每个 Task 返回的结果行数（第一页），默认 20，1~100 |

### 响应 `data`（JobResultResponse）

| 字段 | 类型 | 说明 |
|------|------|------|
| `jobId` | String | Job ID |
| `status` | String | Job 状态 |
| `tasks` | List\<TaskResultItem\> | 各 Task 结果项，按 segmentIndex 排列 |

**TaskResultItem** 字段：

| 分类 | 字段 | 类型 | 说明 |
|------|------|------|------|
| Task 元信息 | `taskId` / `segmentIndex` / `status` / `failStage` / `sqlType` | String/Integer | 同 [TaskSummary](job.md) |
| | `hasResultSet` / `sqlContent` / `prefixSql` | Boolean/String | 同上 |
| | `affectedRows` / `durationMs` / `errorMessage` | Long/String | 同上 |
| 结果第一页 | `schema` | List\<ColumnDto\> | 列定义（仅 `hasResultSet=true` 时有值） |
| | `rows` | List\<String\> | 结果行（JSON 编码，仅 `hasResultSet=true`） |
| | `totalRows` | Long | 总行数 |
| | `hasMore` | Boolean | 是否有更多行 |

### curl 示例

```bash
curl -X POST http://localhost:8080/api/job/result \
  -H "Content-Type: application/json" \
  -H "X-Adhoc-User-Id: u001" \
  -d '{ "jobId": "Job_3f2a9c...", "size": 20 }'
```

可能出现的错误码：同 `/api/task/result`。
