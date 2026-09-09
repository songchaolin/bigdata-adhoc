package io.gitee.songchaolin.adhoc.server.service;

import io.gitee.songchaolin.adhoc.common.config.ConfigHolder;
import io.gitee.songchaolin.adhoc.common.enums.JobStatus;
import io.gitee.songchaolin.adhoc.common.enums.TaskStatus;
import io.gitee.songchaolin.adhoc.common.util.LogConstants;
import io.gitee.songchaolin.adhoc.dao.entity.AdhocQueryJob;
import io.gitee.songchaolin.adhoc.dao.entity.AdhocQueryTask;
import io.gitee.songchaolin.adhoc.dao.mapper.AdhocQueryJobMapper;
import io.gitee.songchaolin.adhoc.dao.mapper.AdhocQueryTaskMapper;
import io.gitee.songchaolin.adhoc.internal.grpc.v1.ServerJobLogResponse;
import io.gitee.songchaolin.adhoc.server.grpc.ExecutorChannelPool;
import io.gitee.songchaolin.adhoc.server.grpc.ServerReconcileClient;
import io.gitee.songchaolin.adhoc.server.ha.JobLog;
import io.gitee.songchaolin.adhoc.server.ha.JobLogRegistry;
import io.gitee.songchaolin.adhoc.server.ha.ServerInstanceInfo;
import io.gitee.songchaolin.adhoc.server.ha.TerminalLogCache;
import io.gitee.songchaolin.adhoc.server.auth.OwnershipChecker;
import io.gitee.songchaolin.adhoc.common.dto.LogResponse;
import io.gitee.songchaolin.adhoc.storage.spi.StorageClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** LogQueryService 单测：getJobLog 本节点读内存 / 远端转发 / fallback OSS。纯单测，mock 全部依赖。 */
class LogQueryServiceTest {

    private final AdhocQueryTaskMapper taskMapper = mock(AdhocQueryTaskMapper.class);
    private final AdhocQueryJobMapper jobMapper = mock(AdhocQueryJobMapper.class);
    private final StorageClient storageClient = mock(StorageClient.class);
    private final ExecutorChannelPool executorChannelPool = mock(ExecutorChannelPool.class);
    private final JobLogRegistry jobLogRegistry = mock(JobLogRegistry.class);
    private final ServerReconcileClient serverReconcileClient = mock(ServerReconcileClient.class);
    private final ServerInstanceInfo serverInstanceInfo = mock(ServerInstanceInfo.class);
    private final TerminalLogCache terminalLogCache = new TerminalLogCache();
    private final ConfigHolder cfg = ConfigHolder.forTest();
    private final OwnershipChecker ownershipChecker = new OwnershipChecker(cfg);
    private final LogQueryService service = new LogQueryService(
            taskMapper, jobMapper, storageClient, executorChannelPool, jobLogRegistry,
            serverReconcileClient, serverInstanceInfo, terminalLogCache, cfg, ownershipChecker);

    @BeforeEach
    void clearCache() {
        terminalLogCache.clear();
    }

    private AdhocQueryJob job(String jobId, String status, String processingServer, String logPath) {
        AdhocQueryJob j = new AdhocQueryJob();
        j.setJobId(jobId);
        j.setUserId("u");
        j.setStatus(status);
        j.setProcessingServerInstance(processingServer);
        j.setPersistentLogPath(logPath);
        return j;
    }

    private AdhocQueryJob job(String jobId, String status, String processingServer, String logPath, long finishTimeAgoMs) {
        AdhocQueryJob j = job(jobId, status, processingServer, logPath);
        j.setFinishTime(new Date(System.currentTimeMillis() - finishTimeAgoMs));
        return j;
    }

    private JobLog jobLogWith(String... lines) {
        JobLog l = new JobLog();
        for (String line : lines) {
            l.append(line);
        }
        return l;
    }

    @Test
    void getJobLog_runningLocal_readsMemory() {
        when(jobMapper.selectById("job1")).thenReturn(
                job("job1", JobStatus.RUNNING.name(), "server-A", null));
        when(serverInstanceInfo.getId()).thenReturn("server-A");
        when(jobLogRegistry.get("job1")).thenReturn(jobLogWith("line1", "line2", "line3"));

        LogResponse resp = service.getJobLog("job1", 0, 2, null);

        assertThat(resp.getLines()).hasSize(2);
        assertThat(resp.isHasMore()).isTrue();
        assertThat(resp.isComplete()).isFalse();
        verify(serverReconcileClient, never()).fetchJobLog(anyString(), anyString(), anyLong(), anyInt());
    }

