package io.gitee.songchaolin.adhoc.common.endpoint;

import java.util.List;

/**
 * Endpoint 选择器接口
 */
public interface EngineEndpointSelector {

    /**
     * 从 endpoint 列表中选择一个
     *
     * @param endpoints 可用 endpoint 列表
     * @return 选中的 endpoint
     * @throws IllegalStateException 如果没有可用 endpoint
     */
    EngineEndpoint select(List<EngineEndpoint> endpoints);
}