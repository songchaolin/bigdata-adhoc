package io.gitee.songchaolin.adhoc.executor.ha;

import io.gitee.songchaolin.adhoc.common.model.JvmMetrics;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JvmMetricsTest {

    @Test
    void collect_returnsSensibleValues() {
        JvmMetrics m = JvmMetrics.collect(2);
        assertThat(m).isNotNull();
        assertThat(m.getCpuUsagePct()).isGreaterThanOrEqualTo(0);
        assertThat(m.getSystemCpuUsagePct()).isGreaterThanOrEqualTo(0);
        assertThat(m.getMemoryUsedMb()).isGreaterThanOrEqualTo(0);
        assertThat(m.getMemoryMaxMb()).isGreaterThanOrEqualTo(0);
        assertThat(m.getMemoryUsagePct()).isGreaterThanOrEqualTo(0);
        assertThat(m.getThreadCount()).isGreaterThan(0);
        assertThat(m.getDaemonThreadCount()).isGreaterThan(0);
        assertThat(m.getHeapCommittedMb()).isGreaterThanOrEqualTo(0);
        assertThat(m.getNonHeapUsedMb()).isGreaterThanOrEqualTo(0);
        assertThat(m.getNonHeapCommittedMb()).isGreaterThanOrEqualTo(0);
        assertThat(m.getGcCount()).isGreaterThanOrEqualTo(0);
        assertThat(m.getGcTimeMs()).isGreaterThanOrEqualTo(0);
        assertThat(m.getGcTimeRatioPct()).isGreaterThanOrEqualTo(0);
        assertThat(m.getLoadedClassCount()).isGreaterThanOrEqualTo(0);
        assertThat(m.getUptimeMs()).isGreaterThan(0);
        assertThat(m.getLoadScore()).isGreaterThanOrEqualTo(0);
        assertThat(m.getDiskUsage()).isNull();      // 暂不采
        assertThat(m.getLastGcPauseMs()).isNull();  // 暂不采
        assertThat(m.getCpuUsage()).isEqualTo(m.getCpuUsagePct());  // 旧字段别名
        assertThat(m.getMemUsage()).isEqualTo(m.getMemoryUsagePct());

        // 新增字段：CPU/OS、堆分代、直接内存、GC 拆分、线程扩展、类加载、运行时
        assertThat(m.getProcessCpuTimeMs()).isGreaterThanOrEqualTo(0L);
        assertThat(m.getPhysMemTotalMb()).isGreaterThanOrEqualTo(0d);
        assertThat(m.getPhysMemUsedMb()).isGreaterThanOrEqualTo(0d);
        assertThat(m.getPhysMemUsedPct()).isGreaterThanOrEqualTo(0d);
        // systemLoadAvg 在 Windows 上为 null，Linux >=0，这里只校验非负
        if (m.getSystemLoadAvg() != null) {
            assertThat(m.getSystemLoadAvg()).isGreaterThanOrEqualTo(0d);
        }
        assertThat(m.getEdenUsedMb()).isGreaterThanOrEqualTo(0d);
        assertThat(m.getSurvivorUsedMb()).isGreaterThanOrEqualTo(0d);
        assertThat(m.getOldUsedMb()).isGreaterThanOrEqualTo(0d);
        assertThat(m.getMetaspaceUsedMb()).isGreaterThanOrEqualTo(0d);
        assertThat(m.getCodeCacheUsedMb()).isGreaterThanOrEqualTo(0d);
        assertThat(m.getDirectBufferCount()).isGreaterThanOrEqualTo(0L);
        assertThat(m.getDirectBufferUsedMb()).isGreaterThanOrEqualTo(0d);
        assertThat(m.getPeakThreadCount()).isGreaterThan(0);
        assertThat(m.getTotalStartedThreadCount()).isGreaterThan(0L);
        assertThat(m.getDeadlockCount()).isGreaterThanOrEqualTo(0);
        assertThat(m.getTotalLoadedClassCount()).isGreaterThanOrEqualTo(0L);
        assertThat(m.getUnloadedClassCount()).isGreaterThanOrEqualTo(0L);
        assertThat(m.getStartTimeMs()).isGreaterThan(0L);
    }

    @Test
    void collect_gcYoungFullSplitSumsToTotal() {
        // young + full GC 计数/耗时之和 == 总累计（每个 collector 非 young 即 full）
        JvmMetrics m = JvmMetrics.collect();
        assertThat(m.getYoungGcCount() + m.getFullGcCount()).isEqualTo(m.getGcCount());
        assertThat(m.getYoungGcTimeMs() + m.getFullGcTimeMs()).isEqualTo(m.getGcTimeMs());
    }

    @Test
    void collect_noRunningTask_serverStyle() {
        // server 重载：runningTaskCount=0，loadScore = cpu+mem（不崩）
        JvmMetrics m = JvmMetrics.collect();
        assertThat(m).isNotNull();
        assertThat(m.getLoadScore()).isGreaterThanOrEqualTo(0);
    }
}
