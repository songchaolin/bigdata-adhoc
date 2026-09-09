package io.gitee.songchaolin.adhoc.metadata.fetcher;

import io.gitee.songchaolin.adhoc.metadata.dto.MetadataColumn;
import io.gitee.songchaolin.adhoc.metadata.dto.MetadataTable;

import java.util.List;

/**
 * 元数据 fetcher：按引擎实现 库/表/列 查询。返回全量（{@link io.gitee.songchaolin.adhoc.metadata.MetadataCache} 全量缓存，
 * keyword 前缀过滤由 {@link io.gitee.songchaolin.adhoc.metadata.MetadataService} 在内存做，保证缓存命中率）。
 *
 * <p>多实例：方法首参 {@code instance} 指定引擎实例（如 kyuubi-02），空则由 fetcher 取默认实例。
 * 每实例独立连接池（{@code adhoc.metadata.{kind}.{instance}.*}），见 {@link io.gitee.songchaolin.adhoc.metadata.fetcher.AbstractJdbcMetadataFetcher}。
 */
public interface MetadataFetcher {

    /** 库列表（全量）。 */
    List<String> listDatabases(String instance);

    /** 表列表（全量）。 */
    List<MetadataTable> listTables(String instance, String database);

    /** 列列表（全量，含 Hive 分区键，{@code partition=true} 标记）。 */
    List<MetadataColumn> listColumns(String instance, String database, String table);

    /** 解析实例名：空 -> 默认实例。供缓存键用，避免 default_instance 变更后缓存串集群。 */
    String resolveInstanceName(String instance);
}