    @Test
    void getJobLog_runningRemote_forwardsToProcessingServer() {
        when(jobMapper.selectById("job1")).thenReturn(
                job("job1", JobStatus.RUNNING.name(), "server-B", null));
        when(serverInstanceInfo.getId()).thenReturn("server-A");
        when(jobLogRegistry.get("job1")).thenReturn(null);
        when(serverReconcileClient.fetchJobLog(eq("server-B"), eq("job1"), anyLong(), anyInt()))
                .thenReturn(ServerJobLogResponse.newBuilder()
                        .setFound(true).addLines("remote-line1").addLines("remote-line2").setHasMore(false).build());

        LogResponse resp = service.getJobLog("job1", 0, 100, null);

        assertThat(resp.getLines()).containsExactly("remote-line1", "remote-line2");
        // running job：上游分页到底（hasMore=false），但日志未 finalize（complete=false）-> 客户端据 complete 继续轮询
        assertThat(resp.isHasMore()).isFalse();
        assertThat(resp.isComplete()).isFalse();
    }

    @Test
    void getJobLog_runningRemoteNotFound_fallbackOss() {
        when(jobMapper.selectById("job1")).thenReturn(
                job("job1", JobStatus.RUNNING.name(), "server-B", "ossKey"));
        when(serverInstanceInfo.getId()).thenReturn("server-A");
        when(jobLogRegistry.get("job1")).thenReturn(null);
        when(serverReconcileClient.fetchJobLog(eq("server-B"), eq("job1"), anyLong(), anyInt()))
                .thenReturn(ServerJobLogResponse.newBuilder().setFound(false).build());
        when(storageClient.download("ossKey")).thenReturn(
                new ByteArrayInputStream("oss-line1\noss-line2".getBytes(StandardCharsets.UTF_8)));

        LogResponse resp = service.getJobLog("job1", 0, 100, null);

        assertThat(resp.getLines()).containsExactly("oss-line1", "oss-line2");
    }

    @Test
    void getJobLog_runningRemoteForwardFails_fallbackOss() {
        when(jobMapper.selectById("job1")).thenReturn(
                job("job1", JobStatus.RUNNING.name(), "server-B", "ossKey"));
        when(serverInstanceInfo.getId()).thenReturn("server-A");
        when(jobLogRegistry.get("job1")).thenReturn(null);
        when(serverReconcileClient.fetchJobLog(eq("server-B"), eq("job1"), anyLong(), anyInt()))
                .thenThrow(new RuntimeException("server-B down"));
        when(storageClient.download("ossKey")).thenReturn(
                new ByteArrayInputStream("oss-line1".getBytes(StandardCharsets.UTF_8)));

        LogResponse resp = service.getJobLog("job1", 0, 100, null);

        assertThat(resp.getLines()).containsExactly("oss-line1");
    }

    @Test
    void getJobLog_runningLocalServerButRegistryEmpty_fallbackOss() {
        // processing_server = 本节点但 registry 无（异常）-> 不转发，fallback OSS
        when(jobMapper.selectById("job1")).thenReturn(
                job("job1", JobStatus.RUNNING.name(), "server-A", "ossKey"));
        when(serverInstanceInfo.getId()).thenReturn("server-A");
        when(jobLogRegistry.get("job1")).thenReturn(null);
        when(storageClient.download("ossKey")).thenReturn(
                new ByteArrayInputStream("oss-line1".getBytes(StandardCharsets.UTF_8)));

        LogResponse resp = service.getJobLog("job1", 0, 100, null);

        verify(serverReconcileClient, never()).fetchJobLog(anyString(), anyString(), anyLong(), anyInt());
        assertThat(resp.getLines()).containsExactly("oss-line1");
    }

    @Test
    void getJobLog_terminal_readsOss() {
        when(jobMapper.selectById("job1")).thenReturn(
                job("job1", JobStatus.SUCCESS.name(), "server-A", "ossKey"));
        when(storageClient.download("ossKey")).thenReturn(
                new ByteArrayInputStream("oss-line1\noss-line2".getBytes(StandardCharsets.UTF_8)));

        LogResponse resp = service.getJobLog("job1", 0, 100, null);

        assertThat(resp.getLines()).containsExactly("oss-line1", "oss-line2");
        verify(jobLogRegistry, never()).get(anyString());
        verify(serverReconcileClient, never()).fetchJobLog(anyString(), anyString(), anyLong(), anyInt());
    }

