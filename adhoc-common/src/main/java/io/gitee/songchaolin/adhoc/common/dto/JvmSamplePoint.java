package io.gitee.songchaolin.adhoc.common.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * JVM/OS 指标采样点（时间曲线单点）。{@code ts} = 采样时刻 epoch ms（ECharts time 轴对齐用，避免多实例采样时刻不齐致留空）；
 * 其余为该时刻指标值。由 {@link io.gitee.songchaolin.adhoc.server.service.MetricsService#getJvmSeries} 从 adhoc_jvm_metric_sample 行映射，
 * 按 instanceId 分组返回给前端 {@code /api/metrics/jvm-series}。
 */
@Data
@AllArgsConstructor
public class JvmSamplePoint {
    private Long ts;
    // CPU / OS
    private Double cpuUsagePct;
    private Double systemCpuUsagePct;
    private Double systemLoadAvg;
    private Double physMemUsedPct;
    // 堆
    private Double heapUsedMb;
    private Double heapUsedPct;
    private Double heapCommittedMb;
    private Double heapMaxMb;
    private Double nonHeapUsedMb;
    // 堆分代
    private Double edenUsedMb;
    private Double oldUsedMb;
    // GC
    private Long youngGcCount;
    private Long fullGcCount;
    private Double gcTimeRatioPct;
    private Long gcCount;
    private Long gcTimeMs;
    // 线程
    private Integer threadCount;
    private Integer daemonThreadCount;
    // 直接内存 / 类加载
    private Double directBufferUsedMb;
    private Integer loadedClassCount;
}
