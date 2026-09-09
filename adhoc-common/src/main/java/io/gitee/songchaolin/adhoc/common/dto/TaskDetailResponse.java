package io.gitee.songchaolin.adhoc.common.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * Task 详情响应
 * <p>
 * 包含 Task 的完整信息，包括基本信息、执行统计、结果信息、时间信息等
 */
@Data
@NoArgsConstructor
@ApiModel(description = "Task 详情响应")
public class TaskDetailResponse {

    // ========== 基本信息 ==========
    @ApiModelProperty("Task ID")
    private String taskId;

    @ApiModelProperty("所属 Job ID")
    private String jobId;

    @ApiModelProperty("段序号")
    private Integer segmentIndex;

    @ApiModelProperty("Task 状态")
    private String status;

    @ApiModelProperty("SQL 类型")
    private String sqlType;

    @ApiModelProperty("是否有结果集")
    private Boolean hasResultSet;

    @ApiModelProperty("SQL 内容")
    private String sqlContent;

    @ApiModelProperty("Prefix SQL (SET/USE)")
    private String prefixSql;

    // ========== 执行统计 ==========
    @ApiModelProperty("结果行数（DQL 查询返回的行数）")
    private Long resultRows;

    @ApiModelProperty("影响行数（DDL/DML 影响的行数）")
    private Long affectedRows;

    @ApiModelProperty("扫描行数")
    private Long scanRows;

    @ApiModelProperty("扫描字节数")
    private Long scanBytes;

    @ApiModelProperty("执行耗时(ms)")
    private Long durationMs;

    // ========== 失败信息 ==========
    @ApiModelProperty("失败阶段")
    private String failStage;

    @ApiModelProperty("失败原因分类")
    private String failReasonCategory;

    @ApiModelProperty("错误码")
    private String errorCode;

    @ApiModelProperty("错误消息")
    private String errorMessage;

    // ========== 复用信息 ==========
    @ApiModelProperty("复用的源 Task ID")
    private String reusedFromTaskId;

    // ========== 引擎信息 ==========
    @ApiModelProperty("引擎类型")
    private String engineType;

    @ApiModelProperty("引擎实例")
    private String engineInstance;

    @ApiModelProperty("Executor 实例")
    private String executorInstance;

    // ========== 时间信息 ==========
    @ApiModelProperty("入队时间")
    private Date enqueueTime;

    @ApiModelProperty("开始时间")
    private Date startTime;

    @ApiModelProperty("开始 Fetch 时间")
    private Date fetchStartTime;

    @ApiModelProperty("开始写入时间")
    private Date writeStartTime;

    @ApiModelProperty("完成时间")
    private Date finishTime;
}