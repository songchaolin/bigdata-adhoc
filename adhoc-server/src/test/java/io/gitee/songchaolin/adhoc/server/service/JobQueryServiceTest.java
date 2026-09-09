package io.gitee.songchaolin.adhoc.server.service;

import io.gitee.songchaolin.adhoc.common.exception.AdhocErrorCode;
import io.gitee.songchaolin.adhoc.common.exception.AdhocException;
import io.gitee.songchaolin.adhoc.dao.mapper.AdhocQueryJobMapper;
import io.gitee.songchaolin.adhoc.common.dto.JobDetailResponse;
import io.gitee.songchaolin.adhoc.common.dto.JobStatusResponse;
import io.gitee.songchaolin.adhoc.common.dto.request.JobSubmitRequest;
import io.gitee.songchaolin.adhoc.common.dto.JobSubmitResponse;
import io.gitee.songchaolin.adhoc.server.service.JobQueryService;
import io.gitee.songchaolin.adhoc.server.service.JobService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P3-T3 JobQueryService 测试：getJobDetail（Job + 空 Task 列表）+ getJobStatus。
 * 连真实 MySQL 库（ADHOC_MYSQL_* 环境变量提供连接，未设置自动跳过）。
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "ADHOC_MYSQL_HOST", matches = ".+")
class JobQueryServiceTest {

    @Autowired
    private JobService jobService;
    @Autowired
    private JobQueryService jobQueryService;
    @Autowired
    private AdhocQueryJobMapper jobMapper;

    @Test
    void getJobDetailAndStatus() {
        JobSubmitRequest req = new JobSubmitRequest();
        req.setSqlContent("SELECT 1");
        req.setEngineType("KYUUBI");
        JobSubmitResponse resp = jobService.submit(req, "u", "n");

        JobDetailResponse detail = jobQueryService.getJobDetail(resp.getJobId(), "u");
        assertThat(detail.getJobId()).isEqualTo(resp.getJobId());
        assertThat(detail.getStatus()).isEqualTo("PENDING");
        assertThat(detail.getSqlContent()).isEqualTo("SELECT 1");
        assertThat(detail.getTasks()).isEmpty(); // P3-T6 才创建 Task

        JobStatusResponse status = jobQueryService.getJobStatus(resp.getJobId(), "u");
        assertThat(status.getStatus()).isEqualTo("PENDING");

        jobMapper.deleteById(resp.getJobId());
    }

    @Test
    void getJobDetail_notFound() {
        JobSubmitRequest req = new JobSubmitRequest();
        req.setSqlContent("SELECT 1");
        req.setEngineType("KYUUBI");
        JobSubmitResponse resp = jobService.submit(req, "u", "n");
        jobMapper.deleteById(resp.getJobId());

        assertThatThrownBy(() -> jobQueryService.getJobDetail(resp.getJobId(), "u"))
                .isInstanceOfSatisfying(AdhocException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(AdhocErrorCode.ADHOC_JOB_NOT_FOUND));
    }

    @Test
    void getJobDetail_forbidden_whenNotOwner() {
        JobSubmitRequest req = new JobSubmitRequest();
        req.setSqlContent("SELECT 1");
        req.setEngineType("KYUUBI");
        JobSubmitResponse resp = jobService.submit(req, "u", "n");

        // 非本人且非管理员 -> ADHOC_JOB_FORBIDDEN（adhoc.admin.user-ids 未配置=无管理员）
        assertThatThrownBy(() -> jobQueryService.getJobDetail(resp.getJobId(), "other"))
                .isInstanceOfSatisfying(AdhocException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(AdhocErrorCode.ADHOC_JOB_FORBIDDEN));

        // 系统级访问（null）放行
        JobDetailResponse detail = jobQueryService.getJobDetail(resp.getJobId(), null);
        assertThat(detail.getJobId()).isEqualTo(resp.getJobId());

        jobMapper.deleteById(resp.getJobId());
    }
}
