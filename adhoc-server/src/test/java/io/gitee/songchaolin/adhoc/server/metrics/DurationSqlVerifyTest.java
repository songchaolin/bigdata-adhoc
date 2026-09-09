package io.gitee.songchaolin.adhoc.server.metrics;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * 轻量级 SQL 校验：不启 Spring 上下文（避免与运行中 server 抢 gRPC 9090 端口），
 * 直接 JDBC 连平台元数据库，验证 duration 统计 SQL 的 COALESCE 回退（duration_ms 未落库时
 * 用 finish_time - submit_time）能正确算出 Job 耗时与分桶。
 *
 * <p>连接读环境变量 ADHOC_MYSQL_HOST/ADHOC_MYSQL_USER/ADHOC_MYSQL_PASSWORD（未设置自动跳过）。
 *
 * <pre>mvn -pl adhoc-server -am test -Dtest=DurationSqlVerifyTest -DfailIfNoTests=false</pre>
 */
@EnabledIfEnvironmentVariable(named = "ADHOC_MYSQL_HOST", matches = ".+")
public class DurationSqlVerifyTest {

    private static final String URL =
            "jdbc:mysql://" + System.getenv("ADHOC_MYSQL_HOST") + ":3306/adhoc?useSSL=false&characterEncoding=utf8&serverTimezone=Asia/Shanghai";
    private static final String USER = System.getenv("ADHOC_MYSQL_USER");
    private static final String PASS = System.getenv("ADHOC_MYSQL_PASSWORD");

    @Test
    public void verifyDurationFallback() throws Exception {
        try (Connection c = DriverManager.getConnection(URL, USER, PASS)) {
            // 1. 逐行看回退后的 dur
            String rowSql =
                    "SELECT COALESCE(duration_ms, TIMESTAMPDIFF(MICROSECOND, submit_time, finish_time) DIV 1000) AS dur, " +
                    "       duration_ms, status, submit_time, finish_time " +
                    "FROM adhoc_query_job " +
                    "WHERE submit_time >= DATE_SUB(NOW(), INTERVAL 168 HOUR) AND is_deleted = 0 AND finish_time IS NOT NULL " +
                    "ORDER BY submit_time";
            System.out.println("==== rows (168h, finish_time 非空) ====");
            try (PreparedStatement ps = c.prepareStatement(rowSql); ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    System.out.println("dur=" + rs.getLong("dur") + "ms" +
                            " | duration_ms=" + rs.getObject("duration_ms") +
                            " | status=" + rs.getString("status") +
                            " | submit=" + rs.getTimestamp("submit_time") +
                            " | finish=" + rs.getTimestamp("finish_time"));
                }
            }

            // 2. 完整聚合（与 selectDurationStats 同结构）
            String aggSql =
                    "SELECT IFNULL(AVG(dur), 0) AS avgMs, IFNULL(MAX(dur), 0) AS maxMs, COUNT(*) AS total, " +
                    "  SUM(CASE WHEN dur < 1000 THEN 1 ELSE 0 END) AS bucket0to1s, " +
                    "  SUM(CASE WHEN dur >= 1000 AND dur < 10000 THEN 1 ELSE 0 END) AS bucket1to10s, " +
                    "  SUM(CASE WHEN dur >= 10000 AND dur < 60000 THEN 1 ELSE 0 END) AS bucket10to60s, " +
                    "  SUM(CASE WHEN dur >= 60000 THEN 1 ELSE 0 END) AS bucketGt60s " +
                    "FROM (" +
                    "  SELECT COALESCE(duration_ms, TIMESTAMPDIFF(MICROSECOND, submit_time, finish_time) DIV 1000) AS dur " +
                    "  FROM adhoc_query_job " +
                    "  WHERE submit_time >= DATE_SUB(NOW(), INTERVAL 168 HOUR) AND is_deleted = 0 AND finish_time IS NOT NULL" +
                    ") t";
            System.out.println("==== aggregate (168h) ====");
            try (PreparedStatement ps = c.prepareStatement(aggSql); ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    System.out.println("avgMs=" + rs.getDouble("avgMs") +
                            " maxMs=" + rs.getLong("maxMs") +
                            " total=" + rs.getLong("total") +
                            " buckets=[0-1s:" + rs.getLong("bucket0to1s") +
                            ", 1-10s:" + rs.getLong("bucket1to10s") +
                            ", 10-60s:" + rs.getLong("bucket10to60s") +
                            ", >60s:" + rs.getLong("bucketGt60s") + "]");
                }
            }
        }
    }
}
