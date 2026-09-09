package io.gitee.songchaolin.adhoc.server.grpc;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import io.gitee.songchaolin.adhoc.common.enums.FailReasonCategory;
import io.gitee.songchaolin.adhoc.common.enums.FailStage;
import io.gitee.songchaolin.adhoc.common.enums.JobStatus;
import io.gitee.songchaolin.adhoc.common.enums.TaskStatus;
import io.gitee.songchaolin.adhoc.dao.entity.AdhocQueryJob;
import io.gitee.songchaolin.adhoc.dao.entity.AdhocQueryTask;
import io.gitee.songchaolin.adhoc.dao.mapper.AdhocQueryJobMapper;
import io.gitee.songchaolin.adhoc.dao.mapper.AdhocQueryTaskMapper;
import io.gitee.songchaolin.adhoc.internal.grpc.v1.HeartbeatRequest;
import io.gitee.songchaolin.adhoc.internal.grpc.v1.HeartbeatResponse;
import io.gitee.songchaolin.adhoc.internal.grpc.v1.HeartbeatServiceGrpc;
import io.gitee.songchaolin.adhoc.server.ha.JobLogRegistry;
import io.gitee.songchaolin.adhoc.server.ha.RunningJobRegistry;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PreDestroy;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * server 侧 HeartbeatService：收到 executor gRPC 心跳 ->
 * <ol>
 *   <li>Task 对账（同步，本地 DB）：DB RUNNING Task 不在心跳 running_task_ids -> TASK_LOST。</li>
 *   <li>Job 探查（异步，不阻塞心跳响应）：gRPC 调 executor getRunningJobs 取其在跑 jobId 集合，
 *       DB 里 executor_instance=该 executor 且 status=RUNNING 的 job 不在集合 -> 判定孤儿
 *       （executor 重启/丢失 session）-> markFailed + 释放 RunningJobRegistry + 清残留 PENDING/RUNNING task。
 *       这是 server 探查 executor 上 job 是否失效的核心能力：executor 快速重启（同 instance_id，未达 30s 心跳超时）
 *       时实例保持 UP，ExecutorCrashCompensation 不触发，靠此探查 5s 内清理孤儿 job，释放 per-user 额度。</li>
 * </ol>
 * 不再写 executor instance 行（executor 自写，见 ExecutorHeartbeatTask）。
 */
