package io.gitee.songchaolin.adhoc.executor.runner;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.gitee.songchaolin.adhoc.dao.entity.AdhocQueryJob;
import io.gitee.songchaolin.adhoc.dao.entity.AdhocQueryTask;
import io.gitee.songchaolin.adhoc.dao.entity.AdhocResultSummary;
import io.gitee.songchaolin.adhoc.dao.mapper.AdhocQueryJobMapper;
import io.gitee.songchaolin.adhoc.dao.mapper.AdhocQueryTaskMapper;
import io.gitee.songchaolin.adhoc.dao.mapper.AdhocResultSummaryMapper;
import io.gitee.songchaolin.adhoc.internal.grpc.v1.DispatchJobRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P3-T6 集成测试：runner 执行 "SELECT 1" -> 连真 Kyuubi + 写本地结果 + 直写 DB。
 * 验证 Job SUCCESS + Task SUCCESS + has_result_set + result_summary(LOCAL, 1 行)。
 * Kyuubi 地址由 ADHOC_KYUUBI_ENDPOINTS 环境变量提供（未设置自动跳过；DB 同理由 ADHOC_MYSQL_* 提供）。
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "ADHOC_KYUUBI_ENDPOINTS", matches = ".+")
class JobExecutionRunnerTest {

    @Autowired
    private JobExecutionRunner runner;
    @Autowired
    private AdhocQueryJobMapper jobMapper;
    @Autowired
    private AdhocQueryTaskMapper taskMapper;
    @Autowired
    private AdhocResultSummaryMapper resultSummaryMapper;

    @Test
    void runSimpleSelect() {
        String sql = "--conf@set configuration runtime wds.linkis.engine.runtime.datasource=\n" +
                "--\n" +
                "SELECT 'yl_lmdm_sys_category' AS type, COUNT(1) AS cnt FROM paimon_catalog.au_pm_ods.ods_yl_lmdm_sys_category\n" +
                "UNION ALL\n" +
                "SELECT 'yl_lmdm_sys_dictionary' AS type, COUNT(1) AS cnt FROM paimon_catalog.au_pm_ods.ods_yl_lmdm_sys_dictionary\n" +
                "UNION ALL\n" +
                "SELECT 'yl_lmdm_sys_first_code' AS type, COUNT(1) AS cnt FROM paimon_catalog.au_pm_ods.ods_yl_lmdm_sys_first_code\n" +
                "UNION ALL\n" +
                "SELECT 'yl_lmdm_sys_network' AS type, COUNT(1) AS cnt FROM paimon_catalog.au_pm_ods.ods_yl_lmdm_sys_network\n" +
                "UNION ALL\n" +
                "SELECT 'yl_lmdm_sys_network_distributi' AS type, COUNT(1) AS cnt FROM paimon_catalog.au_pm_ods.ods_yl_lmdm_sys_network_distributi\n" +
                "UNION ALL\n" +
                "SELECT 'yl_lmdm_sys_second_code' AS type, COUNT(1) AS cnt FROM paimon_catalog.au_pm_ods.ods_yl_lmdm_sys_second_code\n" +
                "UNION ALL\n" +
                "SELECT 'yl_lmdm_sys_staff' AS type, COUNT(1) AS cnt FROM paimon_catalog.au_pm_ods.ods_yl_lmdm_sys_staff\n" +
                "ORDER BY type desc ;";
        String jobId = UUID.randomUUID().toString().replace("-", "");
        AdhocQueryJob job = new AdhocQueryJob();
        job.setJobId(jobId);
        job.setUserId("test");
        job.setSqlContent("use demo_db;SELECT * from demo_metrics limit 100");
        job.setEngineType("KYUUBI");
        job.setStatus("DISPATCHING"); // 模拟 server claimJob 后（runner.markRunning 的 CAS 要 DISPATCHING -> RUNNING）
        job.setSubmitTime(new Date());
        jobMapper.insert(job);

        DispatchJobRequest req = DispatchJobRequest.newBuilder()
                .setJobId(jobId)
                .setUserId("test")
                .setEngineType("KYUUBI")
                .setSqlContent(job.getSqlContent())
                .build();
        runner.run(req);

        AdhocQueryJob updated = jobMapper.selectById(jobId);
        assertThat(updated.getStatus()).isEqualTo("SUCCESS");

        List<AdhocQueryTask> tasks = taskMapper.selectList(
                new LambdaQueryWrapper<AdhocQueryTask>().eq(AdhocQueryTask::getJobId, jobId));
        assertThat(tasks).hasSize(1);
        assertThat(tasks.get(0).getStatus()).isEqualTo("SUCCESS");
        assertThat(tasks.get(0).getHasResultSet()).isEqualTo(1);

        AdhocResultSummary rs = resultSummaryMapper.selectById(tasks.get(0).getQueryId());
        assertThat(rs).isNotNull();
        assertThat(rs.getResultRows()).isGreaterThan(0);
        assertThat(rs.getStorageType()).isEqualTo("PERSISTENT");

        // cleanup
        resultSummaryMapper.deleteById(tasks.get(0).getQueryId());
        taskMapper.delete(new LambdaQueryWrapper<AdhocQueryTask>().eq(AdhocQueryTask::getJobId, jobId));
        jobMapper.deleteById(jobId);
    }
}
