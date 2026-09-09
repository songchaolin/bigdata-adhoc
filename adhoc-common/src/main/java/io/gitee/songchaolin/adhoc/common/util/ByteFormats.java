package io.gitee.songchaolin.adhoc.common.util;

/**
 * 字节数格式化（B / KB / MB / GB）。无状态静态工具。
 * 规则：< 1024 -> "XB"；< 1024² -> "X.XXKB"；< 1024³ -> "X.XXMB"；否则 "X.XXGB"。
 */
public final class ByteFormats {

    private ByteFormats() {}

    /** 字节数自动格式化：B / KB / MB / GB（两位小数）。 */
    public static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + "B";
        if (bytes < 1024 * 1024) return String.format("%.2fKB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format("%.2fMB", bytes / (1024.0 * 1024));
        return String.format("%.2fGB", bytes / (1024.0 * 1024 * 1024));
    }
}
