package io.gitee.songchaolin.adhoc.executor.runner;

/**
 * 失败上下文
 * <p>
 * 用于跟踪上游失败并传播到下游 Task
 */
public class FailureContext {

    private boolean failed = false;
    private String failedTaskId = null;
    private String failedReason = null;

    public boolean isFailed() {
        return failed;
    }

    public String getFailedTaskId() {
        return failedTaskId;
    }

    public String getFailedReason() {
        return failedReason;
    }

    public void markFailed(String taskId, String reason) {
        this.failed = true;
        this.failedTaskId = taskId;
        this.failedReason = reason;
    }

    public void reset() {
        this.failed = false;
        this.failedTaskId = null;
        this.failedReason = null;
    }
}