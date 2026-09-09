-- ============================================================================
-- bigdata-adhoc 运维故障排查 SQL 集合
-- 目标库：adhoc（MySQL 8）
-- 用途：上线后按故障场景快速定位排查问题，均为只读查询，可直接在生产库执行
-- 维护：新增查询请归入对应场景小节，并注明排查目的与字段含义
-- 更新：2026-08-28
-- ----------------------------------------------------------------------------
-- 使用说明：
--   1. 查询中的时间窗（INTERVAL N HOUR / 最近 N 小时）按需调整
--   2. <变量> 为占位符，替换为实际值（如 'Job_xxx'、'user001'、'10.0.0.1:9090'）
--   3. 所有查询均带 LIMIT，避免大表全扫；如需更多结果自行调大
--   4. 状态枚举：
--        Job:   PENDING/DISPATCHING/RUNNING/SUCCESS/PARTIAL_FAILED/FAILED/CANCELED
--        Task:  PENDING/RUNNING/SUCCESS/FAILED/CANCELED
--      失败阶段 fail_stage:        DISPATCH/SPLIT/EXECUTING/FETCHING/WRITING/OSS_UPLOAD
--      失败分类 fail_reason_category: ENGINE_ERROR/EXECUTOR_CRASHED/TASK_LOST/
--        SKIPPED_DUE_TO_PRIOR_FAILURE/SKIPPED_DUE_TO_SESSION_LOSS/WRITE_ERROR/
--        FETCH_ERROR/SPLIT_ERROR/CANCELED
-- ============================================================================


-- ############################################################################
-- # 场景 0：整体健康度速览（值班巡检入口，先跑这组）
-- ############################################################################

-- 0.1 各状态 Job 数量分布（近 24h）—— 一眼看是否堆积/异常终态过多
SELECT status,
       COUNT(*) AS cnt
FROM adhoc_query_job
WHERE submit_time >= DATE_SUB(NOW(), INTERVAL 24 HOUR)
  AND is_deleted = 0
GROUP BY status
ORDER BY FIELD(status,'PENDING','DISPATCHING','RUNNING','SUCCESS','PARTIAL_FAILED','FAILED','CANCELED');

-- 0.2 各状态 Task 数量分布（近 24h）
SELECT status,
       COUNT(*) AS cnt
FROM adhoc_query_task
WHERE enqueue_time >= DATE_SUB(NOW(), INTERVAL 24 HOUR)
  AND is_deleted = 0
GROUP BY status
ORDER BY FIELD(status,'PENDING','RUNNING','SUCCESS','FAILED','CANCELED');

-- 0.3 server / executor 实例健康度（心跳超时即视为离线风险，阈值按部署调整）
SELECT 'SERVER'   AS role, instance_id, status, accepting,
       heartbeat_time,
       TIMESTAMPDIFF(SECOND, heartbeat_time, NOW()) AS hb_lag_sec,
       active_jobs, load_score
FROM adhoc_server_instance
UNION ALL
SELECT 'EXECUTOR' AS role, instance_id, status, accepting,
       heartbeat_time,
       TIMESTAMPDIFF(SECOND, heartbeat_time, NOW()) AS hb_lag_sec,
       0 AS active_jobs, load_score
FROM adhoc_executor_instance
ORDER BY role, hb_lag_sec DESC;

-- 0.4 近 1h 失败 Job 汇总（按失败分类）—— 快速判断是引擎问题还是调度问题
SELECT j.status,
       t.fail_reason_category,
       t.fail_stage,
       COUNT(DISTINCT j.job_id) AS failed_jobs,
       COUNT(*)                 AS failed_tasks
FROM adhoc_query_task t
JOIN adhoc_query_job j ON j.job_id = t.job_id
WHERE t.status = 'FAILED'
  AND t.finish_time >= DATE_SUB(NOW(), INTERVAL 1 HOUR)
  AND j.is_deleted = 0
GROUP BY j.status, t.fail_reason_category, t.fail_stage
ORDER BY failed_tasks DESC;


-- ############################################################################
-- # 场景 1：Job 卡在 PENDING（提交了不动）
-- ############################################################################

-- 1.1 PENDING Job 明细（按提交时间，最久的在前）—— 看是否限流堆积或 dispatcher 不工作
SELECT job_id, user_id, user_name, engine_type, engine_instance,
       client_ip, submit_time,
       TIMESTAMPDIFF(SECOND, submit_time, NOW()) AS pending_sec,
       processing_server_instance
FROM adhoc_query_job
WHERE status = 'PENDING'
  AND is_deleted = 0
ORDER BY submit_time ASC
LIMIT 50;

-- 1.2 PENDING 积压 TOP 用户 —— 是否单用户大量提交卡住（触发 per-user 限流）
SELECT user_id, MAX(user_name) AS user_name, COUNT(*) AS pending_cnt
FROM adhoc_query_job
WHERE status = 'PENDING'
  AND is_deleted = 0
  AND submit_time >= DATE_SUB(NOW(), INTERVAL 1 HOUR)
