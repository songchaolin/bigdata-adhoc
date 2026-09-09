# 模块 11：表结构清单（MySQL DDL 汇总）

> 本模块汇总业务模块涉及的所有 MySQL 表的完整 DDL，含字段、索引、注释。跨模块新增字段在注释中标注来源模块。

## 1. 概述

### 1.1 文档职责

- 汇总所有业务表的 CREATE TABLE 语句
- 标注跨模块新增字段的来源
- 提供索引设计说明
- 作为建表脚本的基础

### 1.2 通用约定

- 存储引擎：InnoDB
- 字符集：utf8mb4，collation：utf8mb4_unicode_ci
- 主键：业务表用 varchar(64) UUID，实例/配置表用 bigint 自增
- 时间字段：datetime，精确到秒；需要毫秒的用 datetime(3)
- 软删除：is_deleted tinyint（0=正常，1=已删除）
- 冗余字段：所有含 user_id 的表带 user_name（中文名）
- 实例标识统一命名：`instance_id`（格式 ip:port），心跳时间统一命名：`heartbeat_time`

### 1.3 表清单

| 序号 | 表名 | 所属模块 | 用途 |
|---|---|---|---|
| 1 | adhoc_query_job | 模块 1 | Job 主表 |
| 2 | adhoc_query_task | 模块 1 | Task 主表 |
| 3 | adhoc_query_governance | 模块 2 | 治理记录（executor 端写入） |
| 4 | adhoc_query_table_ref | 模块 2 | SQL 表引用与血缘 |
| 5 | adhoc_engine_param_rule | 模块 2 | 引擎参数白名单规则（server 前置校验用） |
| 6 | adhoc_result_summary | 模块 4 | 结果摘要（含存储 key 与上传状态） |
| 7 | adhoc_server_instance | 模块 5 | server 实例 |
| 8 | adhoc_executor_instance | 模块 5 | executor 实例 |
| 9 | adhoc_file_node | 模块 8 | 用户文件树节点（保存的 SQL） |

共 9 张表。不建 adhoc_query_event（事件流水表，用执行日志替代）、不建 adhoc_query_resource（资源表，scan_rows/scan_bytes 在 task 表）。

**不建的关联表**：

| 表名 | 排除原因 |
|---|---|
| adhoc_query_task_engine_attempt | 无 AUTO 模式，不需要引擎尝试明细 |
| adhoc_query_result_schema | schema 存文件头（结果文件 MAGIC + schema 行），不建表 |
| adhoc_query_result_file | 一个 Task 一个文件，路径存 result_summary.persistent_path（存储 key） |
| 权限相关表（adhoc_permission_rule、adhoc_grant_log 等） | 无平台层鉴权，依赖引擎侧权限 |
| adhoc_user_quota | per-user 流控改用 Apollo 全局配置（max_pending_jobs_per_user / max_running_jobs_per_user） |

## 2. 任务调度与执行（模块 1）

### 2.1 adhoc_query_job

```sql
CREATE TABLE adhoc_query_job (
  job_id                      VARCHAR(64)   NOT NULL COMMENT 'Job ID（UUID）',
  user_id                     VARCHAR(64)   NOT NULL COMMENT '提交人 ID',
  user_name                   VARCHAR(64)   DEFAULT NULL COMMENT '提交人中文名（冗余）',
  sql_content                 TEXT          NOT NULL COMMENT '原始 SQL（拆分前，整段提交给 executor）',
  engine_type                 VARCHAR(16)   NOT NULL COMMENT 'KYUUBI/STARROCKS（用户必填）',
  engine_instance             VARCHAR(64)   DEFAULT NULL COMMENT '指定 instance，未指定用该 engine_type 默认 instance（Apollo 配置）',
  engine_params               TEXT          DEFAULT NULL COMMENT 'JSON，session 初始参数（SET 可覆盖）',
  executor_instance           VARCHAR(128)  DEFAULT NULL COMMENT '执行 Job 的 executor instance_id（dispatchJob 时确定）',
  status                      VARCHAR(16)   NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/DISPATCHING/RUNNING/SUCCESS/PARTIAL_FAILED/FAILED/CANCELED（PENDING->DISPATCHING 在 claimJob CAS 抢占时，DISPATCHING->RUNNING 在 dispatchJob 下发时）',
  cancel_requested            TINYINT       NOT NULL DEFAULT 0 COMMENT '取消请求标志位',
  cancel_requested_time       DATETIME      DEFAULT NULL COMMENT '取消请求时刻',
  processing_server_instance  VARCHAR(128)  DEFAULT NULL COMMENT '承接 Job 的 server instance_id（server 宕机时置 NULL，等 executor 直写状态）',
  client_ip                   VARCHAR(64)   DEFAULT NULL COMMENT '客户端 IP',
  client_user_agent           VARCHAR(256)  DEFAULT NULL COMMENT 'UA',
  client_request_id           VARCHAR(64)   DEFAULT NULL COMMENT '客户端请求 ID（审计）',
  source_file_node_id         VARCHAR(64)   DEFAULT NULL COMMENT '来源文件节点ID（模块 8，从文件提交时有值）',
  submit_time                 DATETIME      NOT NULL COMMENT 'Job 提交时间（server 创建 Job 记录时）',
  validate_finish_time        DATETIME      DEFAULT NULL COMMENT '前置校验完成时间（server 前置校验通过后；校验失败不创建 Job，此字段不设置）',
  dispatch_time               DATETIME      DEFAULT NULL COMMENT 'dispatchJob 下发时间（server -> executor）',
  split_finish_time           DATETIME      DEFAULT NULL COMMENT 'executor 拆分完成时间（Task 记录创建后，RUNNING 阶段内的时间戳）',
  start_time                  DATETIME      DEFAULT NULL COMMENT '第一个 Task 开始执行时间',
  finish_time                 DATETIME      DEFAULT NULL COMMENT 'Job 终态时间',
  duration_ms                 BIGINT        DEFAULT NULL COMMENT 'Job 总耗时（finish_time - submit_time）',
  persistent_log_path         VARCHAR(256)  DEFAULT NULL COMMENT 'job.log 存储key，如 log/{jobId}/job.log（Job 终态一次性 uploadLog 存储）',
  is_deleted                  TINYINT       NOT NULL DEFAULT 0 COMMENT '软删除',
  create_time                 DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time                 DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (job_id),
  KEY idx_user_status (user_id, status),
  KEY idx_status_submit (status, submit_time),
  KEY idx_executor_status (executor_instance, status),
  KEY idx_client_ip (client_ip),
  KEY idx_user_submit (user_id, submit_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Job 主表';
```

