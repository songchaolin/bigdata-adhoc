package io.gitee.songchaolin.adhoc.common.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Task 摘要（Job 详情返回）。前端据此：
 * - 判断 task 是否有结果集（{@code hasResultSet}/ {@code sqlType}）-> 决定是否调 /task/result；
 * - 展示该段 SQL（{@code sqlContent} + {@code prefixSql}）；
 * - 失败 task 直接看原因（{@code errorMessage}）、耗时（{@code durationMs}）、影响行数（{@code affectedRows}）。
 */
@Data
@NoArgsConstructor
public class TaskSummary {
    private String taskId;
    private Integer segmentIndex;
    private String status;
    private String failStage;
    private String sqlType;          // DQL/CTAS/DDL/DML/SESSION_CONFIG/AUX...
    private Boolean hasResultSet;    // 是否有结果集，前端据此决定是否调 /task/result
    private String sqlContent;       // 该段实际执行的 SQL
    private String prefixSql;        // 累积的 SET/USE 前缀
    private Long resultRows;         // 结果行数（DQL：查出的行数；DDL/DML：null）
    private Long affectedRows;      // 影响行数（DDL/DML：INSERT/UPDATE 改了几行；DQL：null）
    private Long durationMs;         // 执行耗时(ms)
    private String errorMessage;     // 失败原因（FAILED 时）
}
