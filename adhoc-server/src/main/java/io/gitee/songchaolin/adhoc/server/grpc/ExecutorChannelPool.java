package io.gitee.songchaolin.adhoc.server.grpc;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.gitee.songchaolin.adhoc.common.enums.InstanceStatus;
import io.gitee.songchaolin.adhoc.dao.entity.AdhocExecutorInstance;
import io.gitee.songchaolin.adhoc.dao.mapper.AdhocExecutorInstanceMapper;
import io.gitee.songchaolin.adhoc.internal.grpc.v1.CancelJobRequest;
import io.gitee.songchaolin.adhoc.internal.grpc.v1.ClearJobLogRequest;
import io.gitee.songchaolin.adhoc.internal.grpc.v1.DataFetcherGrpc;
import io.gitee.songchaolin.adhoc.internal.grpc.v1.DispatchJobRequest;
import io.gitee.songchaolin.adhoc.internal.grpc.v1.DispatchJobResponse;
import io.gitee.songchaolin.adhoc.internal.grpc.v1.FetchJobLogRequest;
import io.gitee.songchaolin.adhoc.internal.grpc.v1.FetchJobLogResponse;
import io.gitee.songchaolin.adhoc.internal.grpc.v1.FetchLogRequest;
import io.gitee.songchaolin.adhoc.internal.grpc.v1.FetchLogResponse;
import io.gitee.songchaolin.adhoc.internal.grpc.v1.GetRunningJobsRequest;
import io.gitee.songchaolin.adhoc.internal.grpc.v1.GetRunningJobsResponse;
import io.gitee.songchaolin.adhoc.internal.grpc.v1.JobExecutorGrpc;
import io.gitee.songchaolin.adhoc.internal.grpc.v1.JobManagerGrpc;
import io.gitee.songchaolin.adhoc.internal.grpc.v1.PingRequest;
import io.gitee.songchaolin.adhoc.internal.grpc.v1.PingResponse;
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
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * server 按 executor 动态建 gRPC channel：dispatchJob（QueueWorker 下发到选中的 executor）
 * + fetchLog（LogController 实时读 executor 内存日志）。
 *
 * channel 用 guava Cache 管理：expireAfterAccess 10min + maximumSize 100（空闲/超量自动淘汰+shutdown，防泄漏）
 * + 连接类失败（UNAVAILABLE/DEADLINE_EXCEEDED）invalidate 关坏 channel；应用类错误不关（channel 仍健康）。
 * 调用带 deadline + connectTimeout，防 executor 不可达时阻塞调用方。
 * ExecutorClient 固定 stub 仅 EchoController demo 用。
 */
@Component
public class ExecutorChannelPool {

    private static final Logger log = LoggerFactory.getLogger(ExecutorChannelPool.class);
    private static final int DEFAULT_EXECUTOR_GRPC_PORT = 9091;
    private static final long DISPATCH_DEADLINE_SEC = 10;
    private static final long FETCH_LOG_DEADLINE_SEC = 10;
    private static final long PING_DEADLINE_SEC = 2;
    private static final long CLEAR_LOG_DEADLINE_SEC = 5;
    private static final long CANCEL_JOB_DEADLINE_SEC = 5;
    private static final long GET_RUNNING_JOBS_DEADLINE_SEC = 5;
    private static final long CHANNEL_EXPIRE_MIN = 10;
    private static final int CHANNEL_MAX_SIZE = 100;

    private final AdhocExecutorInstanceMapper executorMapper;
    private final Cache<String, ManagedChannel> channels = CacheBuilder.newBuilder()
            .expireAfterAccess(CHANNEL_EXPIRE_MIN, TimeUnit.MINUTES)
            .maximumSize(CHANNEL_MAX_SIZE)
            .removalListener((RemovalNotification<String, ManagedChannel> n) -> {
                ManagedChannel ch = n.getValue();
                if (ch != null && !ch.isShutdown()) {
                    ch.shutdown(); // graceful：让并发活跃调用跑完（不用 shutdownNow，避免误伤并发调用）
                }
            })
            .build();

    public ExecutorChannelPool(AdhocExecutorInstanceMapper executorMapper) {
        this.executorMapper = executorMapper;
    }