@GrpcService
public class HeartbeatServiceImpl extends HeartbeatServiceGrpc.HeartbeatServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(HeartbeatServiceImpl.class);
    private final AdhocQueryTaskMapper taskMapper;
    private final AdhocQueryJobMapper jobMapper;
    private final ExecutorChannelPool executorChannelPool;
    private final RunningJobRegistry runningJobRegistry;
    private final JobLogRegistry jobLogRegistry;
    /** job 探查异步执行（不阻塞心跳响应；单线程串行，探查幂等可排队）。 */
    private final ExecutorService jobProbeExecutor;

    public HeartbeatServiceImpl(AdhocQueryTaskMapper taskMapper, AdhocQueryJobMapper jobMapper,
                                ExecutorChannelPool executorChannelPool,
                                RunningJobRegistry runningJobRegistry, JobLogRegistry jobLogRegistry) {
        this.taskMapper = taskMapper;
        this.jobMapper = jobMapper;
        this.executorChannelPool = executorChannelPool;
        this.runningJobRegistry = runningJobRegistry;
        this.jobLogRegistry = jobLogRegistry;
        this.jobProbeExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "executor-job-probe");
            t.setDaemon(true);
            return t;
        });
    }

    @Override
    public void heartbeat(HeartbeatRequest request, StreamObserver<HeartbeatResponse> responseObserver) {
        String execId = request.getInstanceId();
        // 1. Task 级对账（同步，本地 DB，快）
        reconcileRunningTasks(execId, request.getRunningTaskIdsList());
        // 2. Job 级探查（异步，不阻塞心跳响应）
        try {
            jobProbeExecutor.submit(() -> reconcileJobsViaProbe(execId));
        } catch (RejectedExecutionException e) {
            log.debug("job-probe submit rejected (shutting down): {}", e.getMessage());
        }
        log.debug("heartbeat from {}: runningTasks={}", execId, request.getRunningTaskIdsCount());
        HeartbeatResponse response = HeartbeatResponse.newBuilder()
                .setAccepted(true)
                .setAction("NONE")
                .build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    private void reconcileRunningTasks(String executorInstanceId, List<String> heartbeatTaskIds) {
        Set<String> heartbeatSet = new HashSet<>(heartbeatTaskIds);
        List<AdhocQueryTask> dbRunning = taskMapper.selectList(new LambdaQueryWrapper<AdhocQueryTask>()
                .eq(AdhocQueryTask::getExecutorInstance, executorInstanceId)
                .eq(AdhocQueryTask::getStatus, TaskStatus.RUNNING.name()));
        for (AdhocQueryTask t : dbRunning) {
            if (!heartbeatSet.contains(t.getQueryId())) {
                taskMapper.markTaskLost(t.getQueryId());
                log.warn("TASK_LOST: task {} on executor {} not in heartbeat", t.getQueryId(), executorInstanceId);
            }
        }
    }

    /**
     * Job 级探查：getRunningJobs(executor) 拿 executor 内存里在跑的 jobId 集合，
     * DB 里 executor_instance=该 executor 且 status IN (DISPATCHING,RUNNING) 的 job 不在集合 -> 孤儿 -> markFailed。
     * 探查失败/executor 非 UP -> 本轮跳过（由 DOWN 补偿兜底）。
     */
    private void reconcileJobsViaProbe(String executorInstanceId) {
        List<String> runningOnExecutor;
        try {
            runningOnExecutor = executorChannelPool.getRunningJobs(executorInstanceId);
        } catch (Exception e) {
            log.debug("getRunningJobs for {} failed, skip this round: {}", executorInstanceId, e.getMessage());
            return;
        }
        if (runningOnExecutor == null) {
            return;  // executor 非 UP / 探查失败，本轮跳过（DOWN 补偿兜底）
        }
        Set<String> runningSet = new HashSet<>(runningOnExecutor);
        List<AdhocQueryJob> dbRunning = jobMapper.selectList(new LambdaQueryWrapper<AdhocQueryJob>()
                .eq(AdhocQueryJob::getExecutorInstance, executorInstanceId)
                .in(AdhocQueryJob::getStatus, JobStatus.DISPATCHING.name(), JobStatus.RUNNING.name()));
        for (AdhocQueryJob job : dbRunning) {
            if (!runningSet.contains(job.getJobId())) {
                markJobFailedAsOrphan(job, executorInstanceId);
            }
        }
    }

    /** 孤儿 job 清理：CAS PENDING/DISPATCHING/RUNNING -> FAILED（防误改终态）+ 释放 server 内存 + 清残留 PENDING/RUNNING task。 */
    private void markJobFailedAsOrphan(AdhocQueryJob job, String executorInstanceId) {
        int updated = jobMapper.update(null, new LambdaUpdateWrapper<AdhocQueryJob>()
                .eq(AdhocQueryJob::getJobId, job.getJobId())
                .in(AdhocQueryJob::getStatus,
                        JobStatus.PENDING.name(), JobStatus.DISPATCHING.name(), JobStatus.RUNNING.name())
                .set(AdhocQueryJob::getStatus, JobStatus.FAILED.name())
                .set(AdhocQueryJob::getFinishTime, new Date()));
        if (updated == 0) {
            return;  // 已终态（executor 刚好回写 / 并发清理），不重复处理
        }
        log.warn("[job={}] orphan on executor {} (not in getRunningJobs probe), mark FAILED + release registry", job.getJobId(), executorInstanceId);
        jobLogRegistry.append(job.getJobId(),
                "[server] [WARN] [job=" + job.getJobId() + "] orphan on executor " + executorInstanceId + " (probe: not running), mark FAILED");
        runningJobRegistry.remove(job.getJobId());  // 释放 server 内存（RunningJobRegistry）
        // 清残留 PENDING/RUNNING task（session 丢失无法继续）：CAS 防误改已终态（TASK_LOST 的 RUNNING task 不受影响）
        taskMapper.update(null, new LambdaUpdateWrapper<AdhocQueryTask>()
                .eq(AdhocQueryTask::getJobId, job.getJobId())
                .in(AdhocQueryTask::getStatus, TaskStatus.PENDING.name(), TaskStatus.RUNNING.name())
                .eq(AdhocQueryTask::getIsDeleted, 0)
                .set(AdhocQueryTask::getStatus, TaskStatus.FAILED.name())
                .set(AdhocQueryTask::getFailStage, FailStage.DISPATCH.name())  // 孤儿 job：executor 否认持有，session 丢失，task 未完成执行管线
                .set(AdhocQueryTask::getFailReasonCategory, FailReasonCategory.SKIPPED_DUE_TO_SESSION_LOSS.name())
                .set(AdhocQueryTask::getFinishTime, new Date()));
    }

    @PreDestroy
    public void stop() {
        jobProbeExecutor.shutdown();
    }
}