**字段说明**：

时间戳字段（按阶段状态设计）：
- `validate_finish_time DATETIME`：前置校验完成时间，server 前置校验通过后设置。校验失败不创建 Job 记录，此字段不设置。
- `dispatch_time DATETIME`：dispatchJob 下发时间，server 发起 dispatchJob RPC 时设置。
- `split_finish_time DATETIME`：executor 拆分完成时间，在 RUNNING 阶段内。
- `duration_ms BIGINT`：Job 总耗时，`finish_time - submit_time`。
- `executor_instance VARCHAR(128)`：执行 Job 的 executor instance_id，dispatchJob 时确定。server/executor 宕机补偿均依赖此字段。
- `persistent_log_path VARCHAR(256)`：job.log 存储 key（如 `log/{jobId}/job.log`），Job 终态一次性 uploadLog 存储。

status 枚举 7 态：PENDING/DISPATCHING/RUNNING/SUCCESS/PARTIAL_FAILED/FAILED/CANCELED。PENDING->DISPATCHING 在 claimJob CAS 抢占时，DISPATCHING->RUNNING 在 dispatchJob 下发时。

索引：
- `idx_executor_status (executor_instance, status)`：executor 宕机补偿时按 executor + status 扫描关联 Job。

### 2.2 adhoc_query_task

```sql
CREATE TABLE adhoc_query_task (
  query_id                    VARCHAR(64)   NOT NULL COMMENT 'Task ID（UUID）',
  job_id                      VARCHAR(64)   NOT NULL COMMENT '所属 Job',
  segment_index               INT           NOT NULL COMMENT 'Job 内序号（executor 拆分后排序）',
  user_id                     VARCHAR(64)   NOT NULL COMMENT '提交人 ID（冗余）',
  user_name                   VARCHAR(64)   DEFAULT NULL COMMENT '提交人中文名（冗余）',
  prefix_sql                  TEXT          DEFAULT NULL COMMENT 'SET/USE 前缀语句（Job 内累积，不含 sql_content 本身；executor 拆分时写入）',
  sql_content                 TEXT          NOT NULL COMMENT '单段可执行 SQL（不含 SET/USE 前缀）',
  sql_hash                    VARCHAR(64)   DEFAULT NULL COMMENT 'SHA256(prefix_sql + sql_content)，结果复用用（前缀参与 hash）',
  sql_type                    VARCHAR(16)   DEFAULT NULL COMMENT 'DQL/DDL_CREATE/DDL_ALTER/DDL_DROP/DML_INSERT/DML_MODIFY/CTAS/AUX/DCL/SESSION_CONFIG/UNKNOWN',
  has_result_set              TINYINT       NOT NULL DEFAULT 0 COMMENT '是否有结果集',
  affected_rows               BIGINT        DEFAULT NULL COMMENT 'DML 影响行数',
  engine_params               TEXT          DEFAULT NULL COMMENT 'JSON，session 参数（Job 级，冗余到 Task 便于审计）',
  status                      VARCHAR(16)   NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/RUNNING/SUCCESS/FAILED/CANCELED（5 态）',
  stage                       VARCHAR(16)   DEFAULT NULL COMMENT 'EXECUTING/FETCHING/WRITING（仅 status=RUNNING 时有值，executor 直写）',
  cancel_requested            TINYINT       NOT NULL DEFAULT 0 COMMENT '取消请求标志位',
  cancel_requested_time       DATETIME      DEFAULT NULL COMMENT '取消请求时刻',
  fail_stage                  VARCHAR(16)   DEFAULT NULL COMMENT 'SPLIT/EXECUTING/FETCHING/WRITING/OSS_UPLOAD（终态时记录）',
  fail_reason_category        VARCHAR(64)   DEFAULT NULL COMMENT 'ENGINE_ERROR/EXECUTOR_CRASHED/TASK_LOST/SKIPPED_DUE_TO_PRIOR_FAILURE/SKIPPED_DUE_TO_SESSION_LOSS/WRITE_ERROR/FETCH_ERROR/SPLIT_ERROR/CANCELED（9 类）',
  error_code                  VARCHAR(64)   DEFAULT NULL COMMENT '失败错误码（ADHOC_* 前缀）',
  error_message               TEXT          DEFAULT NULL COMMENT '失败详情（权限拒绝由引擎层处理，统一作为 ENGINE_ERROR，error_message 区分）',
  engine_type                 VARCHAR(16)   DEFAULT NULL COMMENT '实际执行引擎（KYUUBI/STARROCKS）',
  engine_instance             VARCHAR(64)   DEFAULT NULL COMMENT '实际执行引擎实例',
  executor_instance           VARCHAR(128)  DEFAULT NULL COMMENT '执行的 executor instance_id（与 job.executor_instance 一致）',
  processing_server_instance  VARCHAR(128)  DEFAULT NULL COMMENT '承接 Job 的 server instance_id（冗余，便于 server 宕机补偿）',
  reused_from_task_id         VARCHAR(64)   DEFAULT NULL COMMENT '命中复用时指向原 Task（复用前提：原 Task storage_type = PERSISTENT）',
  scan_rows                   BIGINT        DEFAULT NULL COMMENT '引擎扫描行数（模块 6，可空）',
  scan_bytes                  BIGINT        DEFAULT NULL COMMENT '引擎扫描字节数（模块 6，可空）',
  persistent_log_path         VARCHAR(256)  DEFAULT NULL COMMENT 'task.log 存储 key，如 log/{jobId}/{taskId}/task.log（executor 内存 buffer + 定期(2s) storageClient.uploadLog 覆盖快照 + 终态最后 flush；server 读路径：运行中 + executor UP -> gRPC FetchLog 读内存，executor DOWN/终态 -> storageClient.download 读存储快照）',
  enqueue_time                DATETIME      DEFAULT NULL COMMENT 'Task 入队时间（executor 拆分时创建，status=PENDING）',
  start_time                  DATETIME      DEFAULT NULL COMMENT 'Task 开始执行时间（stage=EXECUTING）',
  fetch_start_time            DATETIME      DEFAULT NULL COMMENT '开始拉结果时间（stage=FETCHING）',
  write_start_time            DATETIME      DEFAULT NULL COMMENT '开始序列化+上传存储时间（stage=WRITING）',
  local_write_finish_time     DATETIME      DEFAULT NULL COMMENT '结果序列化完成时间（内存，不写本地，isLast 时）',
  oss_upload_time             DATETIME      DEFAULT NULL COMMENT '存储上传完成时间（上传失败为 NULL）',
  finish_time                 DATETIME      DEFAULT NULL COMMENT 'Task 终态时间',
  duration_ms                 BIGINT        DEFAULT NULL COMMENT 'Task 执行耗时（finish_time - start_time）',
  is_deleted                  TINYINT       NOT NULL DEFAULT 0 COMMENT '软删除',
  create_time                 DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time                 DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (query_id),
  KEY idx_status_enqueue (status, enqueue_time),
  KEY idx_user_sql_hash_status_finish (user_id, sql_hash, status, finish_time),
  KEY idx_processing_server_status (processing_server_instance, status),
  KEY idx_executor_status (executor_instance, status),
  KEY idx_job_segment (job_id, segment_index),
  KEY idx_user_status (user_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Task 主表';
```