GROUP BY user_id
ORDER BY pending_cnt DESC
LIMIT 20;

-- 1.3 PENDING 积压按引擎/实例分布 —— 是否某实例配置缺失导致无人承接
SELECT engine_type, engine_instance, COUNT(*) AS cnt
FROM adhoc_query_job
WHERE status = 'PENDING'
  AND is_deleted = 0
GROUP BY engine_type, engine_instance
ORDER BY cnt DESC;

-- 1.4 可承接该引擎的 executor 是否存在（PENDING 堆积时核对：是否无可用 executor）
SELECT instance_id, host, grpc_port, status, accepting, engine_types,
       max_concurrent_tasks, heartbeat_time,
       TIMESTAMPDIFF(SECOND, heartbeat_time, NOW()) AS hb_lag_sec
FROM adhoc_executor_instance
WHERE status = 'UP' AND accepting = 1
ORDER BY hb_lag_sec ASC;


-- ############################################################################
-- # 场景 2：Job 卡在 RUNNING（长时间不结束）
-- ############################################################################

-- 2.1 RUNNING Job 明细（按耗时，最久在前）—— 看是否需取消或引擎 hang
SELECT j.job_id, j.user_id, j.user_name, j.engine_type, j.engine_instance,
       j.executor_instance, j.processing_server_instance,
       j.dispatch_time, j.start_time,
       TIMESTAMPDIFF(SECOND, IFNULL(j.start_time, j.dispatch_time), NOW()) AS running_sec,
       j.cancel_requested
FROM adhoc_query_job j
WHERE j.status = 'RUNNING'
  AND j.is_deleted = 0
ORDER BY running_sec DESC
LIMIT 50;

-- 2.2 RUNNING Job 下各 Task 状态 —— 定位卡在哪个 task/哪个阶段
SELECT t.job_id, t.query_id, t.segment_index, t.status, t.stage,
       t.executor_instance, t.engine_instance,
       t.enqueue_time, t.start_time, t.fetch_start_time, t.write_start_time,
       TIMESTAMPDIFF(SECOND, IFNULL(t.start_time, t.enqueue_time), NOW()) AS task_sec
FROM adhoc_query_task t
WHERE t.job_id IN (
    SELECT job_id FROM adhoc_query_job
    WHERE status = 'RUNNING' AND is_deleted = 0
)
ORDER BY t.job_id, t.segment_index;

-- 2.3 executor 在跑任务数与其上限对比 —— 是否某 executor 过载
SELECT e.instance_id, e.status, e.accepting, e.max_concurrent_tasks,
       e.engine_types, e.heartbeat_time,
       TIMESTAMPDIFF(SECOND, e.heartbeat_time, NOW()) AS hb_lag_sec
FROM adhoc_executor_instance e
WHERE e.status = 'UP'
ORDER BY e.max_concurrent_tasks DESC;

-- 2.4 RUNNING Job 关联的 server 是否还活着 —— server 宕机可能导致 RUNNING 无进度
SELECT j.job_id, j.processing_server_instance,
       s.status AS server_status, s.accepting, s.heartbeat_time,
       TIMESTAMPDIFF(SECOND, s.heartbeat_time, NOW()) AS server_hb_lag_sec
FROM adhoc_query_job j
LEFT JOIN adhoc_server_instance s ON s.instance_id = j.processing_server_instance
WHERE j.status = 'RUNNING' AND j.is_deleted = 0
ORDER BY server_hb_lag_sec DESC;


-- ############################################################################
-- # 场景 3：Job/Task 失败排查（FAILED / PARTIAL_FAILED）
-- ############################################################################

-- 3.1 失败 Task 明细（近 1h，按时间倒序）—— 直接看错误码与错误信息
SELECT t.query_id, t.job_id, t.user_id, t.user_name, t.segment_index,
       t.engine_type, t.engine_instance, t.executor_instance,
       t.status, t.fail_stage, t.fail_reason_category, t.error_code,
       LEFT(t.error_message, 500) AS error_message,
       t.start_time, t.finish_time, t.duration_ms
FROM adhoc_query_task t
WHERE t.status = 'FAILED'
  AND t.is_deleted = 0
  AND t.finish_time >= DATE_SUB(NOW(), INTERVAL 1 HOUR)
ORDER BY t.finish_time DESC
LIMIT 50;

-- 3.2 按 error_code 聚合（近 24h）—— 定位高频错误类型
SELECT error_code, fail_reason_category, fail_stage, COUNT(*) AS cnt
FROM adhoc_query_task
WHERE status = 'FAILED'
  AND finish_time >= DATE_SUB(NOW(), INTERVAL 24 HOUR)
