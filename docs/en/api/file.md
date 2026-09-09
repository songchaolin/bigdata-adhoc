# File Node API

SQL script bookmarks: each user has an independent directory tree (directories + SQL files) for saving, organizing commonly-used scripts, and submitting them for execution directly.

← Back to [API reference home](README.md)

## Model

- **Node types**: `DIRECTORY` (directory) / `FILE` (SQL file, content stored in `sqlContent`).
- **User isolation**: each user can only see and operate on their own directory tree; node ownership checks prevent unauthorized access to other users' nodes.
- **Root node**: the root directory automatically created by the system for each user; cannot be renamed, moved, or deleted (`ADHOC_ROOT_NODE_IMMUTABLE`).
- **Delete = recycle bin**: deletion is a soft delete (goes to the recycle bin), recoverable via `restore`; non-empty directories cannot be deleted (`ADHOC_DIRECTORY_NOT_EMPTY`).
- **Submit chain**: `POST /api/file/submit` reuses the same execution chain as `POST /api/job` — submits a Job using the file node's SQL content (or temporarily overridden content from the request body), returns a `jobId`, and then the query/cancel endpoints in [job.md](job.md) can be used.

**Auth**: user-identity endpoint (`X-Adhoc-User-Id` header, normally injected by the gateway); see [common conventions](README.md#authentication).

**Common node response fields** (`FileNodeResponse`):

| Field | Type | Description |
|------|------|------|
| `nodeId` | String | Node ID |
| `parentNodeId` | String | Parent node ID |
| `nodeType` | String | `DIRECTORY` / `FILE` |
| `nodeName` | String | Node name |
| `sqlContent` | String | SQL content (FILE only) |
| `description` | String | Description (FILE only) |
| `createTime` | Date | Creation time |
| `updateTime` | Date | Update time |
| `children` | List | Child nodes (populated only for the tree endpoint) |

---

## Create Node

`POST /api/file/node/create`

Create a directory or SQL file under the specified parent directory.

**Request Parameters**

| Field | Type | Required | Description |
|------|------|------|------|
| `parentNodeId` | String | No | Parent node ID; empty = attach to the root directory |
| `nodeType` | String | Yes | `DIRECTORY` / `FILE` |
| `nodeName` | String | Yes | Node name |
| `sqlContent` | String | Required for FILE | SQL content |
| `description` | String | No | Description |

**Response**: `data` is the newly-created node's `FileNodeResponse` (see common fields table).

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

## Get Full Directory Tree

`POST /api/file/tree`

Returns the current user's full directory tree (the root node's direct child list, nested via `children`).

**Request Parameters**: none (an empty body `{}` is fine).

**Response**: `data` is `List<FileNodeResponse>`, top-level list only; each node is recursively nested via `children`.

```bash
curl -X POST http://localhost:8080/api/file/tree \
  -H "X-Adhoc-User-Id: u001"
```

---

## List Child Nodes

`POST /api/file/nodes`

Lists the direct children of the specified directory (single level, non-recursive).

**Request Parameters**

| Field | Type | Required | Description |
|------|------|------|------|
| `parentNodeId` | String | No | Parent node ID; empty = return the root directory's children |

**Response**: `data` is `List<FileNodeResponse>` (`children` not populated).

```bash
curl -X POST http://localhost:8080/api/file/nodes \
  -H "Content-Type: application/json" \
  -H "X-Adhoc-User-Id: u001" \
  -d '{ "parentNodeId": "root_u001" }'
```

---

## Query Node Detail

`POST /api/file/node/get`

Query a single node (including SQL content).

**Request Parameters**

| Field | Type | Required | Description |
|------|------|------|------|
| `nodeId` | String | Yes | Node ID |

**Response**: `data` is `FileNodeResponse` (`children` not populated).

```bash
curl -X POST http://localhost:8080/api/file/node/get \
  -H "Content-Type: application/json" \
  -H "X-Adhoc-User-Id: u001" \
  -d '{ "nodeId": "Node_a1b2c3" }'
```

---

## Rename Node

`POST /api/file/node/rename`

**Request Parameters**

| Field | Type | Required | Description |
|------|------|------|------|
| `nodeId` | String | Yes | Node ID |
| `newName` | String | Yes | New name |

