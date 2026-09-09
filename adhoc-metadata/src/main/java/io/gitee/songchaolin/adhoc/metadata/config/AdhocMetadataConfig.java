package io.gitee.songchaolin.adhoc.metadata.config;

import io.gitee.songchaolin.adhoc.common.config.ConfigItem;

/**
 * 元数据自动补全配置项（adhoc.metadata.*）。
 * <ul>
 *   <li>Hive metastore：直连 MySQL 查 DBS/TBLS/COLUMNS_V2/PARTITION_KEYS（Hive 3.x schema）。超管账号，
 *       补全绕过 Ranger，实际查询由 Kyuubi proxyUser + Ranger 兜底鉴权。</li>
 *   <li>StarRocks：直连 information_schema（MySQL 协议），与 executor 的 StarRocks 引擎实例同名实例，
 *       server 独立配置项（铁律：server 不依赖 executor），Apollo 配同值即可。</li>
 *   <li>缓存：Caffeine，全局缓存（直连超管所有用户数据一致）+ 全量缓存 + 内存前缀过滤 keyword。</li>
 * </ul>
 *
 * <p><b>多实例</b>（与 executor 引擎连接一致：实例名作 key 中段，默认实例 + 每实例独立 url/user/password/pool-size）：
 * <pre>
 * adhoc.metadata.hive-metastore.default_instance = kyuubi-01
 * adhoc.metadata.hive-metastore.kyuubi-01.url = jdbc:mysql://hm-host:3306/hive?...
 * adhoc.metadata.hive-metastore.kyuubi-01.user/password = ...
 * adhoc.metadata.starrocks.default_instance = starrocks-01
 * adhoc.metadata.starrocks.starrocks-01.url/user/password = ...
 * </pre>
 * 实例名与 executor 侧 {@code adhoc.engine.{ENGINE}.{instance}} 对齐（同逻辑集群同名）。
 * 连接源（url/user/password）全部在 yml/Apollo 每实例配置，<b>无引擎级回退、无代码默认凭据</b>；
 * 未配 url 抛 {@code ADHOC_METADATA_NOT_CONFIGURED}。pool-size 为操作性配置，保留引擎级回退（默认 4）。
 *
 * <p>平铺 {@code public static final ConfigItem}，Apollo 配了听 Apollo，没配用默认值。
 * 配置自描述：加进 server {@code ConfigController} 的 CATALOG 后在 {@code GET /api/config} 可见。
 */
public final class AdhocMetadataConfig {

    // ===== Hive metastore MySQL（adhoc.metadata.hive-metastore.*） =====
    // 连接源（url/user/password）每实例配 adhoc.metadata.hive-metastore.{instance}.*，无引擎级回退。
    public static final ConfigItem<String> HIVE_METASTORE_DEFAULT_INSTANCE = ConfigItem.of(
            "adhoc.metadata.hive-metastore.default_instance", "kyuubi-01",
            "Hive metastore 默认实例名（与 adhoc.engine.KYUUBI.default_instance 对齐）",
            "元数据查询未传 instance 时用此实例");
    public static final ConfigItem<Integer> HIVE_METASTORE_POOL_SIZE = ConfigItem.of(
            "adhoc.metadata.hive-metastore.pool-size", 4,
            "Hive metastore Druid 连接池大小（引擎级回退；每实例可配 adhoc.metadata.hive-metastore.{instance}.pool-size 覆盖）",
            "补全高频，池化复用；调大并发高");

    // ===== StarRocks 元数据（adhoc.metadata.starrocks.*） =====
    // 连接源（url/user/password）每实例配 adhoc.metadata.starrocks.{instance}.*，无引擎级回退。
    public static final ConfigItem<String> STARROCKS_METADATA_DEFAULT_INSTANCE = ConfigItem.of(
            "adhoc.metadata.starrocks.default_instance", "starrocks-01",
            "StarRocks 元数据默认实例名（与 adhoc.engine.STARROCKS.default_instance 对齐）",
            "元数据查询未传 instance 时用此实例");
    public static final ConfigItem<Integer> STARROCKS_METADATA_POOL_SIZE = ConfigItem.of(
            "adhoc.metadata.starrocks.pool-size", 4,
            "StarRocks 元数据 Druid 连接池大小（引擎级回退；每实例可配 adhoc.metadata.starrocks.{instance}.pool-size 覆盖）",
            "");

    // ===== 缓存 TTL（adhoc.metadata.cache.*） =====
    public static final ConfigItem<Long> META_CACHE_DB_TTL_MIN = ConfigItem.of(
            "adhoc.metadata.cache.db-ttl-min", 30L,
            "库列表缓存 TTL(分钟)",
            "库变更不频繁，长 TTL");
    public static final ConfigItem<Long> META_CACHE_TABLE_TTL_MIN = ConfigItem.of(
            "adhoc.metadata.cache.table-ttl-min", 10L,
            "表列表缓存 TTL(分钟)",
            "表变更相对频繁，短 TTL");
    public static final ConfigItem<Long> META_CACHE_COLUMN_TTL_MIN = ConfigItem.of(
            "adhoc.metadata.cache.column-ttl-min", 30L,
            "列列表缓存 TTL(分钟)",
            "列稳定，长 TTL");

    private AdhocMetadataConfig() {
    }
}
