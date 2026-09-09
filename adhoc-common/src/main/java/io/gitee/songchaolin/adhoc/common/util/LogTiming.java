package io.gitee.songchaolin.adhoc.common.util;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 日志耗时格式化（Job/Task 状态间时段）。无状态静态工具。
 * 规则：ms < 1000 -> "Xms"；ms >= 1000 -> "X.Xs"；负值钳 0（防跨进程时钟 skew）。
 * null 端点：单段返回 null（该段跳过），摘要里该段省略。
 */
public final class LogTiming {

    private LogTiming() {}

    /** 毫秒 -> "Xms"(<1s) 或 "X.Xs"(>=1s)。负值钳 0。 */
    public static String fmtMs(long ms) {
        if (ms < 0) ms = 0;
        return ms < 1000 ? ms + "ms" : String.format("%.1fs", ms / 1000.0);
    }

    /** 两 Date 差 -> fmtMs；任一 null 返回 null。 */
    public static String fmt(Date start, Date end) {
        if (start == null || end == null) return null;
        return fmtMs(end.getTime() - start.getTime());
    }

    /** startMs(long) -> end(Date) 差 -> fmtMs；end null 返回 null。 */
    public static String fmt(long startMs, Date end) {
        if (end == null) return null;
        return fmtMs(end.getTime() - startMs);
    }

    /** 格式化自 startMs 至今的耗时，带括号："(Xms)" / "(X.Xs)"。用于行内 elapsed 标注（progress/result 行）。 */
    public static String elapsed(long startMs) {
        return "(" + fmtMs(System.currentTimeMillis() - startMs) + ")";
    }

    /** task timing 摘要："total=D (queue=D, execute=D)"。内存标记，无 null。 */
    public static String taskTiming(long enqueueMs, long startMs, long doneMs) {
        return "total=" + fmtMs(doneMs - enqueueMs)
                + " (queue=" + fmtMs(startMs - enqueueMs)
                + ", execute=" + fmtMs(doneMs - startMs) + ")";
    }

    /** job timing 摘要："queue=D, dispatch->running=D, execute=D, total=D"（null 段跳过）。 */
    public static String jobTiming(Date submit, Date dispatch, long receiveMs, Date finish) {
        List<String> segs = new ArrayList<>();
        String q = fmt(submit, dispatch);
        if (q != null) segs.add("queue=" + q);
        if (dispatch != null) segs.add("dispatch->running=" + fmtMs(receiveMs - dispatch.getTime()));
        String ex = fmt(receiveMs, finish);
        if (ex != null) segs.add("execute=" + ex);
        String tot = fmt(submit, finish);
        if (tot != null) segs.add("total=" + tot);
        return String.join(", ", segs);
    }

    /** done 行文案：failed==0 -> "all N tasks succeeded"；否则 "success=N, failed=M"，
     *  skipped>0 时追加 "(K skipped due to upstream failure)"（skipped 计入 failed）。 */
    public static String doneSummary(long success, long failed, long skipped) {
        if (failed == 0) return "all " + success + " tasks succeeded";
        String base = "success=" + success + ", failed=" + failed;
        if (skipped > 0) return base + " (" + skipped + " skipped due to upstream failure)";
        return base;
    }
}