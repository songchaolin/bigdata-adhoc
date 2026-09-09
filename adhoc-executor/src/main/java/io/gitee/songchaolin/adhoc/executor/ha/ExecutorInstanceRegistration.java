package io.gitee.songchaolin.adhoc.executor.ha;

import io.gitee.songchaolin.adhoc.dao.mapper.AdhocExecutorInstanceMapper;
import io.gitee.songchaolin.adhoc.common.config.ConfigHolder;
import io.gitee.songchaolin.adhoc.executor.config.AdhocExecutorConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** executor 启动自注册：upsert adhoc_executor_instance（status=UP, accepting=1, engine_types）。instance-id 由 ExecutorInstanceInfo 统一计算。 */
@Component
public class ExecutorInstanceRegistration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ExecutorInstanceRegistration.class);
    private final AdhocExecutorInstanceMapper executorInstanceMapper;
    private final ExecutorInstanceInfo instanceInfo;
    private final String version;
    private final String engineTypes;
    private final int maxConcurrentTasks;

    public ExecutorInstanceRegistration(AdhocExecutorInstanceMapper executorInstanceMapper, ExecutorInstanceInfo instanceInfo,
                                        ConfigHolder cfg) {
        this.executorInstanceMapper = executorInstanceMapper;
        this.instanceInfo = instanceInfo;
        this.version = cfg.get(AdhocExecutorConfig.EXECUTOR_VERSION);
        this.engineTypes = cfg.get(AdhocExecutorConfig.EXECUTOR_ENGINE_TYPES);
        this.maxConcurrentTasks = cfg.get(AdhocExecutorConfig.EXECUTOR_MAX_CONCURRENT_TASKS);
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            executorInstanceMapper.upsertOnRegister(instanceInfo.getId(), instanceInfo.getHost(),
                    instanceInfo.getGrpcPort(), version, engineTypes, maxConcurrentTasks);
            log.info("executor instance registered: {}", instanceInfo.getId());
        } catch (Exception e) {
            log.error("executor instance registration failed: {}", e.getMessage(), e);
        }
    }
}
