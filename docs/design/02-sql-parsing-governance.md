# 模块 02：SQL 解析与治理

> 本模块负责 SQL 解析、SqlType 识别、血缘提取、风险标记，并在 server 端执行前置校验（SQL 语法校验 + Task 段数检查 + 限流 + engine_type 合法性 + engine_params 合法性 + 引擎语句类型限制）。平台不做权限检查；如引擎侧部署了权限插件，引擎报错统一作为 ENGINE_ERROR 处理。

## 1. 概述

### 1.1 模块职责

- **解析**：按 engine_type 选用对应 g4 解析 SQL，产出 AST + SqlType
- **血缘**：提取表级血缘（source_tables + sink_tables），存入 table_ref 表，与 Task 通过 query_id 1:1 绑定
- **风险标记**：标记风险项（全表扫描、跨库、危险函数等），仅标记不阻断
- **前置校验**：server 端在创建 Job 记录前执行 6 项校验，不通过直接拒绝不提交

### 1.2 设计原则

- **多引擎 g4**：每个引擎类型有独立 g4，KYUUBI 使用 Spark SQL g4，STARROCKS 使用 StarRocks g4
- **解析与治理分离**：解析只做提取，前置校验只做合规检查
- **校验不阻断即放行**：6 项校验全部通过才创建 Job 记录并 dispatchJob
- **engine_type 显式指定**：用户提交时必须指定 engine_type（KYUUBI/STARROCKS），不指定或非法直接拒绝
- **STARROCKS 仅查询**：STARROCKS 引擎仅支持 DQL/CTAS/SESSION_CONFIG/AUX，DDL/DML/DCL 在前置校验阶段拦截

### 1.3 不引入的概念

- **权限检查/赋权**：本模块不做权限校验和赋权。如引擎侧部署了权限插件，引擎报错统一作为 ENGINE_ERROR
- **AUTO 模式/引擎链推算**：不根据 SQL 特征推算引擎，engine_type 由用户显式指定
- **字段脱敏/加密**：不做。无脱敏规则表、无 masked_columns_json
- **UDF 注册表**：不做
- **参数白名单**：engine_params 做 JSON 格式校验 + 白名单校验（参数键必须在 `adhoc_engine_param_rule` 表中，值按类型校验范围/枚举），不合法直接拒绝

## 2. 解析流程

### 2.1 解析策略

按用户显式指定的 engine_type 选用对应 g4 解析：

```
用户指定 engine_type=KYUUBI：
  -> 用 Spark SQL g4 解析
  -> 解析失败 -> 拒绝，返回 ADHOC_SQL_SYNTAX_ERROR（前置校验阶段）

用户指定 engine_type=STARROCKS：
  -> 用 StarRocks g4 解析
  -> 解析失败 -> 拒绝，返回 ADHOC_SQL_SYNTAX_ERROR（前置校验阶段）

用户未指定 engine_type：
  -> 直接拒绝，返回 ADHOC_ENGINE_TYPE_REQUIRED
  -> 不进入解析

用户指定 engine_type 非 KYUUBI/STARROCKS：
  -> 直接拒绝，返回 ADHOC_ENGINE_TYPE_INVALID
  -> 不进入解析
```

### 2.2 g4 文件

| g4 | 适用引擎类型 |
|---|---|
| Spark SQL g4 | KYUUBI |
| StarRocks g4 | STARROCKS |

### 2.3 解析流水线

```
原始 SQL
  -> DialectAdapter.normalize()       规范化（大小写、空白、引号）
  -> ANTLR4 parse                     语法解析，产出 AST
  -> SqlTypeExtractor                 语句类型（DQL/DDL_CREATE/SESSION_CONFIG/...）
  -> TableLineageExtractor            表级血缘（source_tables + sink_tables）
  -> RiskItemExtractor                风险项（全表扫描、跨库、危险函数）
  -> SqlParseResult（含 SqlType + 表级血缘 + 风险）
```

### 2.4 解析失败处理

解析在 server 端前置校验阶段执行（见 §5）：

| 失败情况 | 处理 |
|---|---|
| g4 解析语法错误 | 拒绝，错误码 `ADHOC_SQL_SYNTAX_ERROR`，不创建 Job 记录 |
| 解析成功但 SqlType=UNKNOWN | 拒绝，错误码 `ADHOC_SQL_TYPE_NOT_SUPPORTED`，不创建 Job 记录 |

