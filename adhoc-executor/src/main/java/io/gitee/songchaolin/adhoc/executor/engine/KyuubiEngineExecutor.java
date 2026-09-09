package io.gitee.songchaolin.adhoc.executor.engine;

import io.gitee.songchaolin.adhoc.common.config.ConfigHolder;
import io.gitee.songchaolin.adhoc.executor.config.AdhocExecutorConfig;
import io.gitee.songchaolin.adhoc.storage.model.ResultColumn;
import io.gitee.songchaolin.adhoc.storage.model.ResultRow;
import io.gitee.songchaolin.adhoc.storage.model.ResultSchema;
import org.apache.hive.jdbc.HiveStatement;
import org.apache.thrift.transport.TTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Kyuubi 引擎执行器（JDBC，Hive 协议兼容）。连 Kyuubi（jdbc:hive2://...）+ 执行 SET/USE + 执行 DQL 拉结果。
 * 服务端 operation log（Spark 执行日志）通过 {@link OperationLogStreamer} 流式转发给 LogSink。
 *
 * <p>线程安全：hive-jdbc 3.1.1 HiveConnection 用 newSynchronizedClient 包装 client，execute 阻塞轮询
 * GetOperationStatus 之间释放锁，故守护线程并发 getQueryLog 安全（见 OperationLogStreamer javadoc）。
 *
 * <p>cancel：runner 在 connect 后 registerCancelTarget(jobId, conn) 注册 connection（覆盖整个 job，含 prefix
 * executeSessionSql 执行期）。cancel(jobId) 直接关 HiveConnection 底层 TTransport（反射，绕过 synchronized
 * CloseSession）中断阻塞的 execute/prefix，使 runner catch 后 markCanceled。
 * <p>为何不只用 Statement.cancel：prefix（如 use db）是冷启动第一个操作、阻塞在引擎 session/Spark app 创建，
 * 期间无 Statement 注册（executeSessionSql 用临时 Statement），Statement.cancel 拿不到 handle 无效；且 PENDING
 * 期 Kyuubi 忽略 CancelOperation。关 transport 是 OS 级 socket 中断，不依赖锁/handle，prefix 和主查询都能中断。
 * <p>不影响别人：关的是自己的 Kyuubi server session，共享 Spark app（engine session）引用计数，别人还在用不会被 kill。
 */
@Component
public class KyuubiEngineExecutor extends AbstractEngineExecutor {

    private static final Logger log = LoggerFactory.getLogger(KyuubiEngineExecutor.class);

    private final long serverLogIntervalMs;
    private final int rowLimit;

    /** jobId -> 当前 job 的 Connection（cancel 用，覆盖 prefix + 主查询整个 job 生命周期）。 */
    private final ConcurrentHashMap<String, Connection> runningConnections = new ConcurrentHashMap<>();

    public KyuubiEngineExecutor(ConfigHolder cfg) {
        this.serverLogIntervalMs = cfg.get(AdhocExecutorConfig.EXECUTOR_SERVER_LOG_INTERVAL_MS);
        this.rowLimit = cfg.get(AdhocExecutorConfig.EXECUTOR_RESULT_LIMIT);
    }

    @Override
    protected Connection doConnect(String url, String user, String password, String proxyUser) throws SQLException {
        Properties props = new Properties();
        props.setProperty("user", user);
        if (password != null && !password.isEmpty()) {
            props.setProperty("password", password);
        }
        if (proxyUser != null && !proxyUser.isEmpty()) {
            props.setProperty("hive.server2.proxy.user", proxyUser);
        }
        return DriverManager.getConnection(url, props);
    }

    /** 执行 session 级 SQL（SET/USE），忽略结果。冷启动时这里是第一个操作，会阻塞在引擎 session 创建。 */
    @Override
    public void executeSessionSql(Connection conn, String sql) throws SQLException {
        // prefix 含多条 SET/USE（换行拼接），逐条 execute；一次性 execute 会让 Spark 把首条 SET 的值解析成「false + 后续行」
        try (Statement st = conn.createStatement()) {
            for (String stmt : sql.split("\n")) {
                String s = stmt.trim();
                if (!s.isEmpty()) {
                    st.execute(s);
                }
            }
        }
    }

