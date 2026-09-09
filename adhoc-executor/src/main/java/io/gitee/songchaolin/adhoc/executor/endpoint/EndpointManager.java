package io.gitee.songchaolin.adhoc.executor.endpoint;

import com.ctrip.framework.apollo.model.ConfigChangeEvent;
import com.ctrip.framework.apollo.spring.annotation.ApolloConfigChangeListener;
import io.gitee.songchaolin.adhoc.common.endpoint.EngineEndpoint;
import io.gitee.songchaolin.adhoc.common.endpoint.EngineEndpointSelector;
import io.gitee.songchaolin.adhoc.common.endpoint.RoundRobinEndpointSelector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Endpoint 管理器
 * <p>
 * 负责解析配置、缓存 endpoint 列表、提供选择接口。
 * 支持 Apollo 动态更新。
 */
@Component
public class EndpointManager {

    private static final Logger log = LoggerFactory.getLogger(EndpointManager.class);

    private final Map<String, List<EngineEndpoint>> instanceEndpoints = new ConcurrentHashMap<>();
    /** 每实例最近一次注册的 endpoints 原始配置串，用于检测 Apollo 动态变更（变了才 re-parse，避免每次 select 重置轮询计数器）。 */
    private final Map<String, String> instanceEndpointsConfig = new ConcurrentHashMap<>();
    private final EngineEndpointSelector selector = new RoundRobinEndpointSelector();

    /**
     * 刷新指定实例的 endpoint 列表
     *
     * @param instance       实例名（如 kyuubi-01）
     * @param endpointsConfig endpoint 配置（逗号分隔，格式 host:port）
     */
    public void refreshEndpoints(String instance, String endpointsConfig) {
        if (endpointsConfig == null || endpointsConfig.trim().isEmpty()) {
            log.warn("【刷新endpoint】instance={} 配置为空，跳过", instance);
            return;
        }

        String[] parts = endpointsConfig.split(",");
        List<EngineEndpoint> endpoints = new ArrayList<>();
        for (String part : parts) {
            try {
                EngineEndpoint endpoint = EngineEndpoint.fromString(part.trim());
                endpoints.add(endpoint);
            } catch (IllegalArgumentException e) {
                log.warn("【刷新endpoint】解析失败: {}", part, e);
            }
        }

        instanceEndpoints.put(instance, endpoints);
        log.info("【刷新endpoint】instance={} endpoints={}", instance, endpoints);
    }

    /**
     * 确保实例已注册（懒注册）：首次访问该实例 / Apollo 新增该实例 / endpoints 配置变更时刷新。
     * <p>已注册且配置串未变则跳过（保留轮询计数器，不重置）；配置串变了才 re-parse。
     * <p>多实例下 EngineInstanceConfigResolver 每次 resolve 调此方法，非默认实例无需启动预注册。
     *
     * @param instance       实例名
     * @param endpointsConfig endpoint 配置串（逗号分隔 host:port）
     */
    public void ensureRegistered(String instance, String endpointsConfig) {
        if (endpointsConfig == null || endpointsConfig.trim().isEmpty()) {
            return;
        }
        String prev = instanceEndpointsConfig.get(instance);
        if (prev == null || !prev.equals(endpointsConfig)) {
            instanceEndpointsConfig.put(instance, endpointsConfig);
            refreshEndpoints(instance, endpointsConfig);
        }
    }

    /**
     * 选择指定实例的一个 endpoint
     *
     * @param instance 实例名
     * @return 选中的 endpoint
     * @throws IllegalStateException 如果实例不存在或没有可用 endpoint
     */
    public EngineEndpoint selectEndpoint(String instance) {
        List<EngineEndpoint> endpoints = instanceEndpoints.get(instance);
        if (endpoints == null || endpoints.isEmpty()) {
            throw new IllegalStateException("No endpoints for instance: " + instance);
        }

        EngineEndpoint selected = selector.select(endpoints);
        log.debug("【选择endpoint】instance={} endpoints={} selected={}",
                instance, endpoints, selected);

        return selected;
    }

    /**
     * 获取指定实例的 endpoint 列表（只读）
     */
    public List<EngineEndpoint> getEndpoints(String instance) {
        List<EngineEndpoint> endpoints = instanceEndpoints.get(instance);
        return endpoints != null ? new ArrayList<>(endpoints) : new ArrayList<>();
    }

    /**
     * 启动时刷新（由子类调用，传入配置）
     */
    public void onStartup(String instance, String endpointsConfig) {
        refreshEndpoints(instance, endpointsConfig);
    }
}