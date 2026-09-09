package io.gitee.songchaolin.adhoc.metadata.fetcher;

import io.gitee.songchaolin.adhoc.common.config.ConfigHolder;
import io.gitee.songchaolin.adhoc.common.config.ConfigItem;
import io.gitee.songchaolin.adhoc.metadata.config.AdhocMetadataConfig;
import io.gitee.songchaolin.adhoc.metadata.dto.MetadataColumn;
import io.gitee.songchaolin.adhoc.metadata.dto.MetadataTable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Hive metastore 元数据 fetcher：直连 metastore MySQL，查 DBS/TBLS/COLUMNS_V2/PARTITION_KEYS（Hive 3.x schema）。
 * 超管账号，补全绕过 Ranger，实际查询由 Kyuubi proxyUser + Ranger 兜底鉴权。
 *
 * <p>表注释：Hive 无直接注释列，存 TABLE_PARAMS(PARAM_KEY='comment')，LEFT JOIN 取。
 * <p>列：普通列查 COLUMNS_V2（按 INTEGER_IDX 排序）+ 分区键查 PARTITION_KEYS（追加到列尾，partition=true），
 * 使补全 WHERE 时分区列（如 ds）也可候选。
 */
@Component
public class HiveMetastoreFetcher extends AbstractJdbcMetadataFetcher {

    public HiveMetastoreFetcher(ConfigHolder cfg) {
        super(cfg);
    }

    @Override
    protected ConfigItem<Integer> poolSizeItem() { return AdhocMetadataConfig.HIVE_METASTORE_POOL_SIZE; }

    @Override
    protected ConfigItem<String> defaultInstanceItem() { return AdhocMetadataConfig.HIVE_METASTORE_DEFAULT_INSTANCE; }

    @Override
    protected String kind() { return "hive-metastore"; }

    @Override
    public List<String> listDatabases(String instance) {
        // DBS.NAME（DISTINCT 去重：metastore 可能存在同名库多 DB_ID 记录，如重复 default）；Hive 3.x 无 information_schema/sys，但保险过滤
        return jdbc(instance).queryForList(
                "SELECT DISTINCT NAME FROM DBS WHERE NAME NOT IN ('information_schema','sys','mysql') ORDER BY NAME",
                String.class);
    }

    @Override
    public List<MetadataTable> listTables(String instance, String database) {
        // TBLS JOIN DBS；TBL_TYPE 区分表/视图（VIRTUAL_VIEW=视图）；LEFT JOIN TABLE_PARAMS 取表注释
        return jdbc(instance).query(
                "SELECT t.TBL_NAME, t.TBL_TYPE, tp.PARAM_VALUE AS TBL_COMMENT " +
                "FROM TBLS t " +
                "JOIN DBS d ON t.DB_ID = d.DB_ID " +
                "LEFT JOIN TABLE_PARAMS tp ON tp.TBL_ID = t.TBL_ID AND tp.PARAM_KEY = 'comment' " +
                "WHERE d.NAME = ? ORDER BY t.TBL_NAME",
                (rs, i) -> new MetadataTable(
                        rs.getString("TBL_NAME"),
                        rs.getString("TBL_TYPE"),
                        rs.getString("TBL_COMMENT")),
                database);
    }

    @Override
    public List<MetadataColumn> listColumns(String instance, String database, String table) {
        // 普通列：COLUMNS_V2 JOIN SDS(SD_ID->CD_ID) JOIN TBLS JOIN DBS，按 INTEGER_IDX 排序
        List<MetadataColumn> cols = new ArrayList<>(jdbc(instance).query(
                "SELECT c.COLUMN_NAME, c.TYPE_NAME, c.COMMENT " +
                "FROM COLUMNS_V2 c " +
                "JOIN SDS s ON c.CD_ID = s.CD_ID " +
                "JOIN TBLS t ON s.SD_ID = t.SD_ID " +
                "JOIN DBS d ON t.DB_ID = d.DB_ID " +
                "WHERE d.NAME = ? AND t.TBL_NAME = ? " +
                "ORDER BY c.INTEGER_IDX",
                (rs, i) -> new MetadataColumn(
                        rs.getString("COLUMN_NAME"),
                        rs.getString("TYPE_NAME"),
                        rs.getString("COMMENT"),
                        false),
                database, table));
        // 分区键：PARTITION_KEYS JOIN TBLS JOIN DBS，追加到列尾（partition=true，使 WHERE ds=... 可补全）
        cols.addAll(jdbc(instance).query(
                "SELECT p.PKEY_NAME, p.PKEY_TYPE " +
                "FROM PARTITION_KEYS p " +
                "JOIN TBLS t ON p.TBL_ID = t.TBL_ID " +
                "JOIN DBS d ON t.DB_ID = d.DB_ID " +
                "WHERE d.NAME = ? AND t.TBL_NAME = ? " +
                "ORDER BY p.INTEGER_IDX",
                (rs, i) -> new MetadataColumn(
                        rs.getString("PKEY_NAME"),
                        rs.getString("PKEY_TYPE"),
                        null,
                        true),
                database, table));
        return cols;
    }
}
