package io.gitee.songchaolin.adhoc.executor.runner;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 通用日志内存 buffer（task 按 taskId、job 按 jobId 各一个）：append 行 + gRPC read 内存 + snapshot 全量。
 * 纯内存，不负责 OSS 持久化（job 日志终态由 server 通知清理/LogBufferRegistry 兜底补全；task 日志不写 OSS）。
 * append 自动带时间戳前缀（方便排查 + 兜底按时间戳排序合并）。
 *
 * <p>offset 语义为<b>全局行号</b>（从 append 第一行起递增），baseOffset 记录已淘汰行数：
 * buffer 超 MAX_LINES 淘汰最旧行时 baseOffset 递增，read(offset) 用 {@code offset - baseOffset} 定位数组下标。
 * 这样 server 的 lastPulledOffset 单调递增，eviction 后不会"永久空拉"（旧方案 offset 当数组下标，eviction 后索引前移致永久空）。
 */
public class LogBuffer {

    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    /** 最大行数上限（防长 Spark 查询 OOM，保留最新行）。 */
    private static final int MAX_LINES = 5000;

    private final List<String> lines = new ArrayList<>();
    private final ReentrantLock lock = new ReentrantLock();
    /** 已淘汰的行数（全局 offset 基线）。 */
    private long baseOffset = 0;

    public LogBuffer() {
    }

    public void append(String line) {
        lock.lock();
        try {
            lines.add(LocalDateTime.now().format(TS_FMT) + " " + line);
            if (lines.size() > MAX_LINES) {
                int removed = lines.size() - MAX_LINES;
                lines.subList(0, removed).clear();
                baseOffset += removed;
            }
        } finally {
            lock.unlock();
        }
    }

    /** gRPC 读：从全局 offset 行起，最多 limit 行。offset < baseOffset（落后太多）时从 baseOffset 读（跳过已淘汰）。 */
    public List<String> read(long offset, int limit) {
        lock.lock();
        try {
            List<String> result = new ArrayList<>();
            int start = (int) Math.max(0, offset - baseOffset);
            for (int i = start; i < lines.size() && result.size() < limit; i++) {
                result.add(lines.get(i));
            }
            return result;
        } finally {
            lock.unlock();
        }
    }

    /** 全量快照（当前内存行，兜底 reconcile 用）。 */
    public List<String> snapshot() {
        lock.lock();
        try {
            return new ArrayList<>(lines);
        } finally {
            lock.unlock();
        }
    }

    /** 全局行数（baseOffset + 当前行数），gRPC hasMore / nextOffset 判断用。 */
    public long lineCount() {
        lock.lock();
        try {
            return baseOffset + lines.size();
        } finally {
            lock.unlock();
        }
    }

    /** 已淘汰行数（executor 返回 nextOffset 用：actualStart = max(offset, baseOffset)）。 */
    public long getBaseOffset() {
        lock.lock();
        try {
            return baseOffset;
        } finally {
            lock.unlock();
        }
    }
}
