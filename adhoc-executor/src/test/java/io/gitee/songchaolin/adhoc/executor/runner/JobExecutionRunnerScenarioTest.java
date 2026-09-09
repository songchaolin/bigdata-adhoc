package io.gitee.songchaolin.adhoc.executor.runner;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.gitee.songchaolin.adhoc.dao.entity.AdhocQueryJob;
import io.gitee.songchaolin.adhoc.dao.entity.AdhocQueryTask;
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
 * 场景集成测试（需真 Kyuubi + MySQL，地址由 ADHOC_KYUUBI_ENDPOINTS / ADHOC_MYSQL_* 环境变量提供，未设置自动跳过）：
 * ① 上游 task 执行失败 -> 下游 task 被显式标 FAILED（SKIPPED_DUE_TO_PRIOR_FAILURE）+ errorMessage/jobLog 指向上游。
 * ② Kyuubi 服务端 operation log 流式入 job 日志（[SERVER] 行）。
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "ADHOC_KYUUBI_ENDPOINTS", matches = ".+")
class JobExecutionRunnerScenarioTest {

    @Autowired private JobExecutionRunner runner;
    @Autowired private AdhocQueryJobMapper jobMapper;
    @Autowired private AdhocQueryTaskMapper taskMapper;
    @Autowired private AdhocResultSummaryMapper resultSummaryMapper;
    @Autowired private LogBufferRegistry logBufferRegistry;

    /** 上游 task 执行失败（表不存在）-> 下游被跳过，显式日志/DB 指向上游。 */
    @Test
    void upstreamFailureMarksDownstreamSkipped() {
        String sql = "SELECT * FROM adhoc_nonexistent_table_xyz; SELECT 1";
        String jobId = UUID.randomUUID().toString().replace("-", "");
        AdhocQueryJob job = new AdhocQueryJob();
        job.setJobId(jobId);
        job.setUserId("test");
        job.setSqlContent(sql);
        job.setEngineType("KYUUBI");
        job.setStatus("DISPATCHING"); // 模拟 server claimJob 后（runner.markRunning 的 CAS 要 DISPATCHING -> RUNNING）
        job.setSubmitTime(new Date());
        jobMapper.insert(job);

        DispatchJobRequest req = DispatchJobRequest.newBuilder()
                .setJobId(jobId).setUserId("test").setEngineType("KYUUBI").setSqlContent(sql).build();
        runner.run(req);

        AdhocQueryJob updated = jobMapper.selectById(jobId);
        assertThat(updated.getStatus()).isIn("FAILED", "PARTIAL_FAILED");

        List<AdhocQueryTask> tasks = taskMapper.selectList(
                new LambdaQueryWrapper<AdhocQueryTask>().eq(AdhocQueryTask::getJobId, jobId));
        assertThat(tasks).hasSize(2);

        AdhocQueryTask upstream = tasks.stream()
                .filter(t -> "ENGINE_ERROR".equals(t.getFailReasonCategory()))
                .findFirst().orElseThrow(() -> new AssertionError("upstream task should fail with ENGINE_ERROR"));
        assertThat(upstream.getStatus()).isEqualTo("FAILED");

        AdhocQueryTask downstream = tasks.stream()
                .filter(t -> "SKIPPED_DUE_TO_PRIOR_FAILURE".equals(t.getFailReasonCategory()))
                .findFirst().orElseThrow(() -> new AssertionError("downstream task should be SKIPPED_DUE_TO_PRIOR_FAILURE"));
        assertThat(downstream.getStatus()).isEqualTo("FAILED");
        // DB errorMessage 显式指向上游 task
        assertThat(downstream.getErrorMessage())
                .contains("skipped due to upstream task " + upstream.getQueryId());

        // job 日志有显式 [ERROR] ... SKIPPED: skipped due to upstream task 行（统一 [job=X][task=Y] 格式）
        LogBuffer jobLog = logBufferRegistry.get(jobId);
        assertThat(jobLog).isNotNull();
        List<String> lines = jobLog.read(0, 10000);
        assertThat(lines).anyMatch(l -> l.contains("[ERROR]")
                && l.contains("skipped due to upstream task " + upstream.getQueryId()));
        // job done 汇总行体现 skip 计数（1 执行失败 + 1 上游 skip -> failed=2, 1 skipped）
        assertThat(lines).anyMatch(l -> l.contains("job " + jobId + " done")
                && l.contains("1 skipped due to upstream failure"));

        // cleanup
        tasks.forEach(t -> resultSummaryMapper.deleteById(t.getQueryId()));
        taskMapper.delete(new LambdaQueryWrapper<AdhocQueryTask>().eq(AdhocQueryTask::getJobId, jobId));
        jobMapper.deleteById(jobId);
        logBufferRegistry.clearJobLog(jobId);
    }

    /** Kyuubi 服务端 operation log 入 job 日志（[SERVER] 行）。依赖 Kyuubi operation log 开启（默认开）。 */
    @Test
    void kyuubiServerLogStreamedToJobLog() {
        String sql = "SELECT 1";
        String jobId = UUID.randomUUID().toString().replace("-", "");
        AdhocQueryJob job = new AdhocQueryJob();
        job.setJobId(jobId);
        job.setUserId("test");
        job.setSqlContent(sql);
        job.setEngineType("KYUUBI");
        job.setStatus("DISPATCHING"); // 模拟 server claimJob 后（runner.markRunning 的 CAS 要 DISPATCHING -> RUNNING）
        job.setSubmitTime(new Date());
        jobMapper.insert(job);

        DispatchJobRequest req = DispatchJobRequest.newBuilder()
                .setJobId(jobId).setUserId("test").setEngineType("KYUUBI").setSqlContent(sql).build();
        runner.run(req);

        assertThat(jobMapper.selectById(jobId).getStatus()).isEqualTo("SUCCESS");

        LogBuffer jobLog = logBufferRegistry.get(jobId);
        assertThat(jobLog).isNotNull();
        List<String> lines = jobLog.read(0, 10000);
        // 服务端 operation log（Spark 执行日志）应至少有一行 [SERVER]
        assertThat(lines).anyMatch(l -> l.startsWith("[Kyuubi]") || l.contains("[Kyuubi]"));
        System.out.println("[scenario] job " + jobId + " server-log lines = "
                + lines.stream().filter(l -> l.contains("[Kyuubi]")).count());

        // cleanup
        taskMapper.delete(new LambdaQueryWrapper<AdhocQueryTask>().eq(AdhocQueryTask::getJobId, jobId));
        jobMapper.deleteById(jobId);
        logBufferRegistry.clearJobLog(jobId);
    }
}