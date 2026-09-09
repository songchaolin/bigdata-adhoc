package io.gitee.songchaolin.adhoc.sqlparser.engine;

import io.gitee.songchaolin.adhoc.common.model.SqlParseResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StarRocksParserEngineTest {

    private final StarRocksParserEngine engine = new StarRocksParserEngine();

    @Test
    void parse_select_success() {
        SqlParseResult r = engine.parse("SELECT * FROM db.t");
        assertThat(r.isValid()).isTrue();
        assertThat(r.getSqlType()).isEqualTo("DQL");
        assertThat(r.getSourceTables()).contains("db.t");
    }

    @Test
    void parse_lowercase_success() {
        SqlParseResult r = engine.parse("select 1 from db.t");
        assertThat(r.isValid()).isTrue();
        assertThat(r.getSqlType()).isEqualTo("DQL");
    }

    @Test
    void parse_insert_lineage() {
        SqlParseResult r = engine.parse("INSERT INTO db.t2 SELECT * FROM db.t1");
        assertThat(r.isValid()).isTrue();
        assertThat(r.getSqlType()).isEqualTo("DML_INSERT");
        assertThat(r.getSourceTables()).contains("db.t1");
        assertThat(r.getSinkTables()).contains("db.t2");
    }

    @Test
    void parse_syntax_error() {
        SqlParseResult r = engine.parse("SELCT 1 FROM");
        assertThat(r.isValid()).isFalse();
        assertThat(r.getSqlType()).isEqualTo("UNKNOWN");
        assertThat(r.getErrorMessage()).isNotNull();
    }
}