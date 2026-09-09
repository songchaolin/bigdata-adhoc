package io.gitee.songchaolin.adhoc.executor.runner;

import io.gitee.songchaolin.adhoc.common.enums.FailReasonCategory;
import io.gitee.songchaolin.adhoc.common.enums.FailStage;
import io.gitee.songchaolin.adhoc.common.exception.AdhocErrorCode;
import org.springframework.stereotype.Component;

/**
 * Task 状态转换器
 * <p>
 * 封装 TaskStateWriter 调用，统一状态迁移入口
 */
@Component
public class TaskStateTransition {

    private final TaskStateWriter stateWriter;

    public TaskStateTransition(TaskStateWriter stateWriter) {
        this.stateWriter = stateWriter;
    }

    /** 状态迁移：PENDING -> RUNNING */
    public void toRunning(String taskId, long startTimeMs) {
        stateWriter.markRunning(taskId, startTimeMs);
    }

    /** 状态迁移：RUNNING -> FETCHING */
    public void toFetching(String taskId) {
        stateWriter.markFetching(taskId);
    }

    /** 状态迁移：FETCHING -> SUCCESS（无结果集：DDL/DML） */
    public void toSuccess(String taskId, long durationMs) {
        stateWriter.markSuccess(taskId, durationMs);
    }

    /** 状态迁移：FETCHING -> WRITING（开始写结果集，write_start_time 落库） */
    public void toWriting(String taskId, long writeStartMs) {
        stateWriter.markWriting(taskId, writeStartMs);
    }

    /** 状态迁移：WRITING -> SUCCESS（含结果集元数据 has_result_set/scan_rows/oss_upload_time，合并单次 UPDATE） */
    public void toSuccess(String taskId, long durationMs, long scanRows) {
        stateWriter.markSuccess(taskId, durationMs, scanRows);
    }

    /** 状态迁移：-> FAILED */
    public void toFailed(String taskId, FailStage stage, FailReasonCategory reason,
                         String errorCode, String errorMessage) {
        stateWriter.markFailed(taskId, stage, reason, errorCode, errorMessage);
    }

    /** 状态迁移：-> CANCELED */
    public void toCanceled(String taskId) {
        stateWriter.markCanceled(taskId);
    }

    /** 状态迁移：-> SKIPPED */
    public void toSkipped(String taskId, String upstreamTaskId, String reason) {
        stateWriter.markSkipped(taskId, upstreamTaskId, reason);
    }

    /** 状态迁移：-> SYNTAX_ERROR */
    public void toSyntaxError(String taskId, String errorMessage) {
        stateWriter.markSyntaxError(taskId, errorMessage);
    }

    /** 更新影响行数 */
    public void updateAffectedRows(String taskId, Long affectedRows) {
        stateWriter.updateAffectedRows(taskId, affectedRows);
    }
}