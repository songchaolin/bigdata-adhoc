package io.gitee.songchaolin.adhoc.server.config;

import io.gitee.songchaolin.adhoc.common.config.AdhocCommonConfig;
import io.gitee.songchaolin.adhoc.common.config.ConfigCatalog;
import io.gitee.songchaolin.adhoc.common.config.ConfigHolder;
import io.gitee.songchaolin.adhoc.common.dto.ConfigItemView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * server 启动打印全量配置（{@link AdhocCommonConfig}+{@link AdhocServerConfig}）。
 * 枚举逻辑与 {@code ConfigController} 共用 {@link ConfigCatalog}。password 脱敏。
 * 便于启动即知 Apollo 覆盖后的实际生效值。
 */
@Component
public class StartupConfigLogger {
    private static final Logger log = LoggerFactory.getLogger(StartupConfigLogger.class);
    private static final ConfigCatalog CATALOG =
            ConfigCatalog.of(AdhocCommonConfig.class, AdhocServerConfig.class);

    private final ConfigHolder cfg;

    public StartupConfigLogger(ConfigHolder cfg) {
        this.cfg = cfg;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void dump() {
        List<ConfigItemView> items = CATALOG.inspect(cfg);
        log.info("==== server adhoc config ({} 项) ====", items.size());
        for (ConfigItemView item : items) {
            log.info("{}", ConfigCatalog.formatLog(item));
        }
    }
}
