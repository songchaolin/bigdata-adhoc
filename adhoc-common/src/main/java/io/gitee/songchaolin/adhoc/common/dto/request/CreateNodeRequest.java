package io.gitee.songchaolin.adhoc.common.dto.request;

import lombok.Data;

/**
 * 创建节点请求。用户身份由网关 USER 头注入，无需传输。
 */
@Data
public class CreateNodeRequest {

    /**
     * 父节点ID（可选，为空则挂载到根目录）
     */
    private String parentNodeId;

    /**
     * 节点类型：DIRECTORY/FILE（必填）
     */
    private String nodeType;

    /**
     * 节点名称（必填）
     */
    private String nodeName;

    /**
     * SQL内容（FILE类型必填）
     */
    private String sqlContent;

    /**
     * 描述
     */
    private String description;
}