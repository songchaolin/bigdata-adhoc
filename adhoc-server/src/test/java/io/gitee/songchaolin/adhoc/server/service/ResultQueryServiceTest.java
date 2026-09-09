package io.gitee.songchaolin.adhoc.server.service;

import io.gitee.songchaolin.adhoc.common.exception.AdhocErrorCode;
import io.gitee.songchaolin.adhoc.common.exception.AdhocException;
import io.gitee.songchaolin.adhoc.dao.entity.AdhocQueryJob;
import io.gitee.songchaolin.adhoc.dao.entity.AdhocQueryTask;
import io.gitee.songchaolin.adhoc.dao.mapper.AdhocQueryJobMapper;
import io.gitee.songchaolin.adhoc.dao.mapper.AdhocQueryTaskMapper;
import io.gitee.songchaolin.adhoc.dao.mapper.AdhocResultSummaryMapper;
import io.gitee.songchaolin.adhoc.common.dto.JobResultResponse;
import io.gitee.songchaolin.adhoc.common.dto.TaskResultItem;
import io.gitee.songchaolin.adhoc.server.auth.OwnershipChecker;
import io.gitee.songchaolin.adhoc.common.config.ConfigHolder;
import io.gitee.songchaolin.adhoc.storage.spi.StorageClient;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** ResultQueryService 单测：getJobResult 存在性校验（not-found）+ 无结果集 task 聚合。纯 mock，不连 OSS/DB。 */
class ResultQueryServiceTest {

    private final AdhocResultSummaryMapper resultSummaryMapper = mock(AdhocResultSummaryMapper.class);
    private final AdhocQueryTaskMapper taskMapper = mock(AdhocQueryTaskMapper.class);
    private final AdhocQueryJobMapper jobMapper = mock(AdhocQueryJobMapper.class);
    private final StorageClient storageClient = mock(StorageClient.class);
    private final OwnershipChecker ownershipChecker = new OwnershipChecker(ConfigHolder.forTest());
    private final ResultQueryService service = new ResultQueryService(
            resultSummaryMapper, taskMapper, jobMapper, storageClient, ownershipChecker);

    @Test
    void getJobResult_notFound() {
        when(jobMapper.selectById("job1")).thenReturn(null);

        assertThatThrownBy(() -> service.getJobResult("job1", 20, null))
                .isInstanceOfSatisfying(AdhocException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(AdhocErrorCode.ADHOC_JOB_NOT_FOUND));
    }

    @Test
    void getJobResult_noResultSetTask_hasResultSetFalse() {
        AdhocQueryJob job = new AdhocQueryJob();
        job.setJobId("job1");
        job.setUserId("u");
        job.setStatus("SUCCESS");
        when(jobMapper.selectById("job1")).thenReturn(job);

        AdhocQueryTask task = new AdhocQueryTask();
        task.setQueryId("t1");
        task.setJobId("job1");
        task.setSegmentIndex(0);
        task.setStatus("SUCCESS");
        task.setSqlType("DDL");
        task.setSqlContent("CREATE TABLE t (a INT)");
        when(taskMapper.selectList(any())).thenReturn(Collections.singletonList(task));
        // 无结果集：summary 为 null（DDL 无结果）
        when(resultSummaryMapper.selectById("t1")).thenReturn(null);

        JobResultResponse resp = service.getJobResult("job1", 20, null);

        assertThat(resp.getTasks()).hasSize(1);
        TaskResultItem item = resp.getTasks().get(0);
        assertThat(item.getHasResultSet()).isFalse();
        assertThat(item.getSqlType()).isEqualTo("DDL");
        assertThat(item.getSqlContent()).contains("CREATE TABLE");
        assertThat(item.getSchema()).isNull();
        assertThat(item.getRows()).isNull();
    }
}
