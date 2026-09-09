package io.gitee.songchaolin.adhoc.common.dto.request;

import io.gitee.songchaolin.adhoc.common.dto.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import javax.validation.constraints.NotBlank;

/** POST /api/task/result 请求体。分页字段继承公司规范 {@link PageRequest}（current/size，1-based）。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ResultRequest extends PageRequest {

    @NotBlank(message = "taskId 不能为空")
    private String taskId;
}