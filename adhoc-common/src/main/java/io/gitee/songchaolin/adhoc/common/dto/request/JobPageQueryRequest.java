package io.gitee.songchaolin.adhoc.common.dto.request;

import io.gitee.songchaolin.adhoc.common.dto.common.PageRequest;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Job 分页查询请求（对齐公司模版 DemoPlanPageQueryRequest）。 */
@Data
@EqualsAndHashCode(callSuper = true)
@ApiModel(description = "Job 分页查询请求")
public class JobPageQueryRequest extends PageRequest {

    @ApiModelProperty("Job 状态（可选过滤：PENDING/RUNNING/SUCCESS/FAILED/PARTIAL_FAILED/CANCELED）")
    private String status;

    @ApiModelProperty("引擎类型（可选过滤：KYUUBI/STARROCKS）")
    private String engineType;

    @ApiModelProperty("脚本id")
    private String fileNodeId;
}
