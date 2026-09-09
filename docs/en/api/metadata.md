# Metadata & Config API

- **Metadata autocomplete** (`/api/metadata/**`): database / table / column three-level queries, for the frontend SQL editor's autocomplete.
- **Runtime config query** (`/api/config`): enumerates the server's currently-effective config (masked), for ops troubleshooting.

← Back to [API reference home](README.md)

## Metadata Autocomplete

**Auth**: user-identity endpoint (`X-Adhoc-User-Id` header, for audit purposes only; no per-user filtering — the autocomplete scope depends on the metadata connection account's permissions).

**Data sources & caching**:

- `KYUUBI`: connects directly to the Hive metastore (MySQL), querying `DBS` / `TBLS` / `COLUMNS_V2` / `PARTITION_KEYS`
- `STARROCKS`: queries StarRocks `information_schema`

Query results use a **Caffeine full cache** (database / table / column TTL is on the order of minutes, not real-time); newly-created tables/columns may appear in autocomplete results with a delay.

**Common parameters** (consistent across the three endpoints):

| Param | Type | Required | Description |
|------|------|------|------|
| `engineType` | String | Yes | `KYUUBI` / `STARROCKS`; invalid returns `ADHOC_ENGINE_TYPE_INVALID` |
| `instance` | String | No | Engine instance name (selects among multi-instance configs); omitted = default instance |
| `database` | String | Required for tables/columns | Database name |
| `table` | String | Required for columns | Table name |
| `keyword` | String | No | Prefix filter (databases/tables only) |

### Database List

`GET /api/metadata/databases`

**Response**: `data` is `List<MetadataDatabase>`:

| Field | Type | Description |
|------|------|------|
| `name` | String | Database name |

```bash
curl "http://localhost:8080/api/metadata/databases?engineType=KYUUBI" \
  -H "X-Adhoc-User-Id: u001"
```

Response:

```json
{
  "code": 1,
  "msg": "操作成功",
  "data": [ { "name": "dwd" }, { "name": "dim" } ]
}
```

### Table List

`GET /api/metadata/tables`

**Response**: `data` is `List<MetadataTable>`:

| Field | Type | Description |
|------|------|------|
| `name` | String | Table name |
| `type` | String | `TABLE` / `VIEW` |
| `comment` | String | Table comment |

```bash
curl "http://localhost:8080/api/metadata/tables?engineType=KYUUBI&database=dwd&keyword=order" \
  -H "X-Adhoc-User-Id: u001"
```

### Column List

`GET /api/metadata/columns`

**Response**: `data` is `List<MetadataColumn>`:

| Field | Type | Description |
|------|------|------|
| `name` | String | Column name |
| `type` | String | Column type |
| `comment` | String | Column comment |
| `partition` | boolean | Whether it is a partition key (Hive partition columns are also valid columns, returned alongside regular columns) |

```bash
curl "http://localhost:8080/api/metadata/columns?engineType=KYUUBI&database=dwd&table=dwd_order" \
  -H "X-Adhoc-User-Id: u001"
```

---

## Runtime Config Query

`GET /api/config`

Enumerates all currently-effective business config items on the server side (`AdhocCommonConfig` + `AdhocServerConfig` + `AdhocMetadataConfig`); sensitive fields (including password) are masked as `****`. Used to troubleshoot config effectiveness; config-item meanings are in the [configuration reference](../configuration.md).

**Auth**: user-identity endpoint.

**Request Parameters**: none.

**Response**: `data` is `List<ConfigItemView>`:

| Field | Type | Description |
|------|------|------|
| `group` | String | Config group |
| `key` | String | Config key |
| `currentValue` | Object | Current effective value (masked if sensitive, after Apollo override) |
| `defaultValue` | Object | Code default value |
| `description` | String | Config description |
| `effect` | String | Config effect description |
| `source` | String | Value source (Apollo / env var / local yml / default), to troubleshoot whether Apollo is taking effect |

```bash
curl "http://localhost:8080/api/config" \
  -H "X-Adhoc-User-Id: u001"
```

Response (excerpt):

```json
{
  "code": 1,
  "msg": "操作成功",
  "data": [
    {
      "group": "limit",
      "key": "adhoc.limit.max-pending-per-user",
      "currentValue": 5,
      "defaultValue": 5,
      "description": "单用户 PENDING Job 上限",
      "effect": "超出拒绝提交",
      "source": "默认"
    }
  ]
}
```

---

[English](metadata.md) | [中文](../../zh/api/metadata.md)
