package io.gitee.songchaolin.adhoc.server.ha;

import io.gitee.songchaolin.adhoc.common.config.ConfigHolder;
import io.gitee.songchaolin.adhoc.dao.mapper.AdhocQueryJobMapper;
import io.gitee.songchaolin.adhoc.dao.mapper.AdhocQueryTaskMapper;
import io.gitee.songchaolin.adhoc.server.config.AdhocServerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * executor 宕机补偿：扫 DOWN executor 的 RUNNING/PENDING Task -> 标 FAILED
 *（RUNNING=EXECUTOR_CRASHED, PENDING=SKIPPED_DUE_TO_SESSION_LOSS）+ Job FAILED。
 *
 * <p>延迟一轮确认：首轮 DOWN 记入 pendingDownExecs 不标 FAILED（防 30s 心跳抖动/GC 误判），
 * 第二轮仍 DOWN 才标 FAILED。executor 恢复 UP 后从 pendingDownExecs 清除。
 */
@Component
public class ExecutorCrashCompensation {

    private static final Logger log = LoggerFactory.getLogger(ExecutorCrashCompensation.class);
    private final AdhocQueryTaskMapper taskMapper;
    private final AdhocQueryJobMapper jobMapper;
    /** 首轮 DOWN 待确认的 executor（延迟一轮标 FAILED，防抖动误判）。 */
    private final Set<String> pendingDownExecs = ConcurrentHashMap.newKeySet();
    private final ConfigHolder cfg;

    private ScheduledExecutorService scheduler;

    public ExecutorCrashCompensation(AdhocQueryTaskMapper taskMapper, AdhocQueryJobMapper jobMapper,
                                     ConfigHolder cfg) {
        this.taskMapper = taskMapper;
        this.jobMapper = jobMapper;
        this.cfg = cfg;
    }

    public void start() {
        long intervalMs = cfg.get(AdhocServerConfig.COMPENSATION_INTERVAL_MS);
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "executor-crash-comp");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleWithFixedDelay(() -> {
            try { compensate(); } catch (Throwable t) { log.warn("executor-crash-comp caught throwable: {}", t.getMessage()); }
        }, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
        log.info("executor-crash-comp started, interval={}ms", intervalMs);
    }

    public void compensate() {
        Set<String> downExecs = new HashSet<>(taskMapper.selectDownExecutorsWithActiveTasks());
        Set<String> toFail = new HashSet<>();
        for (String exec : downExecs) {
            if (pendingDownExecs.contains(exec)) {
                toFail.add(exec);  // 第二轮仍 DOWN -> 确认宕机，标 FAILED
            } else {
                pendingDownExecs.add(exec);  // 首轮 DOWN -> 延迟一轮确认（防 30s 抖动误判）
            }
        }
        pendingDownExecs.retainAll(downExecs);  // 清理已恢复 UP 的
        pendingDownExecs.removeAll(toFail);
        for (String exec : toFail) {
            int running = taskMapper.markRunningTasksFailedForExecutor(exec);
            int pending = taskMapper.markPendingTasksFailedForExecutor(exec);
            int jobs = jobMapper.markJobsFailedForExecutor(exec);
            if (running + pending + jobs > 0) {
                log.warn("compensated DOWN executor {} (confirmed after grace): {} RUNNING + {} PENDING tasks FAILED, {} jobs FAILED",
                        exec, running, pending, jobs);
            }
        }
    }

    public void stop() {
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }
}
