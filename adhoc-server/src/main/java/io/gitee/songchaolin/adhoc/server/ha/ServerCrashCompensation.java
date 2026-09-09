package io.gitee.songchaolin.adhoc.server.ha;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.gitee.songchaolin.adhoc.common.config.ConfigHolder;
import io.gitee.songchaolin.adhoc.common.enums.JobStatus;
import io.gitee.songchaolin.adhoc.dao.entity.AdhocQueryJob;
import io.gitee.songchaolin.adhoc.dao.mapper.AdhocQueryJobMapper;
import io.gitee.songchaolin.adhoc.server.config.AdhocServerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * server 宕机补偿：
 * ① 启动恢复 recoverOnStartup：本节点重启前承接的 DISPATCHING job 回退 PENDING（重派）；RUNNING job 重建 RunningJobRegistry（防 reconcile 误判 lost）。
 * ② 周期补偿 compensate：DOWN server 的 RUNNING job 清 processing_server（executor 继续）；DISPATCHING job 回退 PENDING（重派）。
 * PENDING Job 由 QueueWorker CAS 抢占（任何 server 可接）。
 */
@Component
public class ServerCrashCompensation {

    private static final Logger log = LoggerFactory.getLogger(ServerCrashCompensation.class);
    private final AdhocQueryJobMapper jobMapper;
    private final ServerInstanceInfo instanceInfo;
    private final RunningJobRegistry runningJobRegistry;
    private final ConfigHolder cfg;

    private ScheduledExecutorService scheduler;

    public ServerCrashCompensation(AdhocQueryJobMapper jobMapper, ServerInstanceInfo instanceInfo,
                                   RunningJobRegistry runningJobRegistry, ConfigHolder cfg) {
        this.jobMapper = jobMapper;
        this.instanceInfo = instanceInfo;
        this.runningJobRegistry = runningJobRegistry;
        this.cfg = cfg;
    }

    public void start() {
        long intervalMs = cfg.get(AdhocServerConfig.COMPENSATION_INTERVAL_MS);
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "server-crash-comp");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleWithFixedDelay(() -> {
            try { compensate(); } catch (Throwable t) { log.warn("server-crash-comp caught throwable: {}", t.getMessage()); }
        }, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
        log.info("server-crash-comp started, interval={}ms", intervalMs);
    }

    public void compensate() {
        int cleared = jobMapper.clearProcessingServerForDownServers();      // RUNNING 清 processing_server
        int reverted = jobMapper.revertDispatchingForDownServers();         // DISPATCHING 回退 PENDING（重派）
        if (cleared > 0 || reverted > 0) {
            log.warn("server-crash-comp: cleared processing_server for {} RUNNING job(s), reverted {} DISPATCHING job(s) to PENDING",
                    cleared, reverted);
        }
    }

    /** server 启动恢复：本节点重启前承接的 DISPATCHING job 回退 PENDING（重派）；RUNNING job 重建 RunningJobRegistry（防 reconcile 误判 lost）。 */
    public void recoverOnStartup() {
        String myServerId = instanceInfo.getId();
        List<AdhocQueryJob> jobs = jobMapper.selectList(new LambdaQueryWrapper<AdhocQueryJob>()
                .eq(AdhocQueryJob::getProcessingServerInstance, myServerId)
                .in(AdhocQueryJob::getStatus, JobStatus.DISPATCHING.name(), JobStatus.RUNNING.name()));
        int reverted = 0, recovered = 0;
        for (AdhocQueryJob job : jobs) {
            if (JobStatus.DISPATCHING.is(job.getStatus())) {
                jobMapper.revertToPending(job.getJobId());
                reverted++;
            } else if (JobStatus.RUNNING.is(job.getStatus())) {
                runningJobRegistry.add(job.getJobId());
                recovered++;
            }
        }
        if (reverted > 0 || recovered > 0) {
            log.warn("server-crash-comp startup recovery: reverted {} DISPATCHING job(s) to PENDING, recovered {} RUNNING job(s) into registry",
                    reverted, recovered);
        }
    }

    public void stop() {
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }
}