    /** dispatch：打到选中的 executor（host:grpcPort 直用）。连接类失败关坏 channel 并抛异常（QueueWorker 兜底回退 PENDING）。 */
    public DispatchJobResponse dispatchJob(AdhocExecutorInstance exec, DispatchJobRequest req) {
        String host = requireHost(exec);
        int port = portOf(exec);
        ManagedChannel ch = getChannel(host, port);
        try {
            return JobExecutorGrpc.newBlockingStub(ch)
                    .withDeadlineAfter(DISPATCH_DEADLINE_SEC, TimeUnit.SECONDS)
                    .dispatchJob(req);
        } catch (StatusRuntimeException e) {
            log.warn("dispatch to {}:{} failed: {} {}", host, port, e.getStatus().getCode(), e.getMessage());
            if (isConnectionIssue(e)) {
                invalidateChannel(host, port);
            }
            throw e;
        } catch (Exception e) {
            log.warn("dispatch to {}:{} failed: {}", host, port, e.getMessage());
            throw e;
        }
    }

    /** fetchLog：按 instance_id 查 UP executor 拿 host:grpcPort，再取 channel。找不到/非 UP/失败抛异常（LogController 兜底 OSS）。 */
    public FetchLogResponse fetchLog(String executorInstanceId, String taskId, long offset, int limit) {
        AdhocExecutorInstance exec = executorMapper.selectOne(new LambdaQueryWrapper<AdhocExecutorInstance>()
                .eq(AdhocExecutorInstance::getInstanceId, executorInstanceId)
                .eq(AdhocExecutorInstance::getStatus, InstanceStatus.UP.name()));
        if (exec == null) {
            throw new RuntimeException("executor not found or not UP: " + executorInstanceId);
        }
        String host = requireHost(exec);
        int port = portOf(exec);
        ManagedChannel ch = getChannel(host, port);
        try {
            return DataFetcherGrpc.newBlockingStub(ch)
                    .withDeadlineAfter(FETCH_LOG_DEADLINE_SEC, TimeUnit.SECONDS)
                    .fetchLog(FetchLogRequest.newBuilder()
                            .setTaskId(taskId).setOffset(offset).setLimit(limit).build());
        } catch (StatusRuntimeException e) {
            log.warn("fetchLog from {}:{} failed: {} {}", host, port, e.getStatus().getCode(), e.getMessage());
            if (isConnectionIssue(e)) {
                invalidateChannel(host, port);
            }
            throw e;
        } catch (Exception e) {
            log.warn("fetchLog from {}:{} failed: {}", host, port, e.getMessage());
            throw e;
        }
    }

    /** fetchJobLog：按 instance_id 查 UP executor 拿 job 内存日志（running 实时读）。找不到/失败抛异常（LogQueryService 兜底 OSS）。 */
    public FetchJobLogResponse fetchJobLog(String executorInstanceId, String jobId, long offset, int limit) {
        AdhocExecutorInstance exec = executorMapper.selectOne(new LambdaQueryWrapper<AdhocExecutorInstance>()
                .eq(AdhocExecutorInstance::getInstanceId, executorInstanceId)
                .eq(AdhocExecutorInstance::getStatus, InstanceStatus.UP.name()));
        if (exec == null) {
            throw new RuntimeException("executor not found or not UP: " + executorInstanceId);
        }
        String host = requireHost(exec);
        int port = portOf(exec);
        ManagedChannel ch = getChannel(host, port);
        try {
            return DataFetcherGrpc.newBlockingStub(ch)
                    .withDeadlineAfter(FETCH_LOG_DEADLINE_SEC, TimeUnit.SECONDS)
                    .fetchJobLog(FetchJobLogRequest.newBuilder()
                            .setJobId(jobId).setOffset(offset).setLimit(limit).build());
        } catch (StatusRuntimeException e) {
            log.warn("fetchJobLog from {}:{} failed: {} {}", host, port, e.getStatus().getCode(), e.getMessage());
            if (isConnectionIssue(e)) {
                invalidateChannel(host, port);
            }
            throw e;
        } catch (Exception e) {
            log.warn("fetchJobLog from {}:{} failed: {}", host, port, e.getMessage());
            throw e;
        }
    }

