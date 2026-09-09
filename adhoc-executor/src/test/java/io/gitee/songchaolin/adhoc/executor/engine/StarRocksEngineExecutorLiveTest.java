package io.gitee.songchaolin.adhoc.executor.engine;

import io.gitee.songchaolin.adhoc.common.config.ConfigHolder;
import io.gitee.songchaolin.adhoc.executor.config.AdhocExecutorConfig;
import io.gitee.songchaolin.adhoc.storage.model.ResultColumn;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * StarRocks 执行器实连集成测试：连真实 StarRocks（ADHOC_SR_ENDPOINTS 环境变量提供 host:port，
 * ADHOC_SR_USER/ADHOC_SR_PASSWORD 提供账号），验证 DQL/AUX 执行、查询超时(#4)、cancel 中断(#3)、
 * 超时异常可识别(#5)。
 * <p>默认跳过（避免 CI 无环境失败）：环境变量未设置自动跳过；另需 -DliveStarRocks=true 显式开启。
 *
 * <p><b>为何用真实聚合查询而非 sleep()</b>：StarRocks 的 {@code sleep()} 内置函数不可被 KILL QUERY
 * 中途打断（cancel/timeout 信号会被记录但等到查询自然结束才抛），不能验证中断时机。
 * 改用对一张大表（百万级）的交叉连接 + GROUP BY，自然耗时数秒，
 * 是可被 KILL QUERY 及时中断的真实执行查询（表/数据需在目标 StarRocks 自备）。
 */
@EnabledIfEnvironmentVariable(named = "ADHOC_SR_ENDPOINTS", matches = ".+")
class StarRocksEngineExecutorLiveTest {

    private static final String URL =
            "jdbc:mysql://" + System.getenv("ADHOC_SR_ENDPOINTS") + "/test_db?useUnicode=true&characterEncoding=UTF-8&useSSL=false&serverTimezone=Asia/Shanghai";
    private static final String USER = System.getenv("ADHOC_SR_USER");
    private static final String PWD = System.getenv("ADHOC_SR_PASSWORD");

    /** 真实可中断重查询：大表交叉连接 + GROUP BY，自然耗时数秒（需自备测试表 test_db.t_big_table/t_dim_user/t_dim_calendar）。 */
    private static final String HEAVY =
            "SELECT t1.id, count(*) FROM test_db.t_big_table t1, test_db.t_dim_user t2, test_db.t_dim_calendar t3 GROUP BY t1.id";

    private StarRocksEngineExecutor executor;
    private Connection conn;

    @BeforeAll
    static void requireLive() {
        Assumptions.assumeTrue(Boolean.getBoolean("liveStarRocks"),
                "set -DliveStarRocks=true to run live StarRocks tests");
    }

    @BeforeEach
    void setup() throws SQLException {
        Map<String, Object> cfg = new HashMap<>();
        cfg.put(AdhocExecutorConfig.EXECUTOR_RESULT_LIMIT.getKey(), 1_000_000);
        cfg.put(AdhocExecutorConfig.EXECUTOR_QUERY_TIMEOUT_SEC.getKey(), 3600);  // 默认不超时
        executor = new StarRocksEngineExecutor(ConfigHolder.forTest(cfg));
        conn = executor.connect(URL, USER, PWD, null);
    }

    @AfterEach
    void tearDown() {
        if (conn != null) {
            try { conn.close(); } catch (SQLException ignored) {}
        }
    }

    @Test
    void dql_select_returnsSchemaAndRows() throws SQLException {
        QueryResult r = executor.executeQuery(conn, "job-dql", "SELECT 1 AS one, 'hello' AS msg", null);
        assertThat(r.isHasResultSet()).isTrue();
        assertThat(r.getRows()).hasSize(1);
        assertThat(r.getSchema().getColumns()).hasSize(2);
        assertThat(r.getSchema().getColumns()).extracting(ResultColumn::getColName)
                .containsExactly("one", "msg");
        System.out.println("[dql] rows=" + r.getRows().size() + " cols=" + r.getSchema().getColumns());
    }

    @Test
    void aux_show_tables_returnsRows() throws SQLException {
        QueryResult r = executor.executeQuery(conn, "job-aux", "SHOW TABLES", null);
        assertThat(r.isHasResultSet()).isTrue();
        assertThat(r.getRows().size()).isGreaterThan(0);   // test_db 有测试表
        System.out.println("[aux] SHOW TABLES rows=" + r.getRows().size());
    }

    /** #4 查询超时：setQueryTimeout(2) 跑 HEAVY(自然 6.7s)，应在 ~2s 被中断（不会跑满 6.7s）。 */
    @Test
    void query_timeout_interrupts() throws SQLException {
        Map<String, Object> cfg = new HashMap<>();
        cfg.put(AdhocExecutorConfig.EXECUTOR_RESULT_LIMIT.getKey(), 1_000_000);
        cfg.put(AdhocExecutorConfig.EXECUTOR_QUERY_TIMEOUT_SEC.getKey(), 2);
        StarRocksEngineExecutor to = new StarRocksEngineExecutor(ConfigHolder.forTest(cfg));
        try (Connection c = to.connect(URL, USER, PWD, null)) {
            long t0 = System.currentTimeMillis();
            Throwable err = catchThrowable(() -> to.executeQuery(c, "job-to", HEAVY, null));
            long cost = System.currentTimeMillis() - t0;
            System.out.println("[timeout] cost=" + cost + "ms err=" + err);
            // 抛 SQLException，且 ~2s 中断（< 5s），不会跑满自然 6.7s
            assertThat(err).isInstanceOf(SQLException.class);
            assertThat(cost).as("应在 ~2s 超时中断，不应跑满 6.7s").isLessThan(5_000L);
            // #5 isQueryTimeout 识别信号：message 含 timeout
            String msg = err.getMessage() != null ? err.getMessage().toLowerCase() : "";
            System.out.println("[timeout] exception message=" + err.getMessage());
            assertThat(msg).as("超时异常 message 应含 timeout 以便 #5 isQueryTimeout 识别").contains("timeout");
        }
    }

    /** #3 cancel：HEAVY 跑起来后异步 cancel，应在 ~1.5s 被中断（不会跑满 6.7s）。 */
    @Test
    void cancel_during_executing() throws Exception {
        AtomicReference<Throwable> err = new AtomicReference<>();
        ExecutorService pool = Executors.newSingleThreadExecutor();
        long t0 = System.currentTimeMillis();
        Future<?> f = pool.submit(() -> {
            try {
                executor.executeQuery(conn, "job-cancel", HEAVY, null);
            } catch (Throwable t) {
                err.set(t);
            }
        });
        Thread.sleep(1_500);          // 让查询进入 executing
        executor.cancel("job-cancel");
        f.get(15, TimeUnit.SECONDS);  // cancel 生效应在 ~1.5s 完成，不会跑满 6.7s
        long cost = System.currentTimeMillis() - t0;
        pool.shutdownNow();
        System.out.println("[cancel] cost=" + cost + "ms err=" + err.get());
        assertThat(err.get()).as("cancel 应中断查询抛 SQLException").isInstanceOf(SQLException.class);
        assertThat(cost).as("cancel 后应快速返回，不应跑满 6.7s").isLessThan(5_000L);
    }
}
