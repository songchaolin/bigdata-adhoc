package io.gitee.songchaolin.adhoc.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.gitee.songchaolin.adhoc.common.model.JvmMetrics;
import lombok.Data;

import java.util.Date;

/**
 * 实例 JVM/OS 指标采样历史（{@code adhoc_jvm_metric_sample}）。server 与 executor 各自心跳每 5s 写一条，
 * server 侧定时清理超保留期数据。大盘 JVM 时间曲线查询用。
 * <p>类型对齐 DDL：DOUBLE->Double、BIGINT->Long、INT->Integer。
 */
@Data
@TableName("adhoc_jvm_metric_sample")
public class AdhocJvmMetricSample {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String instanceId;       // server 或 executor instance_id
    private String role;             // SERVER/EXECUTOR
    private Date sampleTime;        // DATETIME(3)
    private Integer runningTasks;   // executor 在跑任务数；server=0

    // CPU / OS
    private Double cpuUsagePct;
    private Double systemCpuUsagePct;
    private Double systemLoadAvg;
    private Long processCpuTimeMs;
    private Double physMemTotalMb;
    private Double physMemUsedMb;
    private Double physMemUsedPct;

    // 堆总量
    private Double heapUsedMb;
    private Double heapCommittedMb;
    private Double heapMaxMb;
    private Double heapUsedPct;

    // 堆分代
    private Double edenUsedMb;
    private Double survivorUsedMb;
    private Double oldUsedMb;
    private Double oldMaxMb;
    private Double metaspaceUsedMb;
    private Double metaspaceCommittedMb;
    private Double codeCacheUsedMb;

    // 非堆
    private Double nonHeapUsedMb;
    private Double nonHeapCommittedMb;

    // 直接内存
    private Long directBufferCount;
    private Double directBufferUsedMb;

    // GC
    private Long youngGcCount;
    private Long youngGcTimeMs;
    private Long fullGcCount;
    private Long fullGcTimeMs;
    private Long gcCount;
    private Long gcTimeMs;
    private Double gcTimeRatioPct;

    // 线程
    private Integer threadCount;
    private Integer daemonThreadCount;
    private Integer peakThreadCount;
    private Long totalStartedThreadCount;
    private Integer deadlockCount;

    // 类加载
    private Integer loadedClassCount;
    private Long totalLoadedClassCount;
    private Long unloadedClassCount;

    // 运行时
    private Long uptimeMs;
    private Long startTimeMs;

    private Date createTime;       // DB DEFAULT CURRENT_TIMESTAMP(3)，insert 不写

    /**
     * 从 {@link JvmMetrics} 装配采样行（server/executor 共用），sampleTime 取当前时刻。
     * 堆总量字段名在 JvmMetrics 为 memoryUsedMb/memoryMaxMb/heapCommittedMb/memoryUsagePct，此处映射为 heapUsedMb/heapMaxMb/heapCommittedMb/heapUsedPct。
     */
    public static AdhocJvmMetricSample of(String instanceId, String role, int runningTasks, JvmMetrics m) {
        AdhocJvmMetricSample s = new AdhocJvmMetricSample();
        s.setInstanceId(instanceId);
        s.setRole(role);
        s.setSampleTime(new Date());
        s.setRunningTasks(runningTasks);

        s.setCpuUsagePct(m.getCpuUsagePct());
        s.setSystemCpuUsagePct(m.getSystemCpuUsagePct());
        s.setSystemLoadAvg(m.getSystemLoadAvg());
        s.setProcessCpuTimeMs(m.getProcessCpuTimeMs());
        s.setPhysMemTotalMb(m.getPhysMemTotalMb());
        s.setPhysMemUsedMb(m.getPhysMemUsedMb());
        s.setPhysMemUsedPct(m.getPhysMemUsedPct());

        s.setHeapUsedMb(toDouble(m.getMemoryUsedMb()));
        s.setHeapCommittedMb(toDouble(m.getHeapCommittedMb()));
        s.setHeapMaxMb(toDouble(m.getMemoryMaxMb()));
        s.setHeapUsedPct(m.getMemoryUsagePct());

        s.setEdenUsedMb(m.getEdenUsedMb());
        s.setSurvivorUsedMb(m.getSurvivorUsedMb());
        s.setOldUsedMb(m.getOldUsedMb());
        s.setOldMaxMb(m.getOldMaxMb());
        s.setMetaspaceUsedMb(m.getMetaspaceUsedMb());
        s.setMetaspaceCommittedMb(m.getMetaspaceCommittedMb());
        s.setCodeCacheUsedMb(m.getCodeCacheUsedMb());

        s.setNonHeapUsedMb(toDouble(m.getNonHeapUsedMb()));
        s.setNonHeapCommittedMb(toDouble(m.getNonHeapCommittedMb()));

        s.setDirectBufferCount(m.getDirectBufferCount());
        s.setDirectBufferUsedMb(m.getDirectBufferUsedMb());

        s.setYoungGcCount(m.getYoungGcCount());
        s.setYoungGcTimeMs(m.getYoungGcTimeMs());
        s.setFullGcCount(m.getFullGcCount());
        s.setFullGcTimeMs(m.getFullGcTimeMs());
        s.setGcCount(m.getGcCount());
        s.setGcTimeMs(m.getGcTimeMs());
        s.setGcTimeRatioPct(m.getGcTimeRatioPct());

        s.setThreadCount(m.getThreadCount());
        s.setDaemonThreadCount(m.getDaemonThreadCount());
        s.setPeakThreadCount(m.getPeakThreadCount());
        s.setTotalStartedThreadCount(m.getTotalStartedThreadCount());
        s.setDeadlockCount(m.getDeadlockCount());

        s.setLoadedClassCount(m.getLoadedClassCount());
        s.setTotalLoadedClassCount(m.getTotalLoadedClassCount());
        s.setUnloadedClassCount(m.getUnloadedClassCount());

        s.setUptimeMs(m.getUptimeMs());
        s.setStartTimeMs(m.getStartTimeMs());
        return s;
    }

    private static Double toDouble(Long v) {
        return v == null ? null : v.doubleValue();
    }
}