**字段说明**：

- `prefix_sql TEXT`：SET/USE 前缀语句，Job 内累积，executor 拆分时写入。
- `local_write_finish_time DATETIME`：结果序列化完成时间（内存，不写本地，isLast 时）。
- `oss_upload_time DATETIME`：存储上传完成时间，上传失败时为 NULL。
- `persistent_log_path VARCHAR(256)`：task.log 存储 key（如 `log/{jobId}/{taskId}/task.log`），executor 内存 buffer + 定期(2s) `storageClient.uploadLog` 覆盖快照 + 终态最后 flush。server 读路径：运行中 + executor UP -> gRPC `FetchLog` 读内存；executor DOWN 或 Task 终态 -> `storageClient.download` 读存储快照（executor 宕机丢最后 ≤2s 日志）。

枚举说明：
- `sql_hash` 计算为 `SHA256(prefix_sql + sql_content)`。
- `sql_type` 枚举含 `SESSION_CONFIG`（用于 SET/USE 识别，但 SET/USE 合并到下一个 Task 不独立成 Task）。
- `status` 5 态：PENDING/RUNNING/SUCCESS/FAILED/CANCELED。权限拒绝统一作为 ENGINE_ERROR（无 REJECTED），session 模式由引擎层超时控制（无 TIMEOUT）。
- `stage` 3 阶段：EXECUTING/FETCHING/WRITING。
- `fail_stage` 5 类：SPLIT/EXECUTING/FETCHING/WRITING/OSS_UPLOAD（存储 upload 失败 = 结果不可读，走 WRITE_ERROR，Task FAILED）。
- `fail_reason_category` 9 类。
- `engine_type` 只保留 KYUUBI/STARROCKS。

## 3. SQL 解析与治理（模块 2）

### 3.1 adhoc_query_governance

```sql
CREATE TABLE adhoc_query_governance (
  query_id                    VARCHAR(64)   NOT NULL COMMENT 'Task ID',
  sql_type                    VARCHAR(16)   DEFAULT NULL COMMENT 'SqlType 枚举，与 task.sql_type 一致',
  risk_items_json             TEXT          DEFAULT NULL COMMENT '风险项列表 JSON',
  executed_sql_content        TEXT          DEFAULT NULL COMMENT '改写后的实际执行 SQL（如追加 LIMIT 后）',
  governance_result           VARCHAR(16)   DEFAULT NULL COMMENT 'PASSED / DENIED（DENIED 仅用于 executor 端拒绝，如 Job 全是 SET/USE 无可执行 SQL）',
  deny_reason                 VARCHAR(256)  DEFAULT NULL COMMENT '拒绝原因（关卡 + 错误码）',
  create_time                 DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time                 DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (query_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='治理记录（每 Task 一条，executor 端写入）';
```

**字段说明**：
- `governance_result` 语义：server 前置校验失败直接返回错误给前端，不创建 Job 记录；DENIED 仅用于 executor 端拒绝场景（如 `ADHOC_JOB_NO_EXECUTABLE_SQL`）。
- 治理记录由 executor 端写入（server 不再接触 Task 级数据）。

### 3.2 adhoc_query_table_ref

```sql
CREATE TABLE adhoc_query_table_ref (
  id                          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '自增',
  query_id                    VARCHAR(64)   NOT NULL COMMENT 'Task ID',
  source_tables_json          LONGTEXT      DEFAULT NULL COMMENT '源表列表 JSON [{database,table,alias}]',
  sink_tables_json            LONGTEXT      DEFAULT NULL COMMENT '目标表列表 JSON [{database,table,alias}]',
  create_time                 DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time                 DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_query_id (query_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SQL 表引用与表级血缘（每 Task 一条，executor 端写入）';
```