GROUP BY error_code, fail_reason_category, fail_stage
ORDER BY cnt DESC;

-- 3.3 按 fail_stage 聚合 —— 判断失败发生在哪一阶段（DISPATCH/SPLIT/EXECUTING/FETCHING/WRITING/OSS_UPLOAD）
SELECT fail_stage, fail_reason_category, COUNT(*) AS cnt
FROM adhoc_query_task
WHERE status = 'FAILED'
  AND finish_time >= DATE_SUB(NOW(), INTERVAL 24 HOUR)
GROUP BY fail_stage, fail_reason_category
ORDER BY cnt DESC;

-- 3.4 PARTIAL_FAILED Job 的 task 全景 —— 看是哪几段失败、哪几段跳过
SELECT j.job_id, j.user_id, j.status AS job_status,
       t.query_id, t.segment_index, t.status AS task_status,
       t.fail_stage, t.fail_reason_category, t.error_code,
       LEFT(t.error_message, 200) AS error_message
FROM adhoc_query_job j
JOIN adhoc_query_task t ON t.job_id = j.job_id
WHERE j.status = 'PARTIAL_FAILED'
  AND j.is_deleted = 0
  AND j.finish_time >= DATE_SUB(NOW(), INTERVAL 24 HOUR)
ORDER BY j.job_id, t.segment_index;

-- 3.5 因"失败传播"被跳过的 Task —— 确认跳过是预期的（SKIPPED_DUE_TO_PRIOR_FAILURE）
SELECT t.query_id, t.job_id, t.segment_index, t.fail_reason_category, t.error_code,
       LEFT(t.error_message, 200) AS error_message, t.finish_time
FROM adhoc_query_task t
WHERE t.fail_reason_category IN ('SKIPPED_DUE_TO_PRIOR_FAILURE','SKIPPED_DUE_TO_SESSION_LOSS')
  AND t.finish_time >= DATE_SUB(NOW(), INTERVAL 24 HOUR)
ORDER BY t.finish_time DESC
LIMIT 50;

-- 3.6 因 executor 宕机导致的失败（EXECUTOR_CRASHED / TASK_LOST）—— 判断是否需重启或扩容
SELECT t.query_id, t.job_id, t.executor_instance,
       t.fail_reason_category, t.fail_stage, t.error_code,
       LEFT(t.error_message, 300) AS error_message, t.finish_time
FROM adhoc_query_task t
WHERE t.fail_reason_category IN ('EXECUTOR_CRASHED','TASK_LOST')
  AND t.finish_time >= DATE_SUB(NOW(), INTERVAL 24 HOUR)
ORDER BY t.finish_time DESC
LIMIT 50;


-- ############################################################################
-- # 场景 4：单个 Job 全链路追踪（用户反馈具体 job 时使用）
-- ############################################################################

-- 4.1 Job 时间线（各阶段耗时）—— 定位慢在哪一段
SELECT job_id, user_id, user_name, engine_type, engine_instance,
       executor_instance, processing_server_instance, status,
       submit_time, validate_finish_time, dispatch_time, split_finish_time,
       start_time, finish_time, duration_ms, cancel_requested,
       -- 各段耗时（秒），NULL 表示该阶段未到达
       TIMESTAMPDIFF(SECOND, submit_time, validate_finish_time)  AS validate_sec,
       TIMESTAMPDIFF(SECOND, validate_finish_time, dispatch_time) AS dispatch_sec,
       TIMESTAMPDIFF(SECOND, dispatch_time, split_finish_time)   AS split_sec,
       TIMESTAMPDIFF(SECOND, split_finish_time, start_time)      AS wait_start_sec,
       TIMESTAMPDIFF(SECOND, start_time, finish_time)            AS exec_sec
FROM adhoc_query_job
WHERE job_id = '<JOB_ID>';

-- 4.2 该 Job 下所有 Task 时间线 + 结果
SELECT t.query_id, t.segment_index, t.status, t.stage,
       t.sql_type, t.has_result_set, t.affected_rows,
       t.engine_type, t.engine_instance, t.executor_instance,
       t.reused_from_task_id,
       t.enqueue_time, t.start_time, t.fetch_start_time,
       t.write_start_time, t.local_write_finish_time, t.oss_upload_time, t.finish_time,
       t.duration_ms, t.scan_rows, t.scan_bytes,
       t.fail_stage, t.fail_reason_category, t.error_code,
       LEFT(t.error_message, 300) AS error_message
FROM adhoc_query_task t
WHERE t.job_id = '<JOB_ID>'
ORDER BY t.segment_index;

-- 4.3 该 Job 的治理记录（被治理改写/拒绝的 SQL）
SELECT g.query_id, g.sql_type, g.governance_result, g.deny_reason,
       LEFT(g.executed_sql_content, 500) AS executed_sql,
       LEFT(g.risk_items_json, 500)      AS risk_items
