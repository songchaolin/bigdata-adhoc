package io.gitee.songchaolin.adhoc.metadata;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.gitee.songchaolin.adhoc.common.config.ConfigHolder;
import io.gitee.songchaolin.adhoc.metadata.config.AdhocMetadataConfig;
import io.gitee.songchaolin.adhoc.metadata.dto.MetadataColumn;
import io.gitee.songchaolin.adhoc.metadata.dto.MetadataTable;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * 元数据缓存：Caffeine，全局共享（直连超管所有用户数据一致，不分 userId）。
 * 全量缓存（fetcher 返回全量），keyword 前缀过滤由 {@link MetadataService} 内存做，保证缓存命中率。
 *
 * <p>缓存键含 instance：不同引擎实例（不同集群/metastore）元数据不同，按 (engineType, instance) 隔离缓存。
 *
 * <p>TTL：库 30min / 表 10min / 列 30min（{@link AdhocMetadataConfig} 可配）。靠 TTL 自然过期，
 * 不做 metastore 变更主动失效（二期）。
 */
@Component
public class MetadataCache {

    private final Cache<String, List<String>> databasesCache;
    private final Cache<String, List<MetadataTable>> tablesCache;
    private final Cache<String, List<MetadataColumn>> columnsCache;

    public MetadataCache(ConfigHolder cfg) {
        this.databasesCache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(cfg.get(AdhocMetadataConfig.META_CACHE_DB_TTL_MIN)))
                .maximumSize(64)
                .build();
        this.tablesCache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(cfg.get(AdhocMetadataConfig.META_CACHE_TABLE_TTL_MIN)))
                .maximumSize(2048)
                .build();
        this.columnsCache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(cfg.get(AdhocMetadataConfig.META_CACHE_COLUMN_TTL_MIN)))
                .maximumSize(8192)
                .build();
    }

    public List<String> getDatabases(String engineType, String instance) {
        return databasesCache.getIfPresent(key(engineType, instance));
    }

    public void putDatabases(String engineType, String instance, List<String> dbs) {
        databasesCache.put(key(engineType, instance), dbs);
    }

    public List<MetadataTable> getTables(String engineType, String instance, String database) {
        return tablesCache.getIfPresent(key(engineType, instance, database));
    }

    public void putTables(String engineType, String instance, String database, List<MetadataTable> tables) {
        tablesCache.put(key(engineType, instance, database), tables);
    }

    public List<MetadataColumn> getColumns(String engineType, String instance, String database, String table) {
        return columnsCache.getIfPresent(key(engineType, instance, database, table));
    }

    public void putColumns(String engineType, String instance, String database, String table, List<MetadataColumn> cols) {
        columnsCache.put(key(engineType, instance, database, table), cols);
    }

    private static String key(String... parts) {
        return String.join(":", parts);
    }
}
