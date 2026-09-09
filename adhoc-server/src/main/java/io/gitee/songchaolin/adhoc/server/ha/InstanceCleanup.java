package io.gitee.songchaolin.adhoc.server.ha;

import io.gitee.songchaolin.adhoc.common.config.ConfigHolder;
import io.gitee.songchaolin.adhoc.dao.mapper.AdhocExecutorInstanceMapper;
import io.gitee.songchaolin.adhoc.dao.mapper.AdhocServerInstanceMapper;
import io.gitee.songchaolin.adhoc.server.config.AdhocServerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 实例清理：定时删除 DOWN 超过 24 小时的实例（无归档表，物理删除）。
 */
@Component
public class InstanceCleanup {

    private static final Logger log = LoggerFactory.getLogger(InstanceCleanup.class);
    private final AdhocExecutorInstanceMapper executorMapper;
    private final AdhocServerInstanceMapper serverMapper;
    private final ConfigHolder cfg;

    private ScheduledExecutorService scheduler;

    public InstanceCleanup(AdhocExecutorInstanceMapper executorMapper, AdhocServerInstanceMapper serverMapper,
                           ConfigHolder cfg) {
        this.executorMapper = executorMapper;
        this.serverMapper = serverMapper;
        this.cfg = cfg;
    }

    public void start() {
        long intervalMs = cfg.get(AdhocServerConfig.CLEANUP_INTERVAL_MS);
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "instance-cleanup");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleWithFixedDelay(() -> {
            try { cleanup(); } catch (Throwable t) { log.warn("instance-cleanup caught throwable: {}", t.getMessage()); }
        }, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
        log.info("instance-cleanup started, interval={}ms", intervalMs);
    }

    public void cleanup() {
        int execs = executorMapper.cleanupOldDownInstances(24);
        int servers = serverMapper.cleanupOldDownInstances(24);
        if (execs + servers > 0) {
            log.info("cleaned up {} executor + {} server DOWN instances (>24h)", execs, servers);
        }
    }

    public void stop() {
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }
}
