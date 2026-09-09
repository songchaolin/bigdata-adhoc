package io.gitee.songchaolin.adhoc.server.ha;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/** per-job 内存日志 buffer（server 端 Job 日志）。append 自动带时间戳前缀。行数上限 5000 防 OOM。 */
public class JobLog {

    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    /** 最大行数上限（防长 Spark 查询 OOM，保留最新行）。 */
    private static final int MAX_LINES = 5000;

    private final List<String> lines = new ArrayList<>();
    private final ReentrantLock lock = new ReentrantLock();

    public void append(String line) {
        lock.lock();
        try {
            lines.add(LocalDateTime.now().format(TS_FMT) + " " + line);
            if (lines.size() > MAX_LINES) {
                lines.subList(0, lines.size() - MAX_LINES).clear();
            }
        } finally {
            lock.unlock();
        }
    }

    /** 批量追加（不加分页时间戳--executor 拉来的行已带时间戳）。 */
    public void appendAll(List<String> newLines) {
        lock.lock();
        try {
            lines.addAll(newLines);
        } finally {
            lock.unlock();
        }
    }

    public List<String> snapshot() {
        lock.lock();
        try {
            return new ArrayList<>(lines);
        } finally {
            lock.unlock();
        }
    }

    public int lineCount() {
        lock.lock();
        try {
            return lines.size();
        } finally {
            lock.unlock();
        }
    }
}