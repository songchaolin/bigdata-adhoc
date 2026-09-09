-- ============================================================================
-- 增量迁移：业务时间戳字段升级到毫秒精度 DATETIME(3)
-- 背景：进度接口（/api/adhoc/job/progress）各阶段耗时用相邻时间戳相减计算，
--       原 DATETIME 精度到秒，亚秒级阶段（排队/调度/快速 task 执行）被抹平为 0。
--       升级 DATETIME(3) 后毫秒级可见，Java 代码零改动（Date 自带毫秒，原本被 DB 截断）。
-- 目标库：adhoc（MySQL 8，需 5.6.4+ 支持 fractional seconds）。
-- 执行：mysql -h<host> -P<port> -uroot -p<password> adhoc < datetime3-migration.sql
-- 幂等：MODIFY COLUMN 可重复执行，无副作用。旧数据尾部补 .000，新写入保留毫秒。
-- ============================================================================

-- 1. Job 主表
ALTER TABLE adhoc_query_job
  MODIFY COLUMN cancel_requested_time DATETIME(3) DEFAULT NULL,
  MODIFY COLUMN submit_time          DATETIME(3) NOT NULL,
  MODIFY COLUMN validate_finish_time DATETIME(3) DEFAULT NULL,
  MODIFY COLUMN dispatch_time        DATETIME(3) DEFAULT NULL,
  MODIFY COLUMN split_finish_time    DATETIME(3) DEFAULT NULL,
  MODIFY COLUMN start_time           DATETIME(3) DEFAULT NULL,
  MODIFY COLUMN finish_time          DATETIME(3) DEFAULT NULL,
  MODIFY COLUMN create_time          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  MODIFY COLUMN update_time          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3);

-- 2. Task 主表
ALTER TABLE adhoc_query_task
  MODIFY COLUMN cancel_requested_time   DATETIME(3) DEFAULT NULL,
  MODIFY COLUMN enqueue_time            DATETIME(3) DEFAULT NULL,
  MODIFY COLUMN start_time              DATETIME(3) DEFAULT NULL,
  MODIFY COLUMN fetch_start_time        DATETIME(3) DEFAULT NULL,
  MODIFY COLUMN write_start_time        DATETIME(3) DEFAULT NULL,
  MODIFY COLUMN local_write_finish_time DATETIME(3) DEFAULT NULL,
  MODIFY COLUMN oss_upload_time         DATETIME(3) DEFAULT NULL,
  MODIFY COLUMN finish_time             DATETIME(3) DEFAULT NULL,
  MODIFY COLUMN create_time             DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  MODIFY COLUMN update_time             DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3);

-- 3. 治理记录
ALTER TABLE adhoc_query_governance
  MODIFY COLUMN create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  MODIFY COLUMN update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3);

-- 4. 表级血缘
ALTER TABLE adhoc_query_table_ref
  MODIFY COLUMN create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  MODIFY COLUMN update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3);

-- 5. 引擎参数白名单规则
ALTER TABLE adhoc_engine_param_rule
  MODIFY COLUMN create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  MODIFY COLUMN update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3);

-- 6. 结果摘要
ALTER TABLE adhoc_result_summary
  MODIFY COLUMN create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3);

-- 7. server 实例
ALTER TABLE adhoc_server_instance
  MODIFY COLUMN start_time     DATETIME(3) DEFAULT NULL,
  MODIFY COLUMN heartbeat_time DATETIME(3) DEFAULT NULL,
  MODIFY COLUMN create_time    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  MODIFY COLUMN update_time    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3);

-- 8. executor 实例
ALTER TABLE adhoc_executor_instance
  MODIFY COLUMN start_time     DATETIME(3) DEFAULT NULL,
  MODIFY COLUMN heartbeat_time DATETIME(3) DEFAULT NULL,
  MODIFY COLUMN last_down_time DATETIME(3) DEFAULT NULL,
  MODIFY COLUMN create_time    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  MODIFY COLUMN update_time    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3);

-- 9. 用户文件树节点
ALTER TABLE adhoc_file_node
  MODIFY COLUMN create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  MODIFY COLUMN update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3);
