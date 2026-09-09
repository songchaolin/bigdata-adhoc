package io.gitee.songchaolin.adhoc.server.ha;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import io.gitee.songchaolin.adhoc.common.enums.FailReasonCategory;
import io.gitee.songchaolin.adhoc.common.enums.FailStage;
import io.gitee.songchaolin.adhoc.common.enums.InstanceStatus;
import io.gitee.songchaolin.adhoc.common.enums.JobStatus;
import io.gitee.songchaolin.adhoc.common.enums.TaskStatus;
import io.gitee.songchaolin.adhoc.dao.entity.AdhocExecutorInstance;
import io.gitee.songchaolin.adhoc.dao.entity.AdhocQueryJob;
import io.gitee.songchaolin.adhoc.dao.entity.AdhocQueryTask;
import io.gitee.songchaolin.adhoc.dao.entity.AdhocServerInstance;
import io.gitee.songchaolin.adhoc.dao.mapper.AdhocExecutorInstanceMapper;
import io.gitee.songchaolin.adhoc.dao.mapper.AdhocQueryJobMapper;
import io.gitee.songchaolin.adhoc.dao.mapper.AdhocQueryTaskMapper;
import io.gitee.songchaolin.adhoc.dao.mapper.AdhocServerInstanceMapper;
import io.gitee.songchaolin.adhoc.internal.grpc.v1.CheckJobRunningRequest;
import io.gitee.songchaolin.adhoc.internal.grpc.v1.CheckJobRunningResponse;
import io.gitee.songchaolin.adhoc.internal.grpc.v1.ServerReconcileServiceGrpc;
import io.gitee.songchaolin.adhoc.common.config.ConfigHolder;
import io.gitee.songchaolin.adhoc.server.config.AdhocServerConfig;
import io.gitee.songchaolin.adhoc.server.grpc.ExecutorChannelPool;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Job/Task 对账（清理 stuck）：专用后台线程，定期扫描长期未结束的 job/task。
 *
 * Job 对账逻辑（安全，分三层）：
 * 1. 本节点处理的 job（processing_server = 本 server）-> 查 RunningJobRegistry 内存，不在 = 丢失 -> FAILED
 * 2. 其他节点处理的 job -> gRPC 调对应 server 的 checkJobRunning，不在运行 -> FAILED
 *    gRPC 调不通 -> 查 server 状态，DOWN -> 代为 FAILED；UP -> skip（transient）
 * 3. 无 processing_server -> FAILED（stuck）
 *
 * Task 对账：executor DOWN + > 阈值 -> FAILED(TASK_LOST)；executor UP -> skip
 */
@Component
public class JobReconcileTask {

    private static final Logger log = LoggerFactory.getLogger(JobReconcileTask.class);
    private static final int GRPC_DEADLINE_SEC = 5;

    private final AdhocQueryJobMapper jobMapper;
    private final AdhocQueryTaskMapper taskMapper;
    private final AdhocExecutorInstanceMapper executorMapper;
    private final AdhocServerInstanceMapper serverMapper;
    private final JobLogRegistry jobLogRegistry;
    private final RunningJobRegistry runningJobRegistry;
    private final ServerInstanceInfo instanceInfo;
    private final ExecutorChannelPool executorChannelPool;
    private final ConcurrentHashMap<String, ManagedChannel> serverChannels = new ConcurrentHashMap<>();

    private final ConfigHolder cfg;

    private ScheduledExecutorService scheduler;

    public JobReconcileTask(AdhocQueryJobMapper jobMapper, AdhocQueryTaskMapper taskMapper,
                           AdhocExecutorInstanceMapper executorMapper, AdhocServerInstanceMapper serverMapper,
                           JobLogRegistry jobLogRegistry, RunningJobRegistry runningJobRegistry,
                           ServerInstanceInfo instanceInfo, ExecutorChannelPool executorChannelPool, ConfigHolder cfg) {
        this.jobMapper = jobMapper;
        this.taskMapper = taskMapper;
        this.executorMapper = executorMapper;
        this.serverMapper = serverMapper;
        this.jobLogRegistry = jobLogRegistry;
        this.runningJobRegistry = runningJobRegistry;
        this.instanceInfo = instanceInfo;
        this.executorChannelPool = executorChannelPool;
        this.cfg = cfg;
    }

