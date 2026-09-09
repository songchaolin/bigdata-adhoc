package io.gitee.songchaolin.adhoc.common.config;

/**
 * 一个配置项的显式声明（Hadoop {@code <property><name/><value/><description/></property>} 的 Java 类型安全版）。
 * 不可变，承载完整 key + 默认值 + 中文描述 + 效果 + 值类型。在 {@code AdhocCommonConfig}/
 * {@code AdhocServerConfig}/{@code AdhocExecutorConfig} 里平铺为 {@code public static final} 常量。
 *
 * <p>full key 显式权威（不从字段名推导）；默认值单一真相源（无字段初始化器+注解双写）。
 * 运行时由 {@link ConfigHolder#get(ConfigItem)} 读 Environment：Apollo 有则取 Apollo，否则默认值，每次读活值（动态刷新）。
 *
 * @param <T> 值类型（Integer/Long/Boolean/String）
 */
public final class ConfigItem<T> {
    private final String key;
    private final T defaultValue;
    private final String desc;
    private final String effect;
    private final Class<T> type;

    private ConfigItem(String key, T defaultValue, String desc, String effect) {
        this.key = key;
        this.defaultValue = defaultValue;
        this.desc = desc == null ? "" : desc;
        this.effect = effect == null ? "" : effect;
        @SuppressWarnings("unchecked")
        Class<T> t = (Class<T>) defaultValue.getClass();
        this.type = t;
    }

    /** 无效果说明。 */
    public static <T> ConfigItem<T> of(String key, T defaultValue, String desc) {
        return new ConfigItem<>(key, defaultValue, desc, "");
    }

    /** 含效果说明。 */
    public static <T> ConfigItem<T> of(String key, T defaultValue, String desc, String effect) {
        return new ConfigItem<>(key, defaultValue, desc, effect);
    }

    public String getKey() { return key; }
    public T getDefaultValue() { return defaultValue; }
    public String getDesc() { return desc; }
    public String getEffect() { return effect; }
    public Class<T> getType() { return type; }
}
