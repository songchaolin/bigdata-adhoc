package io.gitee.songchaolin.adhoc.common.dto.request;

import lombok.Data;

@Data
public class MoveNodeRequest {
    private String nodeId;
    private String newParentNodeId;
}