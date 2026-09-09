package io.gitee.songchaolin.adhoc.server.ha;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalCause;
import com.google.common.cache.RemovalNotification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 本 server 正在处理的 Job 注册表（内存，guava Cache）。
 * QueueWorker claim 时 add，JobLogCollector 检测终态时 remove，server 重启自动清空（= 全部丢失）。
 * JobReconcileTask 查此判断 job 是否还在本节点运行。
 *
 * guava Cache：expireAfterAccess 2h + maximumSize 10000。
 * 线程安全：guava Cache 的 put/getIfPresent/invalidate 单操作原子，使用场景无 check-then-act 竞态，不需额外锁。
 */
@Component
public class RunningJobRegistry {

    private static final Logger log = LoggerFactory.getLogger(RunningJobRegistry.class);

    private final Cache<String, Boolean> cache = CacheBuilder.newBuilder()
            .expireAfterAccess(4, TimeUnit.HOURS)
            .maximumSize(10000)
            .removalListener((RemovalNotification<String, Boolean> n) -> {
                if (n.getCause() != RemovalCause.EXPLICIT) {
                    log.warn("RunningJobRegistry evicted job {} (cause={}), may be reconciled as FAILED", n.getKey(), n.getCause());
                }
            })
            .build();

    public void add(String jobId) {
        cache.put(jobId, Boolean.TRUE);
    }

    public void remove(String jobId) {
        cache.invalidate(jobId);
    }

    public boolean contains(String jobId) {
        return cache.getIfPresent(jobId) != null;
    }
}