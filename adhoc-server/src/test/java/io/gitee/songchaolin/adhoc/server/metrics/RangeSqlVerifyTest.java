package io.gitee.songchaolin.adhoc.server.metrics;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.Date;

/**
 * 轻量级区间 SQL 校验：不启 Spring 上下文，直接 JDBC 连平台元数据库，
 * 验证指标大盘时间区间改造后的 SQL（submit_time/enqueue_time 落在 [start,end] 闭区间）
 * 能正确执行并返回数据。改造前用 DATE_SUB(NOW(), INTERVAL hours HOUR)，现改为绝对 Date 边界。
 *
 * <p>连接读环境变量 ADHOC_MYSQL_HOST/ADHOC_MYSQL_USER/ADHOC_MYSQL_PASSWORD（未设置自动跳过）。
 *
 * <pre>mvn -pl adhoc-server -am test -Dtest=RangeSqlVerifyTest -DfailIfNoTests=false</pre>
 */
@EnabledIfEnvironmentVariable(named = "ADHOC_MYSQL_HOST", matches = ".+")
public class RangeSqlVerifyTest {

    private static final String URL =
            "jdbc:mysql://" + System.getenv("ADHOC_MYSQL_HOST") + ":3306/adhoc?useSSL=false&characterEncoding=utf8&serverTimezone=Asia/Shanghai";
    private static final String USER = System.getenv("ADHOC_MYSQL_USER");
    private static final String PASS = System.getenv("ADHOC_MYSQL_PASSWORD");

    @Test
    public void verifyRangeBounds() throws Exception {
        long now = System.currentTimeMillis();
        Date start24 = new Date(now - 168L * 3600 * 1000); // 近 7d（该库有数据）
        Date endNow = new Date(now);
        // 偏移区间：7d~14d 之前（验证历史区间，不止「截至 now」）
        Date start48 = new Date(now - 336L * 3600 * 1000);
        Date end24 = new Date(now - 168L * 3600 * 1000);

        try (Connection c = DriverManager.getConnection(URL, USER, PASS)) {
            System.out.println("==== 1. selectStatusDistribution [近24h] ====");
            int total24 = runStatusDist(c, start24, endNow);
            System.out.println("近24h status 合计 = " + total24);

            System.out.println("==== 1b. selectStatusDistribution [48h前~24h前] ====");
            int totalShift = runStatusDist(c, start48, end24);
            System.out.println("偏移区间 status 合计 = " + totalShift + "（应 <= 近24h 同量级或为 0）");

            System.out.println("==== 2. selectDurationStats [近24h] ====");
            runDurationStats(c, start24, endNow);

            System.out.println("==== 3. selectHourlyTrend [近24h]（验证 submit/finish 双分支区间） ====");
            int points = runHourlyTrend(c, start24, endNow);
            System.out.println("趋势点数 = " + points);
        }
    }

    private int runStatusDist(Connection c, Date start, Date end) throws Exception {
        String sql = "SELECT status AS status, COUNT(*) AS cnt FROM adhoc_query_job "
                + "WHERE submit_time >= ? AND submit_time <= ? AND is_deleted = 0 GROUP BY status";
        int total = 0;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setTimestamp(1, new Timestamp(start.getTime()));
            ps.setTimestamp(2, new Timestamp(end.getTime()));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int cnt = rs.getInt("cnt");
                    total += cnt;
                    System.out.println("  " + rs.getString("status") + " = " + cnt);
                }
            }
        }
        return total;
    }

    private void runDurationStats(Connection c, Date start, Date end) throws Exception {
        String sql = "SELECT IFNULL(AVG(dur), 0) AS avgMs, IFNULL(MAX(dur), 0) AS maxMs, COUNT(*) AS total, "
                + "  SUM(CASE WHEN dur < 1000 THEN 1 ELSE 0 END) AS bucket0to1s, "
                + "  SUM(CASE WHEN dur >= 1000 AND dur < 10000 THEN 1 ELSE 0 END) AS bucket1to10s, "
                + "  SUM(CASE WHEN dur >= 10000 AND dur < 60000 THEN 1 ELSE 0 END) AS bucket10to60s, "
                + "  SUM(CASE WHEN dur >= 60000 THEN 1 ELSE 0 END) AS bucketGt60s "
                + "FROM ("
                + "  SELECT COALESCE(duration_ms, TIMESTAMPDIFF(MICROSECOND, submit_time, finish_time) DIV 1000) AS dur "
                + "  FROM adhoc_query_job "
                + "  WHERE submit_time >= ? AND submit_time <= ? AND is_deleted = 0 AND finish_time IS NOT NULL"
                + ") t";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setTimestamp(1, new Timestamp(start.getTime()));
            ps.setTimestamp(2, new Timestamp(end.getTime()));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    System.out.println("  avgMs=" + rs.getDouble("avgMs")
                            + " maxMs=" + rs.getLong("maxMs")
                            + " total=" + rs.getLong("total")
                            + " buckets=[0-1s:" + rs.getLong("bucket0to1s")
                            + ",1-10s:" + rs.getLong("bucket1to10s")
                            + ",10-60s:" + rs.getLong("bucket10to60s")
                            + ",>60s:" + rs.getLong("bucketGt60s") + "]");
                }
            }
        }
    }

    private int runHourlyTrend(Connection c, Date start, Date end) throws Exception {
        String sql = "SELECT hour AS hour, SUM(isSubmitted) AS submitted, SUM(isFinished) AS finished FROM ("
                + "  SELECT DATE_FORMAT(submit_time, '%Y-%m-%d %H:00') AS hour, 1 AS isSubmitted, 0 AS isFinished "
                + "  FROM adhoc_query_job WHERE submit_time >= ? AND submit_time <= ? AND is_deleted = 0 "
                + "  UNION ALL "
                + "  SELECT DATE_FORMAT(finish_time, '%Y-%m-%d %H:00'), 0, 1 FROM adhoc_query_job "
                + "  WHERE finish_time >= ? AND finish_time <= ? AND is_deleted = 0 "
                + "    AND status IN ('SUCCESS','FAILED','PARTIAL_FAILED','CANCELED')"
                + ") t WHERE hour IS NOT NULL GROUP BY hour ORDER BY hour";
        int points = 0;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setTimestamp(1, new Timestamp(start.getTime()));
            ps.setTimestamp(2, new Timestamp(end.getTime()));
            ps.setTimestamp(3, new Timestamp(start.getTime()));
            ps.setTimestamp(4, new Timestamp(end.getTime()));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    points++;
                    if (points <= 3 || points > 1) { /* 全打 */
                        System.out.println("  " + rs.getString("hour")
                                + " submitted=" + rs.getLong("submitted")
                                + " finished=" + rs.getLong("finished"));
                    }
                }
            }
        }
        return points;
    }
}
