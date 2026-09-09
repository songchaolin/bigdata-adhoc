package io.gitee.songchaolin.adhoc.server.ha;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import io.gitee.songchaolin.adhoc.common.config.ConfigHolder;
import io.gitee.songchaolin.adhoc.common.enums.JobStatus;
import io.gitee.songchaolin.adhoc.common.model.TaskStats;
import io.gitee.songchaolin.adhoc.common.util.LogConstants;
import io.gitee.songchaolin.adhoc.dao.entity.AdhocQueryJob;
import io.gitee.songchaolin.adhoc.dao.mapper.AdhocQueryJobMapper;
import io.gitee.songchaolin.adhoc.internal.grpc.v1.FetchJobLogResponse;
import io.gitee.songchaolin.adhoc.server.config.AdhocServerConfig;
import io.gitee.songchaolin.adhoc.server.grpc.ExecutorChannelPool;
import io.gitee.songchaolin.adhoc.storage.spi.StorageClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Job 日志 collector（共享线程，每 2s）：对每个活跃 job
 * ① fetchJobLog 拉 executor job-link 新增 -> append server JobLog
 * ② 有新增 -> upload 全量 OSS（覆盖）
 * ③ job 终态 -> remove
 * 刷写在 collector 线程，不阻业务（业务只 append 内存）。
 */
@Component
public class JobLogCollector {
    private static final Logger log = LoggerFactory.getLogger(JobLogCollector.class);
    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final JobLogRegistry registry;
    private final AdhocQueryJobMapper jobMapper;
    private final ExecutorChannelPool executorChannelPool;
    private final StorageClient storageClient;
    private final RunningJobRegistry runningJobRegistry;
    private final ConfigHolder cfg;

    private ScheduledExecutorService scheduler;

    public JobLogCollector(JobLogRegistry registry, AdhocQueryJobMapper jobMapper,
                           ExecutorChannelPool executorChannelPool, StorageClient storageClient,
                           RunningJobRegistry runningJobRegistry, ConfigHolder cfg) {
        this.registry = registry; this.jobMapper = jobMapper;
        this.executorChannelPool = executorChannelPool; this.storageClient = storageClient;
        this.runningJobRegistry = runningJobRegistry;
        this.cfg = cfg;
    }

