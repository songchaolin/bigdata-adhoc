package io.gitee.songchaolin.adhoc.common.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 指标大盘 Server 视图行（{@code GET /api/metrics/servers} 返回列表项）：
 * 单个 server 实例的实时状态 + 承接标志 + active_jobs + 心跳新鲜度 + 全套 JVM 指标（server 每 5s 自写 adhoc_server_instance，
 * 与 executor 共用 {@code JvmMetrics} 采集）。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class MetricsServerVO {
    /** server 实例 ID（ip:port） */
    private String instanceId;
    /** 主机 IP */
    private String host;
    /** HTTP 端口（大盘访问端口） */
    private Integer httpPort;
    /** gRPC 端口 */
    private Integer grpcPort;
    /** UP/DOWN/UNKNOWN */
    private String status;
    /** 是否承接新任务（1/0） */
    private Integer accepting;
    /** 当前处理 Job 数（active_jobs） */
    private Integer activeJobs;
    /** 心跳距今秒（DB 侧 TIMESTAMPDIFF 算） */
    private Long heartbeatAgeSec;
    /** 启动时间（epoch 秒，前端格式化；null=未启动） */
    private Long startTimeSec;
    /** 版本号 */
    private String version;
    /** JVM 进程 CPU% */
    private Double cpuUsagePct;
    /** 系统整体 CPU% */
    private Double systemCpuUsagePct;
    /** JVM 堆使用率% */
    private Double memoryUsagePct;
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
    /** 综合负载评分（cpu+mem） */
    private Double loadScore;
}
