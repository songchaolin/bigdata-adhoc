-- ======================================================================
-- 迁移：JVM 监控指标列（server + executor 双角色）
-- 适用：存量库（adhoc_server_instance / adhoc_executor_instance 已存在但缺 JVM 列）
-- 特性：MySQL 8 无 ADD COLUMN IF NOT EXISTS，用 information_schema 列存在性守卫 + PREPARE/EXECUTE，
--       每列一段，可重复执行（已存在的列跳过）。全新库走 sql/create.sql 的 CREATE TABLE IF NOT EXISTS。
-- 执行：mysql -u<user> -p adhoc < sql/migrate-jvm-metrics.sql
-- ======================================================================

-- ---------- adhoc_server_instance：补 16 列 JVM 指标（此前 server 表无任何 JVM 列） ----------

SET @col := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='adhoc_server_instance' AND COLUMN_NAME='cpu_usage_pct');
SET @sql := IF(@col=0, "ALTER TABLE adhoc_server_instance ADD COLUMN cpu_usage_pct DOUBLE DEFAULT NULL COMMENT 'JVM 进程 CPU%'", "SELECT 1"); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='adhoc_server_instance' AND COLUMN_NAME='system_cpu_usage_pct');
SET @sql := IF(@col=0, "ALTER TABLE adhoc_server_instance ADD COLUMN system_cpu_usage_pct DOUBLE DEFAULT NULL COMMENT '系统整体 CPU%'", "SELECT 1"); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='adhoc_server_instance' AND COLUMN_NAME='memory_usage_pct');
SET @sql := IF(@col=0, "ALTER TABLE adhoc_server_instance ADD COLUMN memory_usage_pct DOUBLE DEFAULT NULL COMMENT 'JVM 堆使用率%'", "SELECT 1"); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='adhoc_server_instance' AND COLUMN_NAME='memory_used_mb');
SET @sql := IF(@col=0, "ALTER TABLE adhoc_server_instance ADD COLUMN memory_used_mb BIGINT DEFAULT NULL COMMENT 'JVM 堆已用 MB'", "SELECT 1"); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='adhoc_server_instance' AND COLUMN_NAME='memory_max_mb');
SET @sql := IF(@col=0, "ALTER TABLE adhoc_server_instance ADD COLUMN memory_max_mb BIGINT DEFAULT NULL COMMENT 'JVM 堆最大 MB'", "SELECT 1"); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='adhoc_server_instance' AND COLUMN_NAME='heap_committed_mb');
SET @sql := IF(@col=0, "ALTER TABLE adhoc_server_instance ADD COLUMN heap_committed_mb BIGINT DEFAULT NULL COMMENT 'JVM 堆已提交 MB'", "SELECT 1"); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='adhoc_server_instance' AND COLUMN_NAME='non_heap_used_mb');
SET @sql := IF(@col=0, "ALTER TABLE adhoc_server_instance ADD COLUMN non_heap_used_mb BIGINT DEFAULT NULL COMMENT 'JVM 非堆已用 MB'", "SELECT 1"); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='adhoc_server_instance' AND COLUMN_NAME='non_heap_committed_mb');
SET @sql := IF(@col=0, "ALTER TABLE adhoc_server_instance ADD COLUMN non_heap_committed_mb BIGINT DEFAULT NULL COMMENT 'JVM 非堆已提交 MB'", "SELECT 1"); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='adhoc_server_instance' AND COLUMN_NAME='thread_count');
SET @sql := IF(@col=0, "ALTER TABLE adhoc_server_instance ADD COLUMN thread_count INT DEFAULT NULL COMMENT '线程数'", "SELECT 1"); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='adhoc_server_instance' AND COLUMN_NAME='daemon_thread_count');
SET @sql := IF(@col=0, "ALTER TABLE adhoc_server_instance ADD COLUMN daemon_thread_count INT DEFAULT NULL COMMENT 'daemon 线程数'", "SELECT 1"); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='adhoc_server_instance' AND COLUMN_NAME='gc_count');
SET @sql := IF(@col=0, "ALTER TABLE adhoc_server_instance ADD COLUMN gc_count BIGINT DEFAULT NULL COMMENT 'GC 累计次数'", "SELECT 1"); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='adhoc_server_instance' AND COLUMN_NAME='gc_time_ms');
SET @sql := IF(@col=0, "ALTER TABLE adhoc_server_instance ADD COLUMN gc_time_ms BIGINT DEFAULT NULL COMMENT 'GC 累计耗时 ms'", "SELECT 1"); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='adhoc_server_instance' AND COLUMN_NAME='gc_time_ratio_pct');
SET @sql := IF(@col=0, "ALTER TABLE adhoc_server_instance ADD COLUMN gc_time_ratio_pct DOUBLE DEFAULT NULL COMMENT 'GC 时间占比%(gcTime/uptime)'", "SELECT 1"); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='adhoc_server_instance' AND COLUMN_NAME='loaded_class_count');
SET @sql := IF(@col=0, "ALTER TABLE adhoc_server_instance ADD COLUMN loaded_class_count INT DEFAULT NULL COMMENT '已加载类数'", "SELECT 1"); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='adhoc_server_instance' AND COLUMN_NAME='uptime_ms');
SET @sql := IF(@col=0, "ALTER TABLE adhoc_server_instance ADD COLUMN uptime_ms BIGINT DEFAULT NULL COMMENT 'JVM 运行时长 ms'", "SELECT 1"); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='adhoc_server_instance' AND COLUMN_NAME='load_score');
SET @sql := IF(@col=0, "ALTER TABLE adhoc_server_instance ADD COLUMN load_score DOUBLE DEFAULT NULL COMMENT '综合负载评分(cpu+mem)'", "SELECT 1"); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------- adhoc_executor_instance：补 9 列（已有 cpu/mem/thread/load_score，缺以下） ----------