**字段说明**：
- 只做表级血缘（source_tables / sink_tables），不做字段级血缘。每个 Task 一条记录，绑定 query_id，方便按 Task 查询其产生的血缘。
- 由 executor 端在拆分阶段写入。

### 3.3 adhoc_engine_param_rule

```sql
CREATE TABLE adhoc_engine_param_rule (
  id                BIGINT        NOT NULL AUTO_INCREMENT COMMENT '自增',
  engine_type       VARCHAR(16)   NOT NULL COMMENT '引擎类型 KYUUBI/STARROCKS',
  param_key         VARCHAR(128)  NOT NULL COMMENT '参数键名，如 spark.sql.shuffle.partitions、spark.executor.memory',
  param_value_type  VARCHAR(16)   NOT NULL DEFAULT 'STRING' COMMENT '参数值类型 INT/STRING/BOOLEAN',
  min_value         VARCHAR(64)   DEFAULT NULL COMMENT '数值型参数最小值（param_value_type=INT 时生效）',
  max_value         VARCHAR(64)   DEFAULT NULL COMMENT '数值型参数最大值（param_value_type=INT 时生效）',
  allowed_values    TEXT          DEFAULT NULL COMMENT '枚举允许值 CSV（非空时参数值必须在列表内）',
  default_value     VARCHAR(256)  DEFAULT NULL COMMENT '默认值（运维参考，不自动注入）',
  description       VARCHAR(512)  DEFAULT NULL COMMENT '参数说明',
  enabled           TINYINT       NOT NULL DEFAULT 1 COMMENT '是否启用 1=启用 0=禁用',
  create_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_engine_param (engine_type, param_key),
  KEY idx_engine_enabled (engine_type, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='引擎参数白名单规则（server 前置校验用，按 engine_type + param_key 唯一）';
```

**字段说明**：

| 字段 | 说明 |
|---|---|
| engine_type + param_key | 唯一键，同一引擎同一参数只一条规则 |
| param_value_type | INT（数值范围校验）/ STRING（枚举值或自由字符串）/ BOOLEAN（true/false） |
| min_value / max_value | param_value_type=INT 时生效，用户传入值必须在 [min, max] 区间 |
| allowed_values | CSV 枚举值（如 `true,false`、`OFF,SILENT,REPORTING`），非空时参数值必须在列表内；为空则只做类型校验 |
| default_value | 运维参考值，不自动注入（用户未传时由引擎默认值兜底） |
| enabled | 禁用后该参数不校验，等价于未配置规则 |

**校验逻辑**（server 前置校验步骤 5，详见模块 2 §5.1.5）：

1. engine_params JSON 解析出 `{param_key: param_value}` 键值对
2. 对每个 param_key 查 `adhoc_engine_param_rule`（engine_type + param_key + enabled=1）
3. 规则不存在：拒绝，返回 `ADHOC_ENGINE_PARAM_NOT_ALLOWED`（参数不在白名单内）
4. param_value_type=INT：校验 value 在 [min_value, max_value] 区间，不在则拒绝返回 `ADHOC_ENGINE_PARAM_OUT_OF_RANGE`
5. param_value_type=STRING 且 allowed_values 非空：校验 value 在枚举列表内，不在则拒绝返回 `ADHOC_ENGINE_PARAM_VALUE_INVALID`
6. param_value_type=BOOLEAN：校验 value ∈ {true, false}，不在则拒绝返回 `ADHOC_ENGINE_PARAM_VALUE_INVALID`
7. 全部通过：JSON 格式 + 白名单 + 范围/枚举校验通过，继续下一步前置校验

**初始数据示例**：

```sql
INSERT INTO adhoc_engine_param_rule (engine_type, param_key, param_value_type, min_value, max_value, allowed_values, description, enabled) VALUES
('KYUUBI', 'spark.sql.shuffle.partitions', 'INT', '1', '1000', NULL, 'shuffle 分区数', 1),
('KYUUBI', 'spark.executor.memory', 'STRING', NULL, NULL, '1g,2g,4g,8g,16g', 'executor 内存', 1),
('KYUUBI', 'spark.sql.adaptive.enabled', 'BOOLEAN', NULL, NULL, 'true,false', 'AQE 开关', 1),
('STARROCKS', 'parallel_fragment_exec_instance_num', 'INT', '1', '32', NULL, '并行 fragment 实例数', 1),
('STARROCKS', 'enable_spill', 'BOOLEAN', NULL, NULL, 'true,false', '落盘开关', 1);
```

## 4. 结果存储（模块 4）

### 4.1 adhoc_result_summary

```sql
CREATE TABLE adhoc_result_summary (
  query_id                    VARCHAR(64)   NOT NULL COMMENT 'Task ID',
  result_rows                 BIGINT        NOT NULL DEFAULT 0 COMMENT '结果总行数（上限 100w，LIMIT 强制）',
  result_bytes                BIGINT        DEFAULT NULL COMMENT '结果字节数',
  persistent_path             VARCHAR(256)  DEFAULT NULL COMMENT '存储 key（executor storageClient.uploadResult 一次性上传后写入），如 result/{jobId}/{taskId}/result.part-0',
  storage_type                VARCHAR(16)   NOT NULL DEFAULT 'NONE' COMMENT 'NONE/PERSISTENT（两态：NONE 无结果集，PERSISTENT 已上传存储可直读）',
  result_status               VARCHAR(16)   NOT NULL DEFAULT 'WRITING' COMMENT 'WRITING/COMPLETE/INCOMPLETE',
  oss_upload_status           VARCHAR(16)   NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/SUCCESS/FAILED（存储上传状态，storageClient.uploadResult 结果）',
  create_time                 DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (query_id),
  KEY idx_storage_type (storage_type),
  KEY idx_oss_upload_status (oss_upload_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='结果摘要（每 Task 一条，executor 直写）';
```