    public void start() {
        long intervalMs = cfg.get(AdhocServerConfig.RECONCILE_INTERVAL_MS);
        int stuckThresholdMinutes = cfg.get(AdhocServerConfig.RECONCILE_STUCK_THRESHOLD_MINUTES);
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "job-reconcile");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleWithFixedDelay(() -> {
            try { reconcile(); } catch (Throwable t) { log.warn("job-reconcile caught: {}", t.getMessage()); }
        }, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
        log.info("job-reconcile started, interval={}ms, stuckThreshold={}min", intervalMs, stuckThresholdMinutes);
    }

    void reconcile() {
        int stuckThresholdMinutes = cfg.get(AdhocServerConfig.RECONCILE_STUCK_THRESHOLD_MINUTES);
        int absoluteStuckThresholdMinutes = cfg.get(AdhocServerConfig.RECONCILE_ABSOLUTE_STUCK_THRESHOLD_MINUTES);
        Date threshold = new Date(System.currentTimeMillis() - stuckThresholdMinutes * 60_000L);
        String myServerId = instanceInfo.getId();
        int jobFixed = 0, taskFixed = 0;

        // === 1. Stuck jobs ===
        List<AdhocQueryJob> stuckJobs = jobMapper.selectList(new LambdaQueryWrapper<AdhocQueryJob>()
                .in(AdhocQueryJob::getStatus, JobStatus.PENDING.name(), JobStatus.DISPATCHING.name(), JobStatus.RUNNING.name())
                .lt(AdhocQueryJob::getUpdateTime, threshold));

        for (AdhocQueryJob job : stuckJobs) {
            String processingServer = job.getProcessingServerInstance();
            boolean shouldFail = false;
            String reason = null;
            long stuckMinutes = job.getUpdateTime() == null ? Long.MAX_VALUE
                    : (System.currentTimeMillis() - job.getUpdateTime().getTime()) / 60000;

            if (processingServer == null || processingServer.isEmpty()) {
                // 无 processing_server -> stuck
                shouldFail = true;
                reason = "no processing server, stuck > " + stuckThresholdMinutes + "min";
            } else if (processingServer.equals(myServerId)) {
                // 本节点处理的任务 -> 探查 executor 是否还在跑此 job（不再只查 server 内存，否则永远 true 漏判孤儿）
                Boolean running = isJobRunningOnExecutor(job);
                if (Boolean.FALSE.equals(running)) {
                    shouldFail = true;
                    reason = "not running on executor " + job.getExecutorInstance() + " (probe)";
                }
                // running == null（探查失败/无法探查）-> 本轮 skip（DOWN 补偿 / 下轮再试）
            } else {
                // 其他节点处理的任务 -> gRPC 通知对应节点检查
                try {
                    boolean running = checkJobRunningOnServer(processingServer, job.getJobId());
                    if (!running) {
                        shouldFail = true;
                        reason = "not running on server " + processingServer + " (confirmed via gRPC)";
                    }
                } catch (Exception e) {
                    // gRPC 调不通 -> 查 server 是否下线
                    if (!isServerUp(processingServer)) {
                        shouldFail = true;
                        reason = "server " + processingServer + " DOWN, taking over";
                    } else if (stuckMinutes > absoluteStuckThresholdMinutes) {
                        // server UP 但 gRPC reconcile 持续失败超过绝对超时 -> 标 FAILED（防永久卡死）
                        shouldFail = true;
                        reason = "server " + processingServer + " UP but gRPC reconcile failed, stuck > " + absoluteStuckThresholdMinutes + "min";
                    }
                    // 否则 skip（transient，下轮再试）
                }
            }

            if (shouldFail) {
                if (job.getCancelRequested() != null && job.getCancelRequested() == 1) {
                    markJobCanceled(job);  // cancel_requested 兜底：CANCELED 而非 FAILED
                } else {
                    markJobFailed(job, reason);
                }
                taskFixed += markTasksFailedForJob(job.getJobId());
                jobFixed++;
            }
        }

        // === 2. Stuck tasks（executor DOWN 检查）===
        List<AdhocQueryTask> stuckTasks = taskMapper.selectList(new LambdaQueryWrapper<AdhocQueryTask>()
                .in(AdhocQueryTask::getStatus, TaskStatus.PENDING.name(), TaskStatus.RUNNING.name())
                .eq(AdhocQueryTask::getIsDeleted, 0)
                .lt(AdhocQueryTask::getUpdateTime, threshold));
        for (AdhocQueryTask task : stuckTasks) {
            String exec = task.getExecutorInstance();
            if (exec != null && !exec.isEmpty()) {
                if (!isExecutorUp(exec)) {
                    markTaskFailed(task, "executor " + exec + " DOWN, stuck > " + stuckThresholdMinutes + "min");
                    taskFixed++;
                }
            } else {
                markTaskFailed(task, "no executor assigned, stuck > " + stuckThresholdMinutes + "min");
                taskFixed++;
            }
        }

        if (jobFixed > 0 || taskFixed > 0) {
            log.info("reconcile done: {} job(s), {} task(s) marked FAILED", jobFixed, taskFixed);
        }
    }

