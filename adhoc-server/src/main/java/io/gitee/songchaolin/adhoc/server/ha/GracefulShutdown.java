package io.gitee.songchaolin.adhoc.server.ha;

import io.gitee.songchaolin.adhoc.common.config.ConfigHolder;
import io.gitee.songchaolin.adhoc.dao.mapper.AdhocServerInstanceMapper;
import io.gitee.songchaolin.adhoc.server.config.AdhocServerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.net.InetAddress;

/**
 * 优雅停机：JVM 关闭时（@PreDestroy）标记自身 accepting=0（不再承接新 Job）。
 * 健康检查随后标 DOWN（心跳停止 15s 后）。executor 端同理（可加 @PreDestroy 标 accepting=0）。
 */
@Component
public class GracefulShutdown {

    private static final Logger log = LoggerFactory.getLogger(GracefulShutdown.class);
    private final AdhocServerInstanceMapper serverInstanceMapper;
    private final ConfigHolder cfg;
    /** server.port 是 Spring 标准配置，不纳入 adhoc 配置类（Spring 自管）。 */
    @Value("${server.port:8080}")
    private int httpPort;

    public GracefulShutdown(AdhocServerInstanceMapper serverInstanceMapper, ConfigHolder cfg) {
        this.serverInstanceMapper = serverInstanceMapper;
        this.cfg = cfg;
    }

    @PreDestroy
    public void onShutdown() {
        try {
            String instanceId = cfg.get(AdhocServerConfig.SERVER_INSTANCE_ID);
            String host = InetAddress.getLocalHost().getHostAddress();
            String id = (instanceId == null || instanceId.isEmpty()) ? (host + ":" + httpPort) : instanceId;
            serverInstanceMapper.markAcceptingZero(id);
            log.info("graceful shutdown: marked server {} accepting=0", id);
        } catch (Exception e) {
            log.warn("graceful shutdown failed: {}", e.getMessage());
        }
    }
}