**storage_type 2 态说明**：

| storage_type | 含义 | 存储后端 | 读取路径 |
|---|---|---|---|
| NONE | 无结果集 | 无 | 无结果可读 |
| PERSISTENT | 已上传存储 | 有 | server `storageClient.download(persistent_path)` -> `AdhocResultReader` 分页直读 |

> executor 拉完结果行 -> `AdhocResultSerializer` 序列化（MAGIC+schema+rows 长度前缀）-> `byte[]` -> `storageClient.uploadResult` 一次性上传 -> `storage_type=PERSISTENT`、`persistent_path=key`、`oss_upload_status=SUCCESS`。上传失败 `oss_upload_status=FAILED`（重试上限后），Task FAILED（fail_stage=OSS_UPLOAD）。executor 不写本地文件。

**存储配置**（StorageClient SPI，详见模块 4 §15）：

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `adhoc.storage.type` | `local` | 存储后端（local / aliyun） |
| `adhoc.storage.local.base-dir` | `./data/storage` | local 实现存储根目录 |
| `adhoc.storage.aliyun.endpoint` | （无默认） | 阿里云 OSS endpoint |
| `adhoc.storage.aliyun.bucket` | （无默认） | OSS 桶名 |
| `adhoc.result.retention-days` | `30` | 结果/日志存储保留天数（local 自扫描 / aliyun 桶生命周期规则） |
| `adhoc.log.flush-interval-ms` | `2000` | 日志存储快照间隔（覆盖上传） |
| `adhoc.log.flush-batch-lines` | `100` | 日志 buffer 行数阈值 |

存储 key 命名：结果 `result/{jobId}/{taskId}/result.part-0`，日志 `log/{jobId}/{taskId}/task.log`，job 日志 `log/{jobId}/job.log`。`storageClient.uploadLog` 同名覆盖（用于日志定期快照）。

## 5. 高可用与补偿（模块 5）

### 5.1 adhoc_server_instance

```sql
CREATE TABLE adhoc_server_instance (
  id                          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '自增',
  instance_id                 VARCHAR(128)  NOT NULL COMMENT '实例标识 ip:port',
  host                        VARCHAR(64)   NOT NULL COMMENT 'IP',
  http_port                   INT           NOT NULL COMMENT 'HTTP 端口',
  status                      VARCHAR(16)   NOT NULL DEFAULT 'UNKNOWN' COMMENT 'UP/DOWN/UNKNOWN',
  accepting                   TINYINT       NOT NULL DEFAULT 1 COMMENT '是否承接新任务（优雅下线用）',
  start_time                  DATETIME      DEFAULT NULL COMMENT '启动时间',
  version                     VARCHAR(32)   DEFAULT NULL COMMENT '版本号',
  heartbeat_time              DATETIME      DEFAULT NULL COMMENT '最后心跳时间（每 5s 更新）',
  active_jobs                 INT           NOT NULL DEFAULT 0 COMMENT '当前处理 Job 数',
  create_time                 DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time                 DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_instance_id (instance_id),
  KEY idx_status (status),
  KEY idx_heartbeat (heartbeat_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='server 实例（server HA 用）';
```

**字段说明**：
- server 不处理 Task（Task 调度移到 executor），无 `active_tasks` 字段。
- server 之间互相心跳（每 5s），心跳超时阈值 15s。

### 5.2 adhoc_executor_instance

```sql
CREATE TABLE adhoc_executor_instance (
  id                          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '自增',
  instance_id                 VARCHAR(128)  NOT NULL COMMENT '实例标识 ip:port',
  host                        VARCHAR(64)   NOT NULL COMMENT 'IP',
  grpc_port                   INT           NOT NULL COMMENT 'gRPC 端口',
  status                      VARCHAR(16)   NOT NULL DEFAULT 'UNKNOWN' COMMENT 'UP/DOWN/UNKNOWN',
  accepting                   TINYINT       NOT NULL DEFAULT 1 COMMENT '是否承接新任务',
  start_time                  DATETIME      DEFAULT NULL COMMENT '启动时间',
  version                     VARCHAR(32)   DEFAULT NULL COMMENT '版本号',
  heartbeat_time              DATETIME      DEFAULT NULL COMMENT '最后心跳时间（每 5s 更新）',
  last_down_time              DATETIME      DEFAULT NULL COMMENT '最近一次 DOWN 时间（CAS 标记时写入）',
  max_concurrent_tasks        INT           NOT NULL DEFAULT 10 COMMENT '最大并发 Task 数（Apollo 配置）',
  engine_types                VARCHAR(128)  DEFAULT NULL COMMENT '支持的引擎类型 CSV（如 KYUUBI,STARROCKS）',
  cpu_usage                   DOUBLE        DEFAULT NULL COMMENT 'CPU 使用率（心跳上报，load_score 计算用）',
  mem_usage                   DOUBLE        DEFAULT NULL COMMENT '内存使用率（心跳上报，load_score 计算用）',
  disk_usage                  DOUBLE        DEFAULT NULL COMMENT '磁盘使用率（心跳上报，load_score 计算用）',
  running_tasks               TEXT          DEFAULT NULL COMMENT '当前在执行的 Task 列表 JSON（心跳上报，server 对账用，格式：[{task_id, status, stage}]）',
  cpu_usage_pct               DOUBLE        DEFAULT NULL COMMENT 'JVM 进程 CPU（旧字段，保留兼容）',
  system_cpu_usage_pct        DOUBLE        DEFAULT NULL COMMENT '系统整体 CPU（旧字段，保留兼容）',
  memory_usage_pct            DOUBLE        DEFAULT NULL COMMENT 'JVM 堆使用率（旧字段，保留兼容）',
  memory_used_mb              BIGINT        DEFAULT NULL COMMENT 'JVM 堆使用 MB（旧字段，保留兼容）',
  memory_max_mb               BIGINT        DEFAULT NULL COMMENT 'JVM 堆最大 MB（旧字段，保留兼容）',
  thread_count                INT           DEFAULT NULL COMMENT '线程数',
  last_gc_pause_ms            BIGINT        DEFAULT NULL COMMENT '最近 GC 暂停',
  load_score                  DOUBLE        DEFAULT NULL COMMENT '综合负载评分（cpu_usage*0.3 + mem_usage*0.3 + disk_usage*0.2 + running_tasks_ratio*0.2）',
  create_time                 DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time                 DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_instance_id (instance_id),
  KEY idx_status (status),
  KEY idx_heartbeat (heartbeat_time),
  KEY idx_accepting_load (accepting, load_score)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='executor 实例';
```

