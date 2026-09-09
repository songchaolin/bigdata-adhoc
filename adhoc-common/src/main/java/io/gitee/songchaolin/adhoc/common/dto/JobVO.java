package io.gitee.songchaolin.adhoc.common.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.Date;

/** Job 列表/分页 VO（只暴露前端需要的字段，不含 sql_content 等大字段）。纯 POJO，entity->VO 映射由 server 侧完成。 */
@Data
@ApiModel(description = "Job 列表项")
public class JobVO {

    @ApiModelProperty("Job ID")
    private String jobId;
    @ApiModelProperty("用户 ID")
    private String userId;
    @ApiModelProperty("用户名")
    private String userName;
    @ApiModelProperty("引擎类型")
    private String engineType;
    @ApiModelProperty("状态")
    private String status;
    @ApiModelProperty("提交时间")
    private Date submitTime;
    @ApiModelProperty("开始时间")
    private Date startTime;
    @ApiModelProperty("结束时间")
    private Date finishTime;
    @ApiModelProperty("耗时(ms)")
    private Long durationMs;
    @ApiModelProperty("执行 executor")
    private String executorInstance;
}
