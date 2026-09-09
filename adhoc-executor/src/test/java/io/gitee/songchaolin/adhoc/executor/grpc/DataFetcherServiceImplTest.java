package io.gitee.songchaolin.adhoc.executor.grpc;

import io.gitee.songchaolin.adhoc.dao.mapper.AdhocQueryJobMapper;
import io.gitee.songchaolin.adhoc.common.config.ConfigHolder;
import io.gitee.songchaolin.adhoc.executor.runner.LogBuffer;
import io.gitee.songchaolin.adhoc.executor.runner.LogBufferRegistry;
import io.gitee.songchaolin.adhoc.internal.grpc.v1.ClearJobLogRequest;
import io.gitee.songchaolin.adhoc.internal.grpc.v1.DataFetcherGrpc;
import io.gitee.songchaolin.adhoc.internal.grpc.v1.FetchLogRequest;
import io.gitee.songchaolin.adhoc.internal.grpc.v1.FetchLogResponse;
import io.gitee.songchaolin.adhoc.storage.spi.StorageClient;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** DataFetcher 测试：FetchLog 读 LogBuffer 内存 / clearJobLog 清理 job 日志 buffer。纯单元测试，不连 DB。 */
class DataFetcherServiceImplTest {

    private LogBufferRegistry newRegistry() {
        return new LogBufferRegistry(mock(AdhocQueryJobMapper.class), mock(StorageClient.class), ConfigHolder.forTest());
    }

    @Test
    void fetchLogReadsBufferFromOffset() throws Exception {
        LogBufferRegistry registry = newRegistry();
        String taskId = "task-1";
        LogBuffer buffer = new LogBuffer();
        buffer.append("line1");
        buffer.append("line2");
        buffer.append("line3");
        registry.put(taskId, buffer);

        DataFetcherServiceImpl svc = new DataFetcherServiceImpl(registry);
        String name = "df-test-" + System.nanoTime();
        Server server = InProcessServerBuilder.forName(name).directExecutor().addService(svc).build().start();
        ManagedChannel ch = InProcessChannelBuilder.forName(name).directExecutor().build();
        try {
            DataFetcherGrpc.DataFetcherBlockingStub stub = DataFetcherGrpc.newBlockingStub(ch);
            FetchLogResponse resp = stub.fetchLog(FetchLogRequest.newBuilder()
                    .setTaskId(taskId).setOffset(1).setLimit(1).build());

            assertThat(resp.getLinesList()).hasSize(1);
            assertThat(resp.getLinesList().get(0)).endsWith("line2"); // 时间戳前缀 + " line2"
            assertThat(resp.getHasMore()).isTrue(); // offset(1) + 1 < 3
        } finally {
            ch.shutdownNow();
            server.shutdownNow();
        }
    }

    @Test
    void fetchLogReturnsEmptyWhenNoBuffer() throws Exception {
        LogBufferRegistry registry = newRegistry();
        DataFetcherServiceImpl svc = new DataFetcherServiceImpl(registry);
        String name = "df-test2-" + System.nanoTime();
        Server server = InProcessServerBuilder.forName(name).directExecutor().addService(svc).build().start();
        ManagedChannel ch = InProcessChannelBuilder.forName(name).directExecutor().build();
        try {
            DataFetcherGrpc.DataFetcherBlockingStub stub = DataFetcherGrpc.newBlockingStub(ch);
            FetchLogResponse resp = stub.fetchLog(FetchLogRequest.newBuilder()
                    .setTaskId("nonexistent").setOffset(0).setLimit(100).build());

            assertThat(resp.getLinesList()).isEmpty();
            assertThat(resp.getHasMore()).isFalse();
        } finally {
            ch.shutdownNow();
            server.shutdownNow();
        }
    }

    @Test
    void clearJobLogRemovesBuffer() throws Exception {
        LogBufferRegistry registry = newRegistry();
        String jobId = "job-1";
        LogBuffer buffer = new LogBuffer();
        buffer.append("line1");
        registry.putJobLog(jobId, buffer);
        assertThat(registry.get(jobId)).isNotNull();

        DataFetcherServiceImpl svc = new DataFetcherServiceImpl(registry);
        String name = "df-clear-" + System.nanoTime();
        Server server = InProcessServerBuilder.forName(name).directExecutor().addService(svc).build().start();
        ManagedChannel ch = InProcessChannelBuilder.forName(name).directExecutor().build();
        try {
            DataFetcherGrpc.DataFetcherBlockingStub stub = DataFetcherGrpc.newBlockingStub(ch);
            stub.clearJobLog(ClearJobLogRequest.newBuilder().setJobId(jobId).build());
            assertThat(registry.get(jobId)).isNull();
        } finally {
            ch.shutdownNow();
            server.shutdownNow();
        }
    }
}
