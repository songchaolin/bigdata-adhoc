package io.gitee.songchaolin.adhoc.common.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 配置项视图（{@code GET /api/config} 返回 + 启动日志打印）：
 * group + key + 当前值 + 默认值 + 描述 + 效果。由 {@code ConfigCatalog.inspect} 从 {@code ConfigItem} 常量 + Environment 派生。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ConfigItemView {
    private String group;
    private String key;
    private Object currentValue;
    private Object defaultValue;
    private String description;
    private String effect;
    /** 值来源 PropertySource 名（Apollo/systemEnvironment/applicationConfig/默认），排查 Apollo 是否生效。 */
    private String source;
}