    /** gRPC 调目标 server 的 checkJobRunning，返回 job 是否还在该 server 运行。 */
    private boolean checkJobRunningOnServer(String serverInstanceId, String jobId) {
        AdhocServerInstance server = serverMapper.selectOne(new LambdaQueryWrapper<AdhocServerInstance>()
                .eq(AdhocServerInstance::getInstanceId, serverInstanceId));
        if (server == null) {
            throw new RuntimeException("server not found: " + serverInstanceId);
        }
        int port = server.getGrpcPort() == null ? 9090 : server.getGrpcPort();
        String key = server.getHost() + ":" + port;
        ManagedChannel ch = serverChannels.computeIfAbsent(key,
                // dns:/// 强制 DnsNameResolver 直连（server 地址即 host:port，无服务名解析）
                k -> ManagedChannelBuilder.forTarget("dns:///" + key).usePlaintext().build());
        try {
            CheckJobRunningResponse resp = ServerReconcileServiceGrpc.newBlockingStub(ch)
                    .withDeadlineAfter(GRPC_DEADLINE_SEC, TimeUnit.SECONDS)
                    .checkJobRunning(CheckJobRunningRequest.newBuilder().setJobId(jobId).build());
            return resp.getRunning();
        } catch (StatusRuntimeException e) {
            // 失败清坏 channel（防泄漏，server 下线后 channel 不残留）
            if (serverChannels.remove(key) != null) {
                ch.shutdown();
            }
            throw e;
        }
    }

    private boolean isExecutorUp(String instanceId) {
        AdhocExecutorInstance exec = executorMapper.selectOne(new LambdaQueryWrapper<AdhocExecutorInstance>()
                .eq(AdhocExecutorInstance::getInstanceId, instanceId));
        return exec != null && InstanceStatus.UP.is(exec.getStatus());
    }

    private boolean isServerUp(String instanceId) {
        AdhocServerInstance srv = serverMapper.selectOne(new LambdaQueryWrapper<AdhocServerInstance>()
                .eq(AdhocServerInstance::getInstanceId, instanceId));
        return srv != null && InstanceStatus.UP.is(srv.getStatus());
    }

