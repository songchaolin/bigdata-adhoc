package io.gitee.songchaolin.adhoc.common.util;

import java.util.List;

/**
 * 日志相关共享常量（server / executor 模块共用）。
 */
public final class LogConstants {

    private LogConstants() {}

    /**
     * server 终态轮 flush OSS 时在末尾追加的完整标识内容。
     * executor 兜底定时器据此判断 server 是否已完整写 OSS：
     * OSS 末行含此标识 -> server 已完整（通知丢了也无需补全）；否则 -> server 未完整，触发合并补全。
     */
    public static final String COMPLETE_MARKER = "===== ADHOC_LOG_COMPLETE =====";

    /** 完整标识行（含来源标签，append 时 LogBuffer.append 会再加时间戳前缀）。仅 server 终态轮 append。 */
    public static final String COMPLETE_MARKER_LINE = "[server] [INFO] " + COMPLETE_MARKER;

    /**
     * 判断日志行列表是否已完整：含 server 终态轮追加的 {@link #COMPLETE_MARKER}。
     * running / 未 finalize 的日志不含此标识。驱动 {@link io.gitee.songchaolin.adhoc.common.dto.LogResponse#isComplete()}
     * 语义：complete=false 时客户端须继续轮询（即便 hasMore=false，日志仍可能增长）。
     */
    public static boolean containsComplete(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return false;
        }
        for (String line : lines) {
            if (line != null && line.contains(COMPLETE_MARKER)) {
                return true;
            }
        }
        return false;
    }
}