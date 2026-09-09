package io.gitee.songchaolin.adhoc.common.config;

import io.gitee.songchaolin.adhoc.common.dto.ConfigItemView;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * 配置目录枚举器：反射扫描给定配置类的 {@code public static ConfigItem} 常量，输出 {@link ConfigItemView} 列表。
 * server = {@code of(AdhocCommonConfig.class, AdhocServerConfig.class)}，
 * executor = {@code of(AdhocCommonConfig.class, AdhocExecutorConfig.class)}。
 * 由 {@code ConfigController}（HTTP）与 {@code StartupConfigLogger}（启动日志）共用，消除重复。
 */
public final class ConfigCatalog {

    private final List<Class<?>> configClasses;

    private ConfigCatalog(List<Class<?>> configClasses) {
        this.configClasses = configClasses;
    }

    public static ConfigCatalog of(Class<?>... classes) {
        return new ConfigCatalog(Arrays.asList(classes));
    }

    /** 枚举所有 ConfigItem 常量 -> 视图（当前值=Apollo 或默认，password 脱敏），按 key 排序。 */
    public List<ConfigItemView> inspect(ConfigHolder cfg) {
        List<ConfigItemView> views = new ArrayList<>();
        for (Class<?> c : configClasses) {
            for (Field f : c.getFields()) {
                if (!Modifier.isStatic(f.getModifiers())) {
                    continue;
                }
                if (!ConfigItem.class.isAssignableFrom(f.getType())) {
                    continue;
                }
                try {
                    ConfigItem<?> item = (ConfigItem<?>) f.get(null);
                    views.add(toView(item, cfg));
                } catch (IllegalAccessException e) {
                    // public 字段，不应发生
                }
            }
        }
        views.sort(Comparator.comparing(ConfigItemView::getKey));
        return views;
    }

    private static ConfigItemView toView(ConfigItem<?> item, ConfigHolder cfg) {
        Object current = cfg.get(item);
        Object def = item.getDefaultValue();
        if (item.getKey().toLowerCase().contains("password")) {
            current = mask(current);
            def = mask(def);
        }
        return new ConfigItemView(groupOf(item.getKey()), item.getKey(), current, def,
                item.getDesc(), item.getEffect(), cfg.sourceOf(item));
    }

    /** adhoc.limit.max-tasks-per-job -> limit。 */
    private static String groupOf(String key) {
        String[] parts = key.split("\\.");
        return parts.length >= 2 ? parts[1] : key;
    }

    private static Object mask(Object v) {
        return (v == null || v.toString().isEmpty()) ? null : "****";
    }

    /** 单行日志：{@code adhoc.limit.max-tasks-per-job = 20 (默认=20) [来源=Apollo] // 描述 | 效果}。 */
    public static String formatLog(ConfigItemView v) {
        StringBuilder sb = new StringBuilder(v.getKey());
        sb.append(" = ").append(v.getCurrentValue());
        if (v.getDefaultValue() != null && !String.valueOf(v.getDefaultValue()).isEmpty()) {
            sb.append(" (默认=").append(v.getDefaultValue()).append(")");
        }
        if (v.getSource() != null && !v.getSource().isEmpty()) {
            sb.append(" [来源=").append(v.getSource()).append("]");
        }
        if (v.getDescription() != null && !v.getDescription().isEmpty()) {
            sb.append(" // ").append(v.getDescription());
            if (v.getEffect() != null && !v.getEffect().isEmpty()) {
                sb.append(" | ").append(v.getEffect());
            }
        }
        return sb.toString();
    }
}
