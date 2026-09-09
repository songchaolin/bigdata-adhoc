-- ============================================================================
-- adhoc_query_job 加 persistent_log_path 列（job 级日志 OSS key）
-- 背景：job 日志终态 upload OSS（jobId/job.log），需存 key 供 server log 接口直读。
-- 生产一次性执行；dev 亦用此脚本（或重建表）。
-- ============================================================================

ALTER TABLE adhoc_query_job
  ADD COLUMN persistent_log_path VARCHAR(256) DEFAULT NULL COMMENT 'job log OSS key';