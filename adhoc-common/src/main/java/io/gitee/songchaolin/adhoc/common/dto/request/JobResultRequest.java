package io.gitee.songchaolin.adhoc.common.dto.request;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;

/** POST /api/job/result 请求体：一次性返回 Job 下所有 task 的结果第一页（按 segmentIndex 排列）。 */
@Data
@EqualsAndHashCode(callSuper = true)
@ApiModel(description = "Job 结果聚合查询请求")
public class JobResultRequest extends JobIdRequest {

    @ApiModelProperty("每个 task 返回的结果行数（第一页），默认 20，最大 100")
    @Min(value = 1, message = "size 至少为 1")
    @Max(value = 100, message = "size 最大为 100")
    private Integer size = 20;
}
