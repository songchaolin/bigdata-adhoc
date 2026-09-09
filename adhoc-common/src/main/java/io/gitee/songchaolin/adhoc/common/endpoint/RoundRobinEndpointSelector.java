package io.gitee.songchaolin.adhoc.common.endpoint;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 轮询 Endpoint 选择器（一期实现）
 * <p>
 * 简单轮询，按顺序依次选择 endpoint。为后续负载均衡铺路。
 */
public class RoundRobinEndpointSelector implements EngineEndpointSelector {

    private final AtomicInteger index = new AtomicInteger(0);

    @Override
    public EngineEndpoint select(List<EngineEndpoint> endpoints) {
        if (endpoints == null || endpoints.isEmpty()) {
            throw new IllegalStateException("No endpoints available");
        }

        // 过滤健康的 endpoint
        List<EngineEndpoint> healthy = endpoints.stream()
                .filter(EngineEndpoint::isHealthy)
                .collect(java.util.stream.Collectors.toList());

        if (healthy.isEmpty()) {
            throw new IllegalStateException("No healthy endpoints available");
        }

        // 轮询选择
        int idx = Math.abs(index.getAndIncrement() % healthy.size());
        return healthy.get(idx);
    }
}