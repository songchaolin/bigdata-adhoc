package io.gitee.songchaolin.adhoc.executor.ha;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gitee.songchaolin.adhoc.common.model.JvmMetrics;
import io.gitee.songchaolin.adhoc.dao.entity.AdhocJvmMetricSample;
import io.gitee.songchaolin.adhoc.dao.entity.AdhocServerInstance;
import io.gitee.songchaolin.adhoc.dao.mapper.AdhocExecutorInstanceMapper;
import io.gitee.songchaolin.adhoc.dao.mapper.AdhocJvmMetricSampleMapper;
import io.gitee.songchaolin.adhoc.dao.mapper.AdhocServerInstanceMapper;
import io.gitee.songchaolin.adhoc.common.config.ConfigHolder;
import io.gitee.songchaolin.adhoc.executor.config.AdhocExecutorConfig;
import io.gitee.songchaolin.adhoc.internal.grpc.v1.HeartbeatRequest;
import io.gitee.songchaolin.adhoc.internal.grpc.v1.HeartbeatServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * executor 心跳（对齐 HA spec §4.3）：专用后台线程（ScheduledExecutorService，daemon），不占 Spring 共享 @Scheduled 池。
 * ① JvmMetrics.collect 采本进程指标
 * ② executorInstanceMapper.updateSelf 自写 adhoc_executor_instance（status=UP + 指标 + running_tasks + heartbeat_time）-- 不依赖 server
 * ③ gRPC 心跳到 UP server，只做 Task 对账（TASK_LOST），失败转移，带 deadline。
 * 自写先于 gRPC -- gRPC 全失败也不影响 status=UP。
 * 由 AdhocExecutorServiceStarter 调 start() 启动线程 / stop() 关 scheduler + channels。
 */
@Component
public class ExecutorHeartbeatTask {

    private static final Logger log = LoggerFactory.getLogger(ExecutorHeartbeatTask.class);
    private static final int DEFAULT_SERVER_GRPC_PORT = 9090;
    private static final long GRPC_DEADLINE_SEC = 5;

    private final RunningTaskRegistry runningTaskRegistry;
    private final AdhocExecutorInstanceMapper executorInstanceMapper;
    private final AdhocServerInstanceMapper serverInstanceMapper;
    private final AdhocJvmMetricSampleMapper sampleMapper;
    private final ExecutorInstanceInfo instanceInfo;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ConcurrentHashMap<String, ManagedChannel> channels = new ConcurrentHashMap<>();

    private final long intervalMs;

    private ScheduledExecutorService scheduler;

    public ExecutorHeartbeatTask(RunningTaskRegistry runningTaskRegistry,
                                 AdhocExecutorInstanceMapper executorInstanceMapper,
                                 AdhocServerInstanceMapper serverInstanceMapper,
                                 AdhocJvmMetricSampleMapper sampleMapper,
                                 ExecutorInstanceInfo instanceInfo,
                                 ConfigHolder cfg) {
        this.runningTaskRegistry = runningTaskRegistry;
        this.executorInstanceMapper = executorInstanceMapper;
        this.serverInstanceMapper = serverInstanceMapper;
        this.sampleMapper = sampleMapper;
        this.instanceInfo = instanceInfo;
        this.intervalMs = cfg.get(AdhocExecutorConfig.HEARTBEAT_INTERVAL_MS);
    }

    /** 启动专用心跳线程（daemon，独立于 Spring @Scheduled 池）。 */
    public void start() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "executor-heartbeat");
            t.setDaemon(true);
            return t;
        });
        // scheduleWithFixedDelay：若 task 抛异常会静默停止后续执行，故外层兜底 catch Throwable
        scheduler.scheduleWithFixedDelay(() -> {
            try {
                heartbeat();
            } catch (Throwable t) {
                log.warn("heartbeat thread caught throwable: {}", t.getMessage());
            }
        }, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
        log.info("executor heartbeat thread started, interval={}ms", intervalMs);
    }

    public void heartbeat() {
        try {
            String id = instanceInfo.getId();
            List<String> taskIds = new ArrayList<>(runningTaskRegistry.getIds());
            String runningTasksJson;
            try {
                runningTasksJson = objectMapper.writeValueAsString(taskIds);
            } catch (Exception e) {
                runningTasksJson = "[]";
            }
            JvmMetrics metrics = JvmMetrics.collect(taskIds.size());

            // ① 自写自己的实例表行（status=UP + 指标 + running_tasks + heartbeat_time）；失败只 warn，不阻塞采样历史
            try {
                executorInstanceMapper.updateSelf(id, runningTasksJson, metrics);
            } catch (Exception e) {
                log.warn("executor instance self-write failed: {}", e.getMessage());
            }

            // ①.bis 写采样历史（全量字段，供大盘时间曲线查询）；独立于实例表，失败只 warn
            try {
                sampleMapper.insert(AdhocJvmMetricSample.of(id, "EXECUTOR", taskIds.size(), metrics));
            } catch (Exception ex) {
                log.warn("executor sample insert failed: {}", ex.getMessage());
            }

            // ② gRPC 心跳到 UP server，做 Task 对账（TASK_LOST）
            List<AdhocServerInstance> servers = serverInstanceMapper.selectUpInstances();
            if (servers.isEmpty()) {
                log.debug("heartbeat: no UP server for reconcile (self-write done)");
                return;
            }
            HeartbeatRequest req = HeartbeatRequest.newBuilder()
                    .setInstanceId(id)
                    .setInstanceType("EXECUTOR")
                    .setHost(instanceInfo.getHost())
                    .setGrpcPort(instanceInfo.getGrpcPort())
                    .setLoadScore(metrics.getLoadScore() == null ? 0.0 : metrics.getLoadScore())
                    .addAllRunningTaskIds(taskIds)
                    .build();
            for (AdhocServerInstance s : servers) {
                int serverPort = s.getGrpcPort() == null ? DEFAULT_SERVER_GRPC_PORT : s.getGrpcPort();
                try {
                    HeartbeatServiceGrpc.HeartbeatServiceBlockingStub stub =
                            HeartbeatServiceGrpc.newBlockingStub(getChannel(s.getHost(), serverPort))
                                    .withDeadlineAfter(GRPC_DEADLINE_SEC, TimeUnit.SECONDS);
                    stub.heartbeat(req);
                    return; // 一个 server 对账成功即返回
                } catch (Exception e) {
                    log.warn("heartbeat reconcile to server {} ({}:{}) failed: {}, try next",
                            s.getInstanceId(), s.getHost(), serverPort, e.getMessage());
                    closeChannel(s.getHost(), serverPort);
                }
            }
            log.warn("heartbeat reconcile: all {} UP server(s) unreachable (self-write done)", servers.size());
        } catch (Exception e) {
            log.warn("heartbeat error: {}", e.getMessage());
        }
    }

    private ManagedChannel getChannel(String host, int port) {
        return channels.computeIfAbsent(host + ":" + port,
                // dns:/// 强制 DnsNameResolver 直连（server 地址即 host:port，无服务名解析）
                k -> ManagedChannelBuilder.forTarget("dns:///" + host + ":" + port).usePlaintext().build());
    }

    private void closeChannel(String host, int port) {
        ManagedChannel c = channels.remove(host + ":" + port);
        if (c != null) {
            c.shutdown();
        }
    }

    public void stop() {
        if (scheduler != null) {
            scheduler.shutdown();
        }
        channels.values().forEach(ManagedChannel::shutdown);
        channels.clear();
    }
}
