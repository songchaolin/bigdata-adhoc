package io.gitee.songchaolin.adhoc.executor.runner;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SqlLimitEnforcerTest {

    private final SqlLimitEnforcer enforcer = new SqlLimitEnforcer(100);

    @Test
    void dql_noLimit_appended() {
        assertThat(enforcer.enforce("SELECT * FROM t", "DQL")).isEqualTo("SELECT * FROM t LIMIT 100");
    }

    @Test
    void dql_trailingSemicolon_strippedThenAppended() {
        assertThat(enforcer.enforce("SELECT * FROM t;", "DQL")).isEqualTo("SELECT * FROM t LIMIT 100");
    }

    @Test
    void dql_limitOverThreshold_capped() {
        assertThat(enforcer.enforce("SELECT * FROM t LIMIT 1000", "DQL")).isEqualTo("SELECT * FROM t  LIMIT 100");
    }

    @Test
    void dql_limitUnderThreshold_kept() {
        assertThat(enforcer.enforce("SELECT * FROM t LIMIT 50", "DQL")).isEqualTo("SELECT * FROM t LIMIT 50");
    }

    @Test
    void ddl_unchanged() {
        assertThat(enforcer.enforce("CREATE TABLE t (a int)", "DDL_CREATE"))
                .isEqualTo("CREATE TABLE t (a int)");
    }

    @Test
    void set_unchanged() {
        assertThat(enforcer.enforce("SET spark.sql.shuffle.partitions=200", "SESSION_CONFIG"))
                .isEqualTo("SET spark.sql.shuffle.partitions=200");
    }
}