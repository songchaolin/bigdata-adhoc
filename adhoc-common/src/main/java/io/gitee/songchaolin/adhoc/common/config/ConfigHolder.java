package io.gitee.songchaolin.adhoc.common.config;

import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 配置读取器（Environment 驱动）：{@code cfg.get(AdhocServerConfig.MAX_TASKS_PER_JOB)}。
 *
 * <p>Apollo 优先：Apollo property source 高优先级，{@code env.getProperty} 取 Apollo 值。
 * <p>默认兜底：key 未配时返回 {@link ConfigItem#getDefaultValue()}。
 * <p>动态刷新：每次 {@code get} 读 Environment 活值，Apollo auto-update 刷新后下次 get 即新值（无需 @RefreshScope）。
 * <p>类型安全：{@code ConfigItem<Integer>} -> {@code get} 返回 {@code Integer}。
 */
@Component
public class ConfigHolder {

    private final Environment env;

    public ConfigHolder(Environment env) {
        this.env = env;
    }

    /** Apollo 有则取 Apollo，否则 item 默认值。 */
    public <T> T get(ConfigItem<T> item) {
        return env.getProperty(item.getKey(), item.getType(), item.getDefaultValue());
    }

    /** 读任意动态 key（多实例配置用：adhoc.engine.{ENGINE}.{instance}.user 等）。
     *  <p>Apollo > yml > defaultValue，每次读活值，Apollo auto-update 刷新后下次调用即生效。
     *  <p>defaultValue 传 null 时，key 未配返回 null（用于判断「该实例是否配置」）。 */
    public String getString(String key, String defaultValue) {
        return env.getProperty(key, String.class, defaultValue);
    }

    /** 当前 key 是否被（Apollo/yml/测试）显式配置。用于视图区分"默认"与"已覆盖"。 */
    public boolean isOverridden(ConfigItem<?> item) {
        return env.containsProperty(item.getKey());
    }

    /** 配置值来源（排查 Apollo 是否生效）：返回首个含此 key 的 PropertySource 名（如 Apollo / systemEnvironment / applicationConfig）；都未配返回 "默认"。
     *  <p>注：env 变量走大写下划线（如 ADHOC_EXECUTOR_RESULT_LIMIT），Spring getProperty 做了 relaxed binding 但 containsProperty 不做，
     *  故 env 变量来源可能显示"默认"；Apollo / yml 直配的 key 命中准确。 */
    public String sourceOf(ConfigItem<?> item) {
        String key = item.getKey();
        if (env instanceof ConfigurableEnvironment) {
            for (PropertySource<?> ps : ((ConfigurableEnvironment) env).getPropertySources()) {
                if (ps.containsProperty(key)) {
                    return ps.getName();
                }
            }
        }
        return "默认";
    }

    /** 测试用：overrides 覆盖指定 key，其余走 item 默认。 */
    public static ConfigHolder forTest(Map<String, ?> overrides) {
        StandardEnvironment env = new StandardEnvironment();
        if (overrides != null && !overrides.isEmpty()) {
            Map<String, Object> map = new HashMap<>(overrides);
            env.getPropertySources().addFirst(new MapPropertySource("test-overrides", map));
        }
        return new ConfigHolder(env);
    }

    /** 测试用：全默认（无任何覆盖）。 */
    public static ConfigHolder forTest() {
        return forTest(null);
    }
}
