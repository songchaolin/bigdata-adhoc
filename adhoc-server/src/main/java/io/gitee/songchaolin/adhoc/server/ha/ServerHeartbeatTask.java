package io.gitee.songchaolin.adhoc.server.ha;

import io.gitee.songchaolin.adhoc.common.config.ConfigHolder;
import io.gitee.songchaolin.adhoc.common.model.JvmMetrics;
import io.gitee.songchaolin.adhoc.dao.entity.AdhocJvmMetricSample;
import io.gitee.songchaolin.adhoc.dao.mapper.AdhocJvmMetricSampleMapper;
import io.gitee.songchaolin.adhoc.dao.mapper.AdhocServerInstanceMapper;
import io.gitee.songchaolin.adhoc.server.config.AdhocServerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * server 自心跳：定时采集本进程 JVM 指标并自写 adhoc_server_instance（heartbeat_time + status=UP + 指标），
 * 避免 HealthCheckTask（15s 超时）误标 DOWN，同时为大盘提供 server 端 JVM 监控数据。
 * server 与 executor 共用 {@link JvmMetrics} 采集；executor 靠自身心跳任务自写 executor 表，server 本地自写 server 表。
 * accepting 不动（由 GracefulShutdown 控制）。
 */
@Component
public class ServerHeartbeatTask {

    private static final Logger log = LoggerFactory.getLogger(ServerHeartbeatTask.class);
    private final AdhocServerInstanceMapper serverInstanceMapper;
    private final AdhocJvmMetricSampleMapper sampleMapper;
    private final ServerInstanceInfo instanceInfo;
    private final ConfigHolder cfg;

    private ScheduledExecutorService scheduler;

    public ServerHeartbeatTask(AdhocServerInstanceMapper serverInstanceMapper,
                               AdhocJvmMetricSampleMapper sampleMapper,
                               ServerInstanceInfo instanceInfo,
                               ConfigHolder cfg) {
        this.serverInstanceMapper = serverInstanceMapper;
        this.sampleMapper = sampleMapper;
        this.instanceInfo = instanceInfo;
        this.cfg = cfg;
    }

    public void start() {
        long intervalMs = cfg.get(AdhocServerConfig.SERVER_HEARTBEAT_INTERVAL_MS);
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "server-heartbeat");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleWithFixedDelay(() -> {
            try { heartbeat(); } catch (Throwable t) { log.warn("server-heartbeat caught throwable: {}", t.getMessage()); }
        }, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
        log.info("server-heartbeat started, interval={}ms", intervalMs);
    }

    public void heartbeat() {
        try {
            // 采本进程 JVM 指标（MXBean 不可用值内部钳 0/null，不抛）
            JvmMetrics metrics = JvmMetrics.collect();
            String id = instanceInfo.getId();
            // 自写实例表（heartbeat_time + status=UP + 指标）；失败只 warn，不阻塞采样历史
            try {
                serverInstanceMapper.updateSelfWithMetrics(id, metrics);
            } catch (Exception e) {
                log.warn("server instance self-write failed: {}", e.getMessage());
            }
            // 写采样历史（全量字段，供大盘时间曲线查询）；独立于实例表，失败只 warn 不影响心跳
            try {
                sampleMapper.insert(AdhocJvmMetricSample.of(id, "SERVER", 0, metrics));
            } catch (Exception e) {
                log.warn("server sample insert failed: {}", e.getMessage());
            }
        } catch (Throwable t) {
            log.warn("server self-heartbeat caught: {}", t.getMessage());
        }
    }

    public void stop() {
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }
}
