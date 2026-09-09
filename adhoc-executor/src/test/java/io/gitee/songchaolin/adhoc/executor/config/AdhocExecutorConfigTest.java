package io.gitee.songchaolin.adhoc.executor.config;

import io.gitee.songchaolin.adhoc.common.config.AdhocCommonConfig;
import io.gitee.songchaolin.adhoc.common.config.ConfigItem;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 完整性：{@link AdhocCommonConfig}+{@link AdhocExecutorConfig} 的每个 {@link ConfigItem} 常量
 * 都有非空 key + 非空描述 + 非空默认值。新增配置项漏填描述/默认此测试即失败。
 */
class AdhocExecutorConfigTest {

    @Test
    void everyConfigItemHasKeyDescAndDefault() {
        List<String> problems = new ArrayList<>();
        check(AdhocCommonConfig.class, problems);
        check(AdhocExecutorConfig.class, problems);
        assertThat(problems).as(problems.toString()).isEmpty();
    }

    private void check(Class<?> configClass, List<String> problems) {
        for (Field f : configClass.getFields()) {
            if (!Modifier.isStatic(f.getModifiers()) || !ConfigItem.class.isAssignableFrom(f.getType())) {
                continue;
            }
            try {
                ConfigItem<?> item = (ConfigItem<?>) f.get(null);
                if (item.getKey() == null || item.getKey().isEmpty()) {
                    problems.add(configClass.getSimpleName() + "." + f.getName() + ": key 空");
                }
                if (item.getDesc() == null || item.getDesc().isEmpty()) {
                    problems.add(configClass.getSimpleName() + "." + f.getName() + ": desc 空");
                }
                if (item.getDefaultValue() == null) {
                    problems.add(configClass.getSimpleName() + "." + f.getName() + ": default null");
                }
            } catch (Exception e) {
                problems.add(configClass.getSimpleName() + "." + f.getName() + ": " + e.getMessage());
            }
        }
    }
}
