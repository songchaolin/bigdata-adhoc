package io.gitee.songchaolin.adhoc.common.dto.request;

import lombok.Data;

/**
 * 搜索节点请求。用户身份由网关 USER 头注入，无需传输。
 */
@Data
public class SearchNodesRequest {

    /**
     * 搜索关键词
     */
    private String keyword;

    /**
     * 节点类型过滤（可选）
     */
    private String type;
}
