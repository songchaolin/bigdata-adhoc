package io.gitee.songchaolin.adhoc.executor.split;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SqlSplitterTest {

    private final SqlSplitter splitter = new SqlSplitter();

    @Test
    void singleSelect() {
        List<SqlSegment> segs = splitter.split("SELECT 1");
        assertThat(segs).hasSize(1);
        assertThat(segs.get(0).getSql()).isEqualTo("SELECT 1");
        assertThat(segs.get(0).getPrefixSql()).isNull();
        assertThat(segs.get(0).getSegmentIndex()).isEqualTo(0);
    }

    @Test
    void multiStatement() {
        List<SqlSegment> segs = splitter.split("SELECT 1; SELECT 2; SELECT 3");
        assertThat(segs).hasSize(3);
        assertThat(segs.get(0).getSql()).isEqualTo("SELECT 1");
        assertThat(segs.get(1).getSql()).isEqualTo("SELECT 2");
        assertThat(segs.get(2).getSegmentIndex()).isEqualTo(2);
    }

    @Test
    void setUseMergedAsPrefix() {
        List<SqlSegment> segs = splitter.split("SET spark.x=1; USE db; SELECT 1");
        assertThat(segs).hasSize(1);
        assertThat(segs.get(0).getSql()).isEqualTo("SELECT 1");
        assertThat(segs.get(0).getPrefixSql()).isEqualTo("SET spark.x=1\nUSE db");
    }

    @Test
    void prefixResetsPerSegment() {
        List<SqlSegment> segs = splitter.split("SET a=1; SELECT 1; SET b=2; SELECT 2");
        assertThat(segs).hasSize(2);
        assertThat(segs.get(0).getPrefixSql()).isEqualTo("SET a=1");
        assertThat(segs.get(0).getSql()).isEqualTo("SELECT 1");
        assertThat(segs.get(1).getPrefixSql()).isEqualTo("SET b=2");
        assertThat(segs.get(1).getSql()).isEqualTo("SELECT 2");
        assertThat(segs.get(1).getSegmentIndex()).isEqualTo(1);
    }

    @Test
    void allSetUseNoExecutable() {
        List<SqlSegment> segs = splitter.split("SET a=1; USE db");
        assertThat(segs).isEmpty();
    }

    @Test
    void trailingSemicolonAndBlanks() {
        List<SqlSegment> segs = splitter.split("  SELECT 1 ;  ;  ");
        assertThat(segs).hasSize(1);
        assertThat(segs.get(0).getSql()).isEqualTo("SELECT 1");
    }

    @Test
    void caseInsensitiveSetUse() {
        List<SqlSegment> segs = splitter.split("set a=1; use db; select 1");
        assertThat(segs).hasSize(1);
        assertThat(segs.get(0).getPrefixSql()).isEqualTo("set a=1\nuse db");
    }
}
