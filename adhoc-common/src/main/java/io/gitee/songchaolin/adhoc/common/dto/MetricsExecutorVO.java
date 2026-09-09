package io.gitee.songchaolin.adhoc.common.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 指标大盘 Executor 视图行（{@code GET /api/metrics/executors} 返回列表项）：
 * 单个 executor 实例的实时状态 + 并发利用率 + 心跳新鲜度 + 全套 JVM 指标（executor 每 5s 自写 adhoc_executor_instance）。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class MetricsExecutorVO {
    /** executor 实例 ID */
    private String instanceId;
    /** 主机 IP */
    private String host;
    /** gRPC 端口 */
    private Integer grpcPort;
    /** UP/DOWN */
    private String status;
    /** 是否接受新任务（1/0） */
    private Integer accepting;
    /** 在跑任务数（JSON_LENGTH(running_tasks)，DB 侧算） */
    private Integer runningTasks;
    /** 最大并发任务数 */
    private Integer maxConcurrent;
    /** 利用率% = runningTasks / maxConcurrent * 100（Service 侧算，maxConcurrent=0 时 0） */
    private Double utilizationPct;
    /** 心跳距今秒（DB 侧 TIMESTAMPDIFF 算） */
    private Long heartbeatAgeSec;
    /** JVM 进程 CPU 使用率% */
    private Double cpuUsagePct;
    /** JVM 堆内存使用率% */
    private Double memoryUsagePct;
    /** 负载评分（load_score，调度排序用） */
    private Double loadScore;
    /** 系统整体 CPU% */
    private Double systemCpuUsagePct;
    /** JVM 堆已用 MB */
    private Long memoryUsedMb;
    /** JVM 堆最大 MB */
    private Long memoryMaxMb;
    /** JVM 堆已提交 MB */
    private Long heapCommittedMb;
    /** JVM 非堆已用 MB */
    private Long nonHeapUsedMb;
    /** JVM 非堆已提交 MB */
    private Long nonHeapCommittedMb;
    /** 线程数 */
    private Integer threadCount;
    /** daemon 线程数 */
    private Integer daemonThreadCount;
    /** GC 累计次数 */
    private Long gcCount;
    /** GC 累计耗时 ms */
    private Long gcTimeMs;
    /** GC 时间占比% = gcTime/uptime*100 */
    private Double gcTimeRatioPct;
    /** 已加载类数 */
    private Integer loadedClassCount;
    /** JVM 运行时长 ms */
    private Long uptimeMs;
}
