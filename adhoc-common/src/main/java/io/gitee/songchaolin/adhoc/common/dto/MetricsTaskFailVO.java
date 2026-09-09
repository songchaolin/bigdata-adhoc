package io.gitee.songchaolin.adhoc.common.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 指标大盘 Task 失败视图（{@code GET /api/metrics/task-failures} 返回）：
 * 时间窗内失败 Task 的 fail_stage / error_code / sql_type 分布，定位失败高发维度。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class MetricsTaskFailVO {
    /** 失败 stage 分布：SPLIT/EXECUTING/FETCHING/WRITING/OSS_UPLOAD -> 计数 */
    private List<NameCount> byStage;
    /** 错误码分布 TopN：ADHOC_* -> 计数 */
    private List<NameCount> byErrorCode;
    /** sql_type 分布：DQL/DDL/DML/CTAS/AUX -> 计数 */
    private List<NameCount> bySqlType;

    /** 通用 名称+计数 行 */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class NameCount {
        private String name;
        private Long cnt;
    }
}
