package io.gitee.songchaolin.adhoc.server.ha;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.gitee.songchaolin.adhoc.common.enums.JobStatus;
import io.gitee.songchaolin.adhoc.dao.entity.AdhocExecutorInstance;
import io.gitee.songchaolin.adhoc.dao.entity.AdhocQueryJob;
import io.gitee.songchaolin.adhoc.dao.mapper.AdhocExecutorInstanceMapper;
import io.gitee.songchaolin.adhoc.dao.mapper.AdhocQueryJobMapper;
import io.gitee.songchaolin.adhoc.common.config.AdhocCommonConfig;
import io.gitee.songchaolin.adhoc.common.config.ConfigHolder;
import io.gitee.songchaolin.adhoc.server.config.AdhocServerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * server 状态播报（每 adhoc.status.interval-ms，默认 60s）：本实例 + 各状态 Job 计数（PENDING/DISPATCHING/RUNNING）
 * + 本 server processing 数 + executor UP/DOWN + 限流阈值。便于排查/监控。专用后台线程，由 AdhocServerServiceStarter 启停。
 */
@Component
public class ServerStatusReporter {

    private static final Logger log = LoggerFactory.getLogger(ServerStatusReporter.class);

    private final AdhocQueryJobMapper jobMapper;
    private final AdhocExecutorInstanceMapper executorMapper;
    private final ServerInstanceInfo instanceInfo;
    private final ConfigHolder cfg;

    private long intervalMs;
    private ScheduledExecutorService scheduler;

    public ServerStatusReporter(AdhocQueryJobMapper jobMapper, AdhocExecutorInstanceMapper executorMapper,
                                ServerInstanceInfo instanceInfo, ConfigHolder cfg) {
        this.jobMapper = jobMapper;
        this.executorMapper = executorMapper;
        this.instanceInfo = instanceInfo;
        this.cfg = cfg;
        this.intervalMs = cfg.get(AdhocCommonConfig.STATUS_INTERVAL_MS);
    }

    public void start() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "server-status");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleWithFixedDelay(() -> {
            try { report(); } catch (Throwable t) { log.warn("server-status caught: {}", t.getMessage()); }
        }, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
        log.info("server-status-reporter started, interval={}ms", intervalMs);
    }

    public void stop() {
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }

    void report() {
        long pending = countJobByStatus(JobStatus.PENDING.name());
        long dispatching = countJobByStatus(JobStatus.DISPATCHING.name());
        long running = countJobByStatus(JobStatus.RUNNING.name());
        long processing = jobMapper.selectCount(new LambdaQueryWrapper<AdhocQueryJob>()
                .eq(AdhocQueryJob::getProcessingServerInstance, instanceInfo.getId())
                .in(AdhocQueryJob::getStatus, JobStatus.DISPATCHING.name(), JobStatus.RUNNING.name()));
        long execUp = executorMapper.selectCount(new LambdaQueryWrapper<AdhocExecutorInstance>()
                .eq(AdhocExecutorInstance::getStatus, "UP"));
        long execDown = executorMapper.selectCount(new LambdaQueryWrapper<AdhocExecutorInstance>()
                .eq(AdhocExecutorInstance::getStatus, "DOWN"));
        log.info("[status] server={} | jobs PENDING={} DISPATCHING={} RUNNING={} (processing this={}/{}) | executors UP={} DOWN={} | limits running global={}/perUser={}/perServer={}",
                instanceInfo.getId(), pending, dispatching, running, processing, cfg.get(AdhocServerConfig.MAX_RUNNING_JOBS_PER_SERVER),
                execUp, execDown,
                cfg.get(AdhocServerConfig.MAX_RUNNING_JOBS_GLOBAL), cfg.get(AdhocServerConfig.MAX_RUNNING_JOBS_PER_USER), cfg.get(AdhocServerConfig.MAX_RUNNING_JOBS_PER_SERVER));
    }

    private long countJobByStatus(String status) {
        return jobMapper.selectCount(new LambdaQueryWrapper<AdhocQueryJob>()
                .eq(AdhocQueryJob::getStatus, status));
    }
}
