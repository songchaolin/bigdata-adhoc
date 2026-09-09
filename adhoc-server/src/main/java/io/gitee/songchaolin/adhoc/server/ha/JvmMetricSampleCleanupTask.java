package io.gitee.songchaolin.adhoc.server.ha;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import io.gitee.songchaolin.adhoc.common.config.ConfigHolder;
import io.gitee.songchaolin.adhoc.dao.entity.AdhocJvmMetricSample;
import io.gitee.songchaolin.adhoc.dao.mapper.AdhocJvmMetricSampleMapper;
import io.gitee.songchaolin.adhoc.server.config.AdhocServerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * JVM 采样历史清理：定时删除 {@code adhoc_jvm_metric_sample} 中超保留期的行。
 * <p>只在 server 侧跑（单类定时任务）；多 server 并发执行 DELETE 幂等（删已删行=0），不引分布式锁，对齐 CAS 原则。
 * <p>专用后台线程（daemon），不占 Spring 共享 @Scheduled 池，与 {@link InstanceCleanup} 同构。
 */
@Component
public class JvmMetricSampleCleanupTask {

    private static final Logger log = LoggerFactory.getLogger(JvmMetricSampleCleanupTask.class);
    private final AdhocJvmMetricSampleMapper sampleMapper;
    private final ConfigHolder cfg;

    private ScheduledExecutorService scheduler;

    public JvmMetricSampleCleanupTask(AdhocJvmMetricSampleMapper sampleMapper, ConfigHolder cfg) {
        this.sampleMapper = sampleMapper;
        this.cfg = cfg;
    }

    public void start() {
        long intervalMs = cfg.get(AdhocServerConfig.JVM_METRIC_CLEANUP_INTERVAL_MS);
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "jvm-sample-cleanup");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleWithFixedDelay(() -> {
            try { cleanup(); } catch (Throwable t) { log.warn("jvm-sample-cleanup caught throwable: {}", t.getMessage()); }
        }, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
        log.info("jvm-sample-cleanup started, interval={}ms", intervalMs);
    }

    public void cleanup() {
        int hours = cfg.get(AdhocServerConfig.JVM_METRIC_RETENTION_HOURS);
        Date before = new Date(System.currentTimeMillis() - hours * 3_600_000L);
        int n = sampleMapper.delete(new LambdaUpdateWrapper<AdhocJvmMetricSample>()
                .lt(AdhocJvmMetricSample::getSampleTime, before));
        if (n > 0) {
            log.info("cleaned up {} jvm metric samples older than {}h", n, hours);
        }
    }

    public void stop() {
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }
}
