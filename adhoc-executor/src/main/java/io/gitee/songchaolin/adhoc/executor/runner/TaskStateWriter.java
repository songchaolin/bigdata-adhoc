package io.gitee.songchaolin.adhoc.executor.runner;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import io.gitee.songchaolin.adhoc.common.enums.FailReasonCategory;
import io.gitee.songchaolin.adhoc.common.enums.FailStage;
import io.gitee.songchaolin.adhoc.common.enums.TaskStage;
import io.gitee.songchaolin.adhoc.common.enums.TaskStatus;
import io.gitee.songchaolin.adhoc.common.exception.AdhocErrorCode;
import io.gitee.songchaolin.adhoc.dao.entity.AdhocQueryTask;
import io.gitee.songchaolin.adhoc.dao.mapper.AdhocQueryTaskMapper;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * Task DB 状态写（集中 LambdaUpdateWrapper，runner / TaskResultWriter 共用）。
 * 所有方法以 queryId 为条件做单行 update，带 status 守卫防状态倒退/覆盖终态。
 */
@Component
public class TaskStateWriter {

    private final AdhocQueryTaskMapper taskMapper;

    public TaskStateWriter(AdhocQueryTaskMapper taskMapper) {
        this.taskMapper = taskMapper;
    }

    public void markRunning(String taskId, long taskStartMs) {
        taskMapper.update(null, new LambdaUpdateWrapper<AdhocQueryTask>()
                .eq(AdhocQueryTask::getQueryId, taskId)
                .eq(AdhocQueryTask::getStatus, TaskStatus.PENDING.name())
                .set(AdhocQueryTask::getStatus, TaskStatus.RUNNING.name())
                .set(AdhocQueryTask::getStage, TaskStage.EXECUTING.name())
                .set(AdhocQueryTask::getStartTime, new Date(taskStartMs)));
    }

    /** stage 迁移：EXECUTING -> FETCHING（开始拉取结果集） */
    public void markFetching(String taskId) {
        taskMapper.update(null, new LambdaUpdateWrapper<AdhocQueryTask>()
                .eq(AdhocQueryTask::getQueryId, taskId)
                .eq(AdhocQueryTask::getStatus, TaskStatus.RUNNING.name())
                .eq(AdhocQueryTask::getStage, TaskStage.EXECUTING.name())
                .set(AdhocQueryTask::getStage, TaskStage.FETCHING.name())
                .set(AdhocQueryTask::getFetchStartTime, new Date()));
    }

    /** stage 迁移：FETCHING -> WRITING（开始序列化 + 上传 OSS 结果集）。write_start_time 在此刻写入，供进度接口识别"写入中"。 */
    public void markWriting(String taskId, long writeStartMs) {
        taskMapper.update(null, new LambdaUpdateWrapper<AdhocQueryTask>()
                .eq(AdhocQueryTask::getQueryId, taskId)
                .eq(AdhocQueryTask::getStatus, TaskStatus.RUNNING.name())
                .eq(AdhocQueryTask::getStage, TaskStage.FETCHING.name())
                .set(AdhocQueryTask::getStage, TaskStage.WRITING.name())
                .set(AdhocQueryTask::getWriteStartTime, new Date(writeStartMs)));
    }

    public void markSuccess(String taskId, long durationMs) {
        taskMapper.update(null, new LambdaUpdateWrapper<AdhocQueryTask>()
                .eq(AdhocQueryTask::getQueryId, taskId)
                .eq(AdhocQueryTask::getStatus, TaskStatus.RUNNING.name())
                .set(AdhocQueryTask::getStatus, TaskStatus.SUCCESS.name())
                .set(AdhocQueryTask::getStage, TaskStage.SUCCESS.name())  // 成功完成所有阶段
                .set(AdhocQueryTask::getFinishTime, new Date())
                .set(AdhocQueryTask::getDurationMs, durationMs));
    }

    /** 成功 + 结果集元数据（has_result_set/scan_rows/oss_upload_time）合并到一次 UPDATE；write_start_time 已由 markWriting 写入。 */
    public void markSuccess(String taskId, long durationMs, long scanRows) {
        taskMapper.update(null, new LambdaUpdateWrapper<AdhocQueryTask>()
                .eq(AdhocQueryTask::getQueryId, taskId)
                .eq(AdhocQueryTask::getStatus, TaskStatus.RUNNING.name())
                .set(AdhocQueryTask::getStatus, TaskStatus.SUCCESS.name())
                .set(AdhocQueryTask::getStage, TaskStage.SUCCESS.name())
                .set(AdhocQueryTask::getFinishTime, new Date())
                .set(AdhocQueryTask::getDurationMs, durationMs)
                .set(AdhocQueryTask::getHasResultSet, 1)
                .set(AdhocQueryTask::getScanRows, scanRows)
                .set(AdhocQueryTask::getOssUploadTime, new Date()));
    }

