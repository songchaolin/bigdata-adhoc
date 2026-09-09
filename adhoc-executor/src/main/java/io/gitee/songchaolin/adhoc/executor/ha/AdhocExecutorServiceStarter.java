package io.gitee.songchaolin.adhoc.executor.ha;

import io.gitee.songchaolin.adhoc.executor.runner.LogBufferRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

/** executor 后台服务统一启动/停止（ExecutorHeartbeatTask + ExecutorStatusReporter + LogBufferRegistry 兜底定时器）。专用后台线程，不用 @Scheduled。 */
@Component
public class AdhocExecutorServiceStarter {

    private static final Logger log = LoggerFactory.getLogger(AdhocExecutorServiceStarter.class);
    private final ExecutorHeartbeatTask executorHeartbeatTask;
    private final ExecutorStatusReporter executorStatusReporter;
    private final LogBufferRegistry logBufferRegistry;

    public AdhocExecutorServiceStarter(ExecutorHeartbeatTask executorHeartbeatTask,
                                       ExecutorStatusReporter executorStatusReporter,
                                       LogBufferRegistry logBufferRegistry) {
        this.executorHeartbeatTask = executorHeartbeatTask;
        this.executorStatusReporter = executorStatusReporter;
        this.logBufferRegistry = logBufferRegistry;
    }

    @PostConstruct
    public void start() {
        log.info("========== adhoc-executor 后台服务启动开始 ==========");
        executorHeartbeatTask.start();
        executorStatusReporter.start();
        logBufferRegistry.start();
        log.info("========== adhoc-executor 后台服务启动完成 ==========");
    }

    @PreDestroy
    public void stop() {
        log.info("========== adhoc-executor 后台服务停止开始 ==========");
        executorHeartbeatTask.stop();
        executorStatusReporter.stop();
        logBufferRegistry.stop();
        log.info("========== adhoc-executor 后台服务停止完成 ==========");
    }
}
