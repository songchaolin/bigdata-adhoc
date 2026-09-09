package io.gitee.songchaolin.adhoc.server.service;

import io.gitee.songchaolin.adhoc.common.exception.AdhocErrorCode;
import io.gitee.songchaolin.adhoc.common.exception.AdhocException;
import io.gitee.songchaolin.adhoc.dao.mapper.AdhocQueryTaskMapper;
import io.gitee.songchaolin.adhoc.dao.mapper.AdhocResultSummaryMapper;
import io.gitee.songchaolin.adhoc.server.auth.OwnershipChecker;
import io.gitee.songchaolin.adhoc.common.config.ConfigHolder;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** TaskQueryService 单测：getTaskDetail not-found + 归属校验。纯 mock。 */
class TaskQueryServiceTest {

    private final AdhocQueryTaskMapper taskMapper = mock(AdhocQueryTaskMapper.class);
    private final AdhocResultSummaryMapper resultSummaryMapper = mock(AdhocResultSummaryMapper.class);
    private final OwnershipChecker ownershipChecker = new OwnershipChecker(ConfigHolder.forTest());
    private final TaskQueryService service = new TaskQueryService(taskMapper, resultSummaryMapper, ownershipChecker);

    @Test
    void getTaskDetail_notFound() {
        when(taskMapper.selectById("t-missing")).thenReturn(null);

        assertThatThrownBy(() -> service.getTaskDetail("t-missing", null))
                .isInstanceOfSatisfying(AdhocException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(AdhocErrorCode.ADHOC_JOB_NOT_FOUND));
    }
}
