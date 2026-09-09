# 接口文档

bigdata-adhoc 对外 REST 接口说明。所有接口由 **adhoc-server** 提供（默认端口 `8080`）。

> 运行中的服务可访问在线 Swagger 文档：`http://<server-host>:8080/doc.html`（Knife4j）。
> 本静态文档与在线文档同源（Controller 注解），离线阅读以本篇为准。

## 目录

| 文档 | 内容 |
|------|------|
| [job.md](job.md) | Job 提交 / 分页 / 详情 / 状态 / 进度 / 取消、Task 详情 |
| [result.md](result.md) | Task 结果分页、Job 结果聚合 |
| [log.md](log.md) | Task / Job 执行日志（增量轮询） |
| [file.md](file.md) | 文件节点（SQL 脚本目录树）：增删改 / 移动 / 搜索 / 按文件提交 |
| [metadata.md](metadata.md) | 元数据自动补全（库 / 表 / 列）、运行时配置查询 |
| [dashboard.md](dashboard.md) | 监控大盘与 SQL 控制台接口（内置 dashboard 页面使用） |

## 通用约定

### 统一响应结构

所有接口返回统一包装 `Result`：

```json
{
  "code": 1,
  "msg": "操作成功",
  "data": { }
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `code` | Integer | `1` 成功，其余失败 |
| `msg` | String | 消息文本；失败时格式为 `"ADHOC_XXX: 中文提示"` |
| `data` | Object | 业务数据，类型见各接口说明；失败时为 `null` |

常用响应码：

| code | 含义 |
|------|------|
| `1` | 成功 |
| `0` | 业务失败（`msg` 带错误码前缀） |
| `300004` | 参数校验失败（`@Valid`，msg 附字段错误明细） |
| `600007` | 系统内部错误 |

### 鉴权

接口分两类鉴权方式：

**1. 用户身份接口（`/api/**`，不含 `/api/metrics/**`）**

| 请求头 | 必填 | 说明 |
|--------|------|------|
| `X-Adhoc-User-Id` | 是 | 用户标识，缺失返回 `ADHOC_USER_CONTEXT_MISSING` |
| `X-Adhoc-User-Name` | 否 | 用户中文名，冗余展示用 |

请求头通常由网关 / 反向代理注入，业务方不直接传（见[部署指南 - nginx 网关](../deployment.md#nginx-网关与用户头注入)）。

其中**读类与取消接口**（job/detail、job/status、job/progress、job/cancel、task/detail、task/result、job/result、task/log、job/log）在用户身份之上还有归属校验：仅**本人**或**管理员**（配置 `adhoc.admin.user-ids`）可访问他人 Job，否则返回 `ADHOC_JOB_FORBIDDEN`。

**2. Dashboard Token 接口（`/dashboard/**`、`/api/metrics/**`）**

请求头 `X-Dashboard-Token`（或 URL 参数 `?token=`）与配置 `adhoc.dashboard.access-token` 比对；配置为空则不启用鉴权。失败返回 HTTP 401。详见 [dashboard.md](dashboard.md)。

### 请求方式与分页

- 接口全部为 `POST + @RequestBody`（JSON），元数据与大盘接口为 `GET + Query 参数`。
- 分页请求统一继承：

| 字段 | 类型 | 默认 | 说明 |
|------|------|------|------|
| `current` | Long | 1 | 页码 |
| `size` | Long | 10 | 每页条数（上限 100） |

### 状态枚举

**JobStatus**：`PENDING` → `DISPATCHING` → `RUNNING` → `SUCCESS` / `PARTIAL_FAILED` / `FAILED` / `CANCELED`

**TaskStatus**：`PENDING` / `RUNNING` / `SUCCESS` / `FAILED` / `CANCELED`（含失败传播跳过的 `SKIPPED` 语义，见各接口 `failReasonCategory`）

**EngineType**：`KYUUBI`（Hive 生态）、`STARROCKS`（MySQL 协议）

## 错误码

`msg` 中的错误码即 `AdhocErrorCode` 枚举名，可作为机器可读标识做分支处理：

| 错误码 | 默认提示 |
|--------|---------|
| `ADHOC_JOB_TOO_MANY_TASKS` | SQL 语句段数超过单次提交上限 |
| `ADHOC_JOB_LIMIT_EXCEEDED` | Job 提交数量超过限制，请稍后重试 |
| `ADHOC_JOB_NO_EXECUTABLE_SQL` | 未检测到可执行的 SQL 语句 |
| `ADHOC_ENGINE_TYPE_REQUIRED` | 引擎类型不能为空 |
| `ADHOC_ENGINE_TYPE_INVALID` | 引擎类型非法 |
| `ADHOC_ENGINE_PARAMS_INVALID` | 引擎参数非法 |
| `ADHOC_ENGINE_INSTANCE_NOT_FOUND` | 引擎实例不存在或未配置，请检查实例名 |
| `ADHOC_SQL_SYNTAX_ERROR` | SQL 语法错误 |
| `ADHOC_RESULT_NO_RESULT` | 未查询到结果数据 |
| `ADHOC_RESULT_INCOMPLETE` | 结果尚未完整生成，请稍后重试 |
| `ADHOC_RESULT_LOST` | 结果数据已丢失 |
| `ADHOC_LOG_INCOMPLETE` | 日志尚未完整生成，请稍后重试 |
| `ADHOC_EXECUTOR_READ_BUSY` | 执行器读取繁忙，请稍后重试 |
| `ADHOC_EXECUTOR_CRASHED` | 执行器已宕机，任务执行中断 |
| `ADHOC_QUERY_TIMEOUT` | 查询执行超时 |
| `ADHOC_SESSION_LOST` | 引擎会话已失效 |
| `ADHOC_SERVER_CRASHED` | 调度服务异常，请稍后重试 |
| `ADHOC_QUEUE_WAIT_TIMEOUT` | 排队等待超时，请稍后重试 |
| `ADHOC_SCHEDULE_NO_EXEC_AVAILABLE` | 暂无可用执行器，请稍后重试 |
| `ADHOC_ENGINE_SQL_TYPE_NOT_SUPPORTED` | 当前引擎不支持该 SQL 语句类型 |
| `ADHOC_SQL_DANGEROUS_STATEMENT` | 检测到危险 SQL 语句，已被拦截 |
| `ADHOC_JOB_NOT_FOUND` | Job 不存在或已失效 |
| `ADHOC_JOB_FORBIDDEN` | 无权访问该 Job |
| `ADHOC_NODE_NOT_FOUND` | 节点不存在 |
| `ADHOC_NODE_NAME_DUPLICATE` | 节点名称已存在 |
| `ADHOC_NODE_NAME_INVALID` | 节点名称含有非法字符 |
| `ADHOC_DIRECTORY_NOT_EMPTY` | 目录非空，无法删除 |
| `ADHOC_ROOT_NODE_IMMUTABLE` | 根节点不可修改 |
| `ADHOC_PARENT_NOT_DIRECTORY` | 父节点不是目录类型 |
| `ADHOC_MOVE_TO_SELF_OR_CHILD` | 不能将节点移动到自身或其子节点下 |
| `ADHOC_USER_CONTEXT_MISSING` | 缺少用户身份信息，请通过网关访问 |
| `ADHOC_METADATA_QUERY_FAILED` | 元数据查询失败 |
| `ADHOC_METADATA_NOT_CONFIGURED` | 元数据服务未配置 |
| `ADHOC_SQL_QUERY_ONLY_SELECT` | 仅支持 SELECT/SHOW/DESCRIBE 查询语句 |
| `ADHOC_SQL_QUERY_FAILED` | 查询执行失败 |

## 接口总表

### Job / Task（详见 [job.md](job.md)）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/job` | 提交 Job |
| POST | `/api/job/page` | Job 分页查询 |
| POST | `/api/job/detail` | Job 详情（含 Task 摘要） |
| POST | `/api/job/status` | Job 状态 |
| POST | `/api/job/progress` | Job 执行进度（阶段时间线） |
| POST | `/api/job/cancel` | 取消 Job |
| POST | `/api/task/detail` | Task 详情 |

### 结果（详见 [result.md](result.md)）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/task/result` | Task 结果分页 |
| POST | `/api/job/result` | Job 结果聚合（各 Task 第一页） |

### 日志（详见 [log.md](log.md)）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/task/log` | Task 日志增量拉取 |
| POST | `/api/job/log` | Job 日志增量拉取 |

### 文件节点（详见 [file.md](file.md)）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/file/node/create` | 创建节点（目录/文件） |
| POST | `/api/file/tree` | 查询用户目录树 |
| POST | `/api/file/nodes` | 列子节点 |
| POST | `/api/file/node/get` | 查询单个节点 |
| POST | `/api/file/node/rename` | 重命名 |
| POST | `/api/file/node/move` | 移动 |
| POST | `/api/file/node/delete` | 删除（回收站） |
| POST | `/api/file/node/restore` | 从回收站恢复 |
| POST | `/api/file/nodes/search` | 搜索 |
| POST | `/api/file/node/update` | 更新文件内容/描述 |
| POST | `/api/file/submit` | 以文件节点内容提交 Job |

### 元数据与配置（详见 [metadata.md](metadata.md)）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/metadata/databases` | 库列表 |
| GET | `/api/metadata/tables` | 表列表 |
| GET | `/api/metadata/columns` | 列列表 |
| GET | `/api/config` | 运行时配置查询（脱敏） |

### 监控大盘（详见 [dashboard.md](dashboard.md)）

`/api/metrics/**` 共 15 个接口 + SQL 控制台，供内置 dashboard 页面使用，Dashboard Token 鉴权。

## 调用示例（快速上手）

```bash
# 提交一个 Kyuubi 查询 Job
curl -X POST http://localhost:8080/api/job \
  -H "Content-Type: application/json" \
  -H "X-Adhoc-User-Id: u001" \
  -H "X-Adhoc-User-Name: 张三" \
  -d '{
    "sqlContent": "SELECT * FROM dwd_order LIMIT 10",
    "engineType": "KYUUBI"
  }'
```

返回：

```json
{
  "code": 1,
  "msg": "操作成功",
  "data": { "jobId": "Job_3f2a..." }
}
```

更多示例见各分篇文档。Java 调用推荐直接使用 [SDK](../sdk.md)，无需手工处理鉴权头与响应包装。
