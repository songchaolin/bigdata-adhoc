package io.gitee.songchaolin.adhoc.server.ha;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import io.gitee.songchaolin.adhoc.dao.entity.AdhocExecutorInstance;
import io.gitee.songchaolin.adhoc.dao.entity.AdhocQueryJob;
import io.gitee.songchaolin.adhoc.dao.entity.AdhocQueryTask;
import io.gitee.songchaolin.adhoc.dao.mapper.AdhocExecutorInstanceMapper;
import io.gitee.songchaolin.adhoc.dao.mapper.AdhocQueryJobMapper;
import io.gitee.songchaolin.adhoc.dao.mapper.AdhocQueryTaskMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P4-T3 executor 宕机补偿测试：注册 executor + 标 DOWN + 插 Job(RUNNING)+Task(RUNNING/PENDING)
 * -> compensate() -> 验证 Task FAILED(EXECUTOR_CRASHED / SKIPPED_DUE_TO_SESSION_LOSS) + Job FAILED。
 * 连真实 MySQL 库（ADHOC_MYSQL_* 环境变量提供连接，未设置自动跳过）。
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "ADHOC_MYSQL_HOST", matches = ".+")
class ExecutorCrashCompensationTest {

    @Autowired
    private ExecutorCrashCompensation compensation;
    @Autowired
    private AdhocExecutorInstanceMapper executorMapper;
    @Autowired
    private AdhocQueryJobMapper jobMapper;
    @Autowired
    private AdhocQueryTaskMapper taskMapper;

    @Test
    void compensatesDownExecutor() {
        String execId = "crash-exec-" + System.nanoTime();
        executorMapper.upsertOnRegister(execId, "127.0.0.1", 9091, "1.0.0", "KYUUBI", 10);
        // 标 DOWN
        executorMapper.update(null, new LambdaUpdateWrapper<AdhocExecutorInstance>()
                .eq(AdhocExecutorInstance::getInstanceId, execId)
                .set(AdhocExecutorInstance::getStatus, "DOWN"));

        // Job RUNNING on this executor
        String jobId = UUID.randomUUID().toString().replace("-", "");
        AdhocQueryJob job = new AdhocQueryJob();
        job.setJobId(jobId);
        job.setUserId("test");
        job.setSqlContent("SELECT 1");
        job.setEngineType("KYUUBI");
        job.setStatus("RUNNING");
        job.setExecutorInstance(execId);
        job.setSubmitTime(new Date());
        jobMapper.insert(job);

        // Task RUNNING + PENDING on this executor
        AdhocQueryTask runningTask = newTask(jobId, execId, 0, "RUNNING");
        AdhocQueryTask pendingTask = newTask(jobId, execId, 1, "PENDING");
        taskMapper.insert(runningTask);
        taskMapper.insert(pendingTask);

        compensation.compensate();  // 首轮：记入 pendingDownExecs，不标 FAILED（防抖动误判）
        compensation.compensate();  // 第二轮：确认仍 DOWN，标 FAILED

        AdhocQueryTask rt = taskMapper.selectById(runningTask.getQueryId());
        assertThat(rt.getStatus()).isEqualTo("FAILED");
        assertThat(rt.getFailReasonCategory()).isEqualTo("EXECUTOR_CRASHED");

        AdhocQueryTask pt = taskMapper.selectById(pendingTask.getQueryId());
        assertThat(pt.getStatus()).isEqualTo("FAILED");
        assertThat(pt.getFailReasonCategory()).isEqualTo("SKIPPED_DUE_TO_SESSION_LOSS");

        AdhocQueryJob j = jobMapper.selectById(jobId);
        assertThat(j.getStatus()).isEqualTo("FAILED");

        // cleanup
        taskMapper.deleteById(runningTask.getQueryId());
        taskMapper.deleteById(pendingTask.getQueryId());
        jobMapper.deleteById(jobId);
        AdhocExecutorInstance inst = executorMapper.selectOne(
                new LambdaQueryWrapper<AdhocExecutorInstance>().eq(AdhocExecutorInstance::getInstanceId, execId));
        if (inst != null) {
            executorMapper.deleteById(inst.getId());
        }
    }

    private AdhocQueryTask newTask(String jobId, String execId, int segIndex, String status) {
        AdhocQueryTask t = new AdhocQueryTask();
        t.setQueryId(UUID.randomUUID().toString().replace("-", ""));
        t.setJobId(jobId);
        t.setSegmentIndex(segIndex);
        t.setUserId("test");
        t.setSqlContent("SELECT 1");
        t.setStatus(status);
        t.setEngineType("KYUUBI");
        t.setExecutorInstance(execId);
        t.setEnqueueTime(new Date());
        return t;
    }
}