    /** clearJobLog：server 终态轮通知 executor 清理本地 job 日志 buffer（正常路径，server 已完整写 OSS）。失败抛异常（JobLogCollector 兜底，executor 定时器补全）。 */
    public void clearJobLog(String executorInstanceId, String jobId) {
        AdhocExecutorInstance exec = executorMapper.selectOne(new LambdaQueryWrapper<AdhocExecutorInstance>()
                .eq(AdhocExecutorInstance::getInstanceId, executorInstanceId)
                .eq(AdhocExecutorInstance::getStatus, InstanceStatus.UP.name()));
        if (exec == null) {
            throw new RuntimeException("executor not found or not UP: " + executorInstanceId);
        }
        String host = requireHost(exec);
        int port = portOf(exec);
        ManagedChannel ch = getChannel(host, port);
        try {
            DataFetcherGrpc.newBlockingStub(ch)
                    .withDeadlineAfter(CLEAR_LOG_DEADLINE_SEC, TimeUnit.SECONDS)
                    .clearJobLog(ClearJobLogRequest.newBuilder().setJobId(jobId).build());
        } catch (StatusRuntimeException e) {
            log.warn("clearJobLog to {}:{} failed: {} {}", host, port, e.getStatus().getCode(), e.getMessage());
            if (isConnectionIssue(e)) {
                invalidateChannel(host, port);
            }
            throw e;
        } catch (Exception e) {
            log.warn("clearJobLog to {}:{} failed: {}", host, port, e.getMessage());
            throw e;
        }
    }

    /** cancelJob：通知 executor 取消 job（Statement.cancel 中断当前查询，立即生效）。找不到/失败抛异常（JobService 兜底，executor 段间检查也生效）。 */
    public void cancelJob(String executorInstanceId, String jobId) {
        AdhocExecutorInstance exec = executorMapper.selectOne(new LambdaQueryWrapper<AdhocExecutorInstance>()
                .eq(AdhocExecutorInstance::getInstanceId, executorInstanceId)
                .eq(AdhocExecutorInstance::getStatus, InstanceStatus.UP.name()));
        if (exec == null) {
            throw new RuntimeException("executor not found or not UP: " + executorInstanceId);
        }
        String host = requireHost(exec);
        int port = portOf(exec);
        ManagedChannel ch = getChannel(host, port);
        try {
            JobManagerGrpc.newBlockingStub(ch)
                    .withDeadlineAfter(CANCEL_JOB_DEADLINE_SEC, TimeUnit.SECONDS)
                    .cancelJob(CancelJobRequest.newBuilder().setJobId(jobId).build());
        } catch (StatusRuntimeException e) {
            log.warn("cancelJob to {}:{} failed: {} {}", host, port, e.getStatus().getCode(), e.getMessage());
            if (isConnectionIssue(e)) {
                invalidateChannel(host, port);
            }
            throw e;
        } catch (Exception e) {
            log.warn("cancelJob to {}:{} failed: {}", host, port, e.getMessage());
            throw e;
        }
    }

    /**
     * 探查 executor 当前在跑的 jobId 集合（清理孤儿 job 用）：按 instance_id 查 UP executor 拿 host:grpcPort，
     * 5s deadline 调 JobManager.getRunningJobs。
     * @return jobId 集合（executor 在跑 0 个时为空 list）；executor 非 UP / gRPC 失败返回 null（调用方本轮跳过清理，由 DOWN 补偿兜底）
     */
    public List<String> getRunningJobs(String executorInstanceId) {
        AdhocExecutorInstance exec = executorMapper.selectOne(new LambdaQueryWrapper<AdhocExecutorInstance>()
                .eq(AdhocExecutorInstance::getInstanceId, executorInstanceId)
                .eq(AdhocExecutorInstance::getStatus, InstanceStatus.UP.name()));
        if (exec == null) {
            return null;
        }
        String host = requireHost(exec);
        int port = portOf(exec);
        ManagedChannel ch = getChannel(host, port);
        try {
            GetRunningJobsResponse resp = JobManagerGrpc.newBlockingStub(ch)
                    .withDeadlineAfter(GET_RUNNING_JOBS_DEADLINE_SEC, TimeUnit.SECONDS)
                    .getRunningJobs(GetRunningJobsRequest.newBuilder().build());
            return resp.getJobIdsList();
        } catch (StatusRuntimeException e) {
            log.warn("getRunningJobs from {}:{} failed: {} {}", host, port, e.getStatus().getCode(), e.getMessage());
            if (isConnectionIssue(e)) {
                invalidateChannel(host, port);
            }
            return null;
        } catch (Exception e) {
            log.warn("getRunningJobs from {}:{} failed: {}", host, port, e.getMessage());
            return null;
        }
    }

