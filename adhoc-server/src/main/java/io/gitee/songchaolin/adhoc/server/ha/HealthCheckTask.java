package io.gitee.songchaolin.adhoc.server.ha;

import io.gitee.songchaolin.adhoc.dao.mapper.AdhocExecutorInstanceMapper;
import io.gitee.songchaolin.adhoc.dao.mapper.AdhocServerInstanceMapper;
import io.gitee.songchaolin.adhoc.server.config.AdhocServerConfig;
import io.gitee.songchaolin.adhoc.common.config.ConfigHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * server 健康检查：定时扫心跳超时的实例 -> CAS 标 DOWN（executor 30s / server 15s）。
 * executor DOWN 后的 Task 补偿见 ExecutorCrashCompensation（P4-T3）。
 */
@Component
public class HealthCheckTask {

    private static final Logger log = LoggerFactory.getLogger(HealthCheckTask.class);
    private final AdhocExecutorInstanceMapper executorMapper;
    private final AdhocServerInstanceMapper serverMapper;
    private final ConfigHolder cfg;

    private ScheduledExecutorService scheduler;

    public HealthCheckTask(AdhocExecutorInstanceMapper executorMapper, AdhocServerInstanceMapper serverMapper,
                           ConfigHolder cfg) {
        this.executorMapper = executorMapper;
        this.serverMapper = serverMapper;
        this.cfg = cfg;
    }

    public void start() {
        long intervalMs = cfg.get(AdhocServerConfig.HEALTHCHECK_INTERVAL_MS);
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "health-check");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleWithFixedDelay(() -> {
            try { check(); } catch (Throwable t) { log.warn("health-check caught throwable: {}", t.getMessage()); }
        }, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
        log.info("health-check started, interval={}ms", intervalMs);
    }

    public void check() {
        int execDown = executorMapper.markDownTimedOut(30);
        int serverDown = serverMapper.markDownTimedOut(15);
        if (execDown > 0) {
            log.warn("marked {} executor(s) DOWN (heartbeat timeout 30s)", execDown);
        }
        if (serverDown > 0) {
            log.warn("marked {} server(s) DOWN (heartbeat timeout 15s)", serverDown);
        }
    }

    public void stop() {
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }
}
