package io.gitee.songchaolin.adhoc.common.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 指标大盘概览（{@code GET /api/metrics/overview} 返回）：
 * 聚合 Job/Task 状态分布 + 成功率 + 时长 + 引擎 + 实时态 + executor 汇总，单请求覆盖顶部卡片。
 * <p>所有计数为 Long、率为 Double（0-1 或 0-100 见字段注释）；分布 Map 的 key 为枚举 name()。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class MetricsOverviewVO {
    // ----- Job 概览 -----
    /** 时间窗内 Job 总数 */
    private Long jobTotal;
    /** Job 按 status 分布：status -> 计数 */
    private Map<String, Long> jobByStatus;
    /** Job 成功率（0-100），= SUCCESS / 终态数 */
    private Double successRate;
    /** 平均执行耗时（毫秒） */
    private Double avgDurationMs;
    /** 最大执行耗时（毫秒） */
    private Long maxDurationMs;
    /** 时长分桶：{"0-1s","1-10s","10-60s",">60s"} -> 计数 */
    private Map<String, Long> durationBuckets;
    /** 按 engine_type 分布：engineType -> 计数 */
    private Map<String, Long> engineDistribution;

    // ----- 实时态（无时间窗） -----
    /** PENDING 积压数 */
    private Long pending;
    /** RUNNING 数 */
    private Long running;
    /** DISPATCHING 数 */
    private Long dispatching;
    /** processing_server 分布（多 server 负载）：serverInstance -> 计数 */
    private List<MetricsTaskFailVO.NameCount> processingServerDistribution;

    // ----- Executor 集群汇总 -----
    /** executor 总数 */
    private Long executorTotal;
    /** UP 数 */
    private Long executorUp;
    /** DOWN 数 */
    private Long executorDown;
    /** accepting=1 数 */
    private Long executorAccepting;

    // ----- Server 集群汇总 -----
    /** server 总数 */
    private Long serverTotal;
    /** server UP 数 */
    private Long serverUp;

    // ----- Task 概览 -----
    /** 时间窗内 Task 总数 */
    private Long taskTotal;
    /** Task 按 status 分布：status -> 计数 */
    private Map<String, Long> taskByStatus;
    /** Task 成功率（0-100） */
    private Double taskSuccessRate;
}