SET @col := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='adhoc_executor_instance' AND COLUMN_NAME='heap_committed_mb');
SET @sql := IF(@col=0, "ALTER TABLE adhoc_executor_instance ADD COLUMN heap_committed_mb BIGINT DEFAULT NULL COMMENT 'JVM 堆已提交 MB'", "SELECT 1"); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='adhoc_executor_instance' AND COLUMN_NAME='non_heap_used_mb');
SET @sql := IF(@col=0, "ALTER TABLE adhoc_executor_instance ADD COLUMN non_heap_used_mb BIGINT DEFAULT NULL COMMENT 'JVM 非堆已用 MB'", "SELECT 1"); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='adhoc_executor_instance' AND COLUMN_NAME='non_heap_committed_mb');
SET @sql := IF(@col=0, "ALTER TABLE adhoc_executor_instance ADD COLUMN non_heap_committed_mb BIGINT DEFAULT NULL COMMENT 'JVM 非堆已提交 MB'", "SELECT 1"); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='adhoc_executor_instance' AND COLUMN_NAME='daemon_thread_count');
SET @sql := IF(@col=0, "ALTER TABLE adhoc_executor_instance ADD COLUMN daemon_thread_count INT DEFAULT NULL COMMENT 'daemon 线程数'", "SELECT 1"); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='adhoc_executor_instance' AND COLUMN_NAME='gc_count');
SET @sql := IF(@col=0, "ALTER TABLE adhoc_executor_instance ADD COLUMN gc_count BIGINT DEFAULT NULL COMMENT 'GC 累计次数'", "SELECT 1"); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='adhoc_executor_instance' AND COLUMN_NAME='gc_time_ms');
SET @sql := IF(@col=0, "ALTER TABLE adhoc_executor_instance ADD COLUMN gc_time_ms BIGINT DEFAULT NULL COMMENT 'GC 累计耗时 ms'", "SELECT 1"); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='adhoc_executor_instance' AND COLUMN_NAME='gc_time_ratio_pct');
SET @sql := IF(@col=0, "ALTER TABLE adhoc_executor_instance ADD COLUMN gc_time_ratio_pct DOUBLE DEFAULT NULL COMMENT 'GC 时间占比%(gcTime/uptime)'", "SELECT 1"); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='adhoc_executor_instance' AND COLUMN_NAME='loaded_class_count');
SET @sql := IF(@col=0, "ALTER TABLE adhoc_executor_instance ADD COLUMN loaded_class_count INT DEFAULT NULL COMMENT '已加载类数'", "SELECT 1"); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='adhoc_executor_instance' AND COLUMN_NAME='uptime_ms');
SET @sql := IF(@col=0, "ALTER TABLE adhoc_executor_instance ADD COLUMN uptime_ms BIGINT DEFAULT NULL COMMENT 'JVM 运行时长 ms'", "SELECT 1"); PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;