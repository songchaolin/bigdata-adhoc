package io.gitee.songchaolin.adhoc.common.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 单个 task 的结果项（聚合接口 /job/result 返回）：
 * task 元信息（与 {@link TaskSummary} 一致）+ 结果第一页（仅 {@code hasResultSet=true} 时填 schema/rows/totalRows/hasMore）。
 * 前端要看某 task 完整结果，再用 /task/result 翻页。
 */
@Data
@NoArgsConstructor
public class TaskResultItem {
    // task 元信息
    private String taskId;
    private Integer segmentIndex;
    private String status;
    private String failStage;
    private String sqlType;
    private Boolean hasResultSet;
    private String sqlContent;
    private String prefixSql;
    private Long affectedRows;
    private Long durationMs;
    private String errorMessage;
    // 结果第一页（仅 hasResultSet=true 时有值）
    private List<ResultResponse.ColumnDto> schema;
    private List<String> rows;
    private Long totalRows;
    private Boolean hasMore;
}
