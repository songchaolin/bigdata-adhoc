# 文件节点接口

SQL 脚本收藏夹：每个用户一棵独立目录树（目录 + SQL 文件），用于保存、组织常用脚本并可直接提交执行。

← 返回 [接口文档首页](README.md)

## 模型说明

- **节点类型**：`DIRECTORY`（目录）/ `FILE`（SQL 文件，内容存 `sqlContent`）。
- **用户隔离**：每个用户只能看到和操作自己的目录树，节点归属校验防止越权访问他人节点。
- **根节点**：系统为每个用户自动创建的根目录，不可改名、不可移动、不可删除（`ADHOC_ROOT_NODE_IMMUTABLE`）。
- **删除即回收站**：删除是软删除（进回收站），可通过 `restore` 恢复；非空目录无法删除（`ADHOC_DIRECTORY_NOT_EMPTY`）。
- **提交链路**：`POST /api/file/submit` 与 `POST /api/job` 复用同一执行链路——以文件节点的 SQL 内容（或请求体临时覆盖的内容）提交 Job，返回 `jobId` 后即可用 [job.md](job.md) 的查询/取消接口。

**鉴权**：用户身份接口（`X-Adhoc-User-Id` 头，通常由网关注入），详见[通用约定](README.md#鉴权)。

**节点公共响应字段**（`FileNodeResponse`）：

| 字段 | 类型 | 说明 |
|------|------|------|
| `nodeId` | String | 节点 ID |
| `parentNodeId` | String | 父节点 ID |
| `nodeType` | String | `DIRECTORY` / `FILE` |
| `nodeName` | String | 节点名称 |
| `sqlContent` | String | SQL 内容（仅 FILE） |
| `description` | String | 描述（仅 FILE） |
| `createTime` | Date | 创建时间 |
| `updateTime` | Date | 更新时间 |
| `children` | List | 子节点（仅 tree 接口填充） |

---

## 创建节点

`POST /api/file/node/create`

在指定父目录下创建目录或 SQL 文件。

**请求参数**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `parentNodeId` | String | 否 | 父节点 ID，为空挂到根目录 |
| `nodeType` | String | 是 | `DIRECTORY` / `FILE` |
| `nodeName` | String | 是 | 节点名称 |
| `sqlContent` | String | FILE 必填 | SQL 内容 |
| `description` | String | 否 | 描述 |

**响应**：`data` 为新建节点的 `FileNodeResponse`（见公共字段表）。

```bash
curl -X POST http://localhost:8080/api/file/node/create \
  -H "Content-Type: application/json" \
  -H "X-Adhoc-User-Id: u001" \
  -H "X-Adhoc-User-Name: 张三" \
  -d '{
    "nodeType": "FILE",
    "nodeName": "每日订单量",
    "sqlContent": "SELECT dt, count(*) FROM dwd_order GROUP BY dt ORDER BY dt DESC LIMIT 10",
    "description": "dwd_order 按天聚合"
  }'
```

---

## 获取完整目录树

`POST /api/file/tree`

返回当前用户完整目录树（根节点直接子级列表，经 `children` 嵌套）。

**请求参数**：无（空请求体 `{}` 即可）。

**响应**：`data` 为 `List<FileNodeResponse>`，仅顶层列表，各节点经 `children` 递归嵌套。

```bash
curl -X POST http://localhost:8080/api/file/tree \
  -H "X-Adhoc-User-Id: u001"
```

---

## 列出子节点

`POST /api/file/nodes`

列出指定目录的直接子节点（单层，不递归）。

**请求参数**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `parentNodeId` | String | 否 | 父节点 ID，为空返回根目录子节点 |

**响应**：`data` 为 `List<FileNodeResponse>`（不填充 `children`）。

```bash
curl -X POST http://localhost:8080/api/file/nodes \
  -H "Content-Type: application/json" \
  -H "X-Adhoc-User-Id: u001" \
  -d '{ "parentNodeId": "root_u001" }'
```

---

## 查询节点详情

`POST /api/file/node/get`

查询单个节点（含 SQL 内容）。

**请求参数**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `nodeId` | String | 是 | 节点 ID |

**响应**：`data` 为 `FileNodeResponse`（不填充 `children`）。

```bash
curl -X POST http://localhost:8080/api/file/node/get \
  -H "Content-Type: application/json" \
  -H "X-Adhoc-User-Id: u001" \
  -d '{ "nodeId": "Node_a1b2c3" }'
```

---

## 重命名节点

`POST /api/file/node/rename`

**请求参数**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `nodeId` | String | 是 | 节点 ID |
| `newName` | String | 是 | 新名称 |

**响应**：`data` 为 `Boolean`，恒 `true`（失败抛错误码）。

```bash
curl -X POST http://localhost:8080/api/file/node/rename \
  -H "Content-Type: application/json" \
  -H "X-Adhoc-User-Id: u001" \
  -d '{ "nodeId": "Node_a1b2c3", "newName": "每日订单量-v2" }'
```

---

## 移动节点

`POST /api/file/node/move`

将节点移动到新父目录下（含其整棵子树）。

**请求参数**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `nodeId` | String | 是 | 节点 ID |
| `newParentNodeId` | String | 是 | 新父节点 ID（必须是 DIRECTORY） |

**响应**：`data` 为 `Boolean`。

```bash
curl -X POST http://localhost:8080/api/file/node/move \
  -H "Content-Type: application/json" \
  -H "X-Adhoc-User-Id: u001" \
  -d '{ "nodeId": "Node_a1b2c3", "newParentNodeId": "Node_dir01" }'
```

---

## 删除节点（回收站）

`POST /api/file/node/delete`

软删除：节点进回收站，可 `restore` 恢复。非空目录拒绝删除。

**请求参数**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `nodeId` | String | 是 | 节点 ID |

**响应**：`data` 为 `Boolean`。

```bash
curl -X POST http://localhost:8080/api/file/node/delete \
  -H "Content-Type: application/json" \
  -H "X-Adhoc-User-Id: u001" \
  -d '{ "nodeId": "Node_a1b2c3" }'
```

---

## 恢复节点

`POST /api/file/node/restore`

从回收站恢复已删除的节点。

**请求参数**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `nodeId` | String | 是 | 节点 ID |

**响应**：`data` 为 `Boolean`。

```bash
curl -X POST http://localhost:8080/api/file/node/restore \
  -H "Content-Type: application/json" \
  -H "X-Adhoc-User-Id: u001" \
  -d '{ "nodeId": "Node_a1b2c3" }'
```

---

## 搜索节点

`POST /api/file/nodes/search`

按关键词搜索当前用户目录树中的节点（匹配节点名称等）。

**请求参数**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `keyword` | String | 是 | 搜索关键词 |
| `type` | String | 否 | 节点类型过滤：`DIRECTORY` / `FILE` |

**响应**：`data` 为 `List<FileNodeResponse>`（匹配节点平铺列表，不填充 `children`）。

```bash
curl -X POST http://localhost:8080/api/file/nodes/search \
  -H "Content-Type: application/json" \
  -H "X-Adhoc-User-Id: u001" \
  -d '{ "keyword": "订单" }'
```

---

## 更新节点内容

`POST /api/file/node/update`

更新已保存 SQL 文件的内容 / 名称 / 描述。

**请求参数**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `nodeId` | String | 是 | 节点 ID（FILE） |
| `nodeName` | String | 否 | 新节点名称 |
| `sqlContent` | String | 否 | 新 SQL 内容 |
| `description` | String | 否 | 新描述 |

**响应**：`data` 为 `Boolean`。

```bash
curl -X POST http://localhost:8080/api/file/node/update \
  -H "Content-Type: application/json" \
  -H "X-Adhoc-User-Id: u001" \
  -d '{
    "nodeId": "Node_a1b2c3",
    "sqlContent": "SELECT dt, count(*) FROM dwd_order WHERE dt >= 20260901 GROUP BY dt",
    "description": "近 7 天订单量"
  }'
```

---

## 从文件提交查询

`POST /api/file/submit`

以文件节点为 SQL 来源提交 Job（复用 `POST /api/job` 执行链路），返回 `jobId` 后用 [job.md](job.md) 的接口跟踪执行。

**请求参数**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `nodeId` | String | 是 | SQL 文件节点 ID |
| `sqlContent` | String | 否 | 临时覆盖文件内容执行（不回写文件） |
| `engineType` | String | 是 | `KYUUBI` / `STARROCKS` |
| `engineInstance` | String | 否 | 引擎实例名，空 = 默认实例 |
| `engineParams` | String | 否 | 引擎参数 |

**响应**：`data` 为 `JobSubmitResponse`：

| 字段 | 类型 | 说明 |
|------|------|------|
| `jobId` | String | Job ID |

```bash
curl -X POST http://localhost:8080/api/file/submit \
  -H "Content-Type: application/json" \
  -H "X-Adhoc-User-Id: u001" \
  -H "X-Adhoc-User-Name: 张三" \
  -d '{
    "nodeId": "Node_a1b2c3",
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
