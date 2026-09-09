package io.gitee.songchaolin.adhoc.sqlparser.engine;

import io.gitee.songchaolin.adhoc.common.model.SqlParseResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P6 KyuubiSparkParserEngine 测试：SqlType 全覆盖 + 表级血缘 + 语法校验。
 */
class KyuubiSparkParserEngineTest {

    private final KyuubiSparkParserEngine engine = new KyuubiSparkParserEngine();

    // === DQL ===
    @Test
    void parseSelect() {
        SqlParseResult r = engine.parse("SELECT * FROM db.table1");
        assertThat(r.isValid()).isTrue();
        assertThat(r.getSqlType()).isEqualTo("DQL");
        assertThat(r.getSourceTables()).contains("db.table1");
    }

    @Test
    void parseSelectWithJoin() {
        SqlParseResult r = engine.parse("SELECT a.id FROM db.t1 a JOIN db.t2 b ON a.id = b.id");
        assertThat(r.getSqlType()).isEqualTo("DQL");
        assertThat(r.getSourceTables()).contains("db.t1", "db.t2");
    }

    // === DML ===
    @Test
    void parseInsert() {
        SqlParseResult r = engine.parse("INSERT INTO db.target SELECT * FROM db.source");
        assertThat(r.getSqlType()).isEqualTo("DML_INSERT");
        assertThat(r.getSinkTables()).contains("db.target");
        assertThat(r.getSourceTables()).contains("db.source");
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

    // === CTAS / DDL_CREATE ===
    @Test
    void parseCreateTableAsSelect() {
        SqlParseResult r = engine.parse("CREATE TABLE db.new_table AS SELECT * FROM db.old_table");
        assertThat(r.getSqlType()).isEqualTo("CTAS");
        assertThat(r.getSinkTables()).contains("db.new_table");
        assertThat(r.getSourceTables()).contains("db.old_table");
    }

    @Test
    void parseCreateTable() {
        SqlParseResult r = engine.parse("CREATE TABLE db.t1 (id INT, name STRING)");
        assertThat(r.getSqlType()).isEqualTo("DDL_CREATE");
        assertThat(r.getSinkTables()).contains("db.t1");
    }

    @Test
    void parseCreateView() {
        SqlParseResult r = engine.parse("CREATE VIEW db.v1 AS SELECT * FROM db.t1");
        assertThat(r.getSqlType()).isEqualTo("DDL_CREATE");
        assertThat(r.getSinkTables()).contains("db.v1");
        assertThat(r.getSourceTables()).contains("db.t1");
    }

    @Test
    void parseCreateDatabase() {
        SqlParseResult r = engine.parse("CREATE DATABASE IF NOT EXISTS db1");
        assertThat(r.getSqlType()).isEqualTo("DDL_CREATE");
    }

    // === DDL_ALTER ===
    @Test
    void parseAlterTable() {
        SqlParseResult r = engine.parse("ALTER TABLE db.t1 ADD COLUMNS (age INT)");
        assertThat(r.getSqlType()).isEqualTo("DDL_ALTER");
    }

    @Test
    void parseRenameTable() {
        SqlParseResult r = engine.parse("ALTER TABLE db.t1 RENAME TO db.t2");
        assertThat(r.getSqlType()).isEqualTo("DDL_ALTER");
    }

    // === DDL_DROP ===
    @Test
    void parseDropTable() {
        SqlParseResult r = engine.parse("DROP TABLE db.t1");
        assertThat(r.getSqlType()).isEqualTo("DDL_DROP");
    }

    @Test
    void parseDropView() {
        SqlParseResult r = engine.parse("DROP VIEW db.v1");
        assertThat(r.getSqlType()).isEqualTo("DDL_DROP");
    }

    @Test
    void parseTruncateTable() {
        SqlParseResult r = engine.parse("TRUNCATE TABLE db.t1");
        assertThat(r.getSqlType()).isEqualTo("DDL_DROP");
    }

    // === SESSION_CONFIG ===
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

    // === AUX ===
    @Test
    void parseShowTables() {
        SqlParseResult r = engine.parse("SHOW TABLES IN db1");
        assertThat(r.getSqlType()).isEqualTo("AUX");
    }

    @Test
    void parseExplain() {
        SqlParseResult r = engine.parse("EXPLAIN SELECT 1 FROM db.t1");
        assertThat(r.getSqlType()).isEqualTo("AUX");
    }

    @Test
    void parseDescribeTable() {
        SqlParseResult r = engine.parse("DESCRIBE db.t1");
        assertThat(r.getSqlType()).isEqualTo("AUX");
    }

    // === 语法错误 ===
    @Test
    void parseInvalidSql() {
        SqlParseResult r = engine.parse("CREATE TABLE");
        assertThat(r.isValid()).isFalse();
        assertThat(r.getSqlType()).isEqualTo("UNKNOWN");
    }
}
