package io.gitee.songchaolin.adhoc.executor.runner;

import io.gitee.songchaolin.adhoc.common.enums.FailReasonCategory;
import io.gitee.songchaolin.adhoc.common.enums.JobStatus;
import io.gitee.songchaolin.adhoc.common.enums.TaskStatus;
import io.gitee.songchaolin.adhoc.common.util.LogTiming;
import io.gitee.songchaolin.adhoc.dao.entity.AdhocQueryTask;
import io.gitee.songchaolin.adhoc.dao.mapper.AdhocQueryTaskMapper;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

/**
 * Job 聚合器
 * <p>
 * 负责聚合 Job 下所有 Task 的执行结果，计算最终状态
 */
@Component
public class JobAggregator {

    private final AdhocQueryTaskMapper taskMapper;
    private final JobStateWriter jobStateWriter;
    private final CancelChecker cancelChecker;

    public JobAggregator(AdhocQueryTaskMapper taskMapper, JobStateWriter jobStateWriter, CancelChecker cancelChecker) {
        this.taskMapper = taskMapper;
        this.jobStateWriter = jobStateWriter;
        this.cancelChecker = cancelChecker;
    }

    /**
     * 聚合 Job 结果
     *
     * @param jobId       Job ID
     * @param jobLog      Job 日志
     * @param jobStartMs  Job 开始时间（毫秒）
     * @param submitTime  提交时间
     * @param dispatchTime 调度时间
     * @return Job 最终状态
     */
    public JobStatus aggregate(String jobId, LogBuffer jobLog, long jobStartMs,
                               Date submitTime, Date dispatchTime) {
        // 只查询需要的字段（status, fail_reason_category）
        List<AdhocQueryTask> tasks = taskMapper.selectStatisticsByJobId(jobId);

        Statistics stats = computeStatistics(tasks);
        JobStatus status = determineStatus(jobId, stats);

        Date finishTime = new Date();
        jobStateWriter.markTerminal(jobId, status.name(), finishTime);

        emitSummaryLog(jobId, jobLog, status, stats, submitTime, finishTime);
        emitTimingLog(jobId, jobLog, submitTime, dispatchTime, jobStartMs, finishTime);

        return status;
    }

    private Statistics computeStatistics(List<AdhocQueryTask> tasks) {
        long success = tasks.stream()
                .filter(t -> TaskStatus.SUCCESS.is(t.getStatus()))
                .count();
        long failed = tasks.stream()
                .filter(t -> TaskStatus.FAILED.is(t.getStatus()))
                .count();
        long skipped = tasks.stream()
                .filter(t -> FailReasonCategory.SKIPPED_DUE_TO_PRIOR_FAILURE.is(t.getFailReasonCategory()))
                .count();
        return new Statistics(success, failed, skipped);
    }

    private JobStatus determineStatus(String jobId, Statistics stats) {
        if (cancelChecker.isCancelRequested(jobId)) {
            return JobStatus.CANCELED;
        } else if (stats.failedCount == 0) {
            return JobStatus.SUCCESS;
        } else if (stats.successCount == 0) {
            return JobStatus.FAILED;
        } else {
            return JobStatus.PARTIAL_FAILED;
        }
    }

    private void emitSummaryLog(String jobId, LogBuffer jobLog, JobStatus status,
                                Statistics stats, Date submitTime, Date finishTime) {
        String totalDur = LogTiming.fmt(submitTime, finishTime);
        if (status == JobStatus.CANCELED) {
            jobLog.append("[executor] [INFO] job " + jobId + " canceled by request"
                    + (totalDur != null ? " (" + totalDur + ")" : ""));
        } else {
            jobLog.append("[executor] [INFO] job " + jobId + " done, "
                    + LogTiming.doneSummary(stats.successCount, stats.failedCount, stats.skippedCount)
                    + (totalDur != null ? " (" + totalDur + ")" : ""));
        }
    }

    private void emitTimingLog(String jobId, LogBuffer jobLog, Date submitTime,
                               Date dispatchTime, long jobStartMs, Date finishTime) {
        jobLog.append("[executor] [INFO] [job=" + jobId + "] timing: "
                + LogTiming.jobTiming(submitTime, dispatchTime, jobStartMs, finishTime));
    }

    /** 统计结果 */
    public static class Statistics {
        public final long successCount;
        public final long failedCount;
        public final long skippedCount;

        public Statistics(long success, long failed, long skipped) {
            this.successCount = success;
            this.failedCount = failed;
            this.skippedCount = skipped;
        }
    }
}