package io.gitee.songchaolin.adhoc.server.service;

import io.gitee.songchaolin.adhoc.common.exception.AdhocErrorCode;
import io.gitee.songchaolin.adhoc.common.exception.AdhocException;
import io.gitee.songchaolin.adhoc.dao.entity.AdhocQueryJob;
import io.gitee.songchaolin.adhoc.dao.mapper.AdhocQueryJobMapper;
import io.gitee.songchaolin.adhoc.common.dto.request.JobSubmitRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 提交限流测试（连真实 MySQL 库，ADHOC_MYSQL_* 环境变量提供连接，未设置自动跳过）。
 * @TestPropertySource 调小限流阈值便于测，独立 context 不污染 JobServiceTest。
 * 校验：段数超限 / per-user PENDING 超限 / 全局 PENDING 超限 -> 抛 ADHOC_JOB_LIMIT_EXCEEDED、不建 Job。
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "ADHOC_MYSQL_HOST", matches = ".+")
@TestPropertySource(properties = {
        "adhoc.limit.max-tasks-per-job=2",
        "adhoc.limit.max-pending-jobs-per-user=1",
        "adhoc.limit.max-pending-jobs-global=1"
})
class JobServiceLimitTest {

    @Autowired
    private JobService jobService;
    @Autowired
    private AdhocQueryJobMapper jobMapper;

    @Test
    void submit_overTasksPerJob_rejected() {
        JobSubmitRequest req = new JobSubmitRequest();
        req.setSqlContent("SELECT 1; SELECT 2; SELECT 3"); // 3 段 > max-tasks-per-job=2
        req.setEngineType("KYUUBI");
        assertThatThrownBy(() -> jobService.submit(req, "limit-tasks", "n"))
                .isInstanceOfSatisfying(AdhocException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(AdhocErrorCode.ADHOC_JOB_LIMIT_EXCEEDED));
    }

    @Test
    void submit_overPendingPerUser_rejected() {
        AdhocQueryJob existing = insertPending("limit-pu-" + UUID.randomUUID());
        try {
            JobSubmitRequest req = new JobSubmitRequest();
            req.setSqlContent("SELECT 1");
            req.setEngineType("KYUUBI");
            assertThatThrownBy(() -> jobService.submit(req, existing.getUserId(), "n"))
                    .isInstanceOfSatisfying(AdhocException.class,
                            ex -> assertThat(ex.getErrorCode()).isEqualTo(AdhocErrorCode.ADHOC_JOB_LIMIT_EXCEEDED));
        } finally {
            jobMapper.deleteById(existing.getJobId());
        }
    }

    @Test
    void submit_overPendingGlobal_rejected() {
        AdhocQueryJob existing = insertPending("limit-global-" + UUID.randomUUID());
        try {
            JobSubmitRequest req = new JobSubmitRequest();
            req.setSqlContent("SELECT 1");
            req.setEngineType("KYUUBI");
            // 另一个 user 提交，全局 PENDING 已 >=1 -> 拒
            assertThatThrownBy(() -> jobService.submit(req, "another-user-" + UUID.randomUUID(), "n"))
                    .isInstanceOfSatisfying(AdhocException.class,
                            ex -> assertThat(ex.getErrorCode()).isEqualTo(AdhocErrorCode.ADHOC_JOB_LIMIT_EXCEEDED));
        } finally {
            jobMapper.deleteById(existing.getJobId());
        }
    }

    private AdhocQueryJob insertPending(String userId) {
        AdhocQueryJob j = new AdhocQueryJob();
        j.setJobId("Job_" + UUID.randomUUID().toString().replace("-", ""));
        j.setUserId(userId);
        j.setSqlContent("SELECT 1");
        j.setEngineType("KYUUBI");
        j.setStatus("PENDING");
        j.setSubmitTime(new Date());
        jobMapper.insert(j);
        return j;
    }
}