FROM adhoc_query_governance g
WHERE g.query_id IN (SELECT query_id FROM adhoc_query_task WHERE job_id = '<JOB_ID>');

-- 4.4 该 Job 的结果摘要（结果行数 / OSS 路径 / 上传状态）
SELECT t.query_id, t.segment_index, s.result_rows, s.result_bytes,
       s.storage_type, s.result_status, s.oss_upload_status, s.persistent_path
FROM adhoc_query_task t
LEFT JOIN adhoc_result_summary s ON s.query_id = t.query_id
WHERE t.job_id = '<JOB_ID>'
ORDER BY t.segment_index;

-- 4.5 该 Job 的 job log OSS 路径（去 OSS 拉日志看执行明细）
SELECT job_id, persistent_log_path FROM adhoc_query_job WHERE job_id = '<JOB_ID>';


-- ############################################################################
-- # 场景 5：慢查询排查（执行耗时异常）
-- ############################################################################

-- 5.1 近 24h 耗时 TOP 20 的成功 Task —— 哪些 SQL 慢
SELECT t.query_id, t.job_id, t.user_id, t.user_name,
       t.engine_type, t.engine_instance, t.executor_instance,
       t.sql_type, t.has_result_set, t.scan_rows, t.scan_bytes,
       t.duration_ms, t.start_time, t.finish_time
FROM adhoc_query_task t
WHERE t.status = 'SUCCESS'
  AND t.duration_ms IS NOT NULL
  AND t.finish_time >= DATE_SUB(NOW(), INTERVAL 24 HOUR)
ORDER BY t.duration_ms DESC
LIMIT 20;

-- 5.2 各阶段耗时分解（找出 Task 卡在拉结果还是上传 OSS）
SELECT t.query_id, t.job_id, t.duration_ms AS total_ms,
       TIMESTAMPDIFF(MICROSECOND, t.enqueue_time,        t.start_time)/1000             AS wait_ms,
       TIMESTAMPDIFF(MICROSECOND, t.start_time,          t.fetch_start_time)/1000       AS exec_ms,
       TIMESTAMPDIFF(MICROSECOND, t.fetch_start_time,    t.write_start_time)/1000        AS fetch_ms,
       TIMESTAMPDIFF(MICROSECOND, t.write_start_time,    t.local_write_finish_time)/1000 AS serialize_ms,
       TIMESTAMPDIFF(MICROSECOND, t.local_write_finish_time, t.oss_upload_time)/1000    AS oss_ms
FROM adhoc_query_task t
WHERE t.job_id = '<JOB_ID>'
  AND t.status = 'SUCCESS'
ORDER BY t.segment_index;

-- 5.3 扫描行数/字节数 TOP（结果行少但扫描巨大 = 慢查询根因）
SELECT query_id, job_id, user_id, sql_type,
       scan_rows, scan_bytes, result_rows,
       duration_ms, engine_instance
FROM adhoc_query_task
WHERE scan_rows IS NOT NULL
  AND finish_time >= DATE_SUB(NOW(), INTERVAL 24 HOUR)
ORDER BY scan_rows DESC
LIMIT 20;


-- ############################################################################
-- # 场景 6：调度问题（dispatcher 抢占 / 派发失败）
-- ############################################################################

-- 6.1 DISPATCHING 但长时间未转 RUNNING 的 Job（派发卡住）—— 阈值按需调整
SELECT job_id, user_id, engine_type, engine_instance,
       processing_server_instance,
       submit_time, validate_finish_time, dispatch_time,
       TIMESTAMPDIFF(SECOND, dispatch_time, NOW()) AS dispatching_sec
FROM adhoc_query_job
WHERE status = 'DISPATCHING'
  AND is_deleted = 0
  AND dispatch_time IS NOT NULL
  AND dispatch_time < DATE_SUB(NOW(), INTERVAL 5 MINUTE)
ORDER BY dispatch_time ASC;

-- 6.2 DISPATCH 阶段失败的 Task（派发到 executor 失败）
SELECT t.query_id, t.job_id, t.executor_instance, t.engine_instance,
       t.error_code, t.fail_reason_category,
       LEFT(t.error_message, 300) AS error_message, t.finish_time
FROM adhoc_query_task t
WHERE t.fail_stage = 'DISPATCH'
  AND t.status = 'FAILED'
  AND t.finish_time >= DATE_SUB(NOW(), INTERVAL 24 HOUR)
ORDER BY t.finish_time DESC
LIMIT 50;

-- 6.3 processing_server_instance 为 NULL 的 RUNNING Job —— server 宕机后承接关系丢失
SELECT job_id, user_id, status, executor_instance,
       processing_server_instance, dispatch_time, start_time,
       TIMESTAMPDIFF(SECOND, IFNULL(start_time, dispatch_time), NOW()) AS running_sec
