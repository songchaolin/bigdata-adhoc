package io.gitee.songchaolin.adhoc.common.model;

import com.sun.management.OperatingSystemMXBean;
import lombok.Builder;
import lombok.Data;

import java.lang.management.BufferPoolMXBean;
import java.lang.management.ClassLoadingMXBean;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.MemoryType;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;
import java.util.List;

/**
 * 本进程 JVM/OS 指标采集（MXBeans）。server 与 executor 共用：executor 每 5s 自写 adhoc_executor_instance，
 * server 每 5s 自写 adhoc_server_instance（{@code collect()} 重载，无运行 Task 权重）；同时两端各写一条
 * {@code adhoc_jvm_metric_sample} 采样历史（全量字段），供大盘 JVM 时间曲线查询。
 * <p>采集全集（排障常用）：
 * <ul>
 *   <li>CPU/OS：进程/系统 CPU%、系统 1min 负载、进程累计 CPU 时间、物理内存总量/已用/使用率</li>
 *   <li>堆总量：已用/已提交/最大 MB + 使用率%；非堆：已用/已提交 MB</li>
 *   <li>堆分代：Eden/Survivor/Old/Metaspace/CodeCache 已用（Old 含最大）-- 解释 Full GC 频率根因</li>
 *   <li>直接内存：buffer 数 + 已用 MB（off-heap 泄漏排查）</li>
 *   <li>GC：Young/Full 分开累计次数+耗时 + 总次数+总耗时+时间占比%（按 collector 名拆 young/full）</li>
 *   <li>线程：总数/daemon/峰值/累计启动/死锁数</li>
 *   <li>类加载：已加载/累计加载/累计卸载</li>
 *   <li>运行时：uptime ms + 启动时间戳 ms</li>
 * </ul>
 * MXBean 不可用值钳 0/null，不抛。lastGcPauseMs/diskUsage 暂 null（前者需 GC 通知监听器，未采）。
 */
@Data
@Builder
public class JvmMetrics {
    // ===== 既有字段（实例快照表 XML #{metrics.xxx} 仍引用，保持兼容） =====
    private Double cpuUsage;          // 进程 CPU%（旧字段别名 = cpuUsagePct，写给 cpu_usage 列）
    private Double memUsage;          // JVM 堆使用率（旧字段别名 = memoryUsagePct，写给 mem_usage 列）
    private Double diskUsage;         // null（暂不采）
    private Double cpuUsagePct;       // 进程 CPU%
    private Double systemCpuUsagePct; // 系统整体 CPU%
    private Double memoryUsagePct;   // JVM 堆使用率
    private Long memoryUsedMb;        // JVM 堆使用 MB（= heap_used_mb）
    private Long memoryMaxMb;         // JVM 堆最大 MB（= heap_max_mb）
    private Long heapCommittedMb;     // JVM 堆已提交 MB
    private Long nonHeapUsedMb;       // JVM 非堆已用 MB
    private Long nonHeapCommittedMb;   // JVM 非堆已提交 MB
    private Integer threadCount;      // 线程数
    private Integer daemonThreadCount;// daemon 线程数
    private Long gcCount;             // GC 累计次数（所有 collector）
    private Long gcTimeMs;            // GC 累计耗时 ms
    private Double gcTimeRatioPct;    // GC 时间占比% = gcTime/uptime*100
    private Integer loadedClassCount; // 已加载类数
    private Long uptimeMs;            // JVM 运行时长 ms
    private Long lastGcPauseMs;       // null（暂不采）
    private Double loadScore;         // 综合负载评分（QueueWorker 选 load_score ASC；server=cpu+mem）

    // ===== 新增字段（采样历史表全量，实例快照表不引用） =====
    // CPU / OS
    private Double systemLoadAvg;     // 系统 1min 负载（Linux；Windows/不可用->null）
    private Long processCpuTimeMs;    // 进程累计 CPU 时间 ms
    private Double physMemTotalMb;    // 物理内存总量 MB
    private Double physMemUsedMb;     // 物理内存已用 MB
    private Double physMemUsedPct;    // 物理内存使用率%
    // 堆分代
    private Double edenUsedMb;        // Eden 区已用 MB
    private Double survivorUsedMb;    // Survivor 区已用 MB
    private Double oldUsedMb;         // Old 区已用 MB
    private Double oldMaxMb;          // Old 区最大 MB
    private Double metaspaceUsedMb;   // Metaspace 已用 MB
    private Double metaspaceCommittedMb; // Metaspace 已提交 MB
    private Double codeCacheUsedMb;   // Code Cache 已用 MB
    // 直接内存
    private Long directBufferCount;   // 直接内存 buffer 数
    private Double directBufferUsedMb;// 直接内存已用 MB
    // GC young/full 拆分
    private Long youngGcCount;        // Young GC 累计次数
    private Long youngGcTimeMs;       // Young GC 累计耗时 ms
    private Long fullGcCount;         // Full GC 累计次数
    private Long fullGcTimeMs;        // Full GC 累计耗时 ms
    // 线程
    private Integer peakThreadCount;  // 峰值线程数
    private Long totalStartedThreadCount; // 累计启动线程数
    private Integer deadlockCount;    // 死锁线程数
    // 类加载
    private Long totalLoadedClassCount; // 累计加载类数
    private Long unloadedClassCount;  // 累计卸载类数
    // 运行时
    private Long startTimeMs;         // JVM 启动时间戳 ms