**字段说明**：

- `cpu_usage DOUBLE`：CPU 使用率，心跳上报，load_score 计算用。
- `mem_usage DOUBLE`：内存使用率，心跳上报，load_score 计算用。
- `disk_usage DOUBLE`：磁盘使用率，心跳上报，load_score 计算用。
- `running_tasks TEXT`：当前在执行的 Task 列表 JSON，心跳上报，用于 server 对账。格式：`[{"task_id":"...", "status":"RUNNING", "stage":"EXECUTING"}]`。当前运行 Task 数从 JSON 数组长度推导（无 `current_running` 字段）。
- `last_down_time DATETIME`：最近一次 DOWN 时间，server HA 线程 CAS 标记时写入。
- `max_concurrent_tasks INT`：最大并发 Task 数（Apollo 配置），load_score 计算用。
- `engine_types VARCHAR(128)`：支持的引擎类型 CSV，server 调度时按 engine_type 过滤可用 executor。

心跳上报 SQL：
```sql
UPDATE adhoc_executor_instance
SET heartbeat_time = NOW(), status = 'UP',
    cpu_usage = ?, mem_usage = ?, disk_usage = ?,
    running_tasks = ?
WHERE instance_id = ?;
```

load_score 计算：
```
load_score = cpu_usage * 0.3 + mem_usage * 0.3 + disk_usage * 0.2 + running_tasks_ratio * 0.2
```
其中 `running_tasks_ratio = JSON_LENGTH(running_tasks) / max_concurrent_tasks`。

server HA 补偿线程扫描：
```sql
SELECT instance_id FROM adhoc_executor_instance
WHERE status = 'UP' AND heartbeat_time < NOW() - INTERVAL 30 SECOND;
```

## 6. 目录树管理（模块 8）

### 6.1 adhoc_file_node

```sql
CREATE TABLE adhoc_file_node (
  node_id                     VARCHAR(64)   NOT NULL COMMENT '节点 ID（UUID）',
  user_id                     VARCHAR(64)   NOT NULL COMMENT '所属用户（隔离字段）',
  user_name                   VARCHAR(64)   DEFAULT NULL COMMENT '用户中文名（冗余）',
  parent_node_id              VARCHAR(64)   DEFAULT NULL COMMENT '父节点 ID，根为 NULL',
  node_type                   VARCHAR(16)   NOT NULL COMMENT 'DIRECTORY/FILE',
  node_name                   VARCHAR(128)  NOT NULL COMMENT '节点名（同父同 user 唯一）',
  sql_content                 TEXT          DEFAULT NULL COMMENT 'FILE 才有，SQL 原文（最大 1MB）',
  description                 VARCHAR(512)  DEFAULT NULL COMMENT 'FILE 才有，描述',
  is_deleted                  TINYINT       NOT NULL DEFAULT 0 COMMENT '软删除（0=正常，1=已删除）',
  create_time                 DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time                 DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (node_id),
  UNIQUE KEY uk_user_parent_name_del (user_id, parent_node_id, node_name, is_deleted),
  KEY idx_user_parent (user_id, parent_node_id),
  KEY idx_user_id (user_id),
  KEY idx_parent_deleted (parent_node_id, is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户文件树节点（保存的 SQL，每用户独立树）';
```

**字段说明**：此表用于用户保存的 SQL 文件树（前端文件浏览器功能），与 executor 运行存储 key（`result/{jobId}/{taskId}/...`、`log/{jobId}/{taskId}/...`）是不同概念。

## 7. 跨模块字段汇总

以下字段由其他模块新增到模块 1 的 job/task 表，已在上方 CREATE TABLE 中包含：

### 7.1 job 表跨模块字段

| 字段 | 来源模块 | 用途 |
|---|---|---|
| source_file_node_id | 模块 8 | 从文件提交查询时记录来源文件节点 |
| executor_instance | 模块 1/5 | 执行 Job 的 executor instance_id（dispatchJob 时确定，server/executor 宕机补偿用） |
| persistent_log_path | 模块 9 | job.log 存储 key（Job 终态一次性 uploadLog 存储） |

### 7.2 task 表跨模块字段

| 字段 | 来源模块 | 用途 |
|---|---|---|
| prefix_sql | 模块 1 | SET/USE 前缀语句（Job 内累积，executor 拆分时写入） |
| executor_instance | 模块 1/5 | 执行 Task 的 executor instance_id（与 job.executor_instance 一致） |
| scan_rows | 模块 6 | 引擎扫描行数（可空） |
| scan_bytes | 模块 6 | 引擎扫描字节数（可空） |
| persistent_log_path | 模块 9 | task.log 存储 key（executor 内存 buffer + 定期存储快照 + 终态 flush；server 读路径：gRPC FetchLog 读内存 或 storageClient.download 读存储快照） |

## 8. 不建的表（明确排除）

