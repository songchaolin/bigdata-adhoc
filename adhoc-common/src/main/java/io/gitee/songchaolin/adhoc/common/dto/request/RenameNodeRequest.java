package io.gitee.songchaolin.adhoc.common.dto.request;

import lombok.Data;

@Data
public class RenameNodeRequest {
    private String nodeId;
    private String newName;
}