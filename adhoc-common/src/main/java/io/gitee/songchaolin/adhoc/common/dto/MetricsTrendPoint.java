package io.gitee.songchaolin.adhoc.common.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 指标大盘小时趋势点（{@code GET /api/metrics/trends} 与 {@code /task-trends} 返回）：
 * hour 为可读标签（如 "08-20 14:00"），submitted/finished 为该小时提交/完成数。
 * Job 趋势按 submit_time/finish_time，Task 趋势按 enqueue_time/finish_time（同分桶口径，便于对照）。
 * 由 {@code MetricsService.fillTrendPoints} 按 epoch-hour 分桶结果填充并补齐空桶。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class MetricsTrendPoint {
    /** 小时标签（前端 x 轴），格式 MM-dd HH:00 */
    private String hour;
    /** 该小时提交数（Job 按 submit_time / Task 按 enqueue_time） */
    private Long submitted;
    /** 该小时完成数（终态，Job 按 finish_time / Task 按 finish_time） */
    private Long finished;
}