| 表名 | 排除原因 |
|---|---|
| adhoc_query_event | 事件流水表，用执行日志（存储文件）替代（模块 6/9 决策） |
| adhoc_query_resource | 资源统计表，scan_rows/scan_bytes 加到 task 表（模块 6 决策） |
| adhoc_server_instance_history | 历史实例归档表，DOWN 24h 直接物理删除（模块 5 决策） |
| adhoc_executor_instance_history | 同上 |
| adhoc_query_job_log | Job 日志表，用 executor 内存 + 存储 job.log 替代（模块 9 决策） |
| adhoc_query_task_log | Task 日志表，用 executor 内存 buffer + 存储 task.log 快照替代（模块 9 决策） |
| adhoc_query_engine_log | 引擎日志表，JDBC 日志合并到 Task 日志流（模块 9 决策） |
| adhoc_query_task_engine_attempt | 引擎尝试明细表，无 AUTO 模式 |
| adhoc_query_result_schema | 结果列 schema 表，schema 存结果文件头 |
| adhoc_query_result_file | 结果文件表，一个 Task 一个文件，路径存 result_summary.persistent_path |
| adhoc_permission_rule | 权限规则表，无平台层鉴权，依赖引擎侧权限 |
| adhoc_grant_log | 赋权日志表，无平台层鉴权 |
| adhoc_user_quota | per-user 流控覆盖表，改用 Apollo 全局配置 |
| adhoc_user | 用户表，信任请求头 userId，不做用户管理 |
| adhoc_tenant | 租户表，无租户概念（模块 10 决策） |
| adhoc_udf | UDF 注册表，不做 UDF 管理（模块 10 决策） |
| adhoc_mask_rule | 脱敏规则表，不做脱敏（模块 10 决策） |

## 9. 索引设计说明

### 9.1 主键

- 业务表（job/task/file_node）：varchar(64) UUID，全局唯一无序，避免分页问题
- 摘要表（result_summary）：varchar(64) query_id（每 Task 一条，直接用 query_id 作主键）
- 实例表（server_instance/executor_instance）：bigint 自增 + uk_instance_id

### 9.2 唯一索引

| 表 | 唯一索引 | 用途 |
|---|---|---|
| adhoc_query_governance | uk_query_id (PK) | 每 Task 一条治理记录 |
| adhoc_query_table_ref | uk_query_id | 每 Task 一条血缘 |
| adhoc_result_summary | PRIMARY KEY (query_id) | 每 Task 一条摘要 |
| adhoc_server_instance | uk_instance_id | 实例标识唯一 |
| adhoc_executor_instance | uk_instance_id | 实例标识唯一 |
| adhoc_file_node | uk_user_parent_name_del | 同父同 user 同名唯一（含 is_deleted） |

### 9.3 查询索引

| 表 | 索引 | 查询场景 |
|---|---|---|
| adhoc_query_job | idx_status_submit | server 调度扫描 PENDING Job |
| adhoc_query_job | idx_user_status | 查用户 Job 列表 |
| adhoc_query_job | idx_executor_status | executor 宕机补偿（按 executor + status 扫描关联 Job） |
| adhoc_query_job | idx_user_submit | 查用户 Job 列表按提交时间排序 |
| adhoc_query_task | idx_status_enqueue | executor 顺序执行 Task |
| adhoc_query_task | idx_user_sql_hash_status_finish | 结果复用查询 |
| adhoc_query_task | idx_processing_server_status | server 宕机补偿 |
| adhoc_query_task | idx_executor_status | executor 宕机补偿 + 心跳对账 |
| adhoc_query_task | idx_job_segment | Job 内 Task 串行（segment_index ASC） |
| adhoc_query_task | idx_user_status | per-user 流控 |
| adhoc_result_summary | idx_storage_type | 读路径按 storage_type 路由（NONE/PERSISTENT 两态） |
| adhoc_result_summary | idx_oss_upload_status | 监控告警查 oss_upload_status=FAILED |
| adhoc_server_instance | idx_heartbeat | server HA 健康检查扫描超时实例（15s） |
| adhoc_executor_instance | idx_heartbeat | executor HA 健康检查扫描超时实例（30s） |
| adhoc_executor_instance | idx_accepting_load | 调度选 executor（accepting=1 + load_score 排序） |
| adhoc_file_node | idx_user_parent | 查子节点 |

## 10. ER 关系说明

```
adhoc_query_job (1) ---- (*) adhoc_query_task
    │ job_id                          │ job_id（FK 逻辑）
    │                                 │
    │ executor_instance               │ executor_instance（冗余）
    │                                 │
    │                                 ├──> adhoc_query_governance (1:1, query_id)
    │                                 ├──> adhoc_query_table_ref (1:1, query_id)
    │                                 └──> adhoc_result_summary (1:1, query_id)
    │
    ├──> adhoc_file_node (可选, source_file_node_id)
    │
    ├──> adhoc_executor_instance (executor_instance -> instance_id)
    └──> adhoc_server_instance (processing_server_instance -> instance_id)

adhoc_user_quota 不建（per-user 流控用 Apollo 全局配置）
adhoc_engine_param_rule 保留（引擎参数白名单规则，server 前置校验步骤 5 用，详见 §3.3）
```

**关系说明**：
- Job 与 Task 是 1:N 关系，通过 job_id 关联。
- Task 与 result_summary 是 1:1 关系，通过 query_id 关联（有结果集的 Task 才有 summary）。
- Task 与 governance 是 1:1 关系，通过 query_id 关联。
- Task 与 table_ref 是 1:1 关系，通过 query_id 关联。
- Job 的 executor_instance 字段关联 adhoc_executor_instance.instance_id。
- Job 的 processing_server_instance 字段关联 adhoc_server_instance.instance_id。
- Job 的 source_file_node_id 字段关联 adhoc_file_node.node_id（可选）。

## 11. 建表脚本执行顺序

按依赖关系执行：