    /**
     * 执行 SQL：DQL 返回 hasResultSet=true + schema + rows（限 rowLimit 行）；DML 返回 hasResultSet=false。
     * 执行期间守护线程增量拉 getQueryLog 转发给 serverLogSink（服务端日志统一入 job/task 日志），
     * execute 返回后 final drain 捕获尾部。cancel 由 registerCancelTarget 注册的 connection 处理（关 transport）。
     */
    @Override
    public QueryResult executeQuery(Connection conn, String jobId, String sql, LogSink serverLogSink) throws SQLException {
        Statement st = conn.createStatement();
        OperationLogStreamer streamer = null;
        try {
            if (st instanceof HiveStatement) {
                streamer = new OperationLogStreamer((HiveStatement) st, serverLogSink, serverLogIntervalMs, 1000);
                streamer.start();
            }
            try {
                boolean isResultSet = st.execute(sql);
                if (streamer != null) {
                    streamer.drain();
                }
                if (isResultSet && st.getResultSet() != null) {
                    try (ResultSet rs = st.getResultSet()) {
                        ResultSetMetaData md = rs.getMetaData();
                        List<ResultColumn> cols = new ArrayList<>();
                        for (int i = 1; i <= md.getColumnCount(); i++) {
                            cols.add(new ResultColumn(i - 1, md.getColumnLabel(i), md.getColumnTypeName(i)));
                        }
                        List<ResultRow> rows = new ArrayList<>();
                        while (rs.next() && rows.size() < rowLimit) {
                            Object[] values = new Object[md.getColumnCount()];
                            for (int i = 1; i <= md.getColumnCount(); i++) {
                                values[i - 1] = rs.getObject(i);
                            }
                            rows.add(new ResultRow(values));
                        }
                        return new QueryResult(true, new ResultSchema(cols), rows, -1);
                    }
                }
                return new QueryResult(false, null, null, st.getUpdateCount());
            } finally {
                if (streamer != null) {
                    streamer.stop();
                }
            }
        } finally {
            try {
                st.close();
            } catch (SQLException e) {
                // cancel 可能已关 transport，st.close 的 CloseOperation 会失败，忽略（不影响 cancel 语义）
                log.debug("close statement for {} failed (likely canceled): {}", jobId, e.getMessage());
            }
        }
    }

    @Override
    public void registerCancelTarget(String jobId, Connection conn) {
        runningConnections.put(jobId, conn);
    }

    @Override
    public void unregisterCancelTarget(String jobId) {
        runningConnections.remove(jobId);
    }

    /** 取消当前 job：直接关 HiveConnection 底层 TTransport（反射，绕过 synchronized CloseSession），
     *  OS 级 socket 中断阻塞的 execute（prefix 或主查询），使 runner catch 后 markCanceled。
     *  无注册 connection（段间 / job 未开始）则 no-op，靠 runner 段前 isCancelRequested 检查。 */
    @Override
    public void cancel(String jobId) {
        Connection conn = runningConnections.get(jobId);
        if (conn == null) {
            log.info("[executor] cancel Kyuubi for job {}: no connection registered (between tasks?), skip", jobId);
            return;
        }
        closeTransportDirectly(jobId, conn);
    }

    /** 反射拿 HiveConnection.transport 直接 close（绕过 synchronized client 的 CloseSession，避免被阻塞的
     *  execute 持锁卡死）。反射失败（非 HiveConnection / 字段变更）降级 conn.close() 保底。 */
    private void closeTransportDirectly(String jobId, Connection conn) {
        Field f = findField(conn.getClass(), "transport");
        if (f == null) {
            log.warn("[executor] cancel job {}: no 'transport' field on {} (not HiveConnection?), fallback conn.close()",
                    jobId, conn.getClass().getName());
            try {
                conn.close();
                log.info("[executor] cancel job {}: connection closed (fallback)", jobId);
            } catch (SQLException e) {
                log.warn("[executor] cancel job {}: fallback conn.close failed: {}", jobId, e.getMessage());
            }
            return;
        }
        try {
            TTransport t = (TTransport) f.get(conn);
            if (t == null) {
                log.warn("[executor] cancel job {}: transport is null", jobId);
                return;
            }
            t.close();
            log.info("[executor] cancel job {}: transport closed, in-flight query/prefix interrupted", jobId);
        } catch (Exception e) {
            log.warn("[executor] cancel job {}: close transport failed: {}", jobId, e.getMessage());
        }
    }

    /** 沿继承链查找字段（HiveConnection 私有字段 transport）。 */
    private static Field findField(Class<?> clazz, String name) {
        Class<?> c = clazz;
        while (c != null && c != Object.class) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            }
        }
        return null;
    }
}
