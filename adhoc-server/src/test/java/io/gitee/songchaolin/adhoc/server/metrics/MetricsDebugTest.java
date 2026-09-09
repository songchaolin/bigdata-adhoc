package io.gitee.songchaolin.adhoc.server.metrics;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gitee.songchaolin.adhoc.common.dto.MetricsExecutorVO;
import io.gitee.songchaolin.adhoc.common.dto.MetricsOverviewVO;
import io.gitee.songchaolin.adhoc.common.dto.MetricsServerVO;
import io.gitee.songchaolin.adhoc.common.dto.MetricsTaskFailVO;
import io.gitee.songchaolin.adhoc.common.dto.MetricsTopNVO;
import io.gitee.songchaolin.adhoc.common.dto.MetricsTrendPoint;
import io.gitee.songchaolin.adhoc.server.service.MetricsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

/**
 * 指标大盘调试：经 server 完整 Spring 上下文调 {@link MetricsService}，
 * 端到端验证 /api/metrics/* 的真实聚合路径（mapper GROUP BY -> VO 装配）。
 *
 * <p>跑法：
 * <pre>mvn -pl adhoc-server -am test -Dtest=MetricsDebugTest -Dsurefire.useFile=false -DfailIfNoTests=false</pre>
 * 调试时可在 IDE 直接运行本类。改 mapper 聚合 SQL 或 VO 装配后跑此测试即可验证。
 * 依赖 test/resources/application.yml 指向的元数据库（含真实 job/task/executor 数据；
 * ADHOC_MYSQL_* 环境变量提供连接，未设置自动跳过）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EnabledIfEnvironmentVariable(named = "ADHOC_MYSQL_HOST", matches = ".+")
class MetricsDebugTest {

    @Autowired
    private MetricsService metricsService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void debugMetrics() throws Exception {
        int hours = 24;
        long now = System.currentTimeMillis();

        // 1. 概览（hours 预设：startMs/endMs 传 null，Service 内回退 now-Nh..now）
        MetricsOverviewVO overview = metricsService.getOverview(null, null, hours);
        System.out.println("=== overview (hours=" + hours + ") ===");
        System.out.println(json(overview));

        // 2. 趋势
        List<MetricsTrendPoint> trends = metricsService.getTrends(null, null, hours);
        System.out.println("=== trends (points=" + trends.size() + ") ===");
        System.out.println(json(trends));

        // 3. executor 列表（调试看全部，含离线）
        List<MetricsExecutorVO> executors = metricsService.getExecutors(false);
        System.out.println("=== executors (size=" + executors.size() + ") ===");
        System.out.println(json(executors));

        // 3b. server 列表（调试看全部，含离线）
        List<MetricsServerVO> servers = metricsService.getServers(false);
        System.out.println("=== servers (size=" + servers.size() + ") ===");
        System.out.println(json(servers));

        // 4. TopN
        MetricsTopNVO topn = metricsService.getTopN(null, null, hours, 10);
        System.out.println("=== topn ===");
        System.out.println(json(topn));

        // 5. Task 失败维度
        MetricsTaskFailVO failures = metricsService.getTaskFailures(null, null, hours);
        System.out.println("=== task-failures ===");
        System.out.println(json(failures));

        // 6. 7d 概览 + 按天趋势（验证宽窗口按天分桶 + rowsToDist 修复后 jobByStatus 为真实 status）
        MetricsOverviewVO ov7 = metricsService.getOverview(null, null, 168);
        System.out.println("=== overview 7d | jobTotal=" + ov7.getJobTotal()
                + " jobByStatus=" + ov7.getJobByStatus()
                + " serverUp=" + ov7.getServerUp() + "/" + ov7.getServerTotal() + " ===");
        List<MetricsTrendPoint> trends7 = metricsService.getTrends(null, null, 168);
        System.out.println("=== trends 7d (points=" + trends7.size() + ") first/last: "
                + (trends7.isEmpty() ? "-" : trends7.get(0).getHour()) + " .. "
                + (trends7.isEmpty() ? "-" : trends7.get(trends7.size() - 1).getHour()) + " ===");

        // 7. 自定义区间（近 24h，以 startMs/endMs 显式传入，验证区间路径与预设等价）
        MetricsOverviewVO ovRange = metricsService.getOverview(now - 24L * 3600 * 1000, now, hours);
        System.out.println("=== overview custom-range(24h) | jobTotal=" + ovRange.getJobTotal()
                + " sameAsPreset=" + (ovRange.getJobTotal() == overview.getJobTotal()) + " ===");

        System.out.println("=== done ===");
    }

    private String json(Object o) throws Exception {
        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(o);
    }
}