    /** 采集本进程指标。runningTaskCount 参与 load_score 权重（executor 用）；MXBean 不可用时钳 0/null，不抛。 */
    public static JvmMetrics collect(int runningTaskCount) {
        OperatingSystemMXBean os = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
        ThreadMXBean thread = ManagementFactory.getThreadMXBean();
        ClassLoadingMXBean cl = ManagementFactory.getClassLoadingMXBean();
        RuntimeMXBean rt = ManagementFactory.getRuntimeMXBean();
        List<GarbageCollectorMXBean> gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
        List<MemoryPoolMXBean> poolBeans = ManagementFactory.getMemoryPoolMXBeans();
        List<BufferPoolMXBean> bufferBeans = ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class);

        // CPU / OS
        double processCpu = os.getProcessCpuLoad();  // -1 暂不可用
        double systemCpu = os.getSystemCpuLoad();
        double cpuPct = processCpu >= 0 ? processCpu * 100 : 0;
        double sysCpuPct = systemCpu >= 0 ? systemCpu * 100 : 0;
        double sysLoad = os.getSystemLoadAverage();          // -1 不可用（Windows）
        Double systemLoadAvg = sysLoad >= 0 ? round1(sysLoad) : null;
        long processCpuTimeMs = os.getProcessCpuTime() / 1_000_000L;  // ns -> ms
        long physTotal = os.getTotalPhysicalMemorySize() / 1024 / 1024;
        long physFree = os.getFreePhysicalMemorySize() / 1024 / 1024;
        double physUsedMb = physTotal - physFree;
        double physUsedPct = physTotal > 0 ? round1(physUsedMb * 100.0 / physTotal) : 0;

        // 堆总量
        MemoryUsage heap = memory.getHeapMemoryUsage();
        long usedMb = heap.getUsed() / 1024 / 1024;
        long committedMb = heap.getCommitted() / 1024 / 1024;
        long maxMb = heap.getMax() > 0 ? heap.getMax() / 1024 / 1024 : 0;
        double memPct = heap.getMax() > 0 ? round1((double) heap.getUsed() / heap.getMax() * 100) : 0;

        // 非堆
        MemoryUsage nonHeap = memory.getNonHeapMemoryUsage();
        long nonHeapUsedMb = nonHeap.getUsed() / 1024 / 1024;
        long nonHeapCommittedMb = nonHeap.getCommitted() / 1024 / 1024;

        // 堆分代：按 pool 名归类
        double edenUsed = 0, survivorUsed = 0, oldUsed = 0, oldMax = 0;
        double metaspaceUsed = 0, metaspaceCommitted = 0, codeCacheUsed = 0;
        for (MemoryPoolMXBean p : poolBeans) {
            String n = p.getName() == null ? "" : p.getName().toLowerCase();
            MemoryUsage u = p.getUsage();
            if (u == null) {
                continue;
            }
            double used = u.getUsed() / 1024.0 / 1024.0;
            if (n.contains("eden")) {
                edenUsed += used;
            } else if (n.contains("survivor")) {
                survivorUsed += used;
            } else if (n.contains("metaspace")) {
                metaspaceUsed += used;
                metaspaceCommitted += u.getCommitted() / 1024.0 / 1024.0;
            } else if (n.contains("code") || n.contains("compressed")) {
                codeCacheUsed += used;
            } else if (n.contains("old") || n.contains("tenured")) {
                oldUsed += used;
                if (u.getMax() > 0) oldMax = Math.max(oldMax, u.getMax() / 1024.0 / 1024.0);
            }
        }

