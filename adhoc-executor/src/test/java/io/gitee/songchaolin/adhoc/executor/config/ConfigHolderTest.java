package io.gitee.songchaolin.adhoc.executor.config;

import io.gitee.songchaolin.adhoc.common.config.ConfigHolder;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 证明 Apollo（高优先级 property source）对 {@link AdhocExecutorConfig} 的覆盖 + 动态刷新：
 * Apollo bootstrap 把 Apollo 注册为高优先级 PropertySource；ConfigHolder.get = env.getProperty(key,type,default)，
 * 每次 read 活值、无缓存 -> Apollo 配置生效，且运行时推送新值立即生效。
 */
class ConfigHolderTest {

    @Test
    void apolloOverride_andDynamicRefresh() {
        StandardEnvironment env = new StandardEnvironment();
        ConfigHolder cfg = new ConfigHolder(env);

        // 1. 无 Apollo 配置 -> ConfigItem 默认值生效
        assertThat(cfg.get(AdhocExecutorConfig.EXECUTOR_RESULT_LIMIT)).isEqualTo(1000000);
        assertThat(cfg.get(AdhocExecutorConfig.KYUUBI_DEFAULT_INSTANCE)).isEqualTo("kyuubi-01");
        assertThat(cfg.get(AdhocExecutorConfig.EXECUTOR_MAX_CONCURRENT_TASKS)).isEqualTo(10000);

        // 2. Apollo 下发（模拟 Apollo PropertySource 以高优先级 addFirst 注入 Environment）
        Map<String, Object> apollo = new HashMap<>();
        apollo.put("adhoc.executor.result-limit", "500");
        apollo.put("adhoc.engine.KYUUBI.default_instance", "kyuubi-02");
        apollo.put("adhoc.executor.max-concurrent-tasks", "20");
        env.getPropertySources().addFirst(new MapPropertySource("apollo", apollo));

        //    -> Apollo 值覆盖默认（字符串经 Spring 转成 Integer）
        assertThat(cfg.get(AdhocExecutorConfig.EXECUTOR_RESULT_LIMIT)).isEqualTo(500);
        assertThat(cfg.get(AdhocExecutorConfig.KYUUBI_DEFAULT_INSTANCE)).isEqualTo("kyuubi-02");
        assertThat(cfg.get(AdhocExecutorConfig.EXECUTOR_MAX_CONCURRENT_TASKS)).isEqualTo(20);

        // 3. Apollo 运行时改值推送（同一 source 内 key 变更）-> cfg.get 立即读到新值（无缓存，动态刷新）
        apollo.put("adhoc.executor.result-limit", "999");
        assertThat(cfg.get(AdhocExecutorConfig.EXECUTOR_RESULT_LIMIT)).isEqualTo(999);

        // 4. Apollo 删除某 key（移除覆盖）-> 回落 ConfigItem 默认值
        apollo.remove("adhoc.executor.result-limit");
        assertThat(cfg.get(AdhocExecutorConfig.EXECUTOR_RESULT_LIMIT)).isEqualTo(1000000);
    }
}
