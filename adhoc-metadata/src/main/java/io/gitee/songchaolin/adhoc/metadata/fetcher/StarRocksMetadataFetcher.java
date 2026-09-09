package io.gitee.songchaolin.adhoc.metadata.fetcher;

import io.gitee.songchaolin.adhoc.common.config.ConfigHolder;
import io.gitee.songchaolin.adhoc.common.config.ConfigItem;
import io.gitee.songchaolin.adhoc.metadata.config.AdhocMetadataConfig;
import io.gitee.songchaolin.adhoc.metadata.dto.MetadataColumn;
import io.gitee.songchaolin.adhoc.metadata.dto.MetadataTable;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * StarRocks 元数据 fetcher：直连 information_schema（MySQL 协议），与 executor 的 StarRocks 引擎实例（adhoc.engine.STARROCKS.{instance}）同名实例。
 * 固定账号查询，返回该账号能看到的库表（与 SR 查询同账号，权限一致，无代理）。
 *
 * <p>SR/MySQL 的 information_schema 跨库，连 information_schema 库即可查全；{@code partition} 全 false
 *（SR 分区为内部分布机制，非列，补全列不含分区列）。
 */
@Component
public class StarRocksMetadataFetcher extends AbstractJdbcMetadataFetcher {

    public StarRocksMetadataFetcher(ConfigHolder cfg) {
        super(cfg);
    }

    @Override
    protected ConfigItem<Integer> poolSizeItem() { return AdhocMetadataConfig.STARROCKS_METADATA_POOL_SIZE; }

    @Override
    protected ConfigItem<String> defaultInstanceItem() { return AdhocMetadataConfig.STARROCKS_METADATA_DEFAULT_INSTANCE; }

    @Override
    protected String kind() { return "starrocks"; }

    @Override
    public List<String> listDatabases(String instance) {
        return jdbc(instance).queryForList(
                "SELECT SCHEMA_NAME FROM INFORMATION_SCHEMA.SCHEMATA " +
                "WHERE SCHEMA_NAME NOT IN ('information_schema','_statistics_','mysql','sys','performance_schema') " +
                "ORDER BY SCHEMA_NAME",
                String.class);
    }

    @Override
    public List<MetadataTable> listTables(String instance, String database) {
        return jdbc(instance).query(
                "SELECT TABLE_NAME, TABLE_TYPE, TABLE_COMMENT " +
                "FROM INFORMATION_SCHEMA.TABLES " +
                "WHERE TABLE_SCHEMA = ? ORDER BY TABLE_NAME",
                (rs, i) -> new MetadataTable(
                        rs.getString("TABLE_NAME"),
                        rs.getString("TABLE_TYPE"),
                        rs.getString("TABLE_COMMENT")),
                database);
    }

    @Override
    public List<MetadataColumn> listColumns(String instance, String database, String table) {
        return jdbc(instance).query(
                "SELECT COLUMN_NAME, DATA_TYPE, COLUMN_COMMENT " +
                "FROM INFORMATION_SCHEMA.COLUMNS " +
                "WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? ORDER BY ORDINAL_POSITION",
                (rs, i) -> new MetadataColumn(
                        rs.getString("COLUMN_NAME"),
                        rs.getString("DATA_TYPE"),
                        rs.getString("COLUMN_COMMENT"),
                        false),
                database, table);
    }
}
