package io.gitee.songchaolin.adhoc.server.service;

import io.gitee.songchaolin.adhoc.common.dto.SqlConsoleResult;
import io.gitee.songchaolin.adhoc.common.exception.AdhocErrorCode;
import io.gitee.songchaolin.adhoc.common.exception.AdhocException;
import io.gitee.songchaolin.adhoc.common.util.DangerousSqlChecker;
import io.gitee.songchaolin.adhoc.sqlparser.preprocess.SqlCommentRemover;
import io.gitee.songchaolin.adhoc.sqlparser.preprocess.SqlScriptSplitter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * 只读 SQL 控制台服务：对平台主数据源（默认 adhoc 库，adhoc_* 元数据表）执行只读 SELECT 查询，
 * 同步返回行集。仅运维大盘内嵌前端调用，对齐 metrics 端点免登录态。
 *
 * <p>安全防线（不走 g4 解析——MySQL 方言与 Spark/StarRocks g4 不兼容，文本判断更稳）：
 * <ol>
 *   <li>去注释 + 拆分，仅接受<strong>单条</strong>语句（拒多语句）</li>
 *   <li>SELECT-only 文本网关：首关键字须 ∈ {SELECT, WITH, (SELECT, SHOW, DESCRIBE, DESC, EXPLAIN}</li>
 *   <li>{@link DangerousSqlChecker} 危险语句黑名单（INTO OUTFILE/LOAD DATA 等）</li>
 *   <li>SELECT/WITH 包裹为子查询 {@code SELECT * FROM (...) _c LIMIT N+1}，DB 侧硬限流；
 *       SHOW/DESC/EXPLAIN 结果集本身极小，原样执行</li>
 *   <li>只读连接（setReadOnly(true)）+ 查询超时（setQueryTimeout）+ Java 侧行数双重封顶</li>
 * </ol>
 *
 * <p>注意：本服务直连平台自有元数据库（非引擎数据），不违背 "server 不接触数据" 铁律——
 * 与 adhoc-metadata 直连 Hive metastore 同属平台自身元数据查询，不触碰用户业务数据。
 */
@Service
public class SqlConsoleService {

    private static final Logger log = LoggerFactory.getLogger(SqlConsoleService.class);

    /** 单次查询返回行数硬上限。 */
    private static final int MAX_ROWS = 1000;
    /** 查询超时（秒）。 */
    private static final int QUERY_TIMEOUT_SEC = 30;

    /** 首关键字白名单（trim+upper 后须以这些之一开头）。 */
    private static final String[] SELECT_PREFIX = {"SELECT", "WITH", "(SELECT"};
    private static final String[] META_PREFIX = {"SHOW", "DESCRIBE", "DESC", "EXPLAIN"};

    private final DataSource dataSource;

    public SqlConsoleService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * 执行只读查询。
     *
     * @param sql 用户输入 SQL
     * @return 列名 + 行集（最多 {@value MAX_ROWS} 行）
     */
    public SqlConsoleResult query(String sql) {
        // 1. 基础校验
        if (sql == null || sql.trim().isEmpty()) {
            throw new AdhocException(AdhocErrorCode.ADHOC_SQL_QUERY_ONLY_SELECT, "SQL 不能为空");
        }

        // 2. 去注释
        String cleaned = SqlCommentRemover.removeComments(sql);

        // 3. 拆分 -> 仅允许单条可执行语句
        List<String> stmts = SqlScriptSplitter.split(cleaned);
        List<String> nonEmpty = new ArrayList<>();
        for (String s : stmts) {
            if (s != null && !s.trim().isEmpty()) {
                nonEmpty.add(s.trim());
            }
        }
        if (nonEmpty.isEmpty()) {
            throw new AdhocException(AdhocErrorCode.ADHOC_SQL_QUERY_ONLY_SELECT, "未检测到可执行 SQL");
        }
        if (nonEmpty.size() > 1) {
            throw new AdhocException(AdhocErrorCode.ADHOC_SQL_QUERY_ONLY_SELECT,
                    "仅支持单条查询语句（检测到 " + nonEmpty.size() + " 条）");
        }
        String stmt = nonEmpty.get(0);

        // 4. SELECT-only 文本网关
        String upper = stmt.toUpperCase();
        boolean isSelect = startsWithAny(upper, SELECT_PREFIX);
        boolean isMeta = startsWithAny(upper, META_PREFIX);
        if (!isSelect && !isMeta) {
            throw new AdhocException(AdhocErrorCode.ADHOC_SQL_QUERY_ONLY_SELECT,
                    "仅支持 SELECT/SHOW/DESCRIBE/EXPLAIN 查询语句");
        }

        // 5. 危险语句黑名单
        String danger = DangerousSqlChecker.check(stmt);
        if (danger != null) {
            throw new AdhocException(AdhocErrorCode.ADHOC_SQL_DANGEROUS_STATEMENT, danger);
        }

        // 6. 组装最终 SQL：SELECT 系包裹子查询硬限流；元数据语句原样执行
        String finalSql;
        if (isSelect) {
            // 包裹为子查询 + LIMIT (MAX_ROWS+1)：DB 侧硬封顶，且多取 1 行用于探测截断
            finalSql = "SELECT * FROM (\n" + stmt + "\n) _adhoc_console LIMIT " + (MAX_ROWS + 1);
        } else {
            finalSql = stmt;
        }

        // 7. 只读执行
        return executeReadOnly(finalSql);
    }

    private SqlConsoleResult executeReadOnly(String sql) {
        List<String> columns = new ArrayList<>();
        List<List<String>> rows = new ArrayList<>();
        int readCap = MAX_ROWS + 1;
        try (Connection conn = dataSource.getConnection()) {
            conn.setReadOnly(true);
            try (Statement stmt = conn.createStatement(
                    ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)) {
                stmt.setQueryTimeout(QUERY_TIMEOUT_SEC);
                try (java.sql.ResultSet rs = stmt.executeQuery(sql)) {
                    ResultSetMetaData md = rs.getMetaData();
                    int n = md.getColumnCount();
                    for (int i = 1; i <= n; i++) {
                        columns.add(md.getColumnLabel(i));
                    }
                    while (rs.next()) {
                        List<String> row = new ArrayList<>(n);
                        for (int i = 1; i <= n; i++) {
                            Object v = rs.getObject(i);
                            row.add(v == null ? "" : v.toString());
                        }
                        rows.add(row);
                        if (rows.size() >= readCap) {
                            break; // 已达探测上限
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[sql-console] 查询失败: {} | sql={}", e.getMessage(), sql);
            throw new AdhocException(AdhocErrorCode.ADHOC_SQL_QUERY_FAILED,
                    e.getClass().getSimpleName() + ": " + e.getMessage());
        }

        // 探测截断：取到 MAX_ROWS+1 行 => 实际超上限，截断展示前 MAX_ROWS 行
        boolean truncated = rows.size() > MAX_ROWS;
        if (truncated) {
            rows.remove(rows.size() - 1);
        }
        return new SqlConsoleResult(columns, rows, rows.size(), truncated);
    }

    private static boolean startsWithAny(String upper, String[] prefixes) {
        for (String p : prefixes) {
            if (upper.startsWith(p)) {
                return true;
            }
        }
        return false;
    }
}
