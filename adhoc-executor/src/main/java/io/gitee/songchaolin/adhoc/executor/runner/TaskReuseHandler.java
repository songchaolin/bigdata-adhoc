package io.gitee.songchaolin.adhoc.executor.runner;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import io.gitee.songchaolin.adhoc.common.util.SqlHashUtil;
import io.gitee.songchaolin.adhoc.executor.result.ResultReuseChecker;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 结果复用处理器
 * <p>
 * 封装 ResultReuseChecker 调用，处理复用检查和标记。
 * 添加本地缓存（60秒 TTL），避免高频查询 DB。
 */
@Component
public class TaskReuseHandler {

    /** 本地缓存：key = userId:sqlHash，value = taskId 或 "NULL" 表示未命中 */
    private final Cache<String, String> reuseCache;

    private final ResultReuseChecker reuseChecker;

    public TaskReuseHandler(ResultReuseChecker reuseChecker) {
        this.reuseChecker = reuseChecker;
        this.reuseCache = CacheBuilder.newBuilder()
                .expireAfterWrite(60, TimeUnit.SECONDS)  // 60秒过期
                .maximumSize(10000)                        // 最大缓存条目
                .build();
    }

    /**
     * 检查是否可复用历史结果
     *
     * @param userId  用户ID
     * @param sqlHash SQL哈希值（已计算）
     * @return 可复用返回源 Task ID，否则返回 null
     */
    public String checkReuse(String userId, String sqlHash) {
        String cacheKey = userId + ":" + sqlHash;

        // 先查缓存
        String cached = reuseCache.getIfPresent(cacheKey);
        if (cached != null) {
            return cached.equals("NULL") ? null : cached;
        }

        // 缓存未命中，查询 DB
        String existingTaskId = reuseChecker.checkReuse(userId, sqlHash);

        // 写入缓存（用 "NULL" 标记未命中，避免 null 值）
        reuseCache.put(cacheKey, existingTaskId != null ? existingTaskId : "NULL");

        return existingTaskId;
    }

    /**
     * 标记复用成功
     *
     * @param taskId       当前 Task ID
     * @param sourceTaskId 源 Task ID
     * @param sqlHash      SQL 哈希值（已计算）
     * @param startTimeMs  开始时间
     */
    public void markReused(String taskId, String sourceTaskId, String sqlHash, long startTimeMs) {
        reuseChecker.markReusedSuccess(taskId, sourceTaskId, sqlHash, startTimeMs);
    }
}