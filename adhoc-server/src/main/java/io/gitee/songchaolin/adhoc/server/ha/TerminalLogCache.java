package io.gitee.songchaolin.adhoc.server.ha;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import io.gitee.songchaolin.adhoc.common.util.LogConstants;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 终态日志 TTL 缓存：terminal job 的 OSS 日志已 finalize（含 COMPLETE 标识，不可变），
 * 分页 / 轮询查询时缓存全量行，避免每次拉取都整文件下载 OSS（用户翻看已完成 job 日志的常见场景）。
 *
 * <p><b>仅缓存已 finalize 的日志</b>（{@link LogConstants#containsComplete} 为 true）；
 * running / 未 finalize 的日志会增长，缓存即陈旧，故不缓存（每次实时读 OSS，这类是降级场景，较少）。
 *
 * <p>guava Cache：expireAfterWrite 60s（终态日志不可变，60s 后淘汰重读，兼顾内存上限与 OSS 最终一致）+ maximumSize 100（LRU）。
 * 镜像 {@link JobLogRegistry} / executor {@code LogBufferRegistry} 的 guava 缓存模式。
 */
@Component
public class TerminalLogCache {

    private final Cache<String, List<String>> cache = CacheBuilder.newBuilder()
            .expireAfterWrite(60, TimeUnit.SECONDS)
            .maximumSize(100)
            .build();

    /** 取缓存的终态日志全量行；未缓存返回 null（调用方回落整文件读 OSS）。 */
    public List<String> get(String ossKey) {
        return cache.getIfPresent(ossKey);
    }

    /**
     * 缓存终态日志全量行。<b>仅应缓存已含 COMPLETE 标识的 finalize 日志</b>（调用方负责判断）。
     * 防御性拷贝，隔离调用方后续对入参 list 的修改。
     */
    public void put(String ossKey, List<String> lines) {
        if (ossKey != null && lines != null) {
            cache.put(ossKey, new ArrayList<>(lines));
        }
    }

    /** 清空缓存（测试隔离用；生产亦可按需调用）。 */
    public void clear() {
        cache.invalidateAll();
    }
}
