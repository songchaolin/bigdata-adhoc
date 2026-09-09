package io.gitee.songchaolin.adhoc.server.service;

import io.gitee.songchaolin.adhoc.common.exception.AdhocErrorCode;
import io.gitee.songchaolin.adhoc.common.exception.AdhocException;
import io.gitee.songchaolin.adhoc.dao.entity.AdhocQueryJob;
import io.gitee.songchaolin.adhoc.dao.mapper.AdhocQueryJobMapper;
import io.gitee.songchaolin.adhoc.common.dto.request.JobSubmitRequest;
import io.gitee.songchaolin.adhoc.common.dto.JobSubmitResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * JobService 测试：提交入库(PENDING) + client_request_id 幂等 + 校验（engine_type + STARROCKS SqlType 限制）。
 * 连真实 MySQL 库（ADHOC_MYSQL_* 环境变量提供连接，未设置自动跳过）。
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "ADHOC_MYSQL_HOST", matches = ".+")
class JobServiceTest {

    @Autowired
    private JobService jobService;

    @Autowired
    private AdhocQueryJobMapper jobMapper;

    @Test
    void submit_insertsPendingJob() {
        JobSubmitRequest req = new JobSubmitRequest();
        req.setSqlContent("use demo_db;SELECT * from demo_metrics limit 100");
        req.setEngineType("KYUUBI");

        JobSubmitResponse resp = jobService.submit(req, "test-user", "测试");
        assertThat(resp.getJobId()).isNotBlank();

        AdhocQueryJob job = jobMapper.selectById(resp.getJobId());
        assertThat(job).isNotNull();
        assertThat(job.getStatus()).isEqualTo("PENDING");
        assertThat(job.getSqlContent()).isEqualTo("use demo_db;SELECT * from demo_metrics limit 100");
        assertThat(job.getEngineType()).isEqualTo("KYUUBI");
        assertThat(job.getUserId()).isEqualTo("test-user");

        jobMapper.deleteById(resp.getJobId());
    }

    public void testGetLog() {

    }

    @Test
    void submit_idempotentSameClientRequestId() {
        String crid = "crid-" + System.nanoTime();
        JobSubmitRequest req = new JobSubmitRequest();
        req.setSqlContent("SELECT 2");
        req.setEngineType("KYUUBI");
        req.setClientRequestId(crid);

        JobSubmitResponse r1 = jobService.submit(req, "u", "n");
        JobSubmitResponse r2 = jobService.submit(req, "u", "n");
        assertThat(r1.getJobId()).isEqualTo(r2.getJobId());

        jobMapper.deleteById(r1.getJobId());
    }

    @Test
    void submit_invalidEngineTypeThrows() {
        JobSubmitRequest req = new JobSubmitRequest();
        req.setSqlContent("SELECT 1");
        req.setEngineType("HIVE");
        assertThatThrownBy(() -> jobService.submit(req, "u", "n"))
                .isInstanceOf(AdhocException.class);
    }

    @Test
    void submit_starRocksDqlAllowed() {
        JobSubmitRequest req = new JobSubmitRequest();
        req.setSqlContent("SELECT 1");
        req.setEngineType("STARROCKS");
        JobSubmitResponse resp = jobService.submit(req, "u", "n");
        assertThat(resp.getJobId()).isNotBlank();
        jobMapper.deleteById(resp.getJobId());
    }

    @Test
    void submit_starRocksDmlRejected() {
        JobSubmitRequest req = new JobSubmitRequest();
        req.setSqlContent("INSERT INTO t SELECT 1");
        req.setEngineType("STARROCKS");
        assertThatThrownBy(() -> jobService.submit(req, "u", "n"))
                .isInstanceOfSatisfying(AdhocException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(AdhocErrorCode.ADHOC_ENGINE_SQL_TYPE_NOT_SUPPORTED));
    }

    @Test
    void submit_starRocksDdlRejected() {
        JobSubmitRequest req = new JobSubmitRequest();
        req.setSqlContent("CREATE TABLE t (a INT)");
        req.setEngineType("STARROCKS");
        assertThatThrownBy(() -> jobService.submit(req, "u", "n"))
                .isInstanceOfSatisfying(AdhocException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(AdhocErrorCode.ADHOC_ENGINE_SQL_TYPE_NOT_SUPPORTED));
    }

    @Test
    void submit_starRocksSyntaxErrorRejected() {
        JobSubmitRequest req = new JobSubmitRequest();
        req.setSqlContent("SELCT 1 FROM");
        req.setEngineType("STARROCKS");
        assertThatThrownBy(() -> jobService.submit(req, "u", "n"))
                .isInstanceOfSatisfying(AdhocException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(AdhocErrorCode.ADHOC_SQL_SYNTAX_ERROR));
    }

    @Test
    void submit_kyuubiDmlAllowed() {
        // KYUUBI 无 SqlType 限制，INSERT 通过校验
        JobSubmitRequest req = new JobSubmitRequest();
        req.setSqlContent("INSERT INTO t SELECT 1");
        req.setEngineType("KYUUBI");
        JobSubmitResponse resp = jobService.submit(req, "u", "n");
        assertThat(resp.getJobId()).isNotBlank();
        jobMapper.deleteById(resp.getJobId());
    }

    @Test
    void cancelSetsFlag() {
        JobSubmitRequest req = new JobSubmitRequest();
        req.setSqlContent("SELECT 1");
        req.setEngineType("KYUUBI");
        JobSubmitResponse resp = jobService.submit(req, "u", "n");

        boolean ok = jobService.cancel(resp.getJobId(), "u");
        assertThat(ok).isTrue();

        AdhocQueryJob job = jobMapper.selectById(resp.getJobId());
        assertThat(job.getCancelRequested()).isEqualTo(1);
        assertThat(job.getCancelRequestedTime()).isNotNull();

        jobMapper.deleteById(resp.getJobId());
    }

    @Test
    void cancel_byOtherUser_forbidden() {
        JobSubmitRequest req = new JobSubmitRequest();
        req.setSqlContent("SELECT 1");
        req.setEngineType("KYUUBI");
        JobSubmitResponse resp = jobService.submit(req, "u", "n");

        // 归属校验：非本人非管理员 -> ADHOC_JOB_FORBIDDEN，且不置 cancel_requested
        assertThatThrownBy(() -> jobService.cancel(resp.getJobId(), "other"))
                .isInstanceOfSatisfying(AdhocException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(AdhocErrorCode.ADHOC_JOB_FORBIDDEN));

        AdhocQueryJob job = jobMapper.selectById(resp.getJobId());
        assertThat(job.getCancelRequested()).isEqualTo(0);

        jobMapper.deleteById(resp.getJobId());
    }
}