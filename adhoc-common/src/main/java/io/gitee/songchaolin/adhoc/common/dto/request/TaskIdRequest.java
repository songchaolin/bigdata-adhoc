package io.gitee.songchaolin.adhoc.common.dto.request;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * Task 详情查询请求
 */
@Data
@ApiModel(description = "Task 详情查询请求")
public class TaskIdRequest {

    @NotBlank(message = "taskId 不能为空")
    @ApiModelProperty(value = "Task ID", required = true)
    private String taskId;
}