package io.gitee.songchaolin.adhoc.common.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SqlHashUtil 结果指纹测试。
 * 守住正确性：不同 engine/instance/prefix/executedSql -> 不同 hash；同维度 -> 同 hash。
 * 这些维度任一不同都意味着结果可能不同，误复用会导致"结果不对"。
 */
class SqlHashUtilTest {

    @Test
    void sameDimensions_sameHash() {
        String h1 = SqlHashUtil.resultFingerprintHash("KYUUBI", "kyuubi-01", "use demo_db",
                "SELECT * from demo_metrics LIMIT 1000000");
        String h2 = SqlHashUtil.resultFingerprintHash("KYUUBI", "kyuubi-01", "use demo_db",
                "SELECT * from demo_metrics LIMIT 1000000");
        assertThat(h1).isEqualTo(h2);
    }

    @Test
    void differentEngineType_differentHash() {
        // 同一段 SQL 在 KYUUBI(Hive表) 与 STARROCKS(MySQL表) 必须区分，否则跨引擎误复用
        String kyuubi = SqlHashUtil.resultFingerprintHash("KYUUBI", "inst-01", null,
                "SELECT * FROM t LIMIT 1000000");
        String starrocks = SqlHashUtil.resultFingerprintHash("STARROCKS", "inst-01", null,
                "SELECT * FROM t LIMIT 1000000");
        assertThat(kyuubi).isNotEqualTo(starrocks);
    }

    @Test
    void differentEngineInstance_differentHash() {
        // StarRocks 多实例：01 默认 db_test、02 默认 db_prod，同 SQL 不带 use 必须区分
        String inst1 = SqlHashUtil.resultFingerprintHash("STARROCKS", "starrocks-01", null,
                "SELECT * FROM t LIMIT 1000000");
        String inst2 = SqlHashUtil.resultFingerprintHash("STARROCKS", "starrocks-02", null,
                "SELECT * FROM t LIMIT 1000000");
        assertThat(inst1).isNotEqualTo(inst2);
    }

    @Test
    void instanceVsNoInstance_differentHash() {
        // 多实例（有实例名）与单实例时代历史数据（null 实例名）必须区分，避免跨代误复用
        String withInstance = SqlHashUtil.resultFingerprintHash("STARROCKS", "starrocks-01", null,
                "SELECT * FROM t LIMIT 1000000");
        String noInstance = SqlHashUtil.resultFingerprintHash("STARROCKS", null, null,
                "SELECT * FROM t LIMIT 1000000");
        assertThat(withInstance).isNotEqualTo(noInstance);
    }

    @Test
    void differentPrefix_differentHash() {
        // use db_a 与 use db_b 同表名 -> 结果不同，必须区分
        String dbA = SqlHashUtil.resultFingerprintHash("KYUUBI", "kyuubi-01", "use db_a",
                "SELECT * FROM t LIMIT 1000000");
        String dbB = SqlHashUtil.resultFingerprintHash("KYUUBI", "kyuubi-01", "use db_b",
                "SELECT * FROM t LIMIT 1000000");
        assertThat(dbA).isNotEqualTo(dbB);
    }

    @Test
    void prefixVsNoPrefix_differentHash() {
        // 带 use db 与不带（落默认库）必须区分
        String withPrefix = SqlHashUtil.resultFingerprintHash("KYUUBI", "kyuubi-01", "use demo_db",
                "SELECT * FROM t LIMIT 1000000");
        String noPrefix = SqlHashUtil.resultFingerprintHash("KYUUBI", "kyuubi-01", null,
                "SELECT * FROM t LIMIT 1000000");
        assertThat(withPrefix).isNotEqualTo(noPrefix);
    }

    @Test
    void differentExecutedSql_differentHash() {
        // SELECT * FROM t 与 SELECT * FROM t LIMIT 100（规整后不同）必须区分
        String noLimit = SqlHashUtil.resultFingerprintHash("KYUUBI", "kyuubi-01", "use demo_db",
                "SELECT * FROM t LIMIT 1000000");
        String limit100 = SqlHashUtil.resultFingerprintHash("KYUUBI", "kyuubi-01", "use demo_db",
                "SELECT * FROM t LIMIT 100");
        assertThat(noLimit).isNotEqualTo(limit100);
    }

    @Test
    void scenario_twoRunsMatch() {
        // 复刻用户场景：同库同引擎同实例，规整后 SQL 一致 -> 第二次应命中第一次的结果
        String run1 = SqlHashUtil.resultFingerprintHash("KYUUBI", "kyuubi-01", "use demo_db",
                "SELECT * from demo_metrics LIMIT 1000000");
        String run2 = SqlHashUtil.resultFingerprintHash("KYUUBI", "kyuubi-01", "use demo_db",
                "SELECT * from demo_metrics LIMIT 1000000");
        assertThat(run1).isEqualTo(run2);
    }

    @Test
    void hash_singleSql_stillWorks() {
        // 保留的单参 hash 仍可用，且规范化使格式差异不影响
        assertThat(SqlHashUtil.hash("SELECT * FROM t")).isEqualTo(SqlHashUtil.hash("select  *  from  t"));
    }
}
