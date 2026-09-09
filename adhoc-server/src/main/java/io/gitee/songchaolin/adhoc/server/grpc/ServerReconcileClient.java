package io.gitee.songchaolin.adhoc.server.grpc;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.gitee.songchaolin.adhoc.common.enums.InstanceStatus;
import io.gitee.songchaolin.adhoc.dao.entity.AdhocServerInstance;
import io.gitee.songchaolin.adhoc.dao.mapper.AdhocServerInstanceMapper;
import io.gitee.songchaolin.adhoc.internal.grpc.v1.ServerJobLogRequest;
import io.gitee.songchaolin.adhoc.internal.grpc.v1.ServerJobLogResponse;
import io.gitee.songchaolin.adhoc.internal.grpc.v1.ServerReconcileServiceGrpc;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalNotification;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status.Code;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * server->server gRPC client（job 日志转发）：非处理节点查 running job log 时，转发到 processing_server 节点读内存。
 * channel 用 guava Cache 管理（expireAfterAccess 10min + maximumSize 100，空闲/超量自动淘汰+shutdown）；
 * 连接类失败（UNAVAILABLE/DEADLINE_EXCEEDED）invalidate 关坏 channel。调用带 deadline，防目标 server 不可达阻塞。
 */
@Component
public class ServerReconcileClient {

    private static final Logger log = LoggerFactory.getLogger(ServerReconcileClient.class);
    private static final int DEFAULT_SERVER_GRPC_PORT = 9090;
    private static final long FETCH_JOB_LOG_DEADLINE_SEC = 5;
    private static final long CHANNEL_EXPIRE_MIN = 10;
    private static final int CHANNEL_MAX_SIZE = 100;

    private final AdhocServerInstanceMapper serverMapper;
    private final Cache<String, ManagedChannel> channels = CacheBuilder.newBuilder()
            .expireAfterAccess(CHANNEL_EXPIRE_MIN, TimeUnit.MINUTES)
            .maximumSize(CHANNEL_MAX_SIZE)
            .removalListener((RemovalNotification<String, ManagedChannel> n) -> {
                ManagedChannel ch = n.getValue();
                if (ch != null && !ch.isShutdown()) {
                    ch.shutdown(); // graceful：让并发活跃调用跑完
                }
            })
            .build();

    public ServerReconcileClient(AdhocServerInstanceMapper serverMapper) {
        this.serverMapper = serverMapper;
    }

    /** fetchJobLog：转发到 processing_server 节点读 job 内存日志。找不到/非 UP/失败抛异常（LogQueryService 兜底 OSS）。 */
    public ServerJobLogResponse fetchJobLog(String serverInstanceId, String jobId, long offset, int limit) {
        AdhocServerInstance server = serverMapper.selectOne(new LambdaQueryWrapper<AdhocServerInstance>()
                .eq(AdhocServerInstance::getInstanceId, serverInstanceId)
                .eq(AdhocServerInstance::getStatus, InstanceStatus.UP.name()));
        if (server == null) {
            throw new RuntimeException("server not found or not UP: " + serverInstanceId);
        }
        String host = server.getHost();
        if (host == null || host.isEmpty()) {
            throw new RuntimeException("server host is null/empty: " + serverInstanceId);
        }
        int port = server.getGrpcPort() == null ? DEFAULT_SERVER_GRPC_PORT : server.getGrpcPort();
        ManagedChannel ch = getChannel(host, port);
        try {
            return ServerReconcileServiceGrpc.newBlockingStub(ch)
                    .withDeadlineAfter(FETCH_JOB_LOG_DEADLINE_SEC, TimeUnit.SECONDS)
                    .fetchJobLog(ServerJobLogRequest.newBuilder()
                            .setJobId(jobId).setOffset(offset).setLimit(limit).build());
        } catch (StatusRuntimeException e) {
            log.warn("fetchJobLog from {}:{} failed: {} {}", host, port, e.getStatus().getCode(), e.getMessage());
            Code code = e.getStatus().getCode();
            if (code == Code.UNAVAILABLE || code == Code.DEADLINE_EXCEEDED || code == Code.INTERNAL || code == Code.CANCELLED) {
                invalidateChannel(host, port);
            }
            throw e;
        } catch (Exception e) {
            log.warn("fetchJobLog from {}:{} failed: {}", host, port, e.getMessage());
            throw e;
        }
    }

    private ManagedChannel getChannel(String host, int port) {
        String key = host + ":" + port;
        try {
            return channels.get(key, () -> {
                log.debug("creating new gRPC channel to server {}", key);
                // dns:/// 强制 DnsNameResolver 直连（server 地址即 host:port，无服务名解析）
                return ManagedChannelBuilder.forTarget("dns:///" + host + ":" + port)
                        .usePlaintext()
                        .build();
            });
        } catch (ExecutionException e) {
            throw new RuntimeException("failed to build channel to server " + key, e);
        }
    }

    private void invalidateChannel(String host, int port) {
        channels.invalidate(host + ":" + port); // 触发 removalListener -> shutdown
    }

    @PreDestroy
    public void shutdown() {
        channels.invalidateAll();
        channels.cleanUp();
    }
}
