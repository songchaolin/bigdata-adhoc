package io.gitee.songchaolin.adhoc.executor.engine;

import org.apache.hive.jdbc.HiveStatement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Kyuubi/Spark 服务端 operation log 增量轮询。
 *
 * <p>可行性依据（hive-jdbc 3.1.1 反编译确认，非猜测）：
 * <ul>
 *   <li>{@code HiveConnection} 构造里 {@code client = newSynchronizedClient(client)}，Thrift client 是
 *       线程安全包装；</li>
 *   <li>{@code HiveStatement.execute(sql) = runAsyncOnServer + waitForOperationToComplete}，阻塞轮询
 *       GetOperationStatus 之间会释放 synchronized 锁。</li>
 * </ul>
 * 故主线程 execute 阻塞期间，守护线程并发 {@code getQueryLog} 安全且能实时流式（在状态轮询间隙拿锁）。
 *
 * <p>用法：{@code start()}（execute 前）-> execute 阻塞期间守护线程增量转发 ->
 * {@code drain()}（execute 后 final 拉）-> {@code stop()}。
 * opHandle 未就绪 / 操作已关闭时 getQueryLog 抛异常，吞掉重试（不影响主流程）。
 */
public class OperationLogStreamer {

    private static final Logger log = LoggerFactory.getLogger(OperationLogStreamer.class);

    private final HiveStatement stmt;
    private final LogSink sink;
    private final long intervalMs;
    private final int fetchSize;
    private Thread thread;

    public OperationLogStreamer(HiveStatement stmt, LogSink sink, long intervalMs, int fetchSize) {
        this.stmt = stmt;
        this.sink = sink;
        this.intervalMs = intervalMs;
        this.fetchSize = fetchSize;
    }

    /** 一次增量拉取并转发（可单测：mock HiveStatement）。异常吞掉（opHandle 未就绪 / 已关闭）。 */
    void pollOnce() {
        try {
            List<String> lines = stmt.getQueryLog(true, fetchSize);
            if (lines != null) {
                for (String l : lines) {
                    if (l != null && !l.trim().isEmpty()) {
                        sink.append(l);
                    }
                }
            }
        } catch (Exception e) {
            // opHandle 未就绪（execute 还没 runAsyncOnServer）或操作已关闭 -- 跳过本轮
            log.debug("getQueryLog skipped: {}", e.getMessage());
        }
    }

    public void start() {
        thread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(intervalMs);
                } catch (InterruptedException e) {
                    return;
                }
                pollOnce();
            }
        }, "kyuubi-oplog");
        thread.setDaemon(true);
        thread.start();
    }

    /** execute 返回后 final 拉取（捕获尾部日志），主线程调用。 */
    public void drain() {
        pollOnce();
    }

    public void stop() {
        if (thread != null) {
            thread.interrupt();
        }
    }
}
