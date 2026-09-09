package io.gitee.songchaolin.adhoc.executor.runner;

import io.gitee.songchaolin.adhoc.common.enums.JobStatus;
import io.gitee.songchaolin.adhoc.common.util.LogConstants;
import io.gitee.songchaolin.adhoc.dao.entity.AdhocQueryJob;
import io.gitee.songchaolin.adhoc.dao.mapper.AdhocQueryJobMapper;
import io.gitee.songchaolin.adhoc.common.config.ConfigHolder;
import io.gitee.songchaolin.adhoc.storage.spi.StorageClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * LogBufferRegistry 兜底补偿测试：reconcile（标识跳过/合并补全/全量覆盖）+ scan（终态/超时/已删跳过）+ clearJobLog。
 * 直接调 reconcile/scan（包级），不走 scheduler（start() 才建 scheduler）。
 */
class LogBufferRegistryTest {

    private final AdhocQueryJobMapper jobMapper = mock(AdhocQueryJobMapper.class);
    private final StorageClient storageClient = mock(StorageClient.class);
    private final LogBufferRegistry registry = new LogBufferRegistry(jobMapper, storageClient, ConfigHolder.forTest());

    private LogBuffer bufferWith(String... lines) {
        LogBuffer b = new LogBuffer();
        for (String l : lines) {
            b.append(l);
        }
        return b;
    }

    private AdhocQueryJob job(String jobId, String status, Long finishAgoMs, String logPath) {
        AdhocQueryJob j = new AdhocQueryJob();
        j.setJobId(jobId);
        j.setStatus(status);
        j.setFinishTime(finishAgoMs == null ? null : new Date(System.currentTimeMillis() - finishAgoMs));
        j.setPersistentLogPath(logPath);
        return j;
    }

    private void mockStorageClient(String key, String content) throws Exception {
        when(storageClient.download(eq(key))).thenReturn(
                new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void ossHasMarker_skipsReconcile() throws Exception {
        LogBuffer jobLog = bufferWith("[executor] line1", "[executor] line2");
        String ossContent = "2026-07-27 10:00:00.000 [executor] line1\n"
                + "2026-07-27 10:00:01.000 [executor] line2\n"
                + "2026-07-27 10:00:02.000 " + LogConstants.COMPLETE_MARKER_LINE;
        mockStorageClient("ossKey", ossContent);

        registry.reconcile("job1", jobLog, "ossKey");

        verify(storageClient, never()).uploadLog(anyString(), any());
        verify(jobMapper, never()).update(any(), any());
    }

    @Test
    void ossMissingMarker_mergesAndUploads() throws Exception {
        // exec 4 行；OSS 有前 2 行（与 exec 同 ts，模拟 server 之前拉的），无 marker -> 补后 2 行
        LogBuffer jobLog = bufferWith("[executor] line1", "[executor] line2",
                "[executor] line3", "[executor] line4");
        List<String> execLines = jobLog.snapshot();
        String ossContent = execLines.get(0) + "\n" + execLines.get(1);
        mockStorageClient("ossKey", ossContent);
        when(storageClient.uploadLog(eq("ossKey"), any())).thenReturn("ossKey");

        registry.reconcile("job1", jobLog, "ossKey");

        ArgumentCaptor<byte[]> captor = ArgumentCaptor.forClass(byte[].class);
        verify(storageClient).uploadLog(eq("ossKey"), captor.capture());
        String uploaded = new String(captor.getValue(), StandardCharsets.UTF_8);
        String[] lines = uploaded.split("\n");
        // 合并去重：OSS 2 行 + exec 缺失 2 行 + marker = 5 行
        assertThat(lines).hasSize(5);
        assertThat(lines[lines.length - 1]).contains(LogConstants.COMPLETE_MARKER);
        assertThat(uploaded).contains("[executor] line3").contains("[executor] line4");
        verify(jobMapper, never()).update(any(), any());  // key 未变，不更新 DB
    }

    @Test
    void ossNull_overwritesAllWithMarker() throws Exception {
        LogBuffer jobLog = bufferWith("[executor] line1", "[executor] line2");
        when(storageClient.uploadLog(anyString(), any())).thenReturn("newKey");

        registry.reconcile("job1", jobLog, null);

        ArgumentCaptor<byte[]> captor = ArgumentCaptor.forClass(byte[].class);
        verify(storageClient).uploadLog(anyString(), captor.capture());
        String uploaded = new String(captor.getValue(), StandardCharsets.UTF_8);
        String[] lines = uploaded.split("\n");
        assertThat(lines).hasSize(3);  // 2 exec + marker
        assertThat(lines[lines.length - 1]).contains(LogConstants.COMPLETE_MARKER);
        // jobMapper.update 包 try-catch（best-effort），且 LambdaUpdateWrapper 构造依赖 MybatisPlus TableInfo（纯单测未初始化），
        // 故不 verify update；生产环境 Spring 已初始化 TableInfo，update 正常执行。
    }

    @Test
    void scan_jobNotTerminal_skips() throws Exception {
        LogBuffer jobLog = bufferWith("line1");
        registry.putJobLog("job1", jobLog);
        when(jobMapper.selectById("job1")).thenReturn(job("job1", JobStatus.RUNNING.name(), 60000L, "ossKey"));

        registry.scan();

        verify(storageClient, never()).download(anyString());
        verify(storageClient, never()).uploadLog(anyString(), any());
        assertThat(registry.get("job1")).isNotNull();  // 未清理
    }

    @Test
    void scan_jobTerminalButWithinDelay_skips() throws Exception {
        LogBuffer jobLog = bufferWith("line1");
        registry.putJobLog("job1", jobLog);
        when(jobMapper.selectById("job1")).thenReturn(
                job("job1", JobStatus.SUCCESS.name(), 1000L, "ossKey"));  // finishAgo 1s < 30s delay

        registry.scan();

        verify(storageClient, never()).download(anyString());
        assertThat(registry.get("job1")).isNotNull();
    }

    @Test
    void scan_jobDeleted_clearsOnly() throws Exception {
        LogBuffer jobLog = bufferWith("line1");
        registry.putJobLog("job1", jobLog);
        when(jobMapper.selectById("job1")).thenReturn(null);

        registry.scan();

        verify(storageClient, never()).uploadLog(anyString(), any());
        assertThat(registry.get("job1")).isNull();
    }

    @Test
    void scan_ossHasMarker_clearsWithoutReconcile() throws Exception {
        LogBuffer jobLog = bufferWith("[executor] line1");
        registry.putJobLog("job1", jobLog);
        when(jobMapper.selectById("job1")).thenReturn(
                job("job1", JobStatus.SUCCESS.name(), 60000L, "ossKey"));
        mockStorageClient("ossKey", "2026-07-27 10:00:00.000 [executor] line1\n"
                + "2026-07-27 10:00:01.000 " + LogConstants.COMPLETE_MARKER_LINE);

        registry.scan();

        verify(storageClient, never()).uploadLog(anyString(), any());  // 有 marker，不补全
        assertThat(registry.get("job1")).isNull();  // 直接清理
    }

    @Test
    void clearJobLog_removesBufferAndId() {
        LogBuffer jobLog = bufferWith("line1");
        registry.putJobLog("job1", jobLog);
        assertThat(registry.get("job1")).isNotNull();

        registry.clearJobLog("job1");

        assertThat(registry.get("job1")).isNull();
    }
}
