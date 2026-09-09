package io.gitee.songchaolin.adhoc.executor.config;

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
 * executor 启动打印全量配置（{@link AdhocCommonConfig}+{@link AdhocExecutorConfig}）。
 * executor 无 HTTP 端口，配置查看靠此启动日志（password 脱敏）。枚举逻辑与 server {@code ConfigController} 共用 {@link ConfigCatalog}。
 */
@Component
public class StartupConfigLogger {
    private static final Logger log = LoggerFactory.getLogger(StartupConfigLogger.class);
    private static final ConfigCatalog CATALOG =
            ConfigCatalog.of(AdhocCommonConfig.class, AdhocExecutorConfig.class);

    private final ConfigHolder cfg;

    public StartupConfigLogger(ConfigHolder cfg) {
        this.cfg = cfg;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void dump() {
        List<ConfigItemView> items = CATALOG.inspect(cfg);
        log.info("==== executor adhoc config ({} 项) ====", items.size());
        for (ConfigItemView item : items) {
            log.info("{}", ConfigCatalog.formatLog(item));
        }
    }
}
