package io.gitee.songchaolin.adhoc.server.schedule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import io.gitee.songchaolin.adhoc.common.enums.JobStatus;
import io.gitee.songchaolin.adhoc.common.util.LogTiming;
import io.gitee.songchaolin.adhoc.dao.entity.AdhocExecutorInstance;
import io.gitee.songchaolin.adhoc.dao.entity.AdhocQueryJob;
import io.gitee.songchaolin.adhoc.dao.mapper.AdhocExecutorInstanceMapper;
import io.gitee.songchaolin.adhoc.dao.mapper.AdhocQueryJobMapper;
import io.gitee.songchaolin.adhoc.internal.grpc.v1.DispatchJobRequest;
import io.gitee.songchaolin.adhoc.internal.grpc.v1.DispatchJobResponse;
import io.gitee.songchaolin.adhoc.server.grpc.ExecutorChannelPool;
import io.gitee.songchaolin.adhoc.common.config.ConfigHolder;
import io.gitee.songchaolin.adhoc.server.config.AdhocServerConfig;
import io.gitee.songchaolin.adhoc.server.ha.JobLogRegistry;
import io.gitee.songchaolin.adhoc.server.ha.ServerInstanceInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * server 调度 worker：扫描 PENDING Job -> CAS 抢占(RUNNING) -> 按 engine_type 选所有 UP executor（按 load ASC）
 * -> 逐个 gRPC ping 探活 + dispatchJob 下发，ping/dispatch 失败转下一个 executor（failover），全失败才回退 PENDING。
 * MVP：CAS UPDATE 抢占（无 FOR UPDATE SKIP LOCKED，单 worker 够用；多 server 时 CAS 保证不重复）。
 * 不卡在死 executor 上：ping/dispatch 连接类失败的 executor 进 pingBlacklist 短期熔断（默认 10s TTL），
 * TTL 内跳过不重复 2s ping，直到 HealthCheckTask 标 DOWN 或 TTL 到期探测恢复。
 */
@Component
public class QueueWorker {

    private static final Logger log = LoggerFactory.getLogger(QueueWorker.class);

    private final AdhocQueryJobMapper jobMapper;
    private final AdhocExecutorInstanceMapper executorMapper;
    private final ExecutorChannelPool executorChannelPool;
    private final ServerInstanceInfo instanceInfo;
    private final JobLogRegistry jobLogRegistry;
    private final io.gitee.songchaolin.adhoc.server.ha.RunningJobRegistry runningJobRegistry;
    private final ConfigHolder cfg;
    private long intervalMs;
    /** 派发扫描时间窗：只扫最近 N 小时内提交的 PENDING（更老的交给 reconcile 兜底；防全表扫描）。 */
    private int recentWindowHours;
    /** 提交时事件触发派发开关（生产 true；测试 false 走纯显式 dispatcher()，避免异步触发与断言竞态）。 */
    private boolean eventTriggerEnabled;
    private ScheduledExecutorService scheduler;
    /** 提交时事件触发派发用（非阻塞；合并多次触发为一次 dispatcher，轮询兜底不变）。 */
    private ExecutorService triggerExecutor;
    private final AtomicBoolean triggerPending = new AtomicBoolean(false);
    private final String serverGrpcInstance;
    /** ping 失败 executor 短期熔断：TTL 内跳过，避免每个 job 重复 2s ping 死 executor 直到 HealthCheck 标 DOWN。 */
    private final Cache<String, Boolean> pingBlacklist;
    public QueueWorker(AdhocQueryJobMapper jobMapper, AdhocExecutorInstanceMapper executorMapper,
                       ExecutorChannelPool executorChannelPool, ServerInstanceInfo instanceInfo,
                       JobLogRegistry jobLogRegistry,
                       io.gitee.songchaolin.adhoc.server.ha.RunningJobRegistry runningJobRegistry,
                       ConfigHolder cfg) {
        this.jobMapper = jobMapper;
        this.executorMapper = executorMapper;
        this.executorChannelPool = executorChannelPool;
        this.instanceInfo = instanceInfo;
        this.jobLogRegistry = jobLogRegistry;
        this.runningJobRegistry = runningJobRegistry;
        this.cfg = cfg;
        this.intervalMs = cfg.get(AdhocServerConfig.SCHEDULE_INTERVAL_MS);
        this.recentWindowHours = cfg.get(AdhocServerConfig.SCHEDULE_RECENT_WINDOW_HOURS);
        this.eventTriggerEnabled = cfg.get(AdhocServerConfig.SCHEDULE_EVENT_TRIGGER_ENABLED);
        this.serverGrpcInstance = instanceInfo.getGrpcInstance();
        this.pingBlacklist = CacheBuilder.newBuilder()
                .expireAfterWrite(cfg.get(AdhocServerConfig.SCHEDULE_PING_BLACKLIST_MS), TimeUnit.MILLISECONDS)
                .build();
    }