## 3. SqlType 枚举

### 3.1 11 类

| SqlType | 含义 | has_result_set | 示例 |
|---|---|---|---|
| DQL | 查询语句 | 是 | SELECT / WITH SELECT |
| DDL_CREATE | 建表/建视图（非 CTAS） | 否 | CREATE TABLE / CREATE VIEW |
| DDL_ALTER | 修改表结构 | 否 | ALTER TABLE / ALTER VIEW |
| DDL_DROP | 删表/删视图 | 否 | DROP TABLE / DROP VIEW |
| DML_INSERT | 插入数据 | 否 | INSERT INTO / INSERT OVERWRITE |
| DML_MODIFY | 更新/删除 | 否 | UPDATE / DELETE（引擎支持时） |
| CTAS | 建表并查询 | 是 | CREATE TABLE ... AS SELECT |
| AUX | 辅助语句 | 是 | SHOW / DESCRIBE / EXPLAIN |
| SESSION_CONFIG | 会话配置语句 | 否 | SET / USE |
| DCL | 权限操作 | 否 | GRANT / REVOKE（解析识别，由引擎侧拦截） |
| UNKNOWN | 解析成功但未分类 | 否 | - |

### 3.2 关键判定

1. **CTAS 单独一类**：有结果集（SELECT 部分），DDL_CREATE 没有。结果存储链路不同
2. **SESSION_CONFIG 单独一类**：SET/USE 识别为 SESSION_CONFIG，executor 拆分时作为 prefix_sql 合并到下一个真正执行的 Task，不独立成 Task（见 §4）
3. **DCL 不在 server 端拒绝**：本模块不做权限治理。DCL 语句解析识别后正常放行，由引擎侧拦截，引擎报错统一作为 ENGINE_ERROR
4. **UNKNOWN 处理**：解析成功但无法分类，走 ADHOC_SQL_TYPE_NOT_SUPPORTED
5. **AUX 的 has_result_set**：统一为 1（SHOW/DESCRIBE/EXPLAIN 返回行）

### 3.3 has_result_set 决定的链路差异

| has_result_set | 走 WRITING stage | 写对象存储 | 结果读取链路 |
|---|---|---|---|
| 1（DQL/CTAS/AUX） | 是 | 是 | 是 |
| 0（DDL/DML/DCL/SESSION_CONFIG/UNKNOWN） | 否 | 否 | 否（仅返回 affected_rows） |

### 3.4 判定方式

SqlType 由解析器遍历 AST 根节点判定：

| AST 根节点 | SqlType |
|---|---|
| CREATE TABLE ... AS SELECT | CTAS |
| CREATE TABLE / CREATE VIEW | DDL_CREATE |
| SELECT / WITH SELECT | DQL |
| INSERT | DML_INSERT |
| UPDATE / DELETE | DML_MODIFY |
| ALTER | DDL_ALTER |
| DROP | DDL_DROP |
| GRANT / REVOKE | DCL |
| SET / USE | SESSION_CONFIG |
| SHOW / DESCRIBE / EXPLAIN | AUX |
| 其他 | UNKNOWN |

## 4. SET/USE 识别与 executor 拆分

### 4.1 SESSION_CONFIG 类型的作用

g4 解析阶段将 SET/USE 识别为 SESSION_CONFIG。executor 接收 dispatchJob 后按 `;` 拆分，识别 SESSION_CONFIG 段为"会话配置前缀"，合并到下一个真正执行的 Task。

```
用户提交：
  SET spark.sql.shuffle.partitions=100;
  USE db_ods;
  SELECT * FROM table_a WHERE dt='2026-07-01';
  SET spark.executor.memory=4g;
  SELECT * FROM table_b LIMIT 10;

executor 拆分结果（2 个 Task，不是 5 个）：
  Task 1:
    prefix_sql = "SET spark.sql.shuffle.partitions=100; USE db_ods;"
    sql_content = "SELECT * FROM table_a WHERE dt='2026-07-01';"
  Task 2:
    prefix_sql = "SET spark.sql.shuffle.partitions=100; USE db_ods; SET spark.executor.memory=4g;"
    sql_content = "SELECT * FROM table_b LIMIT 10;"
```

