package io.gitee.songchaolin.adhoc.executor.engine;

import io.gitee.songchaolin.adhoc.common.config.ConfigHolder;
import io.gitee.songchaolin.adhoc.executor.config.AdhocExecutorConfig;
import io.gitee.songchaolin.adhoc.storage.model.ResultColumn;
import io.gitee.songchaolin.adhoc.storage.model.ResultRow;
import io.gitee.songchaolin.adhoc.storage.model.ResultSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * StarRocks 引擎执行器（MySQL 协议 JDBC）。
 * 连接信息由 EngineInstanceConfigResolver 按 adhoc.engine.STARROCKS.{instance}.* 每实例解析（endpoints/user/password/database/params）。
 * 与 KyuubiEngineExecutor 接口一致（EngineExecutor），JobExecutionRunner 按 engine_type 路由。
 * MySQL 协议无 operation log 等价 API，serverLogSink 忽略（引擎侧执行细节不入 job 日志）。
 *
 * <p>查询超时：executeQuery 创建 Statement 后 setQueryTimeout（adhoc.executor.query-timeout-sec，默认 3600s，0=不限制），
 * JDBC 客户端级兜底防死循环/卡死查询；超时触发的异常由 TaskExecutionPipeline 识别为 QUERY_TIMEOUT。
 *
 * <p>cancel：executeQuery 执行期注册 Statement 到 runningStatements，cancel(jobId) 先 Statement.cancel()（KILL QUERY，
 * 中断 executing 阶段）再 Statement.close()（关 ResultSet，中断 fetching 阶段），两阶段都能立即生效。
 */
@Component
public class StarRocksEngineExecutor extends AbstractEngineExecutor {

    private static final Logger log = LoggerFactory.getLogger(StarRocksEngineExecutor.class);

    private final int rowLimit;
    private final int queryTimeoutSec;

    /** jobId -> 当前执行 Statement（cancel 用）。 */
    private final ConcurrentHashMap<String, Statement> runningStatements = new ConcurrentHashMap<>();

    public StarRocksEngineExecutor(ConfigHolder cfg) {
        this.rowLimit = cfg.get(AdhocExecutorConfig.EXECUTOR_RESULT_LIMIT);
        this.queryTimeoutSec = cfg.get(AdhocExecutorConfig.EXECUTOR_QUERY_TIMEOUT_SEC);
    }

    @Override
    protected Connection doConnect(String url, String user, String password, String proxyUser) throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    @Override
    public void executeSessionSql(Connection conn, String sql) throws SQLException {
        // prefix 含多条 SET/USE（换行拼接），逐条 execute；一次性 execute 会让引擎把首条 SET 的值解析成「false + 后续行」
        try (Statement st = conn.createStatement()) {
            for (String stmt : sql.split("\n")) {
                String s = stmt.trim();
                if (!s.isEmpty()) {
                    st.execute(s);
                }
            }
        }
    }

    @Override
    public QueryResult executeQuery(Connection conn, String jobId, String sql, LogSink serverLogSink) throws SQLException {
        Statement st = conn.createStatement();
        if (queryTimeoutSec > 0) {
            st.setQueryTimeout(queryTimeoutSec);  // JDBC 客户端级超时兜底，防死循环/卡死查询
        }
        runningStatements.put(jobId, st);
        try {
            boolean isResultSet = st.execute(sql);
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
            runningStatements.remove(jobId);
            try {
                st.close();
            } catch (SQLException e) {
                // cancel 可能已中断查询，close 会失败，忽略（不影响 cancel 语义；对齐 KyuubiEngineExecutor）
                log.debug("close statement for {} failed (likely canceled): {}", jobId, e.getMessage());
            }
        }
    }

    @Override
    public void cancel(String jobId) {
        Statement st = runningStatements.get(jobId);
        if (st != null) {
            try {
                st.cancel();   // 中断查询执行（KILL QUERY），覆盖 executing 阶段
            } catch (SQLException e) {
                log.warn("cancel StarRocks query for {} failed: {}", jobId, e.getMessage());
            }
            try {
                st.close();    // 关 Statement -> 关 ResultSet，中断 fetch，覆盖 fetching 阶段（cancel() 对已结束查询无效）
            } catch (SQLException e) {
                log.debug("close statement for {} on cancel failed: {}", jobId, e.getMessage());
            }
            log.info("[executor] cancel StarRocks query for job {}", jobId);
        }
    }
}
