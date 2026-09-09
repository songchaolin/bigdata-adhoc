package io.gitee.songchaolin.adhoc.common.config;

import com.ctrip.framework.apollo.Config;
import com.ctrip.framework.apollo.ConfigService;
import com.ctrip.framework.apollo.model.ConfigChange;
import com.ctrip.framework.apollo.model.ConfigChangeEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Apollo 配置变更监听：启动时对每个 namespace 注册 {@link Config#addChangeListener}，
 * 配置变更时打印 namespace/key/旧值/新值/变更类型，方便排查 Apollo 是否生效、谁改了什么。
 *
 * <p>{@code @ConditionalOnClass(ConfigService.class)}：仅 apollo-client 在 classpath 时生效
 * （server/executor 有 apollo-client -> 生效；sdk/parser 无 -> 跳过，不引入 apollo 依赖）。
 *
 * <p>监听 namespace 来源：{@code apollo.bootstrap.namespaces}（fallback {@code apollo.namespaces}，默认 application）。
 * <p>password 类 key 脱敏（旧/新值显示 ****）。
 * <p>注册失败（Apollo 未配/不可达）不阻断启动（catch Throwable，记 warn）。
 */
@Component
@ConditionalOnClass(ConfigService.class)
public class ApolloConfigChangeLogger {

    private static final Logger log = LoggerFactory.getLogger(ApolloConfigChangeLogger.class);

    private final Environment env;

    public ApolloConfigChangeLogger(Environment env) {
        this.env = env;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void register() {
        // 测试环境 apollo.bootstrap.enabled=false（且排除 ApolloAutoConfiguration），跳过注册避免 ConfigService 无 meta 时 throw/卡顿
        if (!Boolean.parseBoolean(env.getProperty("apollo.bootstrap.enabled", "false"))) {
            log.debug("【Apollo监听】apollo.bootstrap.enabled=false，跳过配置变更监听注册");
            return;
        }
        String namespaces = env.getProperty("apollo.bootstrap.namespaces",
                env.getProperty("apollo.namespaces", "application"));
        for (String ns : namespaces.split(",")) {
            String namespace = ns.trim();
            if (namespace.isEmpty()) {
                continue;
            }
            try {
                Config config = ConfigService.getConfig(namespace);
                config.addChangeListener(this::onChange);
                log.info("【Apollo监听】配置变更监听已注册: namespace={}", namespace);
            } catch (Throwable e) {
                log.warn("【Apollo监听】注册失败 namespace={}: {}", namespace, e.getMessage());
            }
        }
    }

    private void onChange(ConfigChangeEvent event) {
        for (String key : event.changedKeys()) {
            ConfigChange c = event.getChange(key);
            log.info("【Apollo配置变更】namespace={} key={} 旧值={} 新值={} 变更类型={}",
                    event.getNamespace(), key, maskIfPassword(key, c.getOldValue()),
                    maskIfPassword(key, c.getNewValue()), c.getChangeType());
        }
    }

    private static String maskIfPassword(String key, String value) {
        if (key != null && key.toLowerCase().contains("password") && value != null && !value.isEmpty()) {
            return "****";
        }
        return value;
    }
}