**Response**: `data` is `Boolean`, always `true` (failure throws an error code).

```bash
curl -X POST http://localhost:8080/api/file/node/rename \
  -H "Content-Type: application/json" \
  -H "X-Adhoc-User-Id: u001" \
  -d '{ "nodeId": "Node_a1b2c3", "newName": "每日订单量-v2" }'
```

---

## Move Node

`POST /api/file/node/move`

Move a node to a new parent directory (along with its entire subtree).

**Request Parameters**

| Field | Type | Required | Description |
|------|------|------|------|
| `nodeId` | String | Yes | Node ID |
| `newParentNodeId` | String | Yes | New parent node ID (must be a DIRECTORY) |

**Response**: `data` is `Boolean`.

```bash
curl -X POST http://localhost:8080/api/file/node/move \
  -H "Content-Type: application/json" \
  -H "X-Adhoc-User-Id: u001" \
  -d '{ "nodeId": "Node_a1b2c3", "newParentNodeId": "Node_dir01" }'
```

---

## Delete Node (Recycle Bin)

`POST /api/file/node/delete`

Soft delete: the node goes to the recycle bin and can be recovered via `restore`. Non-empty directories are rejected.

**Request Parameters**

| Field | Type | Required | Description |
|------|------|------|------|
| `nodeId` | String | Yes | Node ID |

**Response**: `data` is `Boolean`.

```bash
curl -X POST http://localhost:8080/api/file/node/delete \
  -H "Content-Type: application/json" \
  -H "X-Adhoc-User-Id: u001" \
  -d '{ "nodeId": "Node_a1b2c3" }'
```

---

## Restore Node

`POST /api/file/node/restore`

Restore a deleted node from the recycle bin.

**Request Parameters**

| Field | Type | Required | Description |
|------|------|------|------|
| `nodeId` | String | Yes | Node ID |

**Response**: `data` is `Boolean`.

```bash
curl -X POST http://localhost:8080/api/file/node/restore \
  -H "Content-Type: application/json" \
  -H "X-Adhoc-User-Id: u001" \
  -d '{ "nodeId": "Node_a1b2c3" }'
```

---

## Search Nodes

`POST /api/file/nodes/search`

Search for nodes in the current user's directory tree by keyword (matches node names, etc.).

**Request Parameters**

| Field | Type | Required | Description |
|------|------|------|------|
| `keyword` | String | Yes | Search keyword |
| `type` | String | No | Node type filter: `DIRECTORY` / `FILE` |

**Response**: `data` is `List<FileNodeResponse>` (flat list of matched nodes, `children` not populated).

```bash
curl -X POST http://localhost:8080/api/file/nodes/search \
  -H "Content-Type: application/json" \
  -H "X-Adhoc-User-Id: u001" \
  -d '{ "keyword": "订单" }'
```

---

## Update Node Content

`POST /api/file/node/update`

Update the content / name / description of a saved SQL file.

**Request Parameters**

| Field | Type | Required | Description |
|------|------|------|------|
| `nodeId` | String | Yes | Node ID (FILE) |
| `nodeName` | String | No | New node name |
| `sqlContent` | String | No | New SQL content |
| `description` | String | No | New description |

**Response**: `data` is `Boolean`.

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

## Submit Query From File

`POST /api/file/submit`

Submit a Job using a file node as the SQL source (reuses the `POST /api/job` execution chain); returns a `jobId`, then use the endpoints in [job.md](job.md) to track execution.

**Request Parameters**

| Field | Type | Required | Description |
|------|------|------|------|
| `nodeId` | String | Yes | SQL file node ID |
| `sqlContent` | String | No | Temporarily override the file content for execution (does not write back to the file) |
| `engineType` | String | Yes | `KYUUBI` / `STARROCKS` |
| `engineInstance` | String | No | Engine instance name; empty = default instance |
| `engineParams` | String | No | Engine parameters |

**Response**: `data` is `JobSubmitResponse`:

| Field | Type | Description |
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

Response:

```json
{
  "code": 1,
  "msg": "操作成功",
  "data": { "jobId": "Job_3f2a..." }
}
```

---

[English](file.md) | [中文](../../zh/api/file.md)
