package io.gitee.songchaolin.adhoc.server.schedule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.gitee.songchaolin.adhoc.dao.entity.AdhocExecutorInstance;
import io.gitee.songchaolin.adhoc.dao.entity.AdhocQueryJob;
import io.gitee.songchaolin.adhoc.dao.mapper.AdhocExecutorInstanceMapper;
import io.gitee.songchaolin.adhoc.dao.mapper.AdhocQueryJobMapper;
import io.gitee.songchaolin.adhoc.server.ha.ServerInstanceInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 调度限流测试（连真实 MySQL 库，ADHOC_MYSQL_* 环境变量提供连接，未设置自动跳过）。
 * @TestPropertySource 调小 running 阈值，独立 context 不污染 QueueWorkerTest。
 * 校验：running global/per-user/per-server 任一满 -> selectOnePendingJobId 返回 null -> scan 不领，PENDING 不变。
 * 断言"未领"对 DB 残留状态鲁棒（残留越多越会 block，仍通过；仅当限流失效才 claim -> 失败）。
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "ADHOC_MYSQL_HOST", matches = ".+")
@TestPropertySource(properties = {
        "adhoc.limit.max-running-jobs-global=3",
        "adhoc.limit.max-running-jobs-per-user=1",
        "adhoc.limit.max-running-jobs-per-server=2",
        "adhoc.schedule.interval-ms=999999999"
})
class QueueWorkerLimitTest {

    @Autowired
    private QueueWorker queueWorker;
    @Autowired
    private AdhocQueryJobMapper jobMapper;
    @Autowired
    private AdhocExecutorInstanceMapper executorMapper;
    @Autowired
    private ServerInstanceInfo instanceInfo;

    @BeforeEach
    void setup() {
        // 清理上次残留的本测试 job + executor
        jobMapper.delete(new LambdaQueryWrapper<AdhocQueryJob>()
                .likeRight(AdhocQueryJob::getUserId, "limit-"));
        executorMapper.delete(new LambdaQueryWrapper<AdhocExecutorInstance>()
                .likeRight(AdhocExecutorInstance::getInstanceId, "test-exec-limit"));
        // 插入一个有空闲容量的 executor，使 selectOnePendingJobId 的 EXISTS 通过
        // （这样 running 限流才是真正卡点，而非"无 executor"）
        AdhocExecutorInstance exec = new AdhocExecutorInstance();
        exec.setInstanceId("test-exec-limit-" + System.nanoTime());
        exec.setHost("127.0.0.1");
        exec.setGrpcPort(9091);
        exec.setStatus("UP");
        exec.setAccepting(1);
        exec.setEngineTypes("KYUUBI,STARROCKS");
        exec.setMaxConcurrentTasks(10);
        exec.setRunningTasks("[]");
        executorMapper.insert(exec);
    }

    @Test
    void scan_skipsWhenRunningGlobalFull() {
        // seed 3 RUNNING（other-user, other-server）-> global 满；候选 PENDING 是另一 user/server，仅 global 拦
        List<String> running = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            running.add(insertJob("limit-global", "RUNNING", "other-server"));
        }
        String pendingId = insertJob("limit-global-pending", "PENDING", null);
        try {
            queueWorker.dispatcher();
            assertThat(jobMapper.selectById(pendingId).getStatus()).isEqualTo("PENDING"); // 未被领
        } finally {
            running.forEach(jobMapper::deleteById);
            jobMapper.deleteById(pendingId);
        }
    }

    @Test
    void scan_skipsWhenPerUserRunningFull() {
        // seed 1 RUNNING（user u, other-server）-> per-user u 满（global 1<3 OK，仅 per-user 拦）
        String running = insertJob("limit-pu", "RUNNING", "other-server");
        String pendingId = insertJob("limit-pu", "PENDING", null);
        try {
            queueWorker.dispatcher();
            assertThat(jobMapper.selectById(pendingId).getStatus()).isEqualTo("PENDING");
        } finally {
            jobMapper.deleteById(running);
            jobMapper.deleteById(pendingId);
        }
    }

    @Test
    void scan_skipsWhenPerServerRunningFull() {
        // seed 2 RUNNING（other-user, 本 server）-> per-server 本 server 满（global 2<3 OK，仅 per-server 拦）
        String serverId = instanceInfo.getId();
        List<String> running = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            running.add(insertJob("limit-ps", "RUNNING", serverId));
        }
        String pendingId = insertJob("limit-ps-pending", "PENDING", null);
        try {
            queueWorker.dispatcher();
            assertThat(jobMapper.selectById(pendingId).getStatus()).isEqualTo("PENDING");
        } finally {
            running.forEach(jobMapper::deleteById);
            jobMapper.deleteById(pendingId);
        }
    }

    private String insertJob(String userId, String status, String processingServerInstance) {
        AdhocQueryJob j = new AdhocQueryJob();
        j.setJobId("Job_" + UUID.randomUUID().toString().replace("-", ""));
        j.setUserId(userId);
        j.setSqlContent("SELECT 1");
        j.setEngineType("KYUUBI");
        j.setStatus(status);
        j.setSubmitTime(new Date());
        j.setProcessingServerInstance(processingServerInstance);
        jobMapper.insert(j);
        return j.getJobId();
    }
}