### 4.2 server 端段数计数

server 端前置校验（见 §5）按 `;` 拆分 sql_content 计数，所有段（含 SET/USE）都计入段数。SET/USE 在 server 端只计数不识别（不做 SESSION_CONFIG 类型判定），到 executor 端再识别合并。

### 4.3 边界规则

- 整个窗口全是 SET/USE（无真正查询）：executor 拆分时发现，标记 Job FAILED，错误码 `ADHOC_JOB_NO_EXECUTABLE_SQL`
- 最后一段是 SET/USE（无后续查询）：该 SET/USE 丢弃（无意义）

### 4.4 prefix_sql 字段

`adhoc_query_task` 有 `prefix_sql` 字段（text 类型，SET/USE 前缀语句，Job 内累积，不含 sql_content 本身）。

sql_hash 计算：`sql_hash = SHA256(prefix_sql + sql_content)`（前缀参与 hash，因为前缀影响结果）。

详细拆分逻辑、session 生命周期与 Task 失败传播见模块 01。

## 5. 前置校验流程

server 接收 Job 提交请求后，按以下顺序执行校验，任一失败直接返回错误给前端，不创建 Job 记录。全部通过后创建 Job 记录（status=PENDING），调度到 executor（dispatchJob）。

```
server 接收 Job 提交请求
  │
  ▼ 1. SQL 语法校验（g4 解析整个 sql_content）
  │   ├── 语法错误：拒绝，返回 ADHOC_SQL_SYNTAX_ERROR
  │   ├── SqlType=UNKNOWN：拒绝，返回 ADHOC_SQL_TYPE_NOT_SUPPORTED
  │   └── 语法正确：继续
  │
  ▼ 2. Task 段数检查（按 ';' 拆分计数）
  │   segment_count = sql_content.split(";").length
  │   if segment_count > max_tasks_per_job:
  │     拒绝，返回 ADHOC_JOB_TOO_MANY_TASKS
  │   （SET/USE 也计入段数，只计数不识别）
  │
  ▼ 3. 限流检查
  │   ├── 全局 PENDING Job 数 <= max_pending_jobs_global
  │   ├── per-user PENDING Job 数 <= max_pending_jobs_per_user
  │   ├── 全局 RUNNING Job 数 <= max_running_jobs_global
  │   └── per-user RUNNING Job 数 <= max_running_jobs_per_user
  │   超限：拒绝，返回 ADHOC_JOB_LIMIT_EXCEEDED
  │
  ▼ 4. engine_type 合法性
  │   if engine_type 为空：拒绝，返回 ADHOC_ENGINE_TYPE_REQUIRED
  │   if engine_type not in (KYUUBI, STARROCKS)：
  │     拒绝，返回 ADHOC_ENGINE_TYPE_INVALID
  │
  ▼ 5. engine_params 合法性（JSON 格式 + 白名单校验）
  │   if engine_params 非空且 JSON 解析失败：
  │     拒绝，返回 ADHOC_ENGINE_PARAMS_INVALID
  │   对每个 param_key 查 adhoc_engine_param_rule（engine_type + param_key + enabled=1）：
  │     规则不存在：拒绝，返回 ADHOC_ENGINE_PARAM_NOT_ALLOWED
  │     param_value_type=INT：校验 value 在 [min_value, max_value] 区间
  │       超范围：拒绝，返回 ADHOC_ENGINE_PARAM_OUT_OF_RANGE
  │     param_value_type=STRING 且 allowed_values 非空：校验 value 在枚举列表内
  │       不在列表：拒绝，返回 ADHOC_ENGINE_PARAM_VALUE_INVALID
  │     param_value_type=BOOLEAN：校验 value ∈ {true, false}
  │       非法值：拒绝，返回 ADHOC_ENGINE_PARAM_VALUE_INVALID
  │
  ▼ 6. 引擎语句类型限制（仅 STARROCKS）
  │   if engine_type = STARROCKS：
  │     按 ';' 拆分 sql_content，对每段解析 SqlType
  │     允许：DQL / CTAS / SESSION_CONFIG / AUX
  │     拒绝：DDL_CREATE / DDL_ALTER / DDL_DROP / DML_INSERT / DML_MODIFY / DCL / UNKNOWN
  │     任一段不在允许集合：拒绝，返回 ADHOC_ENGINE_SQL_TYPE_NOT_SUPPORTED
  │     （理由：STARROCKS 引擎仅支持 SELECT 查询，DDL/DML 修改操作走专用数仓通道，不走 adhoc）
  │
  ▼ 校验通过：
  │   创建 Job 记录（status=PENDING）
  │   调度到 executor（dispatchJob）
```

