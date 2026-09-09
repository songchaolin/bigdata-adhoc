package io.gitee.songchaolin.adhoc.common.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 指标大盘 TopN 视图（{@code GET /api/metrics/topn} 返回）：
 * 时间窗内最慢 Job TopN + 最活跃用户 TopN。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class MetricsTopNVO {
    /** 最慢 Job（duration_ms DESC） */
    private List<SlowJobRow> slowJobs;
    /** 最活跃用户（提交数 DESC） */
    private List<UserRow> activeUsers;

    /** 慢 Job 行： jobId / 用户 / 引擎 / 状态 / 耗时 / 提交时间 */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class SlowJobRow {
        private String jobId;
        private String userId;
        private String userName;
        private String engineType;
        private String status;
        /** 执行耗时（毫秒） */
        private Long durationMs;
        /** 提交时间（epoch millis，前端格式化） */
        private Long submitTime;
    }

    /** 活跃用户行： userId / 用户名 / 提交数 */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class UserRow {
        private String userId;
        private String userName;
        private Long cnt;
    }
}