    public void start() {
        long intervalMs = cfg.get(AdhocServerConfig.LOG_COLLECT_INTERVAL_MS);
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "job-log-collector"); t.setDaemon(true); return t;
        });
        scheduler.scheduleWithFixedDelay(() -> {
            try { collect(); } catch (Throwable t) { log.warn("job-log-collector caught: {}", t.getMessage()); }
        }, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
        log.info("job-log-collector started, interval={}ms", intervalMs);
    }

    void collect() {
        for (String jobId : registry.activeJobIds()) {
            try {
                AdhocQueryJob job = jobMapper.selectById(jobId);
                if (job == null) { registry.remove(jobId); continue; }
                boolean terminal = JobStatus.isTerminal(job.getStatus());

                // ① 拉 executor job-link
                if (job.getExecutorInstance() != null) {
                    try {
                        long offset = registry.getLastPulledOffset(jobId);
                        FetchJobLogResponse resp = executorChannelPool.fetchJobLog(job.getExecutorInstance(), jobId, offset, 10000);
                        List<String> newLines = resp.getLinesList();
                        if (!newLines.isEmpty()) {
                            registry.appendAll(jobId, newLines);
                            registry.setLastPulledOffset(jobId, resp.getNextOffset());
                        }
                    } catch (Exception e) {
                        // executor 不可达，跳过本轮拉取（server 事件已有）
                    }
                }

                // ② 终态轮 append 完整标识（flush 前），让 executor 兜底据此判断 server 已完整写
                JobLog jobLog = registry.get(jobId);
                // ② 终态：服务端日志打印 + 追加 COMPLETE_MARKER_LINE
                if (terminal && jobLog != null) {
                    TaskStats stats = jobMapper.selectTaskStatsByJobId(jobId);
                    long total = stats.getTotal() != null ? stats.getTotal() : 0L;
                    long success = stats.getSuccess() != null ? stats.getSuccess() : 0L;
                    long failed = stats.getFailed() != null ? stats.getFailed() : 0L;          // 含跳过的总失败数
                    long canceled = stats.getCanceled() != null ? stats.getCanceled() : 0L;
                    long skipped = stats.getSkipped() != null ? stats.getSkipped() : 0L;
                    long realFailed = stats.getRealFailed() != null ? stats.getRealFailed() : 0L;

                    String status = job.getStatus();
                    String duration = formatDuration(job.getSubmitTime(), job.getFinishTime());
                    String executorInstance = job.getExecutorInstance();

                    // ✅ 只打印到服务端日志（运维/排查用）
                    log.info("【Job完成】jobId={} status=【{}】 duration={} tasks=总数={}, 成功={}, 失败={}, 取消={}, 跳过={} (含跳过总失败={}) executor={}",
                            jobId, status, duration, total, success, realFailed, canceled, skipped, failed, executorInstance);
                }
                if (terminal && jobLog != null) {
                    jobLog.append(LogConstants.COMPLETE_MARKER_LINE);
                }

                // ③ 有新增或终态标识则刷 OSS（覆盖全量，末尾标识）
                boolean flushedOk = false;
                if (jobLog != null && jobLog.lineCount() > registry.getLastFlushedLines(jobId)) {
                    String key = uploadJobLog(jobId, jobLog.snapshot());
                    if (key != null) {
                        jobMapper.update(null, new LambdaUpdateWrapper<AdhocQueryJob>()
                                .eq(AdhocQueryJob::getJobId, jobId)
                                .set(AdhocQueryJob::getPersistentLogPath, key));
                        registry.setLastFlushedLines(jobId, jobLog.lineCount());
                        flushedOk = true;
                    }
                }

                // ④ 终态：flush 成功（OSS 完整含标识）才通知 executor 清理；失败留给 executor 兜底补全
                if (terminal) {
                    if (flushedOk && job.getExecutorInstance() != null) {
                        try {
                            executorChannelPool.clearJobLog(job.getExecutorInstance(), jobId);
                        } catch (Exception e) {
                            log.warn("clearJobLog notify failed for {}: {}", jobId, e.getMessage());
                        }
                    }
                    registry.remove(jobId);
                    runningJobRegistry.remove(jobId);
                    log.info("[job={}] log collector done (terminal){}", jobId,
                            flushedOk ? ", notified executor to clear" : ", flush failed, leave to executor fallback");
                }
            } catch (Exception e) {
                log.warn("job log collect for {} failed: {}", jobId, e.getMessage());
            }
        }
    }

    private String formatDuration(Date submitTime, Date finishTime) {
        if (submitTime == null || finishTime == null) {
            return "N/A";
        }
        long diffMs = finishTime.getTime() - submitTime.getTime();
        long seconds = diffMs / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long remainingMinutes = minutes % 60;
        long remainingSeconds = seconds % 60;

        if (hours > 0) {
            return String.format("%dh %dm %ds", hours, remainingMinutes, remainingSeconds);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, remainingSeconds);
        } else {
            return String.format("%dms", diffMs);
        }
    }

    private String uploadJobLog(String jobId, List<String> lines) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (String l : lines) {
            byte[] b = l.getBytes(StandardCharsets.UTF_8);
            out.write(b, 0, b.length); out.write('\n');
        }
        String fileName = LocalDate.now().format(DAY_FMT) + "/" + jobId + "/job.log";
        try {
            return storageClient.uploadLog(fileName, out.toByteArray());
        } catch (Exception e) {
            log.warn("job log upload failed for {}: {}", jobId, e.getMessage());
            return null;
        }
    }

    public void stop() { if (scheduler != null) scheduler.shutdown(); }
}