### 5.1 校验项说明

#### 5.1.1 SQL 语法校验

按 engine_type 选用对应 g4（KYUUBI 用 Spark SQL g4，STARROCKS 用 StarRocks g4），对整个 sql_content 做 ANTLR4 解析。g4 解析成功但 SqlType=UNKNOWN 也视为不通过。

#### 5.1.2 Task 段数检查

按 `;` 拆分 sql_content 计数，包含 SET/USE 段。SET/USE 在 server 端只计数不识别，到 executor 端再做 SESSION_CONFIG 类型判定与合并（见 §4）。

#### 5.1.3 限流检查

PENDING Job 数控制排队堆积，RUNNING Job 数控制执行并发。全局 + per-user 双重限制，避免单用户挤占资源或全局过载。

#### 5.1.4 engine_type 合法性

engine_type 必须显式指定且为 KYUUBI 或 STARROCKS。不指定返回 `ADHOC_ENGINE_TYPE_REQUIRED`，非法值返回 `ADHOC_ENGINE_TYPE_INVALID`。

#### 5.1.5 engine_params 合法性（JSON 格式 + 白名单校验）

engine_params 为可选字段，非空时必须为合法 JSON 字符串，且每个参数键值对需通过 `adhoc_engine_param_rule` 表的白名单校验。

**校验流程**：

1. JSON 格式校验：解析失败返回 `ADHOC_ENGINE_PARAMS_INVALID`
2. 对每个 `{param_key: param_value}`，查 `adhoc_engine_param_rule`（`engine_type + param_key + enabled=1`）
3. 规则不存在（参数不在白名单）：拒绝，返回 `ADHOC_ENGINE_PARAM_NOT_ALLOWED`
4. `param_value_type=INT`：校验 `param_value` 在 `[min_value, max_value]` 区间，超范围返回 `ADHOC_ENGINE_PARAM_OUT_OF_RANGE`
5. `param_value_type=STRING` 且 `allowed_values` 非空：校验 `param_value` 在枚举列表内，不在返回 `ADHOC_ENGINE_PARAM_VALUE_INVALID`
6. `param_value_type=BOOLEAN`：校验 `param_value ∈ {true, false}`，非法返回 `ADHOC_ENGINE_PARAM_VALUE_INVALID`
7. 全部通过：继续下一步前置校验

**错误码汇总**：

| 错误码 | 触发条件 |
|---|---|
| `ADHOC_ENGINE_PARAMS_INVALID` | engine_params 非合法 JSON |
| `ADHOC_ENGINE_PARAM_NOT_ALLOWED` | 参数键不在白名单内（adhoc_engine_param_rule 无此 engine_type + param_key 的 enabled 规则） |
| `ADHOC_ENGINE_PARAM_OUT_OF_RANGE` | INT 型参数值超出 [min_value, max_value] |
| `ADHOC_ENGINE_PARAM_VALUE_INVALID` | STRING 枚举不匹配 / BOOLEAN 非 true,false |

白名单规则由运维维护，支持按引擎（KYUUBI/STARROCKS）分别配置，详见模块 11。

#### 5.1.6 引擎语句类型限制（仅 STARROCKS）

STARROCKS 引擎定位为 OLAP 查询引擎，仅支持 SELECT 相关语句；DDL/DML 修改操作走专用数仓通道，不走 adhoc。server 前置校验阶段拦截，避免无效提交占用调度资源。

**校验流程**：
1. 按 `;` 拆分 sql_content（与 Task 段数检查同款拆分）
2. 对每段用 StarRocks g4 解析，提取 SqlType
3. SET/USE 段识别为 SESSION_CONFIG，允许
4. 其他段检查 SqlType 是否在允许集合

