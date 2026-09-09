package io.gitee.songchaolin.adhoc.server.schedule;

import io.gitee.songchaolin.adhoc.dao.entity.AdhocExecutorInstance;
import io.gitee.songchaolin.adhoc.dao.entity.AdhocQueryJob;
import io.gitee.songchaolin.adhoc.dao.mapper.AdhocExecutorInstanceMapper;
import io.gitee.songchaolin.adhoc.dao.mapper.AdhocQueryJobMapper;
import io.gitee.songchaolin.adhoc.internal.grpc.v1.DispatchJobRequest;
import io.gitee.songchaolin.adhoc.internal.grpc.v1.DispatchJobResponse;
import io.gitee.songchaolin.adhoc.server.grpc.ExecutorChannelPool;
import io.gitee.songchaolin.adhoc.server.service.JobService;
import io.gitee.songchaolin.adhoc.common.dto.request.JobSubmitRequest;
import io.gitee.songchaolin.adhoc.common.dto.JobSubmitResponse;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.mockito.ArgumentCaptor;

/**
 * QueueWorker 测试：CAS 抢占 + 按 engine_type 选 executor + dispatch + 失败/无引擎回退。连真实 MySQL 库
 * （ADHOC_MYSQL_* 环境变量提供连接，未设置自动跳过）。
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "ADHOC_MYSQL_HOST", matches = ".+")
class QueueWorkerTest {

    @Autowired
    private QueueWorker queueWorker;
    @Autowired
    private JobService jobService;
    @Autowired
    private AdhocQueryJobMapper jobMapper;
    @Autowired
    private AdhocExecutorInstanceMapper executorMapper;
    @MockBean
    private ExecutorChannelPool executorChannelPool;

    @BeforeEach
    void cleanupLingering() {
        jobMapper.delete(new LambdaQueryWrapper<AdhocQueryJob>().eq(AdhocQueryJob::getStatus, "PENDING"));
        // 清理残留的 test-exec-* 测试 executor（避免影响 engine_type 路由断言）
        executorMapper.delete(new LambdaQueryWrapper<AdhocExecutorInstance>()
                .likeRight(AdhocExecutorInstance::getInstanceId, "test-exec-"));
    }

    @Test
    void scanClaimsAndDispatches() {
        AdhocExecutorInstance exec = insertUpExecutor("KYUUBI,STARROCKS", 0.0);
        JobSubmitResponse resp = submitPendingJob("KYUUBI");
        when(executorChannelPool.ping(anyString())).thenReturn(true);
        when(executorChannelPool.dispatchJob(any(AdhocExecutorInstance.class), any(DispatchJobRequest.class)))
                .thenReturn(DispatchJobResponse.newBuilder().setAccepted(true).build());

        queueWorker.dispatcher();

        AdhocQueryJob job = jobMapper.selectById(resp.getJobId());
        assertThat(job.getStatus()).isEqualTo("DISPATCHING");
        assertThat(job.getDispatchTime()).isNotNull();
        assertThat(job.getProcessingServerInstance()).isNotNull();

        cleanup(resp.getJobId(), exec.getId());
    }

    @Test
    void scanRevertsToPendingOnDispatchFailure() {
        AdhocExecutorInstance exec = insertUpExecutor("KYUUBI,STARROCKS", 0.0);
        JobSubmitResponse resp = submitPendingJob("KYUUBI");
        when(executorChannelPool.ping(anyString())).thenReturn(true);
        when(executorChannelPool.dispatchJob(any(AdhocExecutorInstance.class), any(DispatchJobRequest.class)))
                .thenThrow(new RuntimeException("connection refused"));

        queueWorker.dispatcher();

        AdhocQueryJob job = jobMapper.selectById(resp.getJobId());
        assertThat(job.getStatus()).isEqualTo("PENDING");
        assertThat(job.getDispatchTime()).isNull();

        cleanup(resp.getJobId(), exec.getId());
    }

    @Test
    void scanRevertsToPendingWhenNoExecutorForEngineType() {
        // 提交 engine_type 无任何 executor 支持的 job -> selectOnePendingJobId 的 EXISTS 过滤掉（不 claim）-> 留 PENDING，不派发
        // 直接入库（绕过 JobService 校验）用 NO_ENGINE，确保不受 DB 中真实 executor 干扰
        AdhocExecutorInstance exec = insertUpExecutor("KYUUBI", 0.0);
        String jobId = UUID.randomUUID().toString().replace("-", "");
        AdhocQueryJob pending = new AdhocQueryJob();
        pending.setJobId(jobId);
        pending.setUserId("u");
        pending.setUserName("n");
        pending.setSqlContent("SELECT 1");
        pending.setEngineType("NO_ENGINE");
        pending.setStatus("PENDING");
        pending.setSubmitTime(new Date());
        jobMapper.insert(pending);
        when(executorChannelPool.dispatchJob(any(AdhocExecutorInstance.class), any(DispatchJobRequest.class)))
                .thenReturn(DispatchJobResponse.newBuilder().setAccepted(true).build());

        queueWorker.dispatcher();

        AdhocQueryJob job = jobMapper.selectById(jobId);
        assertThat(job.getStatus()).isEqualTo("PENDING");
        verify(executorChannelPool, never()).dispatchJob(any(AdhocExecutorInstance.class), any(DispatchJobRequest.class));

        cleanup(jobId, exec.getId());
    }

    @Test
    void scanFailoversWhenFirstExecutorPingFails() {
        // 两个 UP executor 支持 KYUUBI：exec1 load=1.0（先选），exec2 load=2.0（后选）。
        // exec1 ping 失败 -> skip；exec2 ping 通 + dispatch 成功 -> 验证 failover 到 exec2。
        // 注：AdhocExecutorInstance 无 equals，dispatchJob 用 any() stub + ArgumentCaptor 按 instanceId 断言目标。
        AdhocExecutorInstance exec1 = insertUpExecutor("KYUUBI,STARROCKS", 1.0);
        AdhocExecutorInstance exec2 = insertUpExecutor("KYUUBI,STARROCKS", 2.0);
        JobSubmitResponse resp = submitPendingJob("KYUUBI");

        when(executorChannelPool.ping(eq(exec1.getInstanceId()))).thenReturn(false);
        when(executorChannelPool.ping(eq(exec2.getInstanceId()))).thenReturn(true);
        when(executorChannelPool.dispatchJob(any(AdhocExecutorInstance.class), any(DispatchJobRequest.class)))
                .thenReturn(DispatchJobResponse.newBuilder().setAccepted(true).build());

        queueWorker.dispatcher();

        AdhocQueryJob job = jobMapper.selectById(resp.getJobId());
        assertThat(job.getStatus()).isEqualTo("DISPATCHING");
        assertThat(job.getDispatchTime()).isNotNull();
        // exec1 ping 失败被跳过 -> dispatch 只调一次，且目标是 exec2（failover 命中）
        ArgumentCaptor<AdhocExecutorInstance> captor = ArgumentCaptor.forClass(AdhocExecutorInstance.class);
        verify(executorChannelPool).dispatchJob(captor.capture(), any(DispatchJobRequest.class));
        assertThat(captor.getValue().getInstanceId()).isEqualTo(exec2.getInstanceId());

        cleanup(resp.getJobId(), exec1.getId());
        executorMapper.deleteById(exec2.getId());
    }

    private AdhocExecutorInstance insertUpExecutor(String engineTypes, double loadScore) {
        AdhocExecutorInstance exec = new AdhocExecutorInstance();
        exec.setInstanceId("test-exec-" + System.nanoTime());
        exec.setHost("127.0.0.1");
        exec.setGrpcPort(9091);
        exec.setStatus("UP");
        exec.setAccepting(1);
        exec.setLoadScore(loadScore);
        exec.setEngineTypes(engineTypes);
        exec.setMaxConcurrentTasks(10);   // EXISTS 容量检查需要
        exec.setRunningTasks("[]");        // 空闲（JSON_LENGTH=0 < 10）
        executorMapper.insert(exec);
        return exec;
    }

    private JobSubmitResponse submitPendingJob(String engineType) {
        JobSubmitRequest req = new JobSubmitRequest();
        req.setSqlContent("SELECT 1");
        req.setEngineType(engineType);
        return jobService.submit(req, "u", "n");
    }

    private void cleanup(String jobId, Long execId) {
        jobMapper.deleteById(jobId);
        if (execId != null) {
            executorMapper.deleteById(execId);
        }
    }
}
