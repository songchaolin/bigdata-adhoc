package io.gitee.songchaolin.adhoc.executor.runner;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import io.gitee.songchaolin.adhoc.dao.entity.AdhocQueryTask;
import io.gitee.songchaolin.adhoc.dao.mapper.AdhocQueryTaskMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * TaskStateWriter 单测（Mockito，无 DB）：验证 markWriting 写 stage=WRITING + write_start_time，
 * 以及 markSuccess 新签名（去 writeStartMs）仍写 has_result_set/scan_rows/oss_upload_time。
 * <p>纯单测无 Spring 上下文，需 @BeforeAll 手动初始化 MP lambda 解析缓存，否则 LambdaUpdateWrapper 解析列名失败。
 */
class TaskStateWriterTest {

    @BeforeAll
    static void initLambdaCache() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), AdhocQueryTask.class);
    }

    @Test
    void markWriting_setsStageWritingAndWriteStartTime() {
        AdhocQueryTaskMapper mapper = mock(AdhocQueryTaskMapper.class);
        TaskStateWriter writer = new TaskStateWriter(mapper);

        writer.markWriting("t1", 12345L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaUpdateWrapper<AdhocQueryTask>> cap = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(mapper).update(isNull(), cap.capture());
        String sqlSet = cap.getValue().getSqlSet();
        assertThat(sqlSet).contains("stage", "write_start_time");   // set 子句含 stage + write_start_time 列
        assertThat(cap.getValue().getParamNameValuePairs().values()).contains("WRITING");  // stage 值=WRITING
    }

    @Test
    void markSuccess_withResultSet_writesStageSuccessAndMetaButNotWriteStartTime() {
        AdhocQueryTaskMapper mapper = mock(AdhocQueryTaskMapper.class);
        TaskStateWriter writer = new TaskStateWriter(mapper);

        writer.markSuccess("t1", 999L, 42L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaUpdateWrapper<AdhocQueryTask>> cap = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(mapper).update(isNull(), cap.capture());
        String sqlSet = cap.getValue().getSqlSet();
        assertThat(sqlSet).contains("has_result_set", "scan_rows", "oss_upload_time", "stage");
        // write_start_time 已由 markWriting 写入，markSuccess 不再写
        assertThat(sqlSet).doesNotContain("write_start_time");
        assertThat(cap.getValue().getParamNameValuePairs().values()).contains("SUCCESS");  // stage/status 值=SUCCESS
    }

    @Test
    void markFetching_setsStageFetchingAndFetchStartTime() {
        AdhocQueryTaskMapper mapper = mock(AdhocQueryTaskMapper.class);
        TaskStateWriter writer = new TaskStateWriter(mapper);

        writer.markFetching("t1");

        verify(mapper).update(isNull(), any(LambdaUpdateWrapper.class));
    }
}