    /** 探查 job 是否还在 executor 跑（reconcile own-job 用，不再只查 server 内存）。
     *  @return true=在跑；false=不在（孤儿，可标 FAILED）；null=探查失败/无法探查（本轮 skip，DOWN 补偿兜底） */
    private Boolean isJobRunningOnExecutor(AdhocQueryJob job) {
        String exec = job.getExecutorInstance();
        if (exec == null || exec.isEmpty()) {
            // DISPATCHING（executor_instance 未设，executor 还没 markRunning）-> server 内存兜底：在内存=在跑，不在=丢失
            return runningJobRegistry.contains(job.getJobId());
        }
        List<String> running = executorChannelPool.getRunningJobs(exec);
        if (running == null) {
            return null;  // executor 非 UP / gRPC 失败，本轮 skip
        }
        return running.contains(job.getJobId());
    }

    private void markJobFailed(AdhocQueryJob job, String reason) {
        jobMapper.update(null, new LambdaUpdateWrapper<AdhocQueryJob>()
                .eq(AdhocQueryJob::getJobId, job.getJobId())
                .in(AdhocQueryJob::getStatus,
                        JobStatus.PENDING.name(), JobStatus.DISPATCHING.name(), JobStatus.RUNNING.name())
                .set(AdhocQueryJob::getStatus, JobStatus.FAILED.name())
                .set(AdhocQueryJob::getFinishTime, new Date()));
        log.warn("[job={}] reconcile: FAILED ({})", job.getJobId(), reason);
        jobLogRegistry.append(job.getJobId(), "[server] [WARN] [job=" + job.getJobId() + "] reconcile: FAILED (" + reason + ")");
        runningJobRegistry.remove(job.getJobId());
    }

    /** cancel_requested 兜底：executor 未及时落 CANCELED 时，reconcile 代为 CANCELED。 */
    private void markJobCanceled(AdhocQueryJob job) {
        jobMapper.update(null, new LambdaUpdateWrapper<AdhocQueryJob>()
                .eq(AdhocQueryJob::getJobId, job.getJobId())
                .in(AdhocQueryJob::getStatus,
                        JobStatus.PENDING.name(), JobStatus.DISPATCHING.name(), JobStatus.RUNNING.name())
                .set(AdhocQueryJob::getStatus, JobStatus.CANCELED.name())
                .set(AdhocQueryJob::getFinishTime, new Date()));
        log.warn("[job={}] reconcile: CANCELED (cancel_requested)", job.getJobId());
        jobLogRegistry.append(job.getJobId(), "[server] [WARN] [job=" + job.getJobId() + "] reconcile: CANCELED (cancel_requested)");
        runningJobRegistry.remove(job.getJobId());
    }

    private int markTasksFailedForJob(String jobId) {
        List<AdhocQueryTask> tasks = taskMapper.selectList(new LambdaQueryWrapper<AdhocQueryTask>()
                .eq(AdhocQueryTask::getJobId, jobId)
                .in(AdhocQueryTask::getStatus, TaskStatus.PENDING.name(), TaskStatus.RUNNING.name())
                .eq(AdhocQueryTask::getIsDeleted, 0));
        for (AdhocQueryTask task : tasks) {
            markTaskFailed(task, "parent job FAILED (reconcile)");
        }
        return tasks.size();
    }

    private void markTaskFailed(AdhocQueryTask task, String reason) {
        taskMapper.update(null, new LambdaUpdateWrapper<AdhocQueryTask>()
                .eq(AdhocQueryTask::getQueryId, task.getQueryId())
                .in(AdhocQueryTask::getStatus, TaskStatus.PENDING.name(), TaskStatus.RUNNING.name())
                .set(AdhocQueryTask::getStatus, TaskStatus.FAILED.name())
                .set(AdhocQueryTask::getFailReasonCategory, FailReasonCategory.TASK_LOST.name())
                .set(AdhocQueryTask::getFailStage, FailStage.EXECUTING.name())
                .set(AdhocQueryTask::getFinishTime, new Date()));
        log.warn("[task={}] reconcile: FAILED ({})", task.getQueryId(), reason);
    }

    @PreDestroy
    public void stop() {
        if (scheduler != null) {
            scheduler.shutdown();
        }
        serverChannels.values().forEach(ManagedChannel::shutdown);
        serverChannels.clear();
    }
}