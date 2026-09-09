package io.gitee.songchaolin.adhoc.sqlparser.engine;

import io.gitee.songchaolin.adhoc.common.model.SqlParseResult;
import io.gitee.songchaolin.adhoc.common.spi.SqlParserEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 智能 SQL 解析引擎（带降级策略）：primaryEngine g4 解析失败 -> 正则模糊解析 -> UNKNOWN。
 * <p>primaryEngine 可注入任意引擎实现：无参构造默认 {@link KyuubiSparkParserEngine}（Kyuubi/Spark SQL g4），
 * 也可传入 {@link StarRocksParserEngine}（StarRocks g4），使各引擎降级策略对称——g4 解析失败不被直接拒，
 * 而是走正则识别类型后放行（仅 UNKNOWN 才拦截）。
 *
 * 解析优先级：
 * 1. g4 精确解析（primaryEngine）
 * 2. 正则模糊解析（RegexFallbackEngine）
 * 3. UNKNOWN（保留原文）
 *
 * 保证即使 g4 解析失败，也能：
 * - 正确识别 SQL 类型
 * - 判断是否有结果集
 * - 不拦截正常 SQL
 */
public class SmartFallbackParserEngine implements SqlParserEngine {

    private static final Logger logger = LoggerFactory.getLogger(SmartFallbackParserEngine.class);

    private final SqlParserEngine primaryEngine;
    private final RegexFallbackEngine fallbackEngine;

    public SmartFallbackParserEngine() {
        this.primaryEngine = new KyuubiSparkParserEngine();
        this.fallbackEngine = new RegexFallbackEngine();
    }

    public SmartFallbackParserEngine(SqlParserEngine primaryEngine) {
        this.primaryEngine = primaryEngine;
        this.fallbackEngine = new RegexFallbackEngine();
    }

    @Override
    public SqlParseResult parse(String sql) {
        // 第一层：g4 精确解析
        SqlParseResult primary = primaryEngine.parse(sql);

        if (primary.isValid()) {
            // g4 解析成功，直接返回
            return primary;
        }

        // 第二层：正则降级解析
        logger.warn("g4 解析失败，启用降级策略: {}",
                primary.getErrorMessage() != null ? primary.getErrorMessage().substring(0, Math.min(100, primary.getErrorMessage().length())) : "unknown");

        SqlParseResult fallback = fallbackEngine.parse(sql);

        // 合并错误信息，标记为降级解析
        fallback.setValid(false);
        fallback.setErrorMessage(String.format(
                "降级解析 (: %s)",
                truncate(primary.getErrorMessage(), 150)
        ));

        logger.info("降级解析结果: sqlType={}, hasResultSet={}",
                fallback.getSqlType(),
                inferHasResultSet(fallback.getSqlType()));

        return fallback;
    }

    /**
     * 推断是否有结果集
     */
    private boolean inferHasResultSet(String sqlType) {
        return "DQL".equals(sqlType) || "AUX".equals(sqlType);
    }

    private String truncate(String str, int maxLen) {
        if (str == null) return "";
        return str.length() > maxLen ? str.substring(0, maxLen) + "..." : str;
    }

    /**
     * 正则降级解析引擎
     */
    static class RegexFallbackEngine {

        // DQL 模式：SELECT / WITH / (SELECT 子查询)
        private static final Pattern[] DQL_PATTERNS = {
                Pattern.compile("^\\s*SELECT\\s", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE),
                Pattern.compile("^\\s*WITH\\s+\\w+\\s+AS\\s*\\(", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE),
                Pattern.compile("^\\s*\\(\\s*SELECT\\s", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE)
        };

