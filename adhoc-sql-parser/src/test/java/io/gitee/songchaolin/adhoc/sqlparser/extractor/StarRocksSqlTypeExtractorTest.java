package io.gitee.songchaolin.adhoc.sqlparser.extractor;

import io.gitee.songchaolin.adhoc.sqlparser.parser.StarRocksSqlParserFactory;
import com.starrocks.sql.parser.StarRocksParser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StarRocksSqlTypeExtractorTest {

    private String sqlType(String sql) {
        StarRocksParser parser = StarRocksSqlParserFactory.createParser(sql);
        return StarRocksSqlTypeExtractor.extract(parser);
    }

    @Test
    void dql_select() {
        assertThat(sqlType("SELECT 1")).isEqualTo("DQL");
    }

    @Test
    void dql_with() {
        assertThat(sqlType("WITH t AS (SELECT 1) SELECT * FROM t")).isEqualTo("DQL");
    }

    @Test
    void dql_explain() {
        assertThat(sqlType("EXPLAIN SELECT 1")).isEqualTo("DQL");
    }

    @Test
    void dql_lowercase() {
        assertThat(sqlType("select 1 from db.t")).isEqualTo("DQL");
    }

    @Test
    void ctas() {
        assertThat(sqlType("CREATE TABLE t AS SELECT 1")).isEqualTo("CTAS");
    }

    @Test
    void dml_insert() {
        assertThat(sqlType("INSERT INTO t SELECT 1")).isEqualTo("DML_INSERT");
    }

    @Test
    void dml_update() {
        assertThat(sqlType("UPDATE t SET a = 1")).isEqualTo("DML_MODIFY");
    }

    @Test
    void dml_delete() {
        assertThat(sqlType("DELETE FROM t WHERE a = 1")).isEqualTo("DML_MODIFY");
    }

    @Test
    void session_set() {
        assertThat(sqlType("SET query_timeout = 100")).isEqualTo("SESSION_CONFIG");
    }

    @Test
    void session_use_db() {
        assertThat(sqlType("USE db")).isEqualTo("SESSION_CONFIG");
    }

    @Test
    void ddl_create() {
        assertThat(sqlType("CREATE TABLE t (a INT)")).isEqualTo("DDL_CREATE");
    }

    @Test
    void ddl_create_view() {
        assertThat(sqlType("CREATE VIEW v AS SELECT 1")).isEqualTo("DDL_CREATE");
    }

    @Test
    void ddl_alter() {
        assertThat(sqlType("ALTER TABLE t ADD COLUMN b INT")).isEqualTo("DDL_ALTER");
    }

    @Test
    void ddl_drop() {
        assertThat(sqlType("DROP TABLE t")).isEqualTo("DDL_DROP");
    }

    @Test
    void ddl_truncate() {
        assertThat(sqlType("TRUNCATE TABLE t")).isEqualTo("DDL_DROP");
    }

    @Test
    void aux_show() {
        assertThat(sqlType("SHOW TABLES")).isEqualTo("AUX");
    }

    @Test
    void aux_desc() {
        assertThat(sqlType("DESC t")).isEqualTo("AUX");
    }

    @Test
    void unknown_kill() {
        assertThat(sqlType("KILL 123")).isEqualTo("UNKNOWN");
    }
}