```sql
-- 1. 模块 1（任务调度与执行）
CREATE TABLE adhoc_query_job (...);
CREATE TABLE adhoc_query_task (...);

-- 2. 模块 2（SQL 解析与治理）
CREATE TABLE adhoc_query_governance (...);
CREATE TABLE adhoc_query_table_ref (...);
CREATE TABLE adhoc_engine_param_rule (...);

-- 3. 模块 4（结果存储）
CREATE TABLE adhoc_result_summary (...);

-- 4. 模块 5（高可用）
CREATE TABLE adhoc_server_instance (...);
CREATE TABLE adhoc_executor_instance (...);

-- 5. 模块 8（用户文件树）
CREATE TABLE adhoc_file_node (...);
```

模块 6（可观测性）、模块 9（执行日志）不新建表，字段已包含在模块 1 的 job/task 表中。无平台层鉴权，依赖引擎侧权限。

## 12. 验收标准

1. 共 9 张表，覆盖业务模块（保留 adhoc_engine_param_rule）
2. job 表含跨模块字段：source_file_node_id（模块 8）、executor_instance（模块 1/5）、persistent_log_path（模块 9，job.log 存储 key）
3. task 表含跨模块字段：prefix_sql（模块 1）、executor_instance（模块 1/5）、scan_rows/scan_bytes（模块 6）、persistent_log_path（模块 9，task.log 存储 key）
4. job 表不含 execution_log 字段（日志在 executor 内存 buffer + 存储后端，key 从 jobId 推导）
5. task 表含 persistent_log_path 字段（task.log 存储 key，executor 内存 buffer + 定期(2s) storageClient.uploadLog 覆盖快照 + 终态最后 flush；server 读路径：运行中 gRPC FetchLog 读内存，executor DOWN/终态 storageClient.download 读存储快照）。task 表不含 local_log_path/log_persistent_backend 字段
6. task 表含 7 个时间戳（enqueue/start/fetch_start/write_start/local_write_finish/oss_upload/finish）+ duration_ms
7. job 表含 6 个时间戳（submit/validate_finish/dispatch/split_finish/start/finish）+ duration_ms
8. task 表不含 grant_status 字段（无平台层鉴权，依赖引擎侧权限）
9. task 表不含 engine_attempt_chain/engine_attempt_index/is_auto_engine 字段（无 AUTO 模式）
10. task 表不含 schedule_retry_count/next_retry_time 字段（executor 内部重试不记 DB）
11. task 表 status 5 态（PENDING/RUNNING/SUCCESS/FAILED/CANCELED），不含 REJECTED/TIMEOUT
12. task 表 stage 3 阶段（EXECUTING/FETCHING/WRITING）
13. task 表 fail_stage 5 类（SPLIT/EXECUTING/FETCHING/WRITING/OSS_UPLOAD）
14. task 表 fail_reason_category 9 类（ENGINE_ERROR/EXECUTOR_CRASHED/TASK_LOST/SKIPPED_DUE_TO_PRIOR_FAILURE/SKIPPED_DUE_TO_SESSION_LOSS/WRITE_ERROR/FETCH_ERROR/SPLIT_ERROR/CANCELED）
15. task 表 sql_hash 计算为 SHA256(prefix_sql + sql_content)
16. task 表 sql_type 枚举含 SESSION_CONFIG（用于 SET/USE 识别）
17. job 表 status 7 态（PENDING/DISPATCHING/RUNNING/SUCCESS/PARTIAL_FAILED/FAILED/CANCELED），PENDING->DISPATCHING 在 claimJob CAS 抢占时
18. result_summary 表用 query_id 作主键，含 persistent_path(存储 key)/storage_type(两态 NONE/PERSISTENT)/result_status/oss_upload_status(PENDING/SUCCESS/FAILED) 字段
19. result_summary 表 persistent_path 存存储 key（storageClient.uploadResult 返回值），executor 一次性上传存储、server storageClient.download 直读
20. executor_instance 表含 cpu_usage/mem_usage/disk_usage/running_tasks 字段（心跳上报 + 对账）
21. executor_instance 表不含 current_running/current_queued 字段（与 running_tasks JSON 冗余）
22. server_instance/executor_instance 表用 instance_id 和 heartbeat_time 命名
23. governance/table_ref 表不含 parse_engine_candidates 字段（无 AUTO）
24. table_ref 表不含 column_lineage_json 字段（只做表级血缘，source_tables_json + sink_tables_json）
25. table_ref 表通过 query_id 与 Task 1:1 绑定，每个 Task 产生的血缘可独立查询
26. 所有含 user_id 的表带 user_name 冗余字段
27. adhoc_file_node 唯一索引含 is_deleted（允许一个活跃 + 一个已删除共存）
28. 不建 adhoc_query_event / adhoc_query_resource / 历史实例归档表
29. 不建 adhoc_query_task_engine_attempt / adhoc_query_result_schema / adhoc_query_result_file
30. 不建权限相关表（adhoc_permission_rule / adhoc_grant_log 等，依赖引擎侧权限）
31. 不建 adhoc_user_quota（per-user 流控用 Apollo 全局配置）
32. 保留 adhoc_engine_param_rule（引擎参数白名单规则，server 前置校验步骤 5 用，按 engine_type + param_key 唯一，支持 INT 范围校验 / STRING 枚举校验 / BOOLEAN 校验）
33. 所有表用 InnoDB + utf8mb4
34. 业务表主键 varchar(64) UUID，result_summary 用 query_id 作主键，实例表 bigint 自增
35. engine_type 枚举只保留 KYUUBI/STARROCKS
36. STARROCKS 引擎仅支持 DQL/CTAS 语句，DDL/DML/DCL 在 server 前置校验阶段拦截（错误码 ADHOC_ENGINE_SQL_TYPE_NOT_SUPPORTED）
