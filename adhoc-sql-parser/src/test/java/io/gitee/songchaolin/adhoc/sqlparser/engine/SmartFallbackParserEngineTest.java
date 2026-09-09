package io.gitee.songchaolin.adhoc.sqlparser.engine;

import io.gitee.songchaolin.adhoc.common.model.SqlParseResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 智能降级解析引擎测试
 */
class SmartFallbackParserEngineTest {

    private final SmartFallbackParserEngine engine = new SmartFallbackParserEngine();

    // === DQL 测试 ===
    @Test
    void parseDqlSelect() {
        SqlParseResult r = engine.parse("SELECT * FROM db.table1");
        assertThat(r.getSqlType()).isEqualTo("DQL");
        assertThat(r.isValid()).isTrue();
    }

    @Test
    void parseDqlWith() {
        SqlParseResult r = engine.parse("WITH cte AS (SELECT 1) SELECT * FROM cte");
        assertThat(r.getSqlType()).isEqualTo("DQL");
    }

    @Test
    void parseDqlSubquery() {
        SqlParseResult r = engine.parse("(SELECT * FROM t) UNION ALL SELECT 1");
        assertThat(r.getSqlType()).isEqualTo("DQL");
    }

    // === DML 测试 ===
    @Test
    void parseInsert() {
        SqlParseResult r = engine.parse("INSERT INTO db.target SELECT * FROM db.source");
        assertThat(r.getSqlType()).isEqualTo("DML_INSERT");
        assertThat(r.getSinkTables()).contains("db.target");
    }

    @Test
    void parseInsertOverwrite() {
        SqlParseResult r = engine.parse("INSERT OVERWRITE TABLE db.t1 PARTITION(dt='2024-01-01') SELECT * FROM t2");
        assertThat(r.getSqlType()).isEqualTo("DML_INSERT");
        assertThat(r.getSinkTables()).contains("db.t1");
    }

    @Test
    void parseUpdate() {
        SqlParseResult r = engine.parse("UPDATE db.t1 SET name = 'x' WHERE id = 1");
        assertThat(r.getSqlType()).isEqualTo("DML_MODIFY");
    }

    @Test
    void parseDelete() {
        SqlParseResult r = engine.parse("DELETE FROM db.t1 WHERE id = 1");
        assertThat(r.getSqlType()).isEqualTo("DML_MODIFY");
    }

    // === CTAS / DDL_CREATE 测试 ===
    @Test
    void parseCtas() {
        SqlParseResult r = engine.parse("CREATE TABLE db.new_table AS SELECT * FROM db.old_table");
        assertThat(r.getSqlType()).isEqualTo("CTAS");
        assertThat(r.getSinkTables()).contains("db.new_table");
    }

    @Test
    void parseCreateTable() {
        SqlParseResult r = engine.parse("CREATE TABLE db.t1 (id INT, name STRING)");
        assertThat(r.getSqlType()).isEqualTo("DDL_CREATE");
    }

    // === SESSION_CONFIG 测试 ===
    @Test
    void parseSetConfig() {
        SqlParseResult r = engine.parse("SET spark.sql.shuffle.partitions = 200");
        assertThat(r.getSqlType()).isEqualTo("SESSION_CONFIG");
    }

    @Test
    void parseUseDatabase() {
        SqlParseResult r = engine.parse("USE db1");
        assertThat(r.getSqlType()).isEqualTo("SESSION_CONFIG");
    }

    // === AUX 测试 ===
    @Test
    void parseShowTables() {
        SqlParseResult r = engine.parse("SHOW TABLES IN db1");
        assertThat(r.getSqlType()).isEqualTo("AUX");
    }

    @Test
    void parseDescribeTable() {
        SqlParseResult r = engine.parse("DESCRIBE db.t1");
        assertThat(r.getSqlType()).isEqualTo("AUX");
    }

    // === 降级测试（g4 无法解析的 SQL）===
    @Test
    void fallbackStarrocksUnsupportedSyntax() {
        // StarRocks g4 不支持的语法（DISTRIBUTE BY 为 Hive/Spark 特有），g4 解析失败 -> 正则降级识别为 DQL
        SmartFallbackParserEngine srEngine = new SmartFallbackParserEngine(new StarRocksParserEngine());
        SqlParseResult r = srEngine.parse("SELECT * FROM t DISTRIBUTE BY id");
        assertThat(r.getSqlType()).isEqualTo("DQL");  // 降级后应识别为 DQL
        assertThat(r.isValid()).isFalse();  // 标记为降级解析
        assertThat(r.getErrorMessage()).contains("降级解析");
    }

    @Test
    void fallbackSparkDistributeBy() {
        // Spark 特有语法，部分版本 g4 不支持
        SqlParseResult r = engine.parse("SELECT * FROM t DISTRIBUTE BY id");
        assertThat(r.getSqlType()).isEqualTo("DQL");  // 降级后应识别为 DQL
    }

    // === 反斜杠转义测试 ===
    @Test
    void parseWithBackslashEscape() {
        SqlParseResult r = engine.parse("SELECT 'test\\'; DROP TABLE t; --'");
        assertThat(r.getSqlType()).isEqualTo("DQL");
        // 不应该在字符串内截断
    }

    // === UNKNOWN 测试 ===
    @Test
    void parseUnknown() {
        SqlParseResult r = engine.parse("INVALID SQL STATEMENT");
        assertThat(r.getSqlType()).isEqualTo("UNKNOWN");
        assertThat(r.isValid()).isFalse();
    }
}