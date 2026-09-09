package io.gitee.songchaolin.adhoc.server.grpc;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.gitee.songchaolin.adhoc.dao.entity.AdhocExecutorInstance;
import io.gitee.songchaolin.adhoc.dao.entity.AdhocQueryTask;
import io.gitee.songchaolin.adhoc.dao.mapper.AdhocExecutorInstanceMapper;
import io.gitee.songchaolin.adhoc.dao.mapper.AdhocQueryJobMapper;
import io.gitee.songchaolin.adhoc.dao.mapper.AdhocQueryTaskMapper;
import io.gitee.songchaolin.adhoc.internal.grpc.v1.HeartbeatRequest;
import io.gitee.songchaolin.adhoc.internal.grpc.v1.HeartbeatResponse;
import io.gitee.songchaolin.adhoc.internal.grpc.v1.HeartbeatServiceGrpc;
import io.gitee.songchaolin.adhoc.server.ha.JobLogRegistry;
import io.gitee.songchaolin.adhoc.server.ha.RunningJobRegistry;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HeartbeatService 测试：心跳对账（DB RUNNING Task 不在心跳 -> TASK_LOST）。连真实 MySQL 库
 * （ADHOC_MYSQL_* 环境变量提供连接，未设置自动跳过）。
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "ADHOC_MYSQL_HOST", matches = ".+")
class HeartbeatServiceImplTest {

    @Autowired
    private AdhocExecutorInstanceMapper executorInstanceMapper;
    @Autowired
    private AdhocQueryTaskMapper taskMapper;
    @Autowired
    private AdhocQueryJobMapper jobMapper;
    @Autowired
    private ExecutorChannelPool executorChannelPool;
    @Autowired
    private RunningJobRegistry runningJobRegistry;
    @Autowired
    private JobLogRegistry jobLogRegistry;

    @Test
    void heartbeatReconcilesLostTask() throws Exception {
        String instanceId = "hb-reconcile-" + System.nanoTime();
        executorInstanceMapper.upsertOnRegister(instanceId, "127.0.0.1", 9091, "1.0.0", "KYUUBI", 10);

        // 插一个 RUNNING Task 属于这个 executor，但心跳不上报它 -> 应被判 TASK_LOST
        String taskId = UUID.randomUUID().toString().replace("-", "");
        AdhocQueryTask task = new AdhocQueryTask();
        task.setQueryId(taskId);
        task.setJobId(UUID.randomUUID().toString().replace("-", ""));
        task.setSegmentIndex(0);
        task.setUserId("test");
        task.setSqlContent("SELECT 1");
        task.setStatus("RUNNING");
        task.setStage("EXECUTING");
        task.setEngineType("KYUUBI");
        task.setExecutorInstance(instanceId);
        task.setEnqueueTime(new Date());
        taskMapper.insert(task);

        HeartbeatServiceImpl svc = new HeartbeatServiceImpl(taskMapper, jobMapper, executorChannelPool, runningJobRegistry, jobLogRegistry);
        String name = "hb-reconcile-" + System.nanoTime();
        Server server = InProcessServerBuilder.forName(name).directExecutor().addService(svc).build().start();
        ManagedChannel ch = InProcessChannelBuilder.forName(name).directExecutor().build();
        try {
            HeartbeatServiceGrpc.HeartbeatServiceBlockingStub stub = HeartbeatServiceGrpc.newBlockingStub(ch);
            stub.heartbeat(HeartbeatRequest.newBuilder()
                    .setInstanceId(instanceId)
                    .setInstanceType("EXECUTOR")
                    .setLoadScore(0.0)
                    .build()); // 不上报 taskId
        } finally {
            ch.shutdownNow();
            server.shutdownNow();
        }

        AdhocQueryTask updated = taskMapper.selectById(taskId);
        assertThat(updated.getStatus()).isEqualTo("FAILED");
        assertThat(updated.getFailReasonCategory()).isEqualTo("TASK_LOST");

        taskMapper.deleteById(taskId);
        AdhocExecutorInstance inst = executorInstanceMapper.selectOne(
                new LambdaQueryWrapper<AdhocExecutorInstance>().eq(AdhocExecutorInstance::getInstanceId, instanceId));
        if (inst != null) executorInstanceMapper.deleteById(inst.getId());
    }
}