        // 直接内存
        long directCount = 0;
        double directUsedMb = 0;
        for (BufferPoolMXBean b : bufferBeans) {
            if ("direct".equalsIgnoreCase(b.getName())) {
                directCount = b.getCount();
                directUsedMb = b.getMemoryUsed() / 1024.0 / 1024.0;
                break;
            }
        }

        // GC：young/full 拆分（按 collector 名；非 young 归 full）
        long gcCount = 0, gcTimeMs = 0;
        long youngCount = 0, youngTime = 0, fullCount = 0, fullTime = 0;
        for (GarbageCollectorMXBean g : gcBeans) {
            long c = g.getCollectionCount();   // -1 = 不可用
            long t = g.getCollectionTime();
            if (c < 0) c = 0;
            if (t < 0) t = 0;
            gcCount += c;
            gcTimeMs += t;
            if (isYoungGc(g.getName())) {
                youngCount += c;
                youngTime += t;
            } else {
                fullCount += c;
                fullTime += t;
            }
        }

        // 线程
        int threadCount = thread.getThreadCount();
        int daemonCount = thread.getDaemonThreadCount();
        int peakThread = thread.getPeakThreadCount();
        long totalStarted = thread.getTotalStartedThreadCount();
        int deadlock = 0;
        try {
            long[] dls = thread.findDeadlockedThreads();
            deadlock = dls == null ? 0 : dls.length;
        } catch (Exception e) {
            // 不支持/权限不足：记 0
        }

        // 类加载
        int loadedClass = cl.getLoadedClassCount();
        if (loadedClass < 0) loadedClass = 0;
        long totalLoaded = cl.getTotalLoadedClassCount();
        long unloaded = cl.getUnloadedClassCount();

        // 运行时
        long uptimeMs = rt.getUptime();
        long startTimeMs = rt.getStartTime();
        double gcRatioPct = uptimeMs > 0 ? round1(gcTimeMs * 100.0 / uptimeMs) : 0;

        double loadScore = cpuPct + memPct + runningTaskCount * 10;

        return JvmMetrics.builder()
                .cpuUsage(cpuPct).memUsage(memPct).diskUsage(null)
                .cpuUsagePct(cpuPct).systemCpuUsagePct(sysCpuPct)
                .memoryUsagePct(memPct).memoryUsedMb(usedMb).memoryMaxMb(maxMb).heapCommittedMb(committedMb)
                .nonHeapUsedMb(nonHeapUsedMb).nonHeapCommittedMb(nonHeapCommittedMb)
                .threadCount(threadCount).daemonThreadCount(daemonCount)
                .gcCount(gcCount).gcTimeMs(gcTimeMs).gcTimeRatioPct(gcRatioPct)
                .loadedClassCount(loadedClass).uptimeMs(uptimeMs).lastGcPauseMs(null).loadScore(loadScore)
                // CPU/OS
                .systemLoadAvg(systemLoadAvg).processCpuTimeMs(processCpuTimeMs)
                .physMemTotalMb((double) physTotal).physMemUsedMb(round1(physUsedMb)).physMemUsedPct(physUsedPct)
                // 堆分代
                .edenUsedMb(round1(edenUsed)).survivorUsedMb(round1(survivorUsed))
                .oldUsedMb(round1(oldUsed)).oldMaxMb(round1(oldMax))
                .metaspaceUsedMb(round1(metaspaceUsed)).metaspaceCommittedMb(round1(metaspaceCommitted))
                .codeCacheUsedMb(round1(codeCacheUsed))
                // 直接内存
                .directBufferCount(directCount).directBufferUsedMb(round1(directUsedMb))
                // GC young/full
                .youngGcCount(youngCount).youngGcTimeMs(youngTime)
                .fullGcCount(fullCount).fullGcTimeMs(fullTime)
                // 线程
                .peakThreadCount(peakThread).totalStartedThreadCount(totalStarted).deadlockCount(deadlock)
                // 类加载
                .totalLoadedClassCount(totalLoaded).unloadedClassCount(unloaded)
                // 运行时
                .startTimeMs(startTimeMs)
                .build();
    }

    /** server 采集重载：无运行 Task 权重，loadScore = cpu+mem。 */
    public static JvmMetrics collect() {
        return collect(0);
    }

    /** Young GC collector 名判定（G1 Young / ParNew / PS Scavenge / Copy 等）；非 young 归 full。 */
    private static boolean isYoungGc(String name) {
        if (name == null) return false;
        String n = name.toLowerCase();
        return n.contains("young") || n.contains("scavenge") || n.contains("parnew") || n.equals("copy");
    }

    private static double round1(double d) {
        return Math.round(d * 10.0) / 10.0;
    }
}