    public void start() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "queue-worker");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleWithFixedDelay(() -> {
            try { dispatcher(); } catch (Throwable t) { log.warn("queue-worker caught throwable: {}", t.getMessage()); }
        }, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
        triggerExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "queue-worker-trigger");
            t.setDaemon(true);
            return t;
        });
        log.info("queue-worker started, interval={}ms, recentWindow={}h", intervalMs, recentWindowHours);
    }

    /** 提交时事件触发派发：非阻塞，合并多次触发为一次 dispatcher()（CAS 抢占保证不重复派发）。
     *  轮询 scheduleWithFixedDelay 仍作兜底（trigger 丢失/revert 回退的 job 下一轮被捞起）。 */
    public void triggerDispatch() {
        if (!eventTriggerEnabled) {
            return;  // 纯轮询模式（测试用）
        }
        if (triggerPending.compareAndSet(false, true)) {
            try {
                triggerExecutor.execute(() -> {
                    try {
                        dispatcher();
                    } catch (Throwable t) {
                        log.warn("trigger-dispatch caught throwable: {}", t.getMessage());
                    } finally {
                        triggerPending.set(false);
                    }
                });
            } catch (java.util.concurrent.RejectedExecutionException e) {
                // executor 已 shutdown（应用关停期），清 flag 防 stuck；job 留 PENDING 给下轮轮询/重启后处理
                triggerPending.set(false);
                log.debug("trigger-dispatch rejected (executor shutting down)");
            }
        }
    }

    public void dispatcher() {
        // 每轮尽量多派发，直到无可调度 job（限流/无容量 executor/队列空）或派发失败回退。
        // selectOnePendingJobId 已过滤 running 限流 + EXISTS(有容量 executor)，故被选中的 job 一定能派发（罕有竞态回退）。
        int dispatchedThisScan = 0;
        int iter = 0;
        // scan 内按 engineType 缓存 executor 列表（一次 scan 内 executor 集合基本不变，避免每 job 重复查 DB）
        Map<String, List<AdhocExecutorInstance>> executorCache = new HashMap<>();
        while (iter++ < 200) {
            String jobId = jobMapper.selectOnePendingJobId(
                    cfg.get(AdhocServerConfig.MAX_RUNNING_JOBS_GLOBAL),
                    cfg.get(AdhocServerConfig.MAX_RUNNING_JOBS_PER_USER),
                    cfg.get(AdhocServerConfig.MAX_RUNNING_JOBS_PER_SERVER),
                    instanceInfo.getId(),
                    recentWindowHours);
            if (jobId == null) {
                if (dispatchedThisScan == 0) {
                    logThrottleIfBlocked(); // 本轮一个都没派出，打印卡在哪
                }
                break;
            }
            AdhocQueryJob job = jobMapper.selectById(jobId);
            if (job == null) {
                continue;
            }
            // 先查 executor 可用性再 claim，避免 claim->revert 抖动（EXISTS 已保证有容量，此处 empty=刚宕机）
            List<AdhocExecutorInstance> executors = executorCache
                    .computeIfAbsent(job.getEngineType(), executorMapper::selectAllUpAcceptingByEngineType);
            if (executors.isEmpty()) {
                log.warn("【无可用executor】jobId={} 引擎={} 无UP executor，本轮停止", jobId, job.getEngineType());
                break;
            }
            Date dispatchTime = new Date();
            int claimed = jobMapper.claimJob(jobId, instanceInfo.getId(), dispatchTime);
            if (claimed == 0) {
                continue; // 被其它 worker 抢，试下一个
            }
            log.info("【调度认领】jobId={} server={}", jobId, instanceInfo.getId());
            runningJobRegistry.add(jobId);
            String queueDur = LogTiming.fmt(job.getSubmitTime(), dispatchTime);
            jobLogRegistry.append(jobId, "[server] [INFO] [job=" + jobId + "] claimed by server=" + instanceInfo.getId()
                    + (queueDur != null ? " (queue=" + queueDur + ")" : ""));

            DispatchJobRequest req = DispatchJobRequest.newBuilder()
                    .setJobId(job.getJobId())
                    .setUserId(job.getUserId() == null ? "" : job.getUserId())
                    .setUserName(job.getUserName() == null ? "" : job.getUserName())
                    .setEngineType(job.getEngineType())
                    .setEngineInstance(job.getEngineInstance() == null ? "" : job.getEngineInstance())
                    .setSqlContent(job.getSqlContent())
                    .setServerInstanceId(serverGrpcInstance)
                    .build();

            if (tryDispatch(jobId, executors, req)) {
                dispatchedThisScan++;
                continue; // 成功，继续下一个 job
            }
            // 全部 executor busy/拒绝/失败 -> 回退 + 清 registry + 本轮停（容量满/竞态，下轮再试，避免同 job 反复 claim/revert）
            runningJobRegistry.remove(jobId);
            jobMapper.revertToPending(jobId);
            jobLogRegistry.append(jobId, "[server] [WARN] [job=" + jobId + "] no executor / dispatch failed, revert PENDING");
            break;
        }
    }

    /** failover 派发：逐个 ping + dispatchJob，accepted=true 即成功返回。全失败/全拒绝返回 false。
     *  ping/dispatch 连接类失败的 executor 进短期黑名单（pingBlacklist），TTL 内跳过，避免每个 job 重复 2s ping 死 executor。 */
    private boolean tryDispatch(String jobId, List<AdhocExecutorInstance> executors, DispatchJobRequest req) {
        for (AdhocExecutorInstance exec : executors) {
            String instanceId = exec.getInstanceId();
            if (pingBlacklist.getIfPresent(instanceId) != null) {
                log.debug("[job={}] executor {} blacklisted (recent ping/dispatch fail), skip", jobId, instanceId);
                continue;
            }
            if (!executorChannelPool.ping(instanceId)) {
                pingBlacklist.put(instanceId, Boolean.TRUE);
                log.warn("[job={}] executor {} ping failed, blacklist", jobId, instanceId);
                continue;
            }
            try {
                DispatchJobResponse resp = executorChannelPool.dispatchJob(exec, req);
                log.info("【派发】jobId={} -> executor={} ({}:{}) accepted={}", jobId, instanceId,
                        exec.getHost(), exec.getGrpcPort(), resp.getAccepted());
                jobLogRegistry.append(jobId, "[server] [INFO] [job=" + jobId + "] dispatched to " + instanceId
                        + ", accepted=" + resp.getAccepted());
                if (resp.getAccepted()) {
                    return true;
                }
                // accepted=false（executor 容量满 Semaphore 拒）-> try next executor
            } catch (Exception e) {
                pingBlacklist.put(instanceId, Boolean.TRUE);
                log.warn("[job={}] dispatch to executor {} failed: {}, blacklist", jobId, instanceId, e.getMessage());
            }
        }
        return false;
    }

    public void stop() {
        if (scheduler != null) {
            scheduler.shutdown();
        }
        if (triggerExecutor != null) {
            triggerExecutor.shutdown();
        }
    }

    /** selectOnePendingJobId 返回 null 且本轮一个都没派出时，打印卡在哪一维（队列空/running 限流/无 executor 容量）。
     *  计数加 recentWindow 时间窗，避免终态历史行参与扫描。 */
    private void logThrottleIfBlocked() {
        Date recent = new Date(System.currentTimeMillis() - recentWindowHours * 3600_000L);
        long pending = jobMapper.selectCount(new LambdaQueryWrapper<AdhocQueryJob>()
                .eq(AdhocQueryJob::getStatus, JobStatus.PENDING.name())
                .gt(AdhocQueryJob::getSubmitTime, recent));
        if (pending == 0) {
            return; // 队列空，无事
        }
        long runningGlobal = jobMapper.selectCount(new LambdaQueryWrapper<AdhocQueryJob>()
                .in(AdhocQueryJob::getStatus, JobStatus.DISPATCHING.name(), JobStatus.RUNNING.name())
                .gt(AdhocQueryJob::getSubmitTime, recent));
        if (runningGlobal >= cfg.get(AdhocServerConfig.MAX_RUNNING_JOBS_GLOBAL)) {
            log.info("【限流等待】{} 个PENDING等待, 卡在 global running {}/{} ; 下轮重试",
                    pending, runningGlobal, cfg.get(AdhocServerConfig.MAX_RUNNING_JOBS_GLOBAL));
            return;
        }
        long processingThisServer = jobMapper.selectCount(new LambdaQueryWrapper<AdhocQueryJob>()
                .eq(AdhocQueryJob::getProcessingServerInstance, instanceInfo.getId())
                .in(AdhocQueryJob::getStatus, JobStatus.DISPATCHING.name(), JobStatus.RUNNING.name())
                .gt(AdhocQueryJob::getSubmitTime, recent));
        if (processingThisServer >= cfg.get(AdhocServerConfig.MAX_RUNNING_JOBS_PER_SERVER)) {
            log.info("【限流等待】{} 个PENDING等待, 卡在 per-server {}/{} ; 下轮重试",
                    pending, processingThisServer, cfg.get(AdhocServerConfig.MAX_RUNNING_JOBS_PER_SERVER));
            return;
        }
        long upExec = executorMapper.selectCount(new LambdaQueryWrapper<AdhocExecutorInstance>()
                .eq(AdhocExecutorInstance::getStatus, "UP")
                .eq(AdhocExecutorInstance::getAccepting, 1));
        if (upExec == 0) {
            log.info("【限流等待】{} 个PENDING等待, 卡在 无UP executor ; 下轮重试", pending);
        } else {
            log.info("【限流等待】{} 个PENDING等待, 卡在 executor容量满 或 per-user限流{} ; 下轮重试",
                    pending, cfg.get(AdhocServerConfig.MAX_RUNNING_JOBS_PER_USER));
        }
    }
}