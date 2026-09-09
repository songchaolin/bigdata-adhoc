package io.gitee.songchaolin.adhoc.common.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 指标大盘「每实例负载」视图（{@code GET /api/metrics/instance-load} 返回）：
 * 时间窗内每个 server / executor 上提交的 Job 数与 Task 数聚合，便于直观对比实例负载。
 * <p>Job 按 {@code processing_server_instance}（server）/{@code executor_instance}（executor）聚合；
 * Task 同字段聚合（task 表冗余这两列，无需 join job）。未分配（NULL）实例归入 {@code (未分配)}。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class MetricsInstanceLoadVO {
    /** 每 server 的 Job/Task 计数（jobs+tasks DESC）。 */
    private List<InstanceLoadRow> serverLoad;
    /** 每 executor 的 Job/Task 计数（jobs+tasks DESC）。 */
    private List<InstanceLoadRow> executorLoad;

    /** 单实例负载行：实例 ID / Job 提交数 / Task 数。 */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class InstanceLoadRow {
        /** 实例 ID（server_instance_id 或 executor_instance_id），未分配为 {@code (未分配)}。 */
        private String instance;
        /** 时间窗内该实例承接的 Job 数。 */
        private Long jobs;
        /** 时间窗内该实例执行的 Task 数。 */
        private Long tasks;
    }
}
