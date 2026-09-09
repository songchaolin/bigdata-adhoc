package io.gitee.songchaolin.adhoc.executor.runner;

import org.springframework.stereotype.Component;

/**
 * Task 日志记录器
 * <p>
 * 统一 [executor] 前缀 + [job=X][task=Y] 上下文格式，保证每行 task 日志都能按 taskId 过滤
 * （/api/task/log 按 [task=ID] 过滤行，见 {@code LogQueryService.readOssTaskLog}）。
 */
@Component
public class TaskLogger {

    /**
     * 记录任务信息日志
     * 格式：[executor] [INFO] [job=X][task=Y] message
     */
    public void info(LogBuffer jobLog, String jobId, String taskId, String message) {
        jobLog.append("[executor] [INFO] [job=" + jobId + "][task=" + taskId + "] " + message);
    }

    /**
     * 记录任务错误日志
     * 格式：[executor] [ERROR] [job=X][task=Y] message
     */
    public void error(LogBuffer jobLog, String jobId, String taskId, String message) {
        jobLog.append("[executor] [ERROR] [job=" + jobId + "][task=" + taskId + "] " + message);
    }

    /**
     * 记录任务分隔符（统一 [job=X][task=Y] 前缀；分隔符正文保留状态+sqlType 便于肉眼定位）
     */
    public void separator(LogBuffer jobLog, String jobId, String taskId, String sqlType, boolean success) {
        String status = success ? "DONE" : "FAILED";
        jobLog.append("[executor] [INFO] [job=" + jobId + "][task=" + taskId + "] "
                + "------------------ " + status + " (" + sqlType + ") ------------------");
    }

    /**
     * 记录复用命中日志
     */
    public void cacheHit(LogBuffer jobLog, String jobId, String taskId, String sourceTaskId) {
        jobLog.append("[executor] [INFO] [job=" + jobId + "][task=" + taskId + "] [CACHE HIT] reused from " + sourceTaskId);
    }

    /**
     * 记录任务成功日志
     */
    public void success(LogBuffer jobLog, String jobId, String taskId, boolean reused) {
        String suffix = reused ? "SUCCESS (reused)" : "SUCCESS";
        jobLog.append("[executor] [INFO] [job=" + jobId + "][task=" + taskId + "] " + suffix);
    }
}