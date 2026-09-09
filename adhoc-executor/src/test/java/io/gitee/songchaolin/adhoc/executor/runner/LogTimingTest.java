package io.gitee.songchaolin.adhoc.executor.runner;

import io.gitee.songchaolin.adhoc.common.util.LogTiming;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class LogTimingTest {

    @Test
    void fmtMs_subSecond() {
        assertThat(LogTiming.fmtMs(0)).isEqualTo("0ms");
        assertThat(LogTiming.fmtMs(323)).isEqualTo("323ms");
        assertThat(LogTiming.fmtMs(999)).isEqualTo("999ms");
    }

    @Test
    void fmtMs_secondOrMore() {
        assertThat(LogTiming.fmtMs(1000)).isEqualTo("1.0s");
        assertThat(LogTiming.fmtMs(37000)).isEqualTo("37.0s");
        assertThat(LogTiming.fmtMs(42800)).isEqualTo("42.8s");
    }

    @Test
    void fmtMs_negativeClampedToZero() {
        assertThat(LogTiming.fmtMs(-50)).isEqualTo("0ms");
    }

    @Test
    void fmt_datesBothPresent() {
        assertThat(LogTiming.fmt(new Date(1000L), new Date(1500L))).isEqualTo("500ms");
    }

    @Test
    void fmt_datesNullReturnNull() {
        assertThat(LogTiming.fmt(null, new Date())).isNull();
        assertThat(LogTiming.fmt(new Date(), null)).isNull();
        assertThat(LogTiming.fmt(null, null)).isNull();
    }

    @Test
    void fmt_longToDate() {
        assertThat(LogTiming.fmt(1000L, new Date(1500L))).isEqualTo("500ms");
        assertThat(LogTiming.fmt(0L, null)).isNull();
    }

    @Test
    void elapsed_wrapsInParens() {
        // startMs 取 500ms 前 -> "(500ms)" 形态；跨秒则 "(X.Xs)"。只断言结构（耗时依赖当前时间）。
        String e = LogTiming.elapsed(System.currentTimeMillis() - 500);
        assertThat(e).startsWith("(").endsWith(")");
        assertThat(e.substring(1, e.length() - 1)).matches("\\d+(ms|\\.\\ds)");
    }

    @Test
    void taskTiming_partitionsTotal() {
        // enqueue=0, start=100, done=42800 -> total=42.8s, queue=100ms, execute=42.7s
        assertThat(LogTiming.taskTiming(0L, 100L, 42800L))
                .isEqualTo("total=42.8s (queue=100ms, execute=42.7s)");
    }

    @Test
    void jobTiming_allPresent() {
        // submit=0, dispatch=1700, receive=1851, finish=43600
        // queue=1.7s, dispatch->running=151ms, execute=41.7s, total=43.6s
        assertThat(LogTiming.jobTiming(new Date(0L), new Date(1700L), 1851L, new Date(43600L)))
                .isEqualTo("queue=1.7s, dispatch->running=151ms, execute=41.7s, total=43.6s");
    }

    @Test
    void jobTiming_dispatchNullSkipsQueueAndDispatchRunning() {
        // dispatch null -> skip queue + dispatch->running；show execute + total
        assertThat(LogTiming.jobTiming(new Date(0L), null, 100L, new Date(1000L)))
                .isEqualTo("execute=900ms, total=1.0s");
    }

    @Test
    void doneSummary_allSuccess() {
        assertThat(LogTiming.doneSummary(2, 0, 0)).isEqualTo("all 2 tasks succeeded");
        assertThat(LogTiming.doneSummary(0, 0, 0)).isEqualTo("all 0 tasks succeeded");
    }

    @Test
    void doneSummary_partialOrAllFailed() {
        assertThat(LogTiming.doneSummary(1, 1, 0)).isEqualTo("success=1, failed=1");
        assertThat(LogTiming.doneSummary(0, 2, 0)).isEqualTo("success=0, failed=2");
    }

    @Test
    void doneSummary_withSkipped() {
        // 1 执行失败 + 2 因上游 skip（skipped 计入 failed=3）
        assertThat(LogTiming.doneSummary(0, 3, 2))
                .isEqualTo("success=0, failed=3 (2 skipped due to upstream failure)");
        // 有成功 + 有 skip
        assertThat(LogTiming.doneSummary(1, 2, 1))
                .isEqualTo("success=1, failed=2 (1 skipped due to upstream failure)");
    }
}