FROM adhoc_query_job
WHERE status = 'RUNNING'
  AND processing_server_instance IS NULL
  AND is_deleted = 0
ORDER BY running_sec DESC;


-- ############################################################################
-- # 场景 7：取消（CANCEL）链路排查
-- ############################################################################

-- 7.1 取消请求但未终态的 Job —— cancel 可能未生效
SELECT job_id, user_id, status, cancel_requested, cancel_requested_time,
       TIMESTAMPDIFF(SECOND, cancel_requested_time, NOW()) AS since_cancel_sec,
       executor_instance, processing_server_instance
FROM adhoc_query_job
WHERE cancel_requested = 1
  AND status NOT IN ('CANCELED','SUCCESS','FAILED','PARTIAL_FAILED')
  AND is_deleted = 0
ORDER BY cancel_requested_time ASC;

-- 7.2 近 24h 取消请求明细及最终状态
SELECT job_id, user_id, status, cancel_requested_time, finish_time,
       TIMESTAMPDIFF(SECOND, cancel_requested_time, finish_time) AS cancel_to_finish_sec
FROM adhoc_query_job
WHERE cancel_requested = 1
  AND cancel_requested_time >= DATE_SUB(NOW(), INTERVAL 24 HOUR)
ORDER BY cancel_requested_time DESC
LIMIT 50;

-- 7.3 取消请求后的 Task 终态分布 —— 是否仍有 Task 未取消（EXECUTOR_CRASHED / 失败传播）
SELECT t.job_id, t.status AS task_status, t.fail_reason_category,
       COUNT(*) AS cnt
FROM adhoc_query_task t
JOIN adhoc_query_job j ON j.job_id = t.job_id
WHERE j.cancel_requested = 1
  AND j.cancel_requested_time >= DATE_SUB(NOW(), INTERVAL 24 HOUR)
GROUP BY t.job_id, t.status, t.fail_reason_category
ORDER BY t.job_id;


-- ############################################################################
-- # 场景 8：结果存储 / OSS 问题
-- ############################################################################

-- 8.1 OSS 上传失败的 Task（结果可能丢失或不完整）
SELECT s.query_id, s.result_rows, s.result_bytes,
       s.storage_type, s.result_status, s.oss_upload_status, s.persistent_path,
       t.job_id, t.user_id, t.finish_time
FROM adhoc_result_summary s
JOIN adhoc_query_task t ON t.query_id = s.query_id
WHERE s.oss_upload_status = 'FAILED'
  AND t.finish_time >= DATE_SUB(NOW(), INTERVAL 24 HOUR)
ORDER BY t.finish_time DESC
LIMIT 50;

-- 8.2 结果状态 INCOMPLETE（结果未写完 / 异常中断）
SELECT s.query_id, s.result_rows, s.result_status, s.oss_upload_status, s.persistent_path,
       t.job_id, t.status AS task_status, t.finish_time
FROM adhoc_result_summary s
JOIN adhoc_query_task t ON t.query_id = s.query_id
WHERE s.result_status = 'INCOMPLETE'
  AND t.finish_time >= DATE_SUB(NOW(), INTERVAL 24 HOUR)
ORDER BY t.finish_time DESC
LIMIT 50;

-- 8.3 WRITING 阶段失败的 Task
SELECT t.query_id, t.job_id, t.fail_stage, t.fail_reason_category, t.error_code,
       LEFT(t.error_message, 300) AS error_message,
       s.result_rows, s.oss_upload_status, s.persistent_path
FROM adhoc_query_task t
LEFT JOIN adhoc_result_summary s ON s.query_id = t.query_id
WHERE t.fail_stage IN ('WRITING','OSS_UPLOAD')
  AND t.status = 'FAILED'
  AND t.finish_time >= DATE_SUB(NOW(), INTERVAL 24 HOUR)
ORDER BY t.finish_time DESC
LIMIT 50;

-- 8.4 结果行数 TOP（排查超大结果集撑爆内存/OSS）
SELECT s.query_id, t.job_id, t.user_id, s.result_rows, s.result_bytes,
       s.persistent_path, t.finish_time
FROM adhoc_result_summary s
JOIN adhoc_query_task t ON t.query_id = s.query_id
WHERE t.finish_time >= DATE_SUB(NOW(), INTERVAL 24 HOUR)
ORDER BY s.result_rows DESC
LIMIT 20;


-- ############################################################################
-- # 场景 9：实例宕机 / 心跳异常
-- ############################################################################

-- 9.1 心跳超时的实例（近 5 分钟无心跳视为离线风险）
SELECT 'SERVER' AS role, instance_id, host, status, accepting,
       heartbeat_time, last_down_time,
       TIMESTAMPDIFF(SECOND, heartbeat_time, NOW()) AS hb_lag_sec
FROM adhoc_server_instance
WHERE heartbeat_time < DATE_SUB(NOW(), INTERVAL 5 MINUTE)
   OR status <> 'UP'