    public void markFailed(String taskId, FailStage stage, FailReasonCategory category,
                           String errorCode, String errorMessage) {
        taskMapper.update(null, new LambdaUpdateWrapper<AdhocQueryTask>()
                .eq(AdhocQueryTask::getQueryId, taskId)
                .in(AdhocQueryTask::getStatus, TaskStatus.PENDING.name(), TaskStatus.RUNNING.name())
                .set(AdhocQueryTask::getStatus, TaskStatus.FAILED.name())
                .set(AdhocQueryTask::getFailStage, stage.name())
                .set(AdhocQueryTask::getFailReasonCategory, category.name())
                .set(AdhocQueryTask::getErrorCode, errorCode)
                .set(AdhocQueryTask::getErrorMessage, errorMessage)
                .set(AdhocQueryTask::getFinishTime, new Date()));
    }

    public void markSyntaxError(String taskId, String errorMessage) {
        taskMapper.update(null, new LambdaUpdateWrapper<AdhocQueryTask>()
                .eq(AdhocQueryTask::getQueryId, taskId)
                .eq(AdhocQueryTask::getStatus, TaskStatus.PENDING.name())
                .set(AdhocQueryTask::getStatus, TaskStatus.FAILED.name())
                .set(AdhocQueryTask::getFailStage, FailStage.SPLIT.name())
                .set(AdhocQueryTask::getFailReasonCategory, FailReasonCategory.ENGINE_ERROR.name())
                .set(AdhocQueryTask::getErrorCode, AdhocErrorCode.ADHOC_SQL_SYNTAX_ERROR.name())
                .set(AdhocQueryTask::getErrorMessage, errorMessage)
                .set(AdhocQueryTask::getFinishTime, new Date()));
    }

    /**
     * 因上游 task 失败被跳过：标 FAILED + SKIPPED_DUE_TO_PRIOR_FAILURE，
     * errorMessage 显式指向上游 task 及其失败原因。
     */
    public void markSkipped(String taskId, String upstreamTaskId, String upstreamReason) {
        String msg = "skipped due to upstream task " + upstreamTaskId + " failure"
                + (upstreamReason != null && !upstreamReason.isEmpty() ? " (" + upstreamReason + ")" : "");
        taskMapper.update(null, new LambdaUpdateWrapper<AdhocQueryTask>()
                .eq(AdhocQueryTask::getQueryId, taskId)
                .eq(AdhocQueryTask::getStatus, TaskStatus.PENDING.name())
                .set(AdhocQueryTask::getStatus, TaskStatus.SKIPPED.name())
                .set(AdhocQueryTask::getFailReasonCategory, FailReasonCategory.SKIPPED_DUE_TO_PRIOR_FAILURE.name())
                .set(AdhocQueryTask::getErrorMessage, msg)
                .set(AdhocQueryTask::getFinishTime, new Date()));
    }

    /** cancel：task PENDING/RUNNING -> CANCELED（executor 每段前检查 cancel_requested 时跳过剩余 task）。 */
    public void markCanceled(String taskId) {
        taskMapper.update(null, new LambdaUpdateWrapper<AdhocQueryTask>()
                .eq(AdhocQueryTask::getQueryId, taskId)
                .in(AdhocQueryTask::getStatus, TaskStatus.PENDING.name(), TaskStatus.RUNNING.name())
                .set(AdhocQueryTask::getStatus, TaskStatus.CANCELED.name())
                .set(AdhocQueryTask::getFailReasonCategory, FailReasonCategory.CANCELED.name())
                .set(AdhocQueryTask::getFinishTime, new Date()));
    }

    public void updateLogPath(String taskId, String ossKey) {
        taskMapper.update(null, new LambdaUpdateWrapper<AdhocQueryTask>()
                .eq(AdhocQueryTask::getQueryId, taskId)
                .set(AdhocQueryTask::getPersistentLogPath, ossKey));
    }

    /** DDL/DML 影响行数（getUpdateCount；null 表示无更新计数，如 CREATE）。 */
    public void updateAffectedRows(String taskId, Long affectedRows) {
        taskMapper.update(null, new LambdaUpdateWrapper<AdhocQueryTask>()
                .eq(AdhocQueryTask::getQueryId, taskId)
                .set(AdhocQueryTask::getAffectedRows, affectedRows));
    }
}
