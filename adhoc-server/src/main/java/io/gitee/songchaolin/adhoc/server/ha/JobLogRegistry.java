package io.gitee.songchaolin.adhoc.server.ha;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalCause;
import com.google.common.cache.RemovalNotification;
import io.gitee.songchaolin.adhoc.dao.entity.AdhocQueryJob;
import io.gitee.songchaolin.adhoc.dao.mapper.AdhocQueryJobMapper;
import io.gitee.songchaolin.adhoc.storage.spi.StorageClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * server 端 per-job JobLog buffer 注册表 + pull/flush offset 跟踪。
 * guava Cache 管理：expireAfterAccess 2h + maximumSize 1000（LRU）。
 * EXPIRED/SIZE eviction 时 best-effort flush OSS（防止 collector 挂了后 append 的行丢失）；
 * EXPLICIT（collector 终态 remove）静默。正常 2s 刷 OSS 由 JobLogCollector 负责。
 * 镜像 executor LogBufferRegistry 的 guava 缓存模式 + eviction flush。
 */
@Component
public class JobLogRegistry {

    private static final Logger log = LoggerFactory.getLogger(JobLogRegistry.class);
    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** per-job 状态：JobLog buffer + pull/flush offset。eviction 整体淘汰（不散落）。 */
    private static final class JobLogState {
        final JobLog jobLog = new JobLog();
        volatile long lastPulledOffset = 0;
        volatile int lastFlushedLines = 0;
    }

    private final StorageClient storageClient;
    private final AdhocQueryJobMapper jobMapper;
    private final Cache<String, JobLogState> cache;

    public JobLogRegistry(StorageClient storageClient, AdhocQueryJobMapper jobMapper) {
        this.storageClient = storageClient;
        this.jobMapper = jobMapper;
        this.cache = CacheBuilder.newBuilder()
                .expireAfterAccess(30, TimeUnit.MINUTES)
                .maximumSize(1000)
                .removalListener((RemovalNotification<String, JobLogState> n) -> {
                    if (n.getCause() != RemovalCause.EXPLICIT && n.getValue() != null) {
                        // EXPIRED/SIZE eviction：best-effort flush OSS，防止 collector 挂了后 append 的行丢失
                        flushToOss(n.getKey(), n.getValue().jobLog);
                    }
                })
                .build();
    }

    /** eviction 兜底 flush：全量 upload OSS（覆盖）+ 更新 persistent_log_path。失败不抛（warn）。 */
    private void flushToOss(String jobId, JobLog jobLog) {
        try {
            List<String> lines = jobLog.snapshot();
            if (lines.isEmpty()) {
                return;
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            for (String l : lines) {
                byte[] b = l.getBytes(StandardCharsets.UTF_8);
                out.write(b, 0, b.length);
                out.write('\n');
            }
            String fileName = LocalDate.now().format(DAY_FMT) + "/" + jobId + "/job.log";
            String key = storageClient.uploadLog(fileName, out.toByteArray());
            if (key != null) {
                jobMapper.update(null, new LambdaUpdateWrapper<AdhocQueryJob>()
                        .eq(AdhocQueryJob::getJobId, jobId)
                        .set(AdhocQueryJob::getPersistentLogPath, key));
            }
            log.info("JobLogRegistry eviction flush job {} -> OSS ({} lines)", jobId, lines.size());
        } catch (Exception e) {
            log.warn("JobLogRegistry eviction flush failed for {}: {}", jobId, e.getMessage());
        }
    }

    public void register(String jobId) {
        cache.put(jobId, new JobLogState());
    }

    public void append(String jobId, String line) {
        JobLogState s = cache.getIfPresent(jobId);
        if (s != null) {
            s.jobLog.append(line);
        }
    }

    public void appendAll(String jobId, List<String> lines) {
        JobLogState s = cache.getIfPresent(jobId);
        if (s != null) {
            s.jobLog.appendAll(lines);
        }
    }

    public JobLog get(String jobId) {
        JobLogState s = cache.getIfPresent(jobId);
        return s != null ? s.jobLog : null;
    }

    public long getLastPulledOffset(String jobId) {
        JobLogState s = cache.getIfPresent(jobId);
        return s != null ? s.lastPulledOffset : 0L;
    }

    public void setLastPulledOffset(String jobId, long offset) {
        JobLogState s = cache.getIfPresent(jobId);
        if (s != null) {
            s.lastPulledOffset = offset;
        }
    }

    public int getLastFlushedLines(String jobId) {
        JobLogState s = cache.getIfPresent(jobId);
        return s != null ? s.lastFlushedLines : 0;
    }

    public void setLastFlushedLines(String jobId, int n) {
        JobLogState s = cache.getIfPresent(jobId);
        if (s != null) {
            s.lastFlushedLines = n;
        }
    }

    public void remove(String jobId) {
        cache.invalidate(jobId);
    }

    public Set<String> activeJobIds() {
        return cache.asMap().keySet();
    }
}