    @Test
    void getJobLog_runningLocal_snapshotExhausted_hasMoreFalseCompleteFalse() {
        // running job，快照 3 行一次性返回（page<limit，size-based hasMore=false），
        // complete=false（job 未 finalize）-> 客户端据 complete 继续轮询，而非依赖 hasMore
        when(jobMapper.selectById("job1")).thenReturn(
                job("job1", JobStatus.RUNNING.name(), "server-A", null));
        when(serverInstanceInfo.getId()).thenReturn("server-A");
        when(jobLogRegistry.get("job1")).thenReturn(jobLogWith("line1", "line2", "line3"));

        LogResponse resp = service.getJobLog("job1", 0, 10, null);

        assertThat(resp.getLines()).hasSize(3);
        assertThat(resp.isHasMore()).isFalse();
        assertThat(resp.isComplete()).isFalse();
    }

    @Test
    void getJobLog_terminal_ossWithCompleteMarker_hasMoreFalseCompleteTrue() {
        // terminal job，OSS 末行含 COMPLETE 标识（server collector 已 finalize），全量返回 ->
        // hasMore=false（无更多行）+ complete=true（已 finalize）-> 客户端停止轮询
        when(jobMapper.selectById("job1")).thenReturn(
                job("job1", JobStatus.SUCCESS.name(), "server-A", "ossKey"));
        String completeLine = "[server] [INFO] " + LogConstants.COMPLETE_MARKER;
        when(storageClient.download("ossKey")).thenReturn(
                new ByteArrayInputStream(("oss-line1\noss-line2\n" + completeLine).getBytes(StandardCharsets.UTF_8)));

        LogResponse resp = service.getJobLog("job1", 0, 100, null);

        assertThat(resp.getLines()).hasSize(3);
        assertThat(resp.isHasMore()).isFalse();
        assertThat(resp.isComplete()).isTrue();
    }

    @Test
    void getJobLog_terminal_ossStaleNoMarker_hasMoreFalseCompleteFalse() {
        // 刚转 terminal 但 collector 尚未 finalize（OSS 无 COMPLETE，finish_time 未超 grace）：
        // hasMore=false（无更多行），complete=false（未 finalize 且未超 grace）-> 客户端据 complete 续轮询直至拉到终态页
        when(jobMapper.selectById("job1")).thenReturn(
                job("job1", JobStatus.SUCCESS.name(), "server-A", "ossKey", 0L));  // finish_time=now，未超 grace
        when(storageClient.download("ossKey")).thenReturn(
                new ByteArrayInputStream("oss-line1\noss-line2".getBytes(StandardCharsets.UTF_8)));

        LogResponse resp = service.getJobLog("job1", 0, 100, null);

        assertThat(resp.getLines()).containsExactly("oss-line1", "oss-line2");
        assertThat(resp.isHasMore()).isFalse();
        assertThat(resp.isComplete()).isFalse();
    }

    @Test
    void getJobLog_terminal_completeMarker_cachedAvoidsRepeatOssDownload() {
        // terminal 已 finalize 日志经 TerminalLogCache 缓存：二次查询命中缓存，不重复下载 OSS
        TerminalLogCache realCache = new TerminalLogCache();
        LogQueryService cachedService = new LogQueryService(
                taskMapper, jobMapper, storageClient, executorChannelPool, jobLogRegistry,
                serverReconcileClient, serverInstanceInfo, realCache, cfg, ownershipChecker);
        when(jobMapper.selectById("job1")).thenReturn(
                job("job1", JobStatus.SUCCESS.name(), "server-A", "ossKey"));
        String completeLine = "[server] [INFO] " + LogConstants.COMPLETE_MARKER;
        when(storageClient.download("ossKey")).thenReturn(
                new ByteArrayInputStream(("oss-line1\n" + completeLine).getBytes(StandardCharsets.UTF_8)));

        LogResponse r1 = cachedService.getJobLog("job1", 0, 100, null);
        LogResponse r2 = cachedService.getJobLog("job1", 0, 100, null);

        assertThat(r1.isComplete()).isTrue();
        assertThat(r2.isComplete()).isTrue();
        verify(storageClient, times(1)).download("ossKey");
    }

    @Test
    void getJobLog_terminal_noMarker_finishStaleGrace_completeTrue() {
        // executor 崩溃致 COMPLETE 标识始终未写：Job 终态且 finish_time 超 grace -> complete=true（兜底，避免客户端死循环轮询）
        when(jobMapper.selectById("job1")).thenReturn(
                job("job1", JobStatus.FAILED.name(), "server-A", "ossKey11", 60000L));  // 60s 前 finalize，超 grace(30s)
        when(storageClient.download("ossKey11")).thenReturn(
                new ByteArrayInputStream("oss-line1\noss-line2".getBytes(StandardCharsets.UTF_8)));  // 无 COMPLETE 标识
        LogResponse resp = service.getJobLog("job1", 0, 100, null);

        assertThat(resp.getLines()).containsExactly("oss-line1", "oss-line2");
        assertThat(resp.isHasMore()).isFalse();
        assertThat(resp.isComplete()).isTrue();  // 兜底：终态 + finish_time 超 grace，标识缺失亦判 complete

        // grace 兜底 complete 的日志不缓存（仅含 COMPLETE 标识才缓存）：二次查询仍整文件读 OSS
        service.getJobLog("job1", 0, 100, null);
        verify(storageClient, times(2)).download("ossKey11");
    }

