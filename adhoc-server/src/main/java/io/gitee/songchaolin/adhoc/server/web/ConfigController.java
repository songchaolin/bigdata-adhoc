package io.gitee.songchaolin.adhoc.server.web;

import io.gitee.songchaolin.adhoc.common.config.AdhocCommonConfig;
import io.gitee.songchaolin.adhoc.common.config.ConfigCatalog;
import io.gitee.songchaolin.adhoc.common.config.ConfigHolder;
import io.gitee.songchaolin.adhoc.common.dto.ConfigItemView;
import io.gitee.songchaolin.adhoc.metadata.config.AdhocMetadataConfig;
import io.gitee.songchaolin.adhoc.server.config.AdhocServerConfig;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 配置自描述端点：GET /api/config 枚举 {@link AdhocCommonConfig}+{@link AdhocServerConfig}+{@link AdhocMetadataConfig}
 * 的 ConfigItem 常量，输出每项 key/当前值(Apollo 覆盖后)/默认值/描述/效果。反射枚举见 {@link ConfigCatalog}（与启动日志共用）。
 * 敏感字段（含 password）脱敏为 ****。裸返回由 {@link io.gitee.songchaolin.adhoc.server.aspect.AdhocResponseAdvice} 统一包 Result。
 */
@Api(tags = "Adhoc Config")
@RestController
@RequestMapping("/api")
public class ConfigController {

    private static final ConfigCatalog CATALOG =
            ConfigCatalog.of(AdhocCommonConfig.class, AdhocServerConfig.class, AdhocMetadataConfig.class);

    private final ConfigHolder cfg;

    public ConfigController(ConfigHolder cfg) {
        this.cfg = cfg;
    }

    @ApiOperation("配置清单（key/当前值/默认值/描述/效果，敏感字段脱敏）")
    @GetMapping("/config")
    public List<ConfigItemView> list() {
        return CATALOG.inspect(cfg);
    }
}