UNION ALL
SELECT 'EXECUTOR' AS role, instance_id, host, status, accepting,
       heartbeat_time, last_down_time,
       TIMESTAMPDIFF(SECOND, heartbeat_time, NOW()) AS hb_lag_sec
FROM adhoc_executor_instance
WHERE heartbeat_time < DATE_SUB(NOW(), INTERVAL 5 MINUTE)
   OR status <> 'UP'
ORDER BY hb_lag_sec DESC;

-- 9.2 疑似孤儿 RUNNING Job（承接的 server/executor 已 DOWN）
SELECT j.job_id, j.status, j.executor_instance, j.processing_server_instance,
       es.status AS executor_status, es.heartbeat_time AS exec_hb,
       ss.status AS server_status, ss.heartbeat_time AS server_hb,
       TIMESTAMPDIFF(SECOND, IFNULL(j.start_time, j.dispatch_time), NOW()) AS running_sec
FROM adhoc_query_job j
LEFT JOIN adhoc_executor_instance es ON es.instance_id = j.executor_instance
LEFT JOIN adhoc_server_instance   ss ON ss.instance_id = j.processing_server_instance
WHERE j.status = 'RUNNING'
  AND j.is_deleted = 0
  AND (es.status <> 'UP' OR ss.status <> 'UP'
       OR es.heartbeat_time < DATE_SUB(NOW(), INTERVAL 5 MINUTE)
       OR ss.heartbeat_time < DATE_SUB(NOW(), INTERVAL 5 MINUTE))
ORDER BY running_sec DESC;

-- 9.3 executor 宕机但 running_tasks 非空（残留任务记录未清理）
SELECT instance_id, host, status, last_down_time,
       running_tasks, max_concurrent_tasks, heartbeat_time
FROM adhoc_executor_instance
WHERE status <> 'UP'
  AND running_tasks IS NOT NULL
  AND running_tasks <> '[]'
  AND running_tasks <> '';


-- ############################################################################
-- # 场景 10：JVM / 资源指标排查（OOM / GC / 高负载）
-- ############################################################################

-- 10.1 各实例最新一条采样快照（当前瞬时负载）
SELECT s.instance_id, s.role, s.sample_time,
       s.cpu_usage_pct, s.system_cpu_usage_pct, s.system_load_avg,
       s.heap_used_mb, s.heap_max_mb, s.heap_used_pct, s.old_used_mb, s.old_max_mb,
       s.metaspace_used_mb, s.direct_buffer_used_mb,
       s.thread_count, s.deadlock_count,
       s.gc_time_ratio_pct, s.full_gc_count, s.young_gc_count
FROM adhoc_jvm_metric_sample s
JOIN (
    SELECT instance_id, MAX(sample_time) AS max_t
    FROM adhoc_jvm_metric_sample
    WHERE sample_time >= DATE_SUB(NOW(), INTERVAL 10 MINUTE)
    GROUP BY instance_id
) m ON m.instance_id = s.instance_id AND m.max_t = s.sample_time
ORDER BY s.heap_used_pct DESC NULLS LAST;

-- 10.2 堆使用率超 85% 的实例（OOM 风险）—— 近 1h 采样
SELECT instance_id, role, sample_time,
       heap_used_mb, heap_max_mb, heap_used_pct, old_used_mb, old_max_mb,
       cpu_usage_pct, thread_count
FROM adhoc_jvm_metric_sample
WHERE sample_time >= DATE_SUB(NOW(), INTERVAL 1 HOUR)
  AND heap_used_pct >= 85
ORDER BY heap_used_pct DESC, sample_time DESC
LIMIT 50;

-- 10.3 出现 Full GC 或死锁的实例
SELECT instance_id, role, sample_time,
       full_gc_count, full_gc_time_ms, young_gc_count, young_gc_time_ms,
       gc_time_ratio_pct, deadlock_count, thread_count, heap_used_pct
FROM adhoc_jvm_metric_sample
WHERE sample_time >= DATE_SUB(NOW(), INTERVAL 1 HOUR)
  AND (full_gc_count > 0 OR deadlock_count > 0)
ORDER BY sample_time DESC
LIMIT 50;

-- 10.4 某实例近 1h 堆内存趋势（按时间正序，便于画图）
SELECT sample_time, heap_used_mb, heap_committed_mb, heap_max_mb, heap_used_pct,
       old_used_mb, metaspace_used_mb, direct_buffer_used_mb,
       cpu_usage_pct, system_load_avg, thread_count
FROM adhoc_jvm_metric_sample
WHERE instance_id = '<INSTANCE_ID>'
  AND sample_time >= DATE_SUB(NOW(), INTERVAL 1 HOUR)
ORDER BY sample_time ASC;