**STARROCKS 允许的 SqlType**：

| SqlType | 允许 | 示例 |
|---|---|---|
| DQL | 是 | SELECT / SHOW / DESCRIBE / EXPLAIN |
| CTAS | 是 | CREATE TABLE t AS SELECT ... |
| SESSION_CONFIG | 是 | SET / USE |
| AUX | 是 | SHOW DATABASES / SHOW TABLES |
| DDL_CREATE | 否 | CREATE TABLE / CREATE VIEW（非 CTAS） |
| DDL_ALTER | 否 | ALTER TABLE |
| DDL_DROP | 否 | DROP TABLE |
| DML_INSERT | 否 | INSERT INTO |
| DML_MODIFY | 否 | UPDATE / DELETE / MERGE |
| DCL | 否 | GRANT / REVOKE |
| UNKNOWN | 否 | g4 解析成功但无法识别类型 |

**KYUUBI 引擎无此限制**：KYUUBI 走 Spark SQL，支持完整 DDL/DML/DQL/DCL，由引擎侧控制权限。

**错误码**：`ADHOC_ENGINE_SQL_TYPE_NOT_SUPPORTED`（错误消息指明哪一段 SQL 的什么类型不被 STARROCKS 支持）

### 5.2 限流参数

| 参数 | 默认值 | 说明 |
|---|---|---|
| max_tasks_per_job | 20 | 单 Job 最大段数（含 SET/USE） |
| max_pending_jobs_global | 100 | 全局排队中 Job 上限 |
| max_pending_jobs_per_user | 5 | per-user 排队中 Job 上限 |
| max_running_jobs_global | 50 | 全局运行中 Job 上限 |
| max_running_jobs_per_user | 3 | per-user 运行中 Job 上限 |

参数通过 Apollo 配置，可动态调整。

## 6. 权限处理

本模块不做权限检查和赋权。

- 如引擎侧部署了权限插件，用户提交 SQL 后由引擎执行时拦截
- 无权限则引擎报错，executor 捕获，标记 Task FAILED（fail_reason_category=ENGINE_ERROR）
- error_message = 引擎报错详情（含权限拒绝信息）
- 不再区分权限失败、SQL 错误等，统一作为 ENGINE_ERROR，由 error_message 区分

## 7. 血缘

### 7.1 表级血缘

只做表级血缘（source_tables + sink_tables），不做字段级血缘。adhoc_query_table_ref 通过 query_id 与 Task 1:1 绑定，每个 Task 产生的血缘可独立查询。

| 级别 | 提取器 | 内容 | 存储位置 |
|---|---|---|---|
| 表级 | TableLineageExtractor | 源表 -> 目标表关系 | adhoc_query_table_ref.source_tables_json / sink_tables_json |

### 7.2 表级血缘规则

source_tables（被读取的表）和 sink_tables（被写入/创建/删除的表）按 SqlType 不同：

| SqlType | source_tables | sink_tables |
|---|---|---|
| DQL | FROM/JOIN 的表 | 无 |
| DDL_CREATE | 无 | CREATE 的表 |
| DDL_ALTER | 无 | ALTER 的表 |
| DDL_DROP | 无 | DROP 的表 |
| DML_INSERT | SELECT 部分的表 | INSERT 的表 |
| DML_MODIFY | 无 | UPDATE/DELETE 的表 |
| CTAS | SELECT 部分的表 | CREATE 的表 |
| AUX | DESCRIBE 的表等 | 无 |
| SESSION_CONFIG | 无 | 无 |
| DCL | 无 | 无 |

JSON 格式：

```json
// source_tables_json
[
  {"database": "dim_db", "table": "dim_user", "alias": "u"},
  {"database": "dim_db", "table": "dim_order", "alias": "o"}
]

// sink_tables_json
[
  {"database": "ods_db", "table": "tmp_user_order", "operation": "CREATE"}
]
```

用途：
- 审计追溯（谁查了哪些表）
- 影响分析
- 按 Task（query_id）查询血缘，定位"哪个任务读了哪些表/写了哪些表"

### 7.3 字段级血缘（后续可扩展）

