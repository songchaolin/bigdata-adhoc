package io.gitee.songchaolin.adhoc.metadata;

import io.gitee.songchaolin.adhoc.common.enums.EngineType;
import io.gitee.songchaolin.adhoc.common.exception.AdhocErrorCode;
import io.gitee.songchaolin.adhoc.common.exception.AdhocException;
import io.gitee.songchaolin.adhoc.metadata.dto.MetadataColumn;
import io.gitee.songchaolin.adhoc.metadata.dto.MetadataDatabase;
import io.gitee.songchaolin.adhoc.metadata.dto.MetadataTable;
import io.gitee.songchaolin.adhoc.metadata.fetcher.HiveMetastoreFetcher;
import io.gitee.songchaolin.adhoc.metadata.fetcher.MetadataFetcher;
import io.gitee.songchaolin.adhoc.metadata.fetcher.StarRocksMetadataFetcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 元数据查询服务：按 engineType 路由 {@link MetadataFetcher} + {@link MetadataCache} 缓存
 * + keyword 内存前缀过滤 + 异常包装。直连超管，不做按用户过滤（用户身份仅审计）；
 * 补全与查询权限分离，实际查询由 Kyuubi proxyUser + Ranger 兜底鉴权。
 *
 * <p>多实例：{@code instance} 指定引擎实例（与 executor 同名逻辑集群），空则走默认实例
 * （fetcher 读 adhoc.metadata.{kind}.default_instance）。缓存键含解析后的实例名，不同实例（不同集群/metastore）数据隔离。
 */
@Service
public class MetadataService {

    private static final Logger log = LoggerFactory.getLogger(MetadataService.class);

    private final HiveMetastoreFetcher hiveFetcher;
    private final StarRocksMetadataFetcher srFetcher;
    private final MetadataCache cache;

    public MetadataService(HiveMetastoreFetcher hiveFetcher, StarRocksMetadataFetcher srFetcher, MetadataCache cache) {
        this.hiveFetcher = hiveFetcher;
        this.srFetcher = srFetcher;
        this.cache = cache;
    }

    // ===== 向后兼容重载（instance=null -> 默认实例） =====

    /** 库列表（默认实例，keyword 前缀过滤）。 */
    public List<MetadataDatabase> listDatabases(String engineType, String keyword) {
        return listDatabases(engineType, null, keyword);
    }

    /** 表列表（默认实例）。 */
    public List<MetadataTable> listTables(String engineType, String database, String keyword) {
        return listTables(engineType, null, database, keyword);
    }

    /** 列列表（默认实例）。 */
    public List<MetadataColumn> listColumns(String engineType, String database, String table) {
        return listColumns(engineType, null, database, table);
    }

    // ===== 多实例核心方法 =====

    /** 库列表（指定实例，keyword 前缀过滤，null/空=全量）。 */
    public List<MetadataDatabase> listDatabases(String engineType, String instance, String keyword) {
        MetadataFetcher f = selectFetcher(engineType);
        String inst = f.resolveInstanceName(instance);
        List<String> dbs = cache.getDatabases(engineType, inst);
        if (dbs == null) {
            dbs = query(() -> f.listDatabases(inst), engineType, inst, "databases");
            cache.putDatabases(engineType, inst, dbs);
        }
        return filterPrefix(dbs, s -> s, keyword).stream()
                .map(MetadataDatabase::new)
                .collect(Collectors.toList());
    }

    /** 表列表（指定实例，keyword 前缀过滤）。 */
    public List<MetadataTable> listTables(String engineType, String instance, String database, String keyword) {
        MetadataFetcher f = selectFetcher(engineType);
        String inst = f.resolveInstanceName(instance);
        List<MetadataTable> tables = cache.getTables(engineType, inst, database);
        if (tables == null) {
            tables = query(() -> f.listTables(inst, database), engineType, inst, "tables:" + database);
            cache.putTables(engineType, inst, database, tables);
        }
        return filterPrefix(tables, MetadataTable::getName, keyword);
    }

    /** 列列表（指定实例，不做 keyword 过滤，补全列时通常全列展示）。 */
    public List<MetadataColumn> listColumns(String engineType, String instance, String database, String table) {
        MetadataFetcher f = selectFetcher(engineType);
        String inst = f.resolveInstanceName(instance);
        List<MetadataColumn> cols = cache.getColumns(engineType, inst, database, table);
        if (cols == null) {
            cols = query(() -> f.listColumns(inst, database, table), engineType, inst, "columns:" + database + "." + table);
            cache.putColumns(engineType, inst, database, table, cols);
        }
        return cols;
    }

    private MetadataFetcher selectFetcher(String engineType) {
        if (EngineType.STARROCKS.is(engineType)) {
            return srFetcher;
        }
        if (EngineType.KYUUBI.is(engineType)) {
            return hiveFetcher;
        }
        throw new AdhocException(AdhocErrorCode.ADHOC_ENGINE_TYPE_INVALID);
    }

    /** 执行 fetcher：业务异常（NOT_CONFIGURED 等）透传，其它异常包成 ADHOC_METADATA_QUERY_FAILED。 */
    private <T> List<T> query(QueryAction<T> action, String engineType, String instance, String context) {
        try {
            return action.run();
        } catch (AdhocException e) {
            throw e;
        } catch (Exception e) {
            log.error("【元数据查询失败】engine={} instance={} context={} 错误={}", engineType, instance, context, e.getMessage(), e);
            throw new AdhocException(AdhocErrorCode.ADHOC_METADATA_QUERY_FAILED,
                    "engine=" + engineType + " instance=" + instance + " " + context + ": " + e.getMessage());
        }
    }

    /** keyword 前缀过滤（大小写不敏感），null/空返回全量。 */
    private <T> List<T> filterPrefix(List<T> all, Function<T, String> nameFn, String keyword) {
        if (keyword == null || keyword.isEmpty()) {
            return all;
        }
        String prefix = keyword.toLowerCase();
        return all.stream()
                .filter(t -> {
                    String n = nameFn.apply(t);
                    return n != null && n.toLowerCase().startsWith(prefix);
                })
                .collect(Collectors.toList());
    }

    @FunctionalInterface
    private interface QueryAction<T> {
        List<T> run() throws Exception;
    }
}
