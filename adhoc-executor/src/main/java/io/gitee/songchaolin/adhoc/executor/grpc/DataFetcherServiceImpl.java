package io.gitee.songchaolin.adhoc.executor.grpc;

import io.gitee.songchaolin.adhoc.executor.runner.LogBuffer;
import io.gitee.songchaolin.adhoc.executor.runner.LogBufferRegistry;
import io.gitee.songchaolin.adhoc.internal.grpc.v1.ClearJobLogRequest;
import io.gitee.songchaolin.adhoc.internal.grpc.v1.ClearJobLogResponse;
import io.gitee.songchaolin.adhoc.internal.grpc.v1.DataFetcherGrpc;
import io.gitee.songchaolin.adhoc.internal.grpc.v1.FetchJobLogRequest;
import io.gitee.songchaolin.adhoc.internal.grpc.v1.FetchJobLogResponse;
import io.gitee.songchaolin.adhoc.internal.grpc.v1.FetchLogRequest;
import io.gitee.songchaolin.adhoc.internal.grpc.v1.FetchLogResponse;
import io.gitee.songchaolin.adhoc.internal.grpc.v1.PingRequest;
import io.gitee.songchaolin.adhoc.internal.grpc.v1.PingResponse;
import io.grpc.stub.StreamObserver;
import java.util.List;
import net.devh.boot.grpc.server.service.GrpcService;

/** executor 侧 DataFetcher：fetchLog 读 task 内存日志，fetchJobLog 读 job 内存日志（fetchResult 已删，server 直读 OSS）。 */
@GrpcService
public class DataFetcherServiceImpl extends DataFetcherGrpc.DataFetcherImplBase {

    private final LogBufferRegistry registry;

    public DataFetcherServiceImpl(LogBufferRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void fetchLog(FetchLogRequest req, StreamObserver<FetchLogResponse> responseObserver) {
        LogBuffer buffer = registry.get(req.getTaskId());
        FetchLogResponse.Builder resp = FetchLogResponse.newBuilder();
        if (buffer == null) {
            // executor 无该 Task buffer（已终态清理或非本 executor）-> server 读 OSS 快照
            resp.setHasMore(false);
        } else {
            int limit = req.getLimit() > 0 ? req.getLimit() : 100;
            resp.addAllLines(buffer.read(req.getOffset(), limit));
            resp.setHasMore(req.getOffset() + resp.getLinesCount() < buffer.lineCount());
        }
        responseObserver.onNext(resp.build());
        responseObserver.onCompleted();
    }

    @Override
    public void fetchJobLog(FetchJobLogRequest req, StreamObserver<FetchJobLogResponse> responseObserver) {
        LogBuffer buffer = registry.get(req.getJobId());
        FetchJobLogResponse.Builder resp = FetchJobLogResponse.newBuilder();
        if (buffer == null) {
            resp.setHasMore(false);
        } else {
            int limit = req.getLimit() > 0 ? req.getLimit() : 100;
            List<String> lines = buffer.read(req.getOffset(), limit);
            resp.addAllLines(lines);
            // next_offset：实际读的全局起始 + 行数（eviction 后 offset<baseOffset 从 baseOffset 读，server 据此调整 lastPulledOffset）
            long actualStart = Math.max(req.getOffset(), buffer.getBaseOffset());
            long nextOffset = actualStart + lines.size();
            resp.setNextOffset(nextOffset);
            resp.setHasMore(nextOffset < buffer.lineCount());
        }
        responseObserver.onNext(resp.build());
        responseObserver.onCompleted();
    }

    /** clearJobLog：server 终态轮通知 executor 清理本地 job 日志 buffer（正常路径，server 已完整写 OSS）。 */
    @Override
    public void clearJobLog(ClearJobLogRequest req, StreamObserver<ClearJobLogResponse> responseObserver) {
        registry.clearJobLog(req.getJobId());
        responseObserver.onNext(ClearJobLogResponse.newBuilder().build());
        responseObserver.onCompleted();
    }

    /** ping：探活（QueueWorker dispatch 前用，可达即 ok）。executor 进程活着就回 ok=true。 */
    @Override
    public void ping(PingRequest req, StreamObserver<PingResponse> responseObserver) {
        responseObserver.onNext(PingResponse.newBuilder().setOk(true).build());
        responseObserver.onCompleted();
    }
}
