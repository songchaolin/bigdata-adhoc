package io.gitee.songchaolin.adhoc.executor.ha;

import io.gitee.songchaolin.adhoc.common.model.JvmMetrics;
import io.gitee.songchaolin.adhoc.common.config.AdhocCommonConfig;
import io.gitee.songchaolin.adhoc.common.config.ConfigHolder;
import io.gitee.songchaolin.adhoc.executor.grpc.JobExecutorServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * executor 状态播报（每 adhoc.status.interval-ms，默认 60s）：本实例 + 运行中 Job 数（Semaphore 占用/上限）
 * + 运行中 task 数 + 本进程负载（CPU/内存/线程/loadScore）。便于排查/监控。专用后台线程，由 AdhocExecutorServiceStarter 启停。
 */
@Component
public class ExecutorStatusReporter {

    private static final Logger log = LoggerFactory.getLogger(ExecutorStatusReporter.class);

    private final ExecutorInstanceInfo instanceInfo;
    private final JobExecutorServiceImpl jobExecutorService;
    private final RunningTaskRegistry runningTaskRegistry;

    private final long intervalMs;
    private ScheduledExecutorService scheduler;

    public ExecutorStatusReporter(ExecutorInstanceInfo instanceInfo,
                                  JobExecutorServiceImpl jobExecutorService,
                                  RunningTaskRegistry runningTaskRegistry,
                                  ConfigHolder cfg) {
        this.instanceInfo = instanceInfo;
        this.jobExecutorService = jobExecutorService;
        this.runningTaskRegistry = runningTaskRegistry;
        this.intervalMs = cfg.get(AdhocCommonConfig.STATUS_INTERVAL_MS);
    }

    public void start() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "executor-status");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleWithFixedDelay(() -> {
            try { report(); } catch (Throwable t) { log.warn("executor-status caught: {}", t.getMessage()); }
        }, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
        log.info("executor-status-reporter started, interval={}ms", intervalMs);
    }

    public void stop() {
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }

    void report() {
        int runningJobs = jobExecutorService.runningJobCount();
        int maxJobs = jobExecutorService.getMaxConcurrentTasks();
        int runningTasks = runningTaskRegistry.getIds().size();
        JvmMetrics m = JvmMetrics.collect(runningTasks);
        log.info("[status] executor={} | running_jobs={}/{} running_tasks={} | cpu={}%(sys={}%) mem={}%({}/{}MB) threads={} load={}",
                instanceInfo.getId(), runningJobs, maxJobs, runningTasks,
                fmt(m.getCpuUsagePct()), fmt(m.getSystemCpuUsagePct()), fmt(m.getMemoryUsagePct()),
                m.getMemoryUsedMb(), m.getMemoryMaxMb(), m.getThreadCount(), fmt(m.getLoadScore()));
    }

    private static String fmt(Double d) {
        return d == null ? "-" : String.format("%.1f", d);
    }
}
