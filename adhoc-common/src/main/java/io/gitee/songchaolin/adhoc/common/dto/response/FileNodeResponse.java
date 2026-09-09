package io.gitee.songchaolin.adhoc.common.dto.response;

import lombok.Data;

import java.util.Date;
import java.util.List;

/**
 * 文件节点响应
 */
@Data
public class FileNodeResponse {
    /** 节点ID */
    private String nodeId;

    /** 父节点ID */
    private String parentNodeId;

    /** 节点类型：DIRECTORY / FILE */
    private String nodeType;

    /** 节点名称 */
    private String nodeName;

    /** SQL 内容（仅 FILE 类型有值） */
    private String sqlContent;

    /** 描述（仅 FILE 类型有值） */
    private String description;

    /** 创建时间 */
    private Date createTime;

    /** 更新时间 */
    private Date updateTime;

    /** 子节点（树形结构时使用） */
    private List<FileNodeResponse> children;
}