        // INSERT 模式（改进版：处理换行和复杂表名）
        private static final Pattern INSERT_PATTERN =
                Pattern.compile("INSERT\\s+(?:INTO|OVERWRITE)\\s+(?:TABLE\\s+)?([\\w.`\\s]+?)(?:\\s+PARTITION|\\s+SELECT|\\s+VALUES|$)",
                        Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

        // UPDATE / DELETE 模式
        private static final Pattern UPDATE_PATTERN =
                Pattern.compile("^\\s*UPDATE\\s+([\\w.`]+)", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

        private static final Pattern DELETE_PATTERN =
                Pattern.compile("^\\s*DELETE\\s+FROM\\s+([\\w.`]+)", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

        // CREATE TABLE 模式
        private static final Pattern CREATE_TABLE_PATTERN =
                Pattern.compile("^\\s*CREATE\\s+(?:EXTERNAL\\s+)?TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?([\\w.`]+)",
                        Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

        // CTAS 检测模式
        private static final Pattern CTAS_PATTERN =
                Pattern.compile("\\bAS\\s*(?:\\(\\s*)?SELECT\\b", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

        // SESSION_CONFIG 模式
        private static final Pattern[] SESSION_CONFIG_PATTERNS = {
                Pattern.compile("^\\s*SET\\s+\\w+", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE),
                Pattern.compile("^\\s*USE\\s+\\w+", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE)
        };

        // AUX 模式
        private static final Pattern[] AUX_PATTERNS = {
                Pattern.compile("^\\s*SHOW\\s+", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE),
                Pattern.compile("^\\s*DESC(?:RIBE)?\\s+", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE),
                Pattern.compile("^\\s*EXPLAIN\\s+", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE)
        };

        public SqlParseResult parse(String sql) {
            String trimmed = sql.trim();

            // 按优先级匹配（从高到低）

            // 1. DQL (SELECT / WITH / 子查询)
            if (matchesAny(trimmed, DQL_PATTERNS)) {
                return new SqlParseResult("DQL", Collections.emptyList(), Collections.emptyList(), false, null);
            }

            // 2. DML_INSERT
            Matcher insertMatcher = INSERT_PATTERN.matcher(trimmed);
            if (insertMatcher.find()) {
                String targetTable = cleanTableName(insertMatcher.group(1));
                return new SqlParseResult("DML_INSERT", Collections.emptyList(),
                        Collections.singletonList(targetTable), false, null);
            }

            // 3. DML_MODIFY (UPDATE)
            Matcher updateMatcher = UPDATE_PATTERN.matcher(trimmed);
            if (updateMatcher.find()) {
                String targetTable = updateMatcher.group(1);
                return new SqlParseResult("DML_MODIFY", Collections.emptyList(),
                        Collections.singletonList(targetTable), false, null);
            }

            // 4. DML_MODIFY (DELETE)
            Matcher deleteMatcher = DELETE_PATTERN.matcher(trimmed);
            if (deleteMatcher.find()) {
                String targetTable = deleteMatcher.group(1);
                return new SqlParseResult("DML_MODIFY", Collections.emptyList(),
                        Collections.singletonList(targetTable), false, null);
            }

            // 5. CTAS / DDL_CREATE
            Matcher createMatcher = CREATE_TABLE_PATTERN.matcher(trimmed);
            if (createMatcher.find()) {
                String targetTable = createMatcher.group(1);
                boolean isCtas = CTAS_PATTERN.matcher(trimmed).find();
                String type = isCtas ? "CTAS" : "DDL_CREATE";
                return new SqlParseResult(type, Collections.emptyList(),
                        Collections.singletonList(targetTable), false, null);
            }

            // 6. 其他 DDL_CREATE (VIEW / FUNCTION / DATABASE)
            if (trimmed.toUpperCase().matches("^\\s*CREATE\\s+(VIEW|FUNCTION|DATABASE|SCHEMA)\\b.*")) {
                return new SqlParseResult("DDL_CREATE", Collections.emptyList(), Collections.emptyList(), false, null);
            }

            // 7. DDL_ALTER
            if (trimmed.toUpperCase().matches("^\\s*ALTER\\s+(TABLE|VIEW|DATABASE|SCHEMA)\\b.*")) {
                return new SqlParseResult("DDL_ALTER", Collections.emptyList(), Collections.emptyList(), false, null);
            }

            // 8. DDL_DROP
            if (trimmed.toUpperCase().matches("^\\s*(DROP|TRUNCATE)\\s+(TABLE|VIEW|DATABASE|SCHEMA)\\b.*")) {
                return new SqlParseResult("DDL_DROP", Collections.emptyList(), Collections.emptyList(), false, null);
            }

            // 9. SESSION_CONFIG (SET / USE)
            if (matchesAny(trimmed, SESSION_CONFIG_PATTERNS)) {
                return new SqlParseResult("SESSION_CONFIG", Collections.emptyList(), Collections.emptyList(), false, null);
            }

            // 10. AUX (SHOW / DESC / EXPLAIN)
            if (matchesAny(trimmed, AUX_PATTERNS)) {
                return new SqlParseResult("AUX", Collections.emptyList(), Collections.emptyList(), false, null);
            }

            // 11. 无法识别，返回 UNKNOWN
            return new SqlParseResult("UNKNOWN", Collections.emptyList(), Collections.emptyList(), false, null);
        }

        private boolean matchesAny(String sql, Pattern[] patterns) {
            for (Pattern p : patterns) {
                if (p.matcher(sql).find()) {
                    return true;
                }
            }
            return false;
        }

        /**
         * 清理表名（去除尾部空格、分号等）
         */
        private String cleanTableName(String tableName) {
            return tableName.trim().replaceAll("[;\\s]+$", "");
        }
    }
}