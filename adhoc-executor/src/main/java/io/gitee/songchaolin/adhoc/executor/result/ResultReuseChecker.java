package io.gitee.songchaolin.adhoc.executor.result;

import io.gitee.songchaolin.adhoc.common.config.AdhocCommonConfig;
import io.gitee.songchaolin.adhoc.common.config.ConfigHolder;
import io.gitee.songchaolin.adhoc.common.enums.TaskStage;
import io.gitee.songchaolin.adhoc.common.enums.TaskStatus;
import io.gitee.songchaolin.adhoc.common.util.SqlHashUtil;
import io.gitee.songchaolin.adhoc.dao.entity.AdhocQueryTask;
import io.gitee.songchaolin.adhoc.dao.entity.AdhocResultSummary;
import io.gitee.songchaolin.adhoc.dao.mapper.AdhocQueryTaskMapper;
import io.gitee.songchaolin.adhoc.dao.mapper.AdhocResultSummaryMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * 结果复用检查器（executor 侧）
 * <p>
 * 在 Task 执行前检查是否可复用最近的成功结果（同 userId + sql_hash + TTL）
 */
@Component
public class ResultReuseChecker {

    private static final Logger log = LoggerFactory.getLogger(ResultReuseChecker.class);

    private final AdhocQueryTaskMapper taskMapper;
    private final AdhocResultSummaryMapper resultSummaryMapper;
    private final ConfigHolder cfg;

    public ResultReuseChecker(AdhocQueryTaskMapper taskMapper, AdhocResultSummaryMapper resultSummaryMapper, ConfigHolder cfg) {
        this.taskMapper = taskMapper;
        this.resultSummaryMapper = resultSummaryMapper;
        this.cfg = cfg;
    }

    /**
     * 检查 Task 是否可复用
     *
     * @param userId  用户ID
     * @param sqlHash SQL哈希值（已计算）
     * @return 可复用返回源 Task ID；否则返回 null
     */
    public String checkReuse(String userId, String sqlHash) {
        int ttlSeconds = cfg.get(AdhocCommonConfig.RESULT_REUSE_TTL_SECONDS);

        String existingTaskId = taskMapper.selectSuccessTaskBySqlHash(userId, sqlHash, ttlSeconds);

        if (existingTaskId != null) {
            log.info("【结果复用检查】sqlHash={} 命中 existingTaskId={}", sqlHash, existingTaskId);
        } else {
            log.debug("【结果复用检查】sqlHash={} 未命中", sqlHash);
        }

        return existingTaskId;
    }

    /**
     * 标记 Task 为复用成功（完整字段复制）
     * <p>
     * 从源 Task 复制：
     * - stage: EXECUTING（与正常执行一致）
     * - duration_ms: 本 Task 实际耗时
     * - scan_rows/scan_bytes: 从源 Task 复制
     * - start_time/finish_time: 本 Task 实际时间
     * <p>
     * 同时复制结果摘要（adhoc_result_summary）：
     * - persistent_path: 复用源 Task 的 OSS 路径
     * - result_rows/result_bytes: 复制
     * - storage_type: PERSISTENT
     *
     * @param taskId            新 Task ID
     * @param reusedFromTaskId  复用的源 Task ID
     * @param sqlHash           SQL 哈希值（已计算）
     * @param taskStartMs       Task 开始时间（毫秒）
     */
    public void markReusedSuccess(String taskId, String reusedFromTaskId, String sqlHash, long taskStartMs) {
        Date now = new Date();
        long durationMs = System.currentTimeMillis() - taskStartMs;

        // 1. 查询源 Task 获取需要复制的字段
        AdhocQueryTask sourceTask = taskMapper.selectById(reusedFromTaskId);
        if (sourceTask == null) {
            log.error("【结果复用失败】源 Task 不存在 taskId={}", reusedFromTaskId);
            return;
        }

        // 2. 更新当前 Task 状态（完整字段）
        AdhocQueryTask task = new AdhocQueryTask();
        task.setQueryId(taskId);
        task.setStatus(TaskStatus.SUCCESS.name());
        task.setStage(TaskStage.SUCCESS.name());  // 成功完成所有阶段
        task.setReusedFromTaskId(reusedFromTaskId);
        task.setSqlHash(sqlHash);
        task.setStartTime(new Date(taskStartMs));  // 本 Task 实际时间
        task.setFinishTime(now);
        task.setDurationMs(durationMs);             // 本 Task 实际耗时
        // 从源 Task 复制统计信息
        task.setScanRows(sourceTask.getScanRows());
        task.setScanBytes(sourceTask.getScanBytes());
        task.setHasResultSet(sourceTask.getHasResultSet());
        task.setAffectedRows(sourceTask.getAffectedRows());
        task.setUpdateTime(now);

        taskMapper.updateById(task);

        // 3. 复制结果摘要（供 server 端查询）
        copyResultSummary(reusedFromTaskId, taskId);

        log.info("【结果复用】taskId={} reused from taskId={} durationMs={}ms", taskId, reusedFromTaskId, durationMs);
    }

    /**
     * 复制结果摘要（adhoc_result_summary）
     */
    private void copyResultSummary(String sourceTaskId, String targetTaskId) {
        AdhocResultSummary sourceSummary = resultSummaryMapper.selectById(sourceTaskId);
        if (sourceSummary == null) {
            log.warn("【结果复用】源 Task 无结果摘要 taskId={}", sourceTaskId);
            return;
        }

        // 复制结果摘要
        AdhocResultSummary targetSummary = new AdhocResultSummary();
        targetSummary.setQueryId(targetTaskId);
        targetSummary.setResultRows(sourceSummary.getResultRows());
        targetSummary.setResultBytes(sourceSummary.getResultBytes());
        targetSummary.setPersistentPath(sourceSummary.getPersistentPath());  // 复用 OSS 路径
        targetSummary.setStorageType(sourceSummary.getStorageType());
        targetSummary.setResultStatus(sourceSummary.getResultStatus());
        targetSummary.setOssUploadStatus(sourceSummary.getOssUploadStatus());

        resultSummaryMapper.insert(targetSummary);

        log.info("【结果复用】复制结果摘要 targetTaskId={} rows={} path={}",
                targetTaskId, sourceSummary.getResultRows(), sourceSummary.getPersistentPath());
    }
}