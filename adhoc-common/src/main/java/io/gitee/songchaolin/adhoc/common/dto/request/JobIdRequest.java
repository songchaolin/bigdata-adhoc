package io.gitee.songchaolin.adhoc.common.dto.request;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/** POST /api/job/detail|status|cancel 请求体。 */
@Data
public class JobIdRequest {
    @NotBlank(message = "jobId 不能为空")
    private String jobId;
}