-- 10.5 实例启动/重启痕迹（uptime 回退 = 重启过）
SELECT instance_id, role, sample_time, uptime_ms, start_time_ms,
       heap_used_pct, cpu_usage_pct, thread_count
FROM adhoc_jvm_metric_sample
WHERE instance_id = '<INSTANCE_ID>'
  AND sample_time >= DATE_SUB(NOW(), INTERVAL 24 HOUR)
ORDER BY sample_time ASC;


-- ############################################################################
-- # 场景 11：用户维度排查（某用户反馈问题）
-- ############################################################################

-- 11.1 某用户近 24h Job 全景
SELECT job_id, engine_type, status, submit_time, dispatch_time, finish_time,
       duration_ms, cancel_requested, client_ip, client_request_id
FROM adhoc_query_job
WHERE user_id = '<USER_ID>'
  AND submit_time >= DATE_SUB(NOW(), INTERVAL 24 HOUR)
  AND is_deleted = 0
ORDER BY submit_time DESC
LIMIT 50;

-- 11.2 某用户近 24h 失败 Task
SELECT query_id, job_id, segment_index, status, fail_stage, fail_reason_category,
       error_code, LEFT(error_message, 300) AS error_message,
       engine_type, engine_instance, finish_time
FROM adhoc_query_task
WHERE user_id = '<USER_ID>'
  AND status = 'FAILED'
  AND finish_time >= DATE_SUB(NOW(), INTERVAL 24 HOUR)
ORDER BY finish_time DESC
LIMIT 50;

-- 11.3 某用户提交频率（近 1h，是否在刷接口）
SELECT user_id, MAX(user_name) AS user_name, COUNT(*) AS submit_cnt,
       SUM(status = 'FAILED') AS failed_cnt,
       SUM(status = 'SUCCESS') AS success_cnt
FROM adhoc_query_job
WHERE submit_time >= DATE_SUB(NOW(), INTERVAL 1 HOUR)
  AND is_deleted = 0
GROUP BY user_id
ORDER BY submit_cnt DESC
LIMIT 20;

-- 11.4 某用户当前在跑/排队 Job（是否占用过多并发）
SELECT user_id, MAX(user_name) AS user_name,
       SUM(status = 'PENDING') AS pending_cnt,
       SUM(status = 'RUNNING') AS running_cnt
FROM adhoc_query_job
WHERE status IN ('PENDING','RUNNING','DISPATCHING')
  AND is_deleted = 0
GROUP BY user_id
HAVING pending_cnt + running_cnt > 0
ORDER BY (pending_cnt + running_cnt) DESC
LIMIT 20;


-- ############################################################################
-- # 场景 12：治理 / 危险 SQL 排查
-- ############################################################################

-- 12.1 近 24h 被治理拒绝的 Task
SELECT g.query_id, g.sql_type, g.governance_result, g.deny_reason,
       LEFT(g.risk_items_json, 300) AS risk_items,
       t.job_id, t.user_id, t.finish_time
FROM adhoc_query_governance g
JOIN adhoc_query_task t ON t.query_id = g.query_id
WHERE g.governance_result = 'DENIED'
  AND t.finish_time >= DATE_SUB(NOW(), INTERVAL 24 HOUR)
ORDER BY t.finish_time DESC
LIMIT 50;

-- 12.2 被改写过的 SQL（executed_sql_content 与原始不同）—— 关注高风险改写
SELECT g.query_id, t.sql_type, t.job_id, t.user_id,
       LEFT(t.sql_content, 300) AS original_sql,
       LEFT(g.executed_sql_content, 300) AS executed_sql
FROM adhoc_query_governance g
JOIN adhoc_query_task t ON t.query_id = g.query_id
WHERE g.governance_result = 'PASSED'
  AND g.executed_sql_content IS NOT NULL
  AND t.finish_time >= DATE_SUB(NOW(), INTERVAL 24 HOUR)
ORDER BY t.finish_time DESC
LIMIT 50;

-- 12.3 各 sql_type 分布（近 24h）—— 看 DDL/DML/CTAS 等占比
SELECT t.sql_type, COUNT(*) AS cnt,
       SUM(t.status = 'FAILED') AS failed_cnt
FROM adhoc_query_task t
WHERE t.finish_time >= DATE_SUB(NOW(), INTERVAL 24 HOUR)
GROUP BY t.sql_type
ORDER BY cnt DESC;


-- ############################################################################
-- # 场景 13：结果复用排查（reused_from_task_id）
-- ############################################################################

-- 13.1 命中结果复用的 Task（近 24h）
SELECT t.query_id, t.job_id, t.user_id, t.reused_from_task_id,
       t.sql_hash, t.finish_time
FROM adhoc_query_task t
WHERE t.reused_from_task_id IS NOT NULL
  AND t.finish_time >= DATE_SUB(NOW(), INTERVAL 24 HOUR)
ORDER BY t.finish_time DESC
LIMIT 50;

