package io.gitee.songchaolin.adhoc.executor.runner;

import io.gitee.songchaolin.adhoc.internal.grpc.v1.DispatchJobRequest;
import io.gitee.songchaolin.adhoc.sqlparser.model.ProcessedSqlSegment;

import java.sql.Connection;

/**
 * Task 执行上下文
 * <p>
 * 不可变数据（final）与可变状态分离
 */
public class TaskExecutionContext {

    // === 不可变数据 ===
    public final String taskId;
    public final String jobId;
    public final int segmentIndex;
    public final ProcessedSqlSegment segment;
    public final Connection connection;
    public final EngineSelector.EngineContext engine;
    public final DispatchJobRequest request;
    public final LogBuffer jobLog;
    public final long tasksEnqueueMs;

    // === 可变状态 ===
    private long startTimeMs;
    private long endTimeMs;
    private String executedSql;

    public TaskExecutionContext(String taskId, String jobId, int segmentIndex,
                                 ProcessedSqlSegment segment, Connection connection,
                                 EngineSelector.EngineContext engine, DispatchJobRequest request,
                                 LogBuffer jobLog, long tasksEnqueueMs) {
        this.taskId = taskId;
        this.jobId = jobId;
        this.segmentIndex = segmentIndex;
        this.segment = segment;
        this.connection = connection;
        this.engine = engine;
        this.request = request;
        this.jobLog = jobLog;
        this.tasksEnqueueMs = tasksEnqueueMs;
    }

    public void setStartTime(long timeMs) {
        this.startTimeMs = timeMs;
    }

    public void setEndTime(long timeMs) {
        this.endTimeMs = timeMs;
    }

    public void setExecutedSql(String sql) {
        this.executedSql = sql;
    }

    public long getStartTimeMs() {
        return startTimeMs;
    }

    public long getEndTimeMs() {
        return endTimeMs;
    }

    public long getDurationMs() {
        return endTimeMs - startTimeMs;
    }

    public String getExecutedSql() {
        return executedSql;
    }
}