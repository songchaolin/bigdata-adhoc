package io.gitee.songchaolin.adhoc.server.ha;

import io.gitee.songchaolin.adhoc.server.schedule.QueueWorker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

/**
 * server 后台服务统一启动/停止：QueueWorker + HealthCheckTask + 两个 CrashCompensation +
 * InstanceCleanup + ServerHeartbeatTask + JvmMetricSampleCleanupTask + JobLogCollector。各任务专用后台线程，不用 @Scheduled 共享池。
 */
@Component
public class AdhocServerServiceStarter {

    private static final Logger log = LoggerFactory.getLogger(AdhocServerServiceStarter.class);

    private final QueueWorker queueWorker;
    private final HealthCheckTask healthCheckTask;
    private final ExecutorCrashCompensation executorCrashCompensation;
    private final ServerCrashCompensation serverCrashCompensation;
    private final InstanceCleanup instanceCleanup;
    private final ServerHeartbeatTask serverHeartbeatTask;
    private final JvmMetricSampleCleanupTask jvmMetricSampleCleanupTask;
    private final JobLogCollector jobLogCollector;
    private final JobReconcileTask jobReconcileTask;
    private final ServerStatusReporter serverStatusReporter;

    public AdhocServerServiceStarter(QueueWorker queueWorker, HealthCheckTask healthCheckTask,
                                     ExecutorCrashCompensation executorCrashCompensation,
                                     ServerCrashCompensation serverCrashCompensation,
                                     InstanceCleanup instanceCleanup,
                                     ServerHeartbeatTask serverHeartbeatTask,
                                     JvmMetricSampleCleanupTask jvmMetricSampleCleanupTask,
                                     JobLogCollector jobLogCollector,
                                     JobReconcileTask jobReconcileTask,
                                     ServerStatusReporter serverStatusReporter) {
        this.queueWorker = queueWorker;
        this.healthCheckTask = healthCheckTask;
        this.executorCrashCompensation = executorCrashCompensation;
        this.serverCrashCompensation = serverCrashCompensation;
        this.instanceCleanup = instanceCleanup;
        this.serverHeartbeatTask = serverHeartbeatTask;
        this.jvmMetricSampleCleanupTask = jvmMetricSampleCleanupTask;
        this.jobLogCollector = jobLogCollector;
        this.jobReconcileTask = jobReconcileTask;
        this.serverStatusReporter = serverStatusReporter;
    }

    @PostConstruct
    public void start() {
        log.info("========== adhoc-server 后台服务启动开始 ==========");
        serverCrashCompensation.recoverOnStartup();  // 启动恢复：本节点重启前承接的 job（在调度前）
        queueWorker.start();
        healthCheckTask.start();
        executorCrashCompensation.start();
        serverCrashCompensation.start();
        instanceCleanup.start();
        serverHeartbeatTask.start();
        jvmMetricSampleCleanupTask.start();
        jobLogCollector.start();
        jobReconcileTask.start();
        serverStatusReporter.start();
        log.info("========== adhoc-server 后台服务启动完成 ==========");
    }

    @PreDestroy
    public void stop() {
        log.info("========== adhoc-server 后台服务停止开始 ==========");
        queueWorker.stop();
        healthCheckTask.stop();
        executorCrashCompensation.stop();
        serverCrashCompensation.stop();
        instanceCleanup.stop();
        serverHeartbeatTask.stop();
        jvmMetricSampleCleanupTask.stop();
        jobLogCollector.stop();
        jobReconcileTask.stop();
        serverStatusReporter.stop();
        log.info("========== adhoc-server 后台服务停止完成 ==========");
    }
}