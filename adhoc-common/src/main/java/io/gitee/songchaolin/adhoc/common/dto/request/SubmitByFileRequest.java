package io.gitee.songchaolin.adhoc.common.dto.request;

import lombok.Data;

@Data
public class SubmitByFileRequest {
    private String nodeId;
    private String sqlContent;
    private String engineType;
    private String engineInstance;
    private String engineParams;
}