    /**
     * ping：探活 executor（QueueWorker dispatch 前用）。按 instance_id 查 UP executor 拿 host:grpcPort，
     * 2s deadline 调 DataFetcher.ping。可达/ok=true 返回 true；不可达/超时/异常/false 返回 false。
     * 连接类失败（UNAVAILABLE/DEADLINE_EXCEEDED）invalidate 关坏 channel。
     */
    public boolean ping(String executorInstanceId) {
        AdhocExecutorInstance exec = executorMapper.selectOne(new LambdaQueryWrapper<AdhocExecutorInstance>()
                .eq(AdhocExecutorInstance::getInstanceId, executorInstanceId)
                .eq(AdhocExecutorInstance::getStatus, InstanceStatus.UP.name()));
        if (exec == null) {
            return false;
        }
        String host = requireHost(exec);
        int port = portOf(exec);
        ManagedChannel ch = getChannel(host, port);
        try {
            PingResponse resp = DataFetcherGrpc.newBlockingStub(ch)
                    .withDeadlineAfter(PING_DEADLINE_SEC, TimeUnit.SECONDS)
                    .ping(PingRequest.newBuilder().build());
            return resp.getOk();
        } catch (StatusRuntimeException e) {
            log.warn("ping {}:{} failed: {} {}", host, port, e.getStatus().getCode(), e.getMessage());
            if (isConnectionIssue(e)) {
                invalidateChannel(host, port);
            }
            return false;
        } catch (Exception e) {
            log.warn("ping {}:{} failed: {}", host, port, e.getMessage());
            return false;
        }
    }

    private ManagedChannel getChannel(String host, int port) {
        String key = host + ":" + port;
        try {
            return channels.get(key, () -> {
                log.debug("creating new gRPC channel to {}", key);
                // dns:/// 强制 DnsNameResolver 直连（executor 地址即 host:port，无服务名解析）
                return ManagedChannelBuilder.forTarget("dns:///" + host + ":" + port)
                        .usePlaintext()
                        .build();
            });
        } catch (ExecutionException e) {
            // loader 抛异常时 guava 不缓存（无需 invalidate）
            throw new RuntimeException("failed to build channel to " + key, e);
        }
    }

    private void invalidateChannel(String host, int port) {
        channels.invalidate(host + ":" + port); // 触发 removalListener -> shutdown
    }

    /** 连接类问题（不可达/超时/连接重置/取消）才关 channel；应用类错误（UNKNOWN 等）channel 仍健康，不关。 */
    private static boolean isConnectionIssue(StatusRuntimeException e) {
        Code code = e.getStatus().getCode();
        return code == Code.UNAVAILABLE || code == Code.DEADLINE_EXCEEDED
                || code == Code.INTERNAL || code == Code.CANCELLED;
    }

    private static String requireHost(AdhocExecutorInstance exec) {
        String host = exec.getHost();
        if (host == null || host.isEmpty()) {
            throw new RuntimeException("executor host is null/empty: " + exec.getInstanceId());
        }
        return host;
    }

    private static int portOf(AdhocExecutorInstance exec) {
        return exec.getGrpcPort() == null ? DEFAULT_EXECUTOR_GRPC_PORT : exec.getGrpcPort();
    }

    @PreDestroy
    public void shutdown() {
        channels.invalidateAll(); // 触发 removalListener -> 逐个 shutdown
        channels.cleanUp();
    }
}