可扩展 column_lineage_json 字段，提取字段 -> 字段的映射关系，含 transform 类型（DIRECT/TRANSFORM/AGGREGATE/EXPRESSION）。当前 table_ref 表无此字段。

### 7.4 写入时机

executor 拆分阶段写入 table_ref 表，供审计和影响分析使用。

## 8. 风险项

RiskItemExtractor 标记风险项，写入 `adhoc_query_governance.risk_items_json`：

```json
[
  {"type": "FULL_TABLE_SCAN", "table": "dim_db.dim_order", "level": "WARN"},
  {"type": "CROSS_DATABASE", "tables": ["dim_db.dim_user", "ods_db.log_click"], "level": "INFO"},
  {"type": "DANGEROUS_FUNCTION", "func": "explode", "level": "WARN"},
  {"type": "CARTESIAN_JOIN", "level": "WARN"}
]
```

### 8.1 风险类型

| type | 含义 | level | 处理 |
|---|---|---|---|
| FULL_TABLE_SCAN | 全表扫描（无分区过滤） | WARN | 标记 |
| CROSS_DATABASE | 跨库查询 | INFO | 标记 |
| DANGEROUS_FUNCTION | 危险函数（explode/posexplode） | WARN | 标记 |
| CARTESIAN_JOIN | 笛卡尔积 JOIN | WARN | 标记 |

仅标记不阻断，不作为治理关卡。LIMIT 强制由 executor 在结果拉取阶段施加（见模块 04），不在治理阶段做 SQL 改写。

## 9. 数据模型

本模块涉及 3 张表（完整 DDL 见模块 11）：

### 9.1 adhoc_query_governance（治理记录）

每 Task 一条，记录解析与风险标记结果：

| 字段 | 类型 | 说明 |
|---|---|---|
| query_id | varchar(64) PK | Task ID |
| sql_type | varchar(16) | SqlType 枚举，与 task.sql_type 一致 |
| risk_items_json | text | 风险项列表 |
| governance_result | varchar(16) | PASSED / DENIED |
| deny_reason | varchar(256) | 拒绝原因（前置校验阶段错误码；executor 阶段一般 PASSED） |
| create_time / update_time | datetime | 创建/更新时间 |

**关键索引**：`uk_query_id`（query_id 唯一）

### 9.2 adhoc_query_table_ref（SQL 表引用与血缘）

每 Task 一条，结构化引用：

| 字段 | 类型 | 说明 |
|---|---|---|
| id | bigint PK | 自增 |
| query_id | varchar(64) UK | Task ID |
| source_tables_json | longtext | 源表列表 JSON |
| sink_tables_json | longtext | 目标表列表 JSON |
| create_time / update_time | datetime | 创建/更新时间 |

**关键索引**：`uk_query_id`（query_id 唯一，与 Task 1:1 绑定）

### 9.3 adhoc_engine_param_rule（引擎参数白名单规则）

server 前置校验步骤 5 用，详见 §5.1.5 和模块 11。

## 10. 与其他模块的接口

### 10.1 任务调度与执行模块（模块 01）

- 模块 01 在前置校验阶段调用本模块的 6 项校验（见 §5）
- 前置校验失败 -> 不创建 Job 记录，直接返回错误给前端
- 引擎执行报错（含权限拒绝）-> 模块 01 标记 fail_reason_category=ENGINE_ERROR，status=FAILED

### 10.2 引擎路由模块（模块 03）

- 本模块不产出 SqlFeature，engine_type 由用户显式指定，模块 03 只做实例选择（用户指定 instance 或用该 engine_type 的默认 instance）

### 10.3 结果存储模块（模块 04）

- 本模块的 has_result_set 决定模块 04 是否走结果写入链路
- 本模块不在 server 端做 SQL 改写（如追加 LIMIT），LIMIT 强制由 executor 在结果拉取阶段施加（见模块 04）
- 本模块不产出脱敏配置，模块 04 直接存储原始结果

### 10.4 可观测模块

- 前置校验决策（通过/拒绝）写入 adhoc_query_governance 表（governance_result + deny_reason）
- 风险项标记写入 adhoc_query_governance.risk_items_json 便于审计
- 不建 adhoc_query_event 表，事件流由 task.status/stage/fail_stage + 时间戳 + execution_log 覆盖（见模块 06/09）
