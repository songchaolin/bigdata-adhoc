package io.gitee.songchaolin.adhoc.executor.runner;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import io.gitee.songchaolin.adhoc.common.enums.JobStatus;
import io.gitee.songchaolin.adhoc.common.util.LogConstants;
import io.gitee.songchaolin.adhoc.dao.entity.AdhocQueryJob;
import io.gitee.songchaolin.adhoc.dao.mapper.AdhocQueryJobMapper;
import io.gitee.songchaolin.adhoc.common.config.ConfigHolder;
import io.gitee.songchaolin.adhoc.executor.config.AdhocExecutorConfig;
import io.gitee.songchaolin.adhoc.storage.spi.StorageClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * LogBuffer 注册表：per id（taskId / jobId）内存日志 buffer，guava cache 管理。
 *
 * <p>job 日志清理/补偿两条路径：
 * <ul>
 *   <li><b>正常</b>：server 终态轮 gRPC clearJobLog 通知 -> {@link #clearJobLog} 直接删（server 已完整写 OSS）。</li>
 *   <li><b>异常</b>（server 挂/通知丢）：{@link #scan()} 低频定时器扫到"终态 + 超时未清理"的 job，
 *       读 OSS 末尾完整标识判断 server 是否已完整写：有标识 -> 直接删（避免无效补全）；
 *       无标识 -> 合并补全 OSS（{@link #reconcile}）后删。补全后不留内存。</li>
 * </ul>
 *
 * <p>task 日志：不写 OSS（内容已合并到 job 日志），运行中 gRPC fetchLog 读内存，终态 {@link #remove}。
 *
 * <p>guava Cache：expireAfterAccess 10min（最后防线清理，不补全）+ maximumSize 1000；eviction 仅 remove 不 flush。
 */
@Component
public class LogBufferRegistry {

    private static final Logger log = LoggerFactory.getLogger(LogBufferRegistry.class);
    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    /** 时间戳前缀长度（yyyy-MM-dd HH:mm:ss.SSS = 23）。 */
    private static final int TS_LEN = 23;

    private final AdhocQueryJobMapper jobMapper;
    private final StorageClient storageClient;
    private final Cache<String, LogBuffer> buffers = CacheBuilder.newBuilder()
            .expireAfterAccess(30, TimeUnit.MINUTES)
            .maximumSize(1000)
            .removalListener(n -> {
                if (n.getCause() != null && !n.getCause().name().equals("EXPLICIT")) {
                    log.debug("LogBuffer evicted id={} cause={}", n.getKey(), n.getCause());
                }
            })
            .build();
    /** job 日志 id 集合（区分 task：仅 job 日志参与兜底 scan）。 */
    private final Set<String> jobLogIds = ConcurrentHashMap.newKeySet();

    private long scanIntervalMs;
    private long finalizeDelayMs;
    private ScheduledExecutorService scheduler;

    public LogBufferRegistry(AdhocQueryJobMapper jobMapper, StorageClient storageClient, ConfigHolder cfg) {
        this.jobMapper = jobMapper;
        this.storageClient = storageClient;
        this.scanIntervalMs = cfg.get(AdhocExecutorConfig.LOG_RECONCILE_SCAN_INTERVAL_MS);
        this.finalizeDelayMs = cfg.get(AdhocExecutorConfig.LOG_FINALIZE_DELAY_MS);
    }

    /** 注册 job 日志 buffer（标记为 job，参与兜底 scan）。 */
    public void putJobLog(String jobId, LogBuffer buffer) {
        buffers.put(jobId, buffer);
        jobLogIds.add(jobId);
    }

    /** 注册 task 日志 buffer（不参与兜底 scan，task 不写 OSS）。 */
    public void put(String id, LogBuffer buffer) {
        buffers.put(id, buffer);
    }

    public LogBuffer get(String id) {
        return buffers.getIfPresent(id);
    }

    /** 正常路径（server gRPC clearJobLog 调）+ 兜底路径共用：删 buffer + jobLogIds。 */
    public void clearJobLog(String jobId) {
        buffers.invalidate(jobId);
        jobLogIds.remove(jobId);
    }

    /** task 终态清理（runner 调）。 */
    public void remove(String id) {
        buffers.invalidate(id);
    }

    public void start() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "job-log-reconciler");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleWithFixedDelay(() -> {
            try {
                scan();
            } catch (Throwable t) {
                log.warn("job-log-reconciler caught: {}", t.getMessage());
            }
        }, scanIntervalMs, scanIntervalMs, TimeUnit.MILLISECONDS);
        log.info("job-log-reconciler started, scanInterval={}ms, finalizeDelay={}ms", scanIntervalMs, finalizeDelayMs);
    }

    @PreDestroy
    public void stop() {
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }

    /**
     * 兜底扫描：对"终态 + finishTime 后超时仍未被 server 通知清理"的 job，
     * 按 OSS 末尾完整标识判断是否需要补全，补全后主动删本地 jobBuffer。
     * 正常情况下 job 终态后 ~2s 已被 server clearJobLog 清理，scan 扫不到 -> 无开销。
     */
    void scan() {
        for (String jobId : jobLogIds) {
            try {
                AdhocQueryJob job = jobMapper.selectById(jobId);
                if (job == null) {
                    clearJobLog(jobId);
                    continue;
                }
                if (!JobStatus.isTerminal(job.getStatus())) {
                    continue;
                }
                if (job.getFinishTime() == null) {
                    clearJobLog(jobId);
                    continue;
                }
                if (System.currentTimeMillis() - job.getFinishTime().getTime() < finalizeDelayMs) {
                    continue;  // 给 server 通知清理的时间
                }
                LogBuffer jobLog = buffers.getIfPresent(jobId);  // touch，重置 expireAfterAccess
                if (jobLog == null) {
                    jobLogIds.remove(jobId);
                    continue;
                }
                reconcile(jobId, jobLog, job.getPersistentLogPath());
                clearJobLog(jobId);  // 补全后（或检测到标识）主动删除
                log.info("[job={}] log reconciler fallback done, local jobBuffer cleared", jobId);
            } catch (Throwable t) {
                log.warn("reconcile fallback for {} caught: {}", jobId, t.getMessage());
            }
        }
    }

    /**
     * 读 OSS 末尾完整标识判断 server 是否已完整写：
     * 有标识 -> 直接返回（不补全，server 已完整，通知丢了也无需补）。
     * 无标识 -> 合并补全 OSS（OSS 已有行含 [server] + 本地缺失行，按时间戳排序）+ 末尾追加标识覆盖写。
     */
    void reconcile(String jobId, LogBuffer jobLog, String ossKey) {
        List<String> execLines = jobLog.snapshot();
        if (execLines.isEmpty()) {
            return;
        }
        List<String> ossLines = readOss(ossKey);
        if (ossLines != null && !ossLines.isEmpty()
                && ossLines.get(ossLines.size() - 1).contains(LogConstants.COMPLETE_MARKER)) {
            // server 已完整写 OSS（clearJobLog 通知丢了），无需补全
            return;
        }
        List<String> merged = mergeAndSort(ossLines, execLines);
        merged.add(LocalDateTime.now().format(TS_FMT) + " [executor] [INFO] " + LogConstants.COMPLETE_MARKER);
        String key = ossKey != null ? ossKey : LocalDate.now().format(DAY_FMT) + "/" + jobId + "/job.log";
        String returned = upload(key, merged);
        if (returned != null && !returned.equals(ossKey)) {
            try {
                jobMapper.update(null, new LambdaUpdateWrapper<AdhocQueryJob>()
                        .eq(AdhocQueryJob::getJobId, jobId)
                        .set(AdhocQueryJob::getPersistentLogPath, returned));
            } catch (Exception e) {
                // DB 更新失败不阻断 OSS 补全（best-effort）
                log.warn("update persistent_log_path for {} failed: {}", jobId, e.getMessage());
            }
        }
        log.info("[job={}] reconciled OSS: oss={} exec={} -> {} (marker appended)",
                jobId, ossLines == null ? 0 : ossLines.size(), execLines.size(), merged.size());
    }

    /** 合并 OSS 已有行 + executor 本地行（按整行去重，缺失的补入），按行首时间戳全局排序。server 行在 OSS 已有，保留不丢。 */
    private List<String> mergeAndSort(List<String> ossLines, List<String> execLines) {
        Set<String> seen = new HashSet<>(ossLines != null ? ossLines : Collections.emptyList());
        List<String> merged = new ArrayList<>(seen.size() + execLines.size());
        if (ossLines != null) {
            merged.addAll(ossLines);
        }
        for (String l : execLines) {
            if (seen.add(l)) {
                merged.add(l);
            }
        }
        merged.sort(Comparator.comparing(LogBufferRegistry::tsOf, Comparator.nullsLast(String::compareTo)));
        return merged;
    }

    /** 取行首时间戳（前 23 字符）；行过短或无时间戳返回 null（排末尾）。 */
    private static String tsOf(String line) {
        if (line == null || line.length() < TS_LEN) {
            return null;
        }
        return line.substring(0, TS_LEN);
    }

    /** 读 OSS 全量行；key 为 null / 下载失败 / 空返回 null。 */
    private List<String> readOss(String key) {
        if (key == null) {
            return null;
        }
        try (InputStream is = storageClient.download(key);
             BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            List<String> lines = new ArrayList<>();
            String line;
            while ((line = br.readLine()) != null) {
                lines.add(line);
            }
            return lines.isEmpty() ? null : lines;
        } catch (Exception e) {
            log.warn("read OSS log {} failed: {}", key, e.getMessage());
            return null;
        }
    }

    /** 合并行 join \n 覆盖上传 OSS，返回 key；失败返回 null。 */
    private String upload(String key, List<String> lines) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (String l : lines) {
            byte[] b = l.getBytes(StandardCharsets.UTF_8);
            out.write(b, 0, b.length);
            out.write('\n');
        }
        try {
            return storageClient.uploadLog(key, out.toByteArray());
        } catch (Exception e) {
            log.warn("upload OSS log {} failed: {}", key, e.getMessage());
            return null;
        }
    }
}