-- 13.2 复用命中率（命中数 / 总 Task 数）
SELECT
    COUNT(*) AS total_tasks,
    SUM(reused_from_task_id IS NOT NULL) AS reused_tasks,
    ROUND(SUM(reused_from_task_id IS NOT NULL) / COUNT(*) * 100, 2) AS reuse_rate_pct
FROM adhoc_query_task
WHERE finish_time >= DATE_SUB(NOW(), INTERVAL 24 HOUR);


-- ############################################################################
-- # 场景 14：幂等 / 重复提交排查
-- ############################################################################

-- 14.1 相同 client_request_id 的 Job（幂等命中，正常应只有一条）
SELECT client_request_id, COUNT(*) AS cnt,
       GROUP_CONCAT(job_id) AS job_ids
FROM adhoc_query_job
WHERE client_request_id IS NOT NULL
  AND submit_time >= DATE_SUB(NOW(), INTERVAL 24 HOUR)
GROUP BY client_request_id
HAVING cnt > 1
ORDER BY cnt DESC
LIMIT 50;

-- 14.2 近 1h 相同 sql_hash 高频提交（疑似刷接口/重复执行）
SELECT user_id, MAX(user_name) AS user_name, sql_hash, COUNT(*) AS cnt,
       MIN(finish_time) AS first_finish, MAX(finish_time) AS last_finish
FROM adhoc_query_task
WHERE sql_hash IS NOT NULL
  AND finish_time >= DATE_SUB(NOW(), INTERVAL 1 HOUR)
GROUP BY user_id, sql_hash
HAVING cnt > 3
ORDER BY cnt DESC
LIMIT 20;


-- ############################################################################
-- # 场景 15：表级血缘溯源
-- ############################################################################

-- 15.1 某表被哪些 Task 读取/写入（近 24h，JSON LIKE 匹配，粗筛）
SELECT r.query_id, t.job_id, t.user_id, t.sql_type,
       LEFT(r.source_tables_json, 300) AS source_tables,
       LEFT(r.sink_tables_json, 300)   AS sink_tables,
       t.finish_time
FROM adhoc_query_table_ref r
JOIN adhoc_query_task t ON t.query_id = r.query_id
WHERE (r.source_tables_json LIKE '%<TABLE_NAME>%'
    OR r.sink_tables_json LIKE '%<TABLE_NAME>%')
  AND t.finish_time >= DATE_SUB(NOW(), INTERVAL 24 HOUR)
ORDER BY t.finish_time DESC
LIMIT 50;


-- ############################################################################
-- # 场景 16：软删除 / 数据一致性核查
-- ############################################################################

-- 16.1 Job 软删除但 Task 未删（一致性核查）
SELECT j.job_id, j.is_deleted AS job_del, COUNT(t.query_id) AS live_tasks
FROM adhoc_query_job j
JOIN adhoc_query_task t ON t.job_id = j.job_id AND t.is_deleted = 0
WHERE j.is_deleted = 1
GROUP BY j.job_id, j.is_deleted
LIMIT 50;

-- 16.2 Task 存在但 Job 不存在（孤儿 Task）
SELECT t.query_id, t.job_id, t.user_id, t.status, t.finish_time
FROM adhoc_query_task t
LEFT JOIN adhoc_query_job j ON j.job_id = t.job_id
WHERE j.job_id IS NULL
  AND t.finish_time >= DATE_SUB(NOW(), INTERVAL 24 HOUR)
LIMIT 50;

-- 16.3 结果摘要但 Task 缺失（孤儿摘要）
SELECT s.query_id, s.result_rows, s.oss_upload_status, s.create_time
FROM adhoc_result_summary s
LEFT JOIN adhoc_query_task t ON t.query_id = s.query_id
WHERE t.query_id IS NULL
  AND s.create_time >= DATE_SUB(NOW(), INTERVAL 24 HOUR)
LIMIT 50;

-- 16.4 终态 Job 但 finish_time 为空（状态机异常）
SELECT job_id, status, start_time, finish_time, update_time
FROM adhoc_query_job
WHERE status IN ('SUCCESS','FAILED','PARTIAL_FAILED','CANCELED')
  AND finish_time IS NULL
  AND is_deleted = 0
LIMIT 50;

-- 16.5 非终态 Job 但长时间无更新（疑似状态机卡死，30 分钟无 update）
SELECT job_id, status, update_time,
       TIMESTAMPDIFF(SECOND, update_time, NOW()) AS stale_sec,
       executor_instance, processing_server_instance
FROM adhoc_query_job
WHERE status NOT IN ('SUCCESS','FAILED','PARTIAL_FAILED','CANCELED')
  AND is_deleted = 0
  AND update_time < DATE_SUB(NOW(), INTERVAL 30 MINUTE)
ORDER BY update_time ASC
LIMIT 50;