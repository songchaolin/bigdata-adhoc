package io.gitee.songchaolin.adhoc.server.ha;

import io.gitee.songchaolin.adhoc.common.config.ConfigHolder;
import io.gitee.songchaolin.adhoc.dao.mapper.AdhocServerInstanceMapper;
import io.gitee.songchaolin.adhoc.server.config.AdhocServerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** server 启动自注册：upsert adhoc_server_instance（status=UP, accepting=1）。instance-id 由 ServerInstanceInfo 统一计算。 */
@Component
public class ServerInstanceRegistration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ServerInstanceRegistration.class);
    private final AdhocServerInstanceMapper serverInstanceMapper;
    private final ServerInstanceInfo instanceInfo;
    private final ConfigHolder cfg;

    public ServerInstanceRegistration(AdhocServerInstanceMapper serverInstanceMapper, ServerInstanceInfo instanceInfo,
                                      ConfigHolder cfg) {
        this.serverInstanceMapper = serverInstanceMapper;
        this.instanceInfo = instanceInfo;
        this.cfg = cfg;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            String version = cfg.get(AdhocServerConfig.SERVER_VERSION);
            serverInstanceMapper.upsertOnRegister(instanceInfo.getId(), instanceInfo.getHost(),
                    instanceInfo.getHttpPort(), instanceInfo.getGrpcPort(), version);
            log.info("server instance registered: {}", instanceInfo.getId());
        } catch (Exception e) {
            log.error("server instance registration failed: {}", e.getMessage(), e);
        }
    }
}
