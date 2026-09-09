# 元数据与配置接口

- **元数据自动补全**（`/api/metadata/**`）：库 / 表 / 列三级查询，供前端 SQL 编辑器自动补全使用。
- **运行时配置查询**（`/api/config`）：枚举 server 当前生效配置（脱敏），运维排查用。

← 返回 [接口文档首页](README.md)

## 元数据自动补全

**鉴权**：用户身份接口（`X-Adhoc-User-Id` 头，仅审计用途，不做按用户过滤——补全范围取决于元数据连接账号的权限）。

**数据来源与缓存**：

- `KYUUBI`：直连 Hive metastore（MySQL），查 `DBS` / `TBLS` / `COLUMNS_V2` / `PARTITION_KEYS`
- `STARROCKS`：查 StarRocks `information_schema`

查询结果有 **Caffeine 全量缓存**（库 / 表 / 列 TTL 为分钟级，非实时），新建表/列可能延迟出现在补全结果中。

**通用参数**（三个接口一致）：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `engineType` | String | 是 | `KYUUBI` / `STARROCKS`，非法返回 `ADHOC_ENGINE_TYPE_INVALID` |
| `instance` | String | 否 | 引擎实例名（多实例配置时选择）；不传走默认实例 |
| `database` | String | tables/columns 必填 | 库名 |
| `table` | String | columns 必填 | 表名 |
| `keyword` | String | 否 | 前缀过滤（仅 databases/tables） |

### 库列表

`GET /api/metadata/databases`

**响应**：`data` 为 `List<MetadataDatabase>`：

| 字段 | 类型 | 说明 |
|------|------|------|
| `name` | String | 库名 |

```bash
curl "http://localhost:8080/api/metadata/databases?engineType=KYUUBI" \
  -H "X-Adhoc-User-Id: u001"
```

返回：

```json
{
  "code": 1,
  "msg": "操作成功",
  "data": [ { "name": "dwd" }, { "name": "dim" } ]
}
```

### 表列表

`GET /api/metadata/tables`

**响应**：`data` 为 `List<MetadataTable>`：

| 字段 | 类型 | 说明 |
|------|------|------|
| `name` | String | 表名 |
| `type` | String | `TABLE` / `VIEW` |
| `comment` | String | 表注释 |

```bash
curl "http://localhost:8080/api/metadata/tables?engineType=KYUUBI&database=dwd&keyword=order" \
  -H "X-Adhoc-User-Id: u001"
```

### 列列表

`GET /api/metadata/columns`

**响应**：`data` 为 `List<MetadataColumn>`：

| 字段 | 类型 | 说明 |
|------|------|------|
| `name` | String | 列名 |
| `type` | String | 列类型 |
| `comment` | String | 列注释 |
| `partition` | boolean | 是否分区键（Hive 分区列也是合法列，与普通列一并返回） |

```bash
curl "http://localhost:8080/api/metadata/columns?engineType=KYUUBI&database=dwd&table=dwd_order" \
  -H "X-Adhoc-User-Id: u001"
```

---

## 运行时配置查询

`GET /api/config`

枚举 server 端当前生效的全部业务配置项（`AdhocCommonConfig` + `AdhocServerConfig` + `AdhocMetadataConfig`），敏感字段（含 password）脱敏为 `****`。用于排查配置生效情况，配置项含义见[配置参考](../configuration.md)。

**鉴权**：用户身份接口。

**请求参数**：无。

**响应**：`data` 为 `List<ConfigItemView>`：

| 字段 | 类型 | 说明 |
|------|------|------|
| `group` | String | 配置分组 |
| `key` | String | 配置键 |
| `currentValue` | Object | 当前生效值（Apollo 覆盖后，敏感脱敏） |
| `defaultValue` | Object | 代码默认值 |
| `description` | String | 配置描述 |
| `effect` | String | 配置效果说明 |
| `source` | String | 值来源（Apollo / 环境变量 / 本地 yml / 默认），排查 Apollo 是否生效 |

```bash
curl "http://localhost:8080/api/config" \
  -H "X-Adhoc-User-Id: u001"
```

返回（节选）：

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
