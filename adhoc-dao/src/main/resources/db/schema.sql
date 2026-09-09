-- ============================================================================
-- bigdata-adhoc 全量表结构 DDL（MySQL 8）。字符集 utf8mb4 / InnoDB。
-- 执行：mysql -h<host> -P<port> -u<user> -p <db> < schema.sql（库名按部署环境自定义，默认建议 adhoc）
-- ============================================================================

-- 1. Job 主表
CREATE TABLE IF NOT EXISTS adhoc_query_job (
  job_id                      VARCHAR(64)   NOT NULL COMMENT 'Job ID（UUID）',
  user_id                     VARCHAR(64)   NOT NULL COMMENT '提交人 ID',
  user_name                   VARCHAR(64)   DEFAULT NULL COMMENT '提交人中文名（冗余）',
  sql_content                 TEXT          NOT NULL COMMENT '原始 SQL（拆分前，整段提交给 executor）',
  engine_type                 VARCHAR(16)   NOT NULL COMMENT 'KYUUBI/STARROCKS（无 AUTO，用户必填）',
  engine_instance             VARCHAR(64)   DEFAULT NULL COMMENT '指定 instance，未指定用该 engine_type 默认 instance（Apollo 配置）',
  engine_params               TEXT          DEFAULT NULL COMMENT 'JSON，session 初始参数（SET 可覆盖）',
  executor_instance           VARCHAR(128)  DEFAULT NULL COMMENT '执行 Job 的 executor instance_id（dispatchJob 时确定）',
  status                      VARCHAR(16)   NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/RUNNING/SUCCESS/PARTIAL_FAILED/FAILED/CANCELED',
  cancel_requested            TINYINT       NOT NULL DEFAULT 0 COMMENT '取消请求标志位',
  cancel_requested_time       DATETIME      DEFAULT NULL COMMENT '取消请求时刻',
  processing_server_instance  VARCHAR(128)  DEFAULT NULL COMMENT '承接 Job 的 server instance_id（server 宕机时置 NULL）',
  client_ip                   VARCHAR(64)   DEFAULT NULL COMMENT '客户端 IP',
  client_user_agent           VARCHAR(256)  DEFAULT NULL COMMENT 'UA',
  client_request_id           VARCHAR(64)   DEFAULT NULL COMMENT '客户端请求 ID（审计）',
  source_file_node_id         VARCHAR(64)   DEFAULT NULL COMMENT '来源文件节点ID（模块 8，从文件提交时有值）',
  submit_time                 DATETIME      NOT NULL COMMENT 'Job 提交时间',
  validate_finish_time        DATETIME      DEFAULT NULL COMMENT '前置校验完成时间',
  dispatch_time               DATETIME      DEFAULT NULL COMMENT 'dispatchJob 下发时间（PENDING->RUNNING 触发点）',
  split_finish_time           DATETIME      DEFAULT NULL COMMENT 'executor 拆分完成时间',
  start_time                  DATETIME      DEFAULT NULL COMMENT '第一个 Task 开始执行时间',
  finish_time                 DATETIME      DEFAULT NULL COMMENT 'Job 终态时间',
  duration_ms                 BIGINT        DEFAULT NULL COMMENT 'Job 总耗时',
  persistent_log_path         VARCHAR(256)  DEFAULT NULL COMMENT 'job log OSS key',
  is_deleted                  TINYINT       NOT NULL DEFAULT 0 COMMENT '软删除',
  create_time                 DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time                 DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (job_id),
  UNIQUE KEY uk_client_request_id (client_request_id),
  KEY idx_user_status (user_id, status),
  KEY idx_status_submit (status, submit_time),
  KEY idx_status_update (status, update_time),
  KEY idx_executor_status (executor_instance, status),
  KEY idx_processing_server_status (processing_server_instance, status),
  KEY idx_client_ip (client_ip),
  KEY idx_user_submit (user_id, submit_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Job 主表';

-- 2. Task 主表
CREATE TABLE IF NOT EXISTS adhoc_query_task (
  query_id                    VARCHAR(64)   NOT NULL COMMENT 'Task ID（UUID）',
  job_id                      VARCHAR(64)   NOT NULL COMMENT '所属 Job',
  segment_index               INT           NOT NULL COMMENT 'Job 内序号',
  user_id                     VARCHAR(64)   NOT NULL COMMENT '提交人 ID（冗余）',
  user_name                   VARCHAR(64)   DEFAULT NULL COMMENT '提交人中文名（冗余）',
  prefix_sql                  TEXT          DEFAULT NULL COMMENT 'SET/USE 前缀语句',
  sql_content                 TEXT          NOT NULL COMMENT '单段可执行 SQL',
  sql_hash                    VARCHAR(64)   DEFAULT NULL COMMENT 'SHA256(prefix_sql + sql_content)',
  sql_type                    VARCHAR(16)   DEFAULT NULL COMMENT 'DQL/DDL_CREATE/DDL_ALTER/DDL_DROP/DML_INSERT/DML_MODIFY/CTAS/AUX/DCL/SESSION_CONFIG/UNKNOWN',
  has_result_set              TINYINT       NOT NULL DEFAULT 0 COMMENT '是否有结果集',
  affected_rows               BIGINT        DEFAULT NULL COMMENT 'DML 影响行数',
  engine_params               TEXT          DEFAULT NULL COMMENT 'JSON，session 参数（冗余）',
  status                      VARCHAR(16)   NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/RUNNING/SUCCESS/FAILED/CANCELED',
  stage                       VARCHAR(16)   DEFAULT NULL COMMENT 'EXECUTING/FETCHING/WRITING',
  cancel_requested            TINYINT       NOT NULL DEFAULT 0 COMMENT '取消请求标志位',
  cancel_requested_time       DATETIME      DEFAULT NULL COMMENT '取消请求时刻',
  fail_stage                  VARCHAR(16)   DEFAULT NULL COMMENT 'DISPATCH/SPLIT/EXECUTING/FETCHING/WRITING/OSS_UPLOAD',
  fail_reason_category        VARCHAR(64)   DEFAULT NULL COMMENT 'ENGINE_ERROR/EXECUTOR_CRASHED/TASK_LOST/SKIPPED_DUE_TO_PRIOR_FAILURE/SKIPPED_DUE_TO_SESSION_LOSS/WRITE_ERROR/FETCH_ERROR/SPLIT_ERROR/CANCELED',
  error_code                  VARCHAR(64)   DEFAULT NULL COMMENT '失败错误码（ADHOC_* 前缀）',
  error_message               TEXT          DEFAULT NULL COMMENT '失败详情',
  engine_type                 VARCHAR(16)   DEFAULT NULL COMMENT '实际执行引擎',
  engine_instance             VARCHAR(64)   DEFAULT NULL COMMENT '实际执行引擎实例',
  executor_instance           VARCHAR(128)  DEFAULT NULL COMMENT '执行 executor instance_id',
  processing_server_instance  VARCHAR(128)  DEFAULT NULL COMMENT '承接 Job 的 server instance_id（冗余）',
  reused_from_task_id         VARCHAR(64)   DEFAULT NULL COMMENT '命中复用时指向原 Task',
  scan_rows                   BIGINT        DEFAULT NULL COMMENT '引擎扫描行数',
  scan_bytes                  BIGINT        DEFAULT NULL COMMENT '引擎扫描字节数',
  persistent_log_path         VARCHAR(256)  DEFAULT NULL COMMENT 'log OSS key',
  enqueue_time                DATETIME      DEFAULT NULL COMMENT 'Task 入队时间',
  start_time                  DATETIME      DEFAULT NULL COMMENT 'Task 开始执行时间',
  fetch_start_time            DATETIME      DEFAULT NULL COMMENT '开始拉结果时间',
  write_start_time            DATETIME      DEFAULT NULL COMMENT '开始序列化+上传 OSS 时间',
  local_write_finish_time     DATETIME      DEFAULT NULL COMMENT '结果序列化完成时间（内存）',
  oss_upload_time             DATETIME      DEFAULT NULL COMMENT 'OSS 上传完成时间',
  finish_time                 DATETIME      DEFAULT NULL COMMENT 'Task 终态时间',
  duration_ms                 BIGINT        DEFAULT NULL COMMENT 'Task 执行耗时',
  is_deleted                  TINYINT       NOT NULL DEFAULT 0 COMMENT '软删除',
  create_time                 DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time                 DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (query_id),
  KEY idx_status_enqueue (status, enqueue_time),
  KEY idx_status_update (status, update_time),
  KEY idx_user_sql_hash_status_finish (user_id, sql_hash, status, finish_time),
  KEY idx_processing_server_status (processing_server_instance, status),
  KEY idx_executor_status (executor_instance, status),
  KEY idx_job_segment (job_id, segment_index),
  KEY idx_user_status (user_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Task 主表';

-- 3. 治理记录
CREATE TABLE IF NOT EXISTS adhoc_query_governance (
  query_id                    VARCHAR(64)   NOT NULL COMMENT 'Task ID',
  sql_type                    VARCHAR(16)   DEFAULT NULL COMMENT 'SqlType 枚举',
  risk_items_json             TEXT          DEFAULT NULL COMMENT '风险项列表 JSON',
  executed_sql_content        TEXT          DEFAULT NULL COMMENT '改写后的实际执行 SQL',
  governance_result           VARCHAR(16)   DEFAULT NULL COMMENT 'PASSED / DENIED',
  deny_reason                 VARCHAR(256)  DEFAULT NULL COMMENT '拒绝原因',
  create_time                 DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time                 DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (query_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='治理记录（每 Task 一条，executor 端写入）';

-- 4. 表级血缘
CREATE TABLE IF NOT EXISTS adhoc_query_table_ref (
  id                          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '自增',
  query_id                    VARCHAR(64)   NOT NULL COMMENT 'Task ID',
  source_tables_json          LONGTEXT      DEFAULT NULL COMMENT '源表列表 JSON',
  sink_tables_json            LONGTEXT      DEFAULT NULL COMMENT '目标表列表 JSON',
  create_time                 DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time                 DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_query_id (query_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SQL 表引用与表级血缘';

-- 5. 引擎参数白名单规则
CREATE TABLE IF NOT EXISTS adhoc_engine_param_rule (
  id                BIGINT        NOT NULL AUTO_INCREMENT COMMENT '自增',
  engine_type       VARCHAR(16)   NOT NULL COMMENT 'KYUUBI/STARROCKS',
  param_key         VARCHAR(128)  NOT NULL COMMENT '参数键名',
  param_value_type  VARCHAR(16)   NOT NULL DEFAULT 'STRING' COMMENT 'INT/STRING/BOOLEAN',
  min_value         VARCHAR(64)   DEFAULT NULL COMMENT '数值型最小值（INT 时生效）',
  max_value         VARCHAR(64)   DEFAULT NULL COMMENT '数值型最大值（INT 时生效）',
  allowed_values    TEXT          DEFAULT NULL COMMENT '枚举允许值 CSV',
  default_value     VARCHAR(256)  DEFAULT NULL COMMENT '默认值（运维参考）',
  description       VARCHAR(512)  DEFAULT NULL COMMENT '参数说明',
  enabled           TINYINT       NOT NULL DEFAULT 1 COMMENT '是否启用',
  create_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_engine_param (engine_type, param_key),
  KEY idx_engine_enabled (engine_type, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='引擎参数白名单规则';

-- 6. 结果摘要
CREATE TABLE IF NOT EXISTS adhoc_result_summary (
  query_id                    VARCHAR(64)   NOT NULL COMMENT 'Task ID',
  result_rows                 BIGINT        NOT NULL DEFAULT 0 COMMENT '结果总行数（上限 100w）',
  result_bytes                BIGINT        DEFAULT NULL COMMENT '结果字节数',
  persistent_path             VARCHAR(256)  DEFAULT NULL COMMENT 'OSS key',
  storage_type                VARCHAR(16)   NOT NULL DEFAULT 'NONE' COMMENT 'NONE/PERSISTENT',
  result_status               VARCHAR(16)   NOT NULL DEFAULT 'WRITING' COMMENT 'WRITING/COMPLETE/INCOMPLETE',
  oss_upload_status           VARCHAR(16)   NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/SUCCESS/FAILED',
  create_time                 DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (query_id),
  KEY idx_storage_type (storage_type),
  KEY idx_oss_upload_status (oss_upload_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='结果摘要（每 Task 一条，executor 直写）';

-- 7. server 实例
CREATE TABLE IF NOT EXISTS adhoc_server_instance (
  id                          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '自增',
  instance_id                 VARCHAR(128)  NOT NULL COMMENT '实例标识 ip:port',
  host                        VARCHAR(64)   NOT NULL COMMENT 'IP',
  http_port                   INT           NOT NULL COMMENT 'HTTP 端口',
  grpc_port                   INT           NOT NULL DEFAULT 9090 COMMENT 'gRPC 端口',
  status                      VARCHAR(16)   NOT NULL DEFAULT 'UNKNOWN' COMMENT 'UP/DOWN/UNKNOWN',
  accepting                   TINYINT       NOT NULL DEFAULT 1 COMMENT '是否承接新任务',
  start_time                  DATETIME      DEFAULT NULL COMMENT '启动时间',
  version                     VARCHAR(32)   DEFAULT NULL COMMENT '版本号',
  heartbeat_time              DATETIME      DEFAULT NULL COMMENT '最后心跳时间',
  active_jobs                 INT           NOT NULL DEFAULT 0 COMMENT '当前处理 Job 数',
  create_time                 DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time                 DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_instance_id (instance_id),
  KEY idx_status (status),
  KEY idx_heartbeat (heartbeat_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='server 实例（server HA 用）';

-- 8. executor 实例
CREATE TABLE IF NOT EXISTS adhoc_executor_instance (
  id                          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '自增',
  instance_id                 VARCHAR(128)  NOT NULL COMMENT '实例标识 ip:port',
  host                        VARCHAR(64)   NOT NULL COMMENT 'IP',
  grpc_port                   INT           NOT NULL COMMENT 'gRPC 端口',
  status                      VARCHAR(16)   NOT NULL DEFAULT 'UNKNOWN' COMMENT 'UP/DOWN/UNKNOWN',
  accepting                   TINYINT       NOT NULL DEFAULT 1 COMMENT '是否承接新任务',
  start_time                  DATETIME      DEFAULT NULL COMMENT '启动时间',
  version                     VARCHAR(32)   DEFAULT NULL COMMENT '版本号',
  heartbeat_time              DATETIME      DEFAULT NULL COMMENT '最后心跳时间',
  last_down_time              DATETIME      DEFAULT NULL COMMENT '最近一次 DOWN 时间',
  max_concurrent_tasks        INT           NOT NULL DEFAULT 10 COMMENT '最大并发 Task 数',
  engine_types                VARCHAR(128)  DEFAULT NULL COMMENT '支持的引擎类型 CSV',
  cpu_usage                   DOUBLE        DEFAULT NULL COMMENT 'CPU 使用率',
  mem_usage                   DOUBLE        DEFAULT NULL COMMENT '内存使用率',
  disk_usage                  DOUBLE        DEFAULT NULL COMMENT '磁盘使用率',
  running_tasks               TEXT          DEFAULT NULL COMMENT '在执行 Task 列表 JSON',
  cpu_usage_pct               DOUBLE        DEFAULT NULL COMMENT 'JVM 进程 CPU（兼容）',
  system_cpu_usage_pct        DOUBLE        DEFAULT NULL COMMENT '系统整体 CPU（兼容）',
  memory_usage_pct            DOUBLE        DEFAULT NULL COMMENT 'JVM 堆使用率（兼容）',
  memory_used_mb              BIGINT        DEFAULT NULL COMMENT 'JVM 堆使用 MB（兼容）',
  memory_max_mb               BIGINT        DEFAULT NULL COMMENT 'JVM 堆最大 MB（兼容）',
  thread_count                INT           DEFAULT NULL COMMENT '线程数',
  last_gc_pause_ms            BIGINT        DEFAULT NULL COMMENT '最近 GC 暂停',
  load_score                  DOUBLE        DEFAULT NULL COMMENT '综合负载评分',
  create_time                 DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time                 DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_instance_id (instance_id),
  KEY idx_status (status),
  KEY idx_heartbeat (heartbeat_time),
  KEY idx_accepting_load (accepting, load_score)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='executor 实例';

-- 9. 用户文件树节点
CREATE TABLE IF NOT EXISTS adhoc_file_node (
  node_id                     VARCHAR(64)   NOT NULL COMMENT '节点 ID（UUID）',
  user_id                     VARCHAR(64)   NOT NULL COMMENT '所属用户',
  user_name                   VARCHAR(64)   DEFAULT NULL COMMENT '用户中文名（冗余）',
  parent_node_id              VARCHAR(64)   DEFAULT NULL COMMENT '父节点 ID，根为 NULL',
  node_type                   VARCHAR(16)   NOT NULL COMMENT 'DIRECTORY/FILE',
  node_name                   VARCHAR(128)  NOT NULL COMMENT '节点名（同父同 user 唯一）',
  sql_content                 TEXT          DEFAULT NULL COMMENT 'FILE 才有，SQL 原文',
  description                 VARCHAR(512)  DEFAULT NULL COMMENT 'FILE 才有，描述',
  is_deleted                  TINYINT       NOT NULL DEFAULT 0 COMMENT '软删除',
  create_time                 DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time                 DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (node_id),
  UNIQUE KEY uk_user_parent_name_del (user_id, parent_node_id, node_name, is_deleted),
  KEY idx_user_parent (user_id, parent_node_id),
  KEY idx_user_id (user_id),
  KEY idx_parent_deleted (parent_node_id, is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户文件树节点';

-- 初始数据：引擎参数白名单
INSERT INTO adhoc_engine_param_rule (engine_type, param_key, param_value_type, min_value, max_value, allowed_values, description, enabled) VALUES
('KYUUBI', 'spark.sql.shuffle.partitions', 'INT', '1', '1000', NULL, 'shuffle 分区数', 1),
('KYUUBI', 'spark.executor.memory', 'STRING', NULL, NULL, '1g,2g,4g,8g,16g', 'executor 内存', 1),
('KYUUBI', 'spark.sql.adaptive.enabled', 'BOOLEAN', NULL, NULL, 'true,false', 'AQE 开关', 1),
('STARROCKS', 'parallel_fragment_exec_instance_num', 'INT', '1', '32', NULL, '并行 fragment 实例数', 1),
('STARROCKS', 'enable_spill', 'BOOLEAN', NULL, NULL, 'true,false', '落盘开关', 1);
