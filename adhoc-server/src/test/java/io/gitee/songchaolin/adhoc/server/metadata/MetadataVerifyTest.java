package io.gitee.songchaolin.adhoc.server.metadata;

import io.gitee.songchaolin.adhoc.common.config.ConfigHolder;
import io.gitee.songchaolin.adhoc.common.exception.AdhocErrorCode;
import io.gitee.songchaolin.adhoc.common.exception.AdhocException;
import io.gitee.songchaolin.adhoc.metadata.MetadataCache;
import io.gitee.songchaolin.adhoc.metadata.MetadataService;
import io.gitee.songchaolin.adhoc.metadata.config.AdhocMetadataConfig;
import io.gitee.songchaolin.adhoc.metadata.dto.MetadataColumn;
import io.gitee.songchaolin.adhoc.metadata.dto.MetadataDatabase;
import io.gitee.songchaolin.adhoc.metadata.dto.MetadataTable;
import io.gitee.songchaolin.adhoc.metadata.fetcher.HiveMetastoreFetcher;
import io.gitee.songchaolin.adhoc.metadata.fetcher.StarRocksMetadataFetcher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 元数据自动补全验证测试（合并自 adhoc-metadata/MetadataSchemaVerifyTest + adhoc-server/MetadataServiceVerifyTest，
 * 统一迁至 adhoc-server/.../server/metadata 下）。
 *
 * <h3>设计要点</h3>
 * <ul>
 *   <li><b>地址读 yml，不硬编码</b>：手动 SnakeYAML 解析 classpath 的 {@code application.yml}，
 *       展平 {@code adhoc.metadata.*} 为 dotted key，经 {@link ConfigHolder#forTest(Map)} 注入 fetcher/cache。
 *       不启 Spring 上下文 -> 不拉 Apollo、不抢 gRPC 9090，避免上下文起不来拖垮构建。</li>
 *   <li><b>读不到地址 / 连不通不 fail</b>：{@code @BeforeAll} 解析 yml + ping metastore；
 *       url 未配 -> {@code configured=false}；网络不通 -> {@code metastoreReachable=false}。
 *       依赖 metastore 的用例以 {@link #assumeMetastoreAvailable()} 守卫，不满足即 <b>跳过</b>（Assumption 失败），
 *       保证 {@code mvn package}/CI 不被阻断；仅在 metastore 可达且断言失败时才算失败（真 bug）。</li>
 *   <li><b>两层覆盖</b>：原 SchemaVerifyTest 的 raw-JDBC 标准表存在性 + SQL 正确性；
 *       原 ServiceVerifyTest 的 MetadataService 路由/缓存/去重/非法引擎。</li>
 * </ul>
 *
 * <p>跑法：
 * <pre>mvn -pl adhoc-server -am test -Dtest=MetadataVerifyTest -DfailIfNoTests=false -Dsurefire.useFile=false</pre>
 * 地址读 test yml（环境变量占位符）；ADHOC_HMS_HOST 未设置时整个类自动跳过，
 * 网络不通时依赖用例 assumeTrue 守卫跳过，均不影响构建。
 */
@EnabledIfEnvironmentVariable(named = "ADHOC_HMS_HOST", matches = ".+")
class MetadataVerifyTest {

    // 已验证存在的真实元数据（demo_db 库 / adhoc_affected_test 表，id+name 两列）
    private static final String DB = "demo_db";
    private static final String TBL = "adhoc_affected_test";
    private static final String[] STD_TABLES = {
            "DBS", "TBLS", "TABLE_PARAMS", "COLUMNS_V2", "SDS", "PARTITION_KEYS", "CDS", "SERDES"
    };

    // 从 application.yml 解析的 adhoc.metadata.* 配置（dotted key -> 字符串值）
    private static Map<String, String> metaConfig = Collections.emptyMap();
    private static String hiveUrl;
    private static String hiveUser;
    private static String hivePwd;
    private static boolean configured;        // yml 是否配了 hive-metastore url/user/password
    private static boolean metastoreReachable; // ping 是否通

    private HiveMetastoreFetcher fetcherToClose;

    @BeforeAll
    static void loadConfig() {
        metaConfig = readAdhocMetadataConfig();
        // 连接源已收敛到每实例（adhoc.metadata.hive-metastore.{instance}.*，无引擎级回退），读默认实例的每实例键
        String defaultInstance = metaConfig.get(AdhocMetadataConfig.HIVE_METASTORE_DEFAULT_INSTANCE.getKey());
        String pfx = "adhoc.metadata.hive-metastore." + defaultInstance + ".";
        hiveUrl = metaConfig.get(pfx + "url");
        hiveUser = metaConfig.get(pfx + "user");
        hivePwd = metaConfig.get(pfx + "password");
        configured = isPresent(hiveUrl) && isPresent(hiveUser) && isPresent(hivePwd);
        metastoreReachable = configured && pingMetastore();
        System.out.println("=== metadata test: configured=" + configured
                + " reachable=" + metastoreReachable
                + " url=" + hiveUrl + " ===");
    }

    @AfterEach
    void cleanup() {
        // 关 Druid 连接池（其 Destroyer 等线程非 daemon，不关会让测试 JVM 不退出）
        if (fetcherToClose != null) {
            fetcherToClose.close();
            fetcherToClose = null;
        }
    }

    // ===== Schema 层（原 MetadataSchemaVerifyTest）=====

    /**
     * 连真实 Hive metastore，验证 8 张标准表存在 + fetcher 的 DBS/TBLS/COLUMNS_V2/PARTITION_KEYS SQL 正确返回。
     * URL 带 /hive 时 catalog 即 metastore 库；未带时扫所有 schema 兜底定位含 DBS 的库。
     */
    @Test
    void verifyMetastoreSchema() throws Exception {
        assumeMetastoreAvailable();
        try (Connection conn = DriverManager.getConnection(hiveUrl, hiveUser, hivePwd)) {
            System.out.println("=== connected catalog=" + conn.getCatalog() + " ===");

            // 定位 metastore 库（含 DBS 的 schema）
            String mdb = conn.getCatalog();
            if (!tableExists(conn, mdb, "DBS")) {
                System.out.println("--- [0] 当前 catalog 无 DBS，扫描所有 schema 找 metastore 库 ---");
                try (Statement st = conn.createStatement();
                     ResultSet rs = st.executeQuery(
                             "SELECT schema_name FROM information_schema.schemata ORDER BY schema_name")) {
                    while (rs.next()) {
                        String s = rs.getString(1);
                        if (tableExists(conn, s, "DBS")) {
                            mdb = s;
                            System.out.println("    找到 metastore 库: " + s);
                            break;
                        }
                    }
                }
            }
            assertTrue(mdb != null && tableExists(conn, mdb, "DBS"), "未找到含 DBS 的 metastore 库");
            try (Statement st = conn.createStatement()) {
                st.execute("USE " + quoteIdent(mdb));
            }
            System.out.println("=== USE " + mdb + " ===");

            // 1. 标准 metastore 表全部存在
            System.out.println("--- [1] metastore 标准表存在性 ---");
            for (String t : STD_TABLES) {
                boolean exists = tableExists(conn, mdb, t);
                System.out.println("    " + t + " : " + (exists ? "存在" : "缺失"));
                assertTrue(exists, "标准表 " + t + " 应存在");
            }

            // 2. DBS 库列表（fetcher listDatabases SQL）
            System.out.println("--- [2] DBS 库列表(LIMIT 20) ---");
            String firstDb = null;
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery(
                         "SELECT NAME FROM DBS WHERE NAME NOT IN ('information_schema','sys','mysql') ORDER BY NAME LIMIT 20")) {
                while (rs.next()) {
                    String name = rs.getString(1);
                    System.out.println("    " + name);
                    if (firstDb == null) firstDb = name;
                }
            }
            assertTrue(firstDb != null, "DBS 库列表应非空");

            // 3. TBLS 表列表（fetcher listTables SQL）
            System.out.println("--- [3] TBLS 表列表 db=" + firstDb + " (LIMIT 10) ---");
            String firstTbl = null;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT t.TBL_NAME, t.TBL_TYPE, tp.PARAM_VALUE AS TBL_COMMENT "
                            + "FROM TBLS t JOIN DBS d ON t.DB_ID=d.DB_ID "
                            + "LEFT JOIN TABLE_PARAMS tp ON tp.TBL_ID=t.TBL_ID AND tp.PARAM_KEY='comment' "
                            + "WHERE d.NAME=? ORDER BY t.TBL_NAME LIMIT 10")) {
                ps.setString(1, firstDb);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String name = rs.getString(1);
                        System.out.println("    " + name + " | " + rs.getString(2) + " | " + rs.getString(3));
                        if (firstTbl == null) firstTbl = name;
                    }
                }
            }
            assertTrue(firstTbl != null, "库 " + firstDb + " 表列表应非空");

            // 4. COLUMNS_V2 普通列（fetcher listColumns SQL）
            System.out.println("--- [4] COLUMNS_V2 列 db=" + firstDb + " tbl=" + firstTbl + " ---");
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT c.COLUMN_NAME, c.TYPE_NAME, c.COMMENT "
                            + "FROM COLUMNS_V2 c "
                            + "JOIN SDS s ON c.CD_ID=s.CD_ID "
                            + "JOIN TBLS t ON s.SD_ID=t.SD_ID "
                            + "JOIN DBS d ON t.DB_ID=d.DB_ID "
                            + "WHERE d.NAME=? AND t.TBL_NAME=? ORDER BY c.INTEGER_IDX")) {
                ps.setString(1, firstDb);
                ps.setString(2, firstTbl);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        System.out.println("    " + rs.getString(1) + " | " + rs.getString(2) + " | " + rs.getString(3));
                    }
                }
            }

            // 5. PARTITION_KEYS 分区键
            System.out.println("--- [5] PARTITION_KEYS 分区键 db=" + firstDb + " tbl=" + firstTbl + " ---");
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT p.PKEY_NAME, p.PKEY_TYPE FROM PARTITION_KEYS p "
                            + "JOIN TBLS t ON p.TBL_ID=t.TBL_ID "
                            + "JOIN DBS d ON t.DB_ID=d.DB_ID "
                            + "WHERE d.NAME=? AND t.TBL_NAME=? ORDER BY p.INTEGER_IDX")) {
                ps.setString(1, firstDb);
                ps.setString(2, firstTbl);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        System.out.println("    " + rs.getString(1) + " | " + rs.getString(2));
                    }
                }
            }
            System.out.println("=== schema verify done ===");
        }
    }

    // ===== Service 层（原 MetadataServiceVerifyTest）=====

    @Test
    void verifyHiveMetadata() {
        assumeMetastoreAvailable();
        MetadataService svc = newService(null);

        // 1. 库列表：非空、含 demo_db、无重名（DISTINCT 去重生效）
        List<MetadataDatabase> dbs = svc.listDatabases("KYUUBI", null);
        System.out.println("=== databases(size=" + dbs.size() + ") ===");
        assertTrue(dbs.size() >= 10, "库列表应非空");
        List<String> dbNames = dbs.stream().map(MetadataDatabase::getName).collect(Collectors.toList());
        assertTrue(dbNames.contains(DB), "应含库 " + DB);
        Map<String, Long> freq = dbNames.stream().collect(Collectors.groupingBy(n -> n, Collectors.counting()));
        List<String> dups = freq.entrySet().stream()
                .filter(e -> e.getValue() > 1).map(Map.Entry::getKey).collect(Collectors.toList());
        assertTrue(dups.isEmpty(), "库列表不应有重名（DISTINCT 失效？）: " + dups);
        System.out.println("    前5: " + dbNames.subList(0, Math.min(5, dbNames.size())));

        // 2. keyword 前缀过滤：bide -> 只剩 bide 开头的库
        List<MetadataDatabase> filtered = svc.listDatabases("KYUUBI", "bide");
        System.out.println("=== databases(keyword=bide, size=" + filtered.size() + ") ===");
        for (MetadataDatabase d : filtered) System.out.println("    " + d.getName());
        assertTrue(filtered.stream().allMatch(d -> d.getName().toLowerCase().startsWith("bide")),
                "过滤后都应以 bide 开头");
        assertTrue(filtered.stream().anyMatch(d -> DB.equals(d.getName())), "应含库 " + DB);

        // 3. 表列表：含 adhoc_affected_test，带类型与注释
        List<MetadataTable> tables = svc.listTables("KYUUBI", DB, null);
        System.out.println("=== tables(db=" + DB + ", size=" + tables.size() + ") ===");
        assertFalse(tables.isEmpty(), "表列表应非空");
        assertTrue(tables.stream().anyMatch(t -> TBL.equals(t.getName())), "应含表 " + TBL);
        MetadataTable t0 = tables.stream().filter(t -> TBL.equals(t.getName())).findFirst().orElse(null);
        System.out.println("    " + t0.getName() + " | " + t0.getType() + " | " + t0.getComment());

        // 4. 列列表：adhoc_affected_test 应有 id(int) + name(string) 两列（非分区列）
        List<MetadataColumn> cols = svc.listColumns("KYUUBI", DB, TBL);
        System.out.println("=== columns(db=" + DB + ", tbl=" + TBL + ", size=" + cols.size() + ") ===");
        for (MetadataColumn c : cols) {
            System.out.println("    " + c.getName() + " | " + c.getType() + " | partition=" + c.isPartition());
        }
        assertEquals(2, cols.size(), TBL + " 应有 2 列");
        assertTrue(cols.stream().anyMatch(c -> "id".equals(c.getName()) && "int".equals(c.getType())),
                "应有 id:int");
        assertTrue(cols.stream().anyMatch(c -> "name".equals(c.getName()) && "string".equals(c.getType())),
                "应有 name:string");
        assertTrue(cols.stream().noneMatch(MetadataColumn::isPartition), TBL + " 无分区键，partition 应全 false");

        System.out.println("=== service verify done ===");
    }

    @Test
    void verifyCacheHitSkipsFetcher() {
        assumeMetastoreAvailable();
        AtomicInteger dbCalls = new AtomicInteger();
        MetadataService svc = newService(dbCalls);

        // 第一次：缓存 miss -> 穿透到 fetcher
        int n1 = svc.listDatabases("KYUUBI", null).size();
        assertEquals(1, dbCalls.get(), "首次应查 fetcher 1 次");

        // 第二次：缓存 hit -> 不再查 fetcher
        int n2 = svc.listDatabases("KYUUBI", null).size();
        assertEquals(1, dbCalls.get(), "二次应命中缓存，fetcher 不应再被调用");
        assertEquals(n1, n2, "两次结果数应一致");

        System.out.println("=== cache hit verified: fetcher.listDatabases 调用 " + dbCalls.get() + " 次（2 次请求） ===");
    }

    @Test
    void verifyInvalidEngineThrows() {
        // 纯逻辑校验，不连 metastore；engineType 非法在路由层即抛，不依赖网络
        MetadataService svc = newService(null);
        AdhocException ex = assertThrows(AdhocException.class, () -> svc.listDatabases("HIVE", null));
        assertEquals(AdhocErrorCode.ADHOC_ENGINE_TYPE_INVALID, ex.getErrorCode(),
                "非法引擎应抛 ADHOC_ENGINE_TYPE_INVALID");
        System.out.println("=== invalid engine -> " + ex.getErrorCode().name() + " ===");
    }

    // ===== helpers =====

    /** 依赖 metastore 可达的用例守卫：未配或连不通即跳过（Assumption 失败，不 fail build）。 */
    private void assumeMetastoreAvailable() {
        assumeTrue(configured, "application.yml 未配 adhoc.metadata.hive-metastore.*，跳过元数据验证");
        assumeTrue(metastoreReachable, "连不上 Hive metastore (" + hiveUrl + ")，跳过（CI 无网络属正常）");
    }

    private MetadataService newService(AtomicInteger dbCallCounter) {
        ConfigHolder cfg = ConfigHolder.forTest(metaConfig); // 全量 yml 配置注入
        HiveMetastoreFetcher hive = (dbCallCounter != null)
                ? new CountingHiveFetcher(cfg, dbCallCounter)
                : new HiveMetastoreFetcher(cfg);
        fetcherToClose = hive;
        StarRocksMetadataFetcher sr = new StarRocksMetadataFetcher(cfg); // 不调不连（lazy）
        MetadataCache cache = new MetadataCache(cfg);
        return new MetadataService(hive, sr, cache);
    }

    /** ping metastore：建一条连接测可用性，失败返回 false（不抛，由守卫转跳过）。 */
    private static boolean pingMetastore() {
        try (Connection c = DriverManager.getConnection(hiveUrl, hiveUser, hivePwd)) {
            return c.isValid(5);
        } catch (Exception e) {
            System.out.println("    ping metastore 失败: " + e.getMessage());
            return false;
        }
    }

    /** 解析 classpath 的 application.yml，展平 adhoc.metadata.* 为 dotted key。yml 不存在或无该块返回空 map。 */
    @SuppressWarnings("unchecked")
    private static Map<String, String> readAdhocMetadataConfig() {
        try (InputStream in = MetadataVerifyTest.class.getClassLoader().getResourceAsStream("application.yml")) {
            if (in == null) {
                return Collections.emptyMap();
            }
            Object root = new Yaml().load(in);
            if (!(root instanceof Map)) {
                return Collections.emptyMap();
            }
            Object adhoc = ((Map<String, Object>) root).get("adhoc");
            if (!(adhoc instanceof Map)) {
                return Collections.emptyMap();
            }
            Object meta = ((Map<String, Object>) adhoc).get("metadata");
            if (!(meta instanceof Map)) {
                return Collections.emptyMap();
            }
            Map<String, String> flat = new HashMap<>();
            flatten("adhoc.metadata", (Map<String, Object>) meta, flat);
            return flat;
        } catch (Exception e) {
            System.out.println("    解析 application.yml 失败: " + e.getMessage());
            return Collections.emptyMap();
        }
    }

    @SuppressWarnings("unchecked")
    private static void flatten(String prefix, Map<String, Object> src, Map<String, String> dst) {
        for (Map.Entry<String, Object> e : src.entrySet()) {
            String key = prefix + "." + e.getKey();
            Object v = e.getValue();
            if (v instanceof Map) {
                flatten(key, (Map<String, Object>) v, dst);
            } else if (v != null) {
                dst.put(key, String.valueOf(v));
            }
        }
    }

    private static boolean isPresent(String s) {
        return s != null && !s.trim().isEmpty();
    }

    private static boolean tableExists(Connection conn, String schema, String table) {
        if (schema == null) return false;
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM information_schema.tables WHERE table_schema=? AND table_name=? LIMIT 1")) {
            ps.setString(1, schema);
            ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            return false;
        }
    }

    private static String quoteIdent(String s) {
        return "`" + s.replace("`", "``") + "`";
    }

    /** 计数子类：验证 MetadataService 缓存命中后不再穿透到 fetcher。 */
    private static final class CountingHiveFetcher extends HiveMetastoreFetcher {
        private final AtomicInteger dbCallCounter;

        CountingHiveFetcher(ConfigHolder cfg, AtomicInteger dbCallCounter) {
            super(cfg);
            this.dbCallCounter = dbCallCounter;
        }

        @Override
        public List<String> listDatabases(String instance) {
            dbCallCounter.incrementAndGet();
            return super.listDatabases(instance);
        }
    }
}