    @Test
    void getJobLog_pending_noLogSource_completeFalse() {
        // 刚提交（PENDING，无任何日志源：无 registry/processingServer/executor/persistentLogPath）
        // complete=false（非终态），客户端续轮询，不误判完成——"刚提交即 complete=true"的正向场景
        when(jobMapper.selectById("job1")).thenReturn(
                job("job1", JobStatus.PENDING.name(), null, null));
        LogResponse resp = service.getJobLog("job1", 0, 500, null);
        assertThat(resp.getLines()).isEmpty();
        assertThat(resp.isComplete()).isFalse();
    }

    @Test
    void getJobLog_terminal_noLogSource_finishRecent_completeFalse() {
        // 终态 Job 但无 OSS 日志（未刷）且 finish_time 未超 grace：complete=false（兜底未触发，续轮询，
        // 防 collector 终态轮刷前误判完成）。这正是"刚提交即 complete=true"的修正场景：终态不再立即判完成
        when(jobMapper.selectById("job1")).thenReturn(
                job("job1", JobStatus.FAILED.name(), "server-A", null, 0L));  // finish_time=now，未超 grace
        LogResponse resp = service.getJobLog("job1", 0, 500, null);
        assertThat(resp.getLines()).isEmpty();
        assertThat(resp.isHasMore()).isFalse();  // 终态，running=false
        assertThat(resp.isComplete()).isFalse();  // 终态但未超 grace，不立即判完成
    }

    @Test
    void getJobLog_terminal_noLogSource_finishStale_completeTrue() {
        // 终态 Job 无日志且 finish_time 超 grace（executor 崩溃等致日志始终未刷）：兜底 complete=true，避免死循环
        when(jobMapper.selectById("job1")).thenReturn(
                job("job1", JobStatus.FAILED.name(), "server-A", null, 60000L));  // 60s 前，超 grace
        LogResponse resp = service.getJobLog("job1", 0, 500, null);
        assertThat(resp.getLines()).isEmpty();
        assertThat(resp.isComplete()).isTrue();  // 终态 + 超 grace，兜底判完成
    }

    @Test
    void getTaskLog_terminal_filtersByTaskId() {
        // task 日志已合并到 job 日志（多 task 混排 + job 级行 + COMPLETE 标识）：按 [task=ID] 过滤，只返回该 task 自身行
        AdhocQueryTask task = new AdhocQueryTask();
        task.setQueryId("task2");
        task.setJobId("job1");
        task.setStatus(TaskStatus.SUCCESS.name());
        when(taskMapper.selectById("task2")).thenReturn(task);
        when(jobMapper.selectById("job1")).thenReturn(
                job("job1", JobStatus.SUCCESS.name(), "server-A", "ossKey"));
        String completeLine = "[server] [INFO] " + LogConstants.COMPLETE_MARKER;
        String log = String.join("\n",
                "[executor] [INFO] [job=job1][task=task1] submitted sql: SELECT 1",
                "[executor] [INFO] [job=job1][task=task1] SUCCESS",
                "[executor] [INFO] [job=job1][task=task2] submitted sql: SELECT 2",
                "[executor] [INFO] [job=job1][task=task2] result: rows=1",
                "[executor] [INFO] [job=job1][task=task2] SUCCESS",
                "job job1 done, success=2 failed=0",
                completeLine);
        when(storageClient.download("ossKey")).thenReturn(
                new ByteArrayInputStream(log.getBytes(StandardCharsets.UTF_8)));

        LogResponse resp = service.getTaskLog("task2", 0, 100, null);

        // 只含 task2 的 3 行；task1 行与 job 级行（done 汇总/COMPLETE 标识）被过滤掉
        assertThat(resp.getLines()).hasSize(3);
        assertThat(resp.getLines()).allMatch(l -> l.contains("[task=task2]"));
        // complete 据全量 job 日志判定（COMPLETE 标识在 job 级行，虽被过滤但仍判 complete=true）
        assertThat(resp.isComplete()).isTrue();
    }
}
