package io.gitee.songchaolin.adhoc.common.dto.request;

import lombok.Data;

/**
 * 列出子节点请求。用户身份由网关 USER 头注入，无需传输。
 */
@Data
public class ListNodesRequest {

    /**
     * 父节点ID（可选，为空则返回根目录）
     */
    private String parentNodeId;
}
