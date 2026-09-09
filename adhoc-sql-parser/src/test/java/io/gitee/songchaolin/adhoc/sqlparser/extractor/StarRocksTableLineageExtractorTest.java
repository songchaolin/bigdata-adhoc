package io.gitee.songchaolin.adhoc.sqlparser.extractor;

import io.gitee.songchaolin.adhoc.sqlparser.parser.StarRocksSqlParserFactory;
import com.starrocks.sql.parser.StarRocksParser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StarRocksTableLineageExtractorTest {

    private StarRocksTableLineageExtractor extract(String sql) {
        StarRocksParser parser = StarRocksSqlParserFactory.createParser(sql);
        return StarRocksTableLineageExtractor.extract(parser);
    }

    @Test
    void select_source() {
        StarRocksTableLineageExtractor ext = extract("SELECT * FROM db.t1");
        assertThat(ext.getSourceTables()).contains("db.t1");
        assertThat(ext.getSinkTables()).isEmpty();
    }

    @Test
    void join_sources() {
        StarRocksTableLineageExtractor ext = extract("SELECT * FROM db.t1 a JOIN db.t2 b ON a.id = b.id");
        assertThat(ext.getSourceTables()).containsExactly("db.t1", "db.t2");
    }

    @Test
    void insert_sink_and_source() {
        StarRocksTableLineageExtractor ext = extract("INSERT INTO db.t2 SELECT * FROM db.t1");
        assertThat(ext.getSourceTables()).contains("db.t1");
        assertThat(ext.getSinkTables()).contains("db.t2");
    }

    @Test
    void create_table_sink() {
        StarRocksTableLineageExtractor ext = extract("CREATE TABLE db.t (a INT)");
        assertThat(ext.getSinkTables()).contains("db.t");
    }

    @Test
    void ctas_sink_and_source() {
        StarRocksTableLineageExtractor ext = extract("CREATE TABLE db.t2 AS SELECT * FROM db.t1");
        assertThat(ext.getSourceTables()).contains("db.t1");
        assertThat(ext.getSinkTables()).contains("db.t2");
    }

    @Test
    void update_sink() {
        StarRocksTableLineageExtractor ext = extract("UPDATE db.t SET a = 1");
        assertThat(ext.getSinkTables()).contains("db.t");
    }

    @Test
    void delete_sink() {
        StarRocksTableLineageExtractor ext = extract("DELETE FROM db.t WHERE a = 1");
        assertThat(ext.getSinkTables()).contains("db.t");
    }
}