package io.gitee.songchaolin.adhoc.server.grpc;

import io.gitee.songchaolin.adhoc.common.util.LogConstants;
import io.gitee.songchaolin.adhoc.internal.grpc.v1.CheckJobRunningRequest;
import io.gitee.songchaolin.adhoc.internal.grpc.v1.CheckJobRunningResponse;
import io.gitee.songchaolin.adhoc.internal.grpc.v1.ServerJobLogRequest;
import io.gitee.songchaolin.adhoc.internal.grpc.v1.ServerJobLogResponse;
import io.gitee.songchaolin.adhoc.internal.grpc.v1.ServerReconcileServiceGrpc;
import io.gitee.songchaolin.adhoc.server.ha.JobLog;
import io.gitee.songchaolin.adhoc.server.ha.JobLogRegistry;
import io.gitee.songchaolin.adhoc.server.ha.RunningJobRegistry;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;

import java.util.ArrayList;
import java.util.List;

/** server 间对账 gRPC：checkJobRunning 查 RunningJobRegistry / fetchJobLog 读 JobLogRegistry 内存分页。 */
@GrpcService
public class ServerReconcileServiceImpl extends ServerReconcileServiceGrpc.ServerReconcileServiceImplBase {

    private final RunningJobRegistry runningJobRegistry;
    private final JobLogRegistry jobLogRegistry;

    public ServerReconcileServiceImpl(RunningJobRegistry runningJobRegistry, JobLogRegistry jobLogRegistry) {
        this.runningJobRegistry = runningJobRegistry;
        this.jobLogRegistry = jobLogRegistry;
    }

    @Override
    public void checkJobRunning(CheckJobRunningRequest req, StreamObserver<CheckJobRunningResponse> responseObserver) {
        boolean running = runningJobRegistry.contains(req.getJobId());
        responseObserver.onNext(CheckJobRunningResponse.newBuilder().setRunning(running).build());
        responseObserver.onCompleted();
    }

    /** 转发读 job 日志：本节点 JobLogRegistry 有则分页返回 found=true，无则 found=false（调用方 fallback OSS）。 */
    @Override
    public void fetchJobLog(ServerJobLogRequest req, StreamObserver<ServerJobLogResponse> responseObserver) {
        ServerJobLogResponse.Builder resp = ServerJobLogResponse.newBuilder();
        JobLog jobLog = jobLogRegistry.get(req.getJobId());
        if (jobLog == null) {
            resp.setFound(false);
        } else {
            List<String> all = jobLog.snapshot();
            List<String> page = new ArrayList<>();
            int limit = req.getLimit() > 0 ? req.getLimit() : 1000;
            for (long i = req.getOffset(); i < all.size() && page.size() < limit; i++) {
                page.add(all.get((int) i));
            }
            resp.setFound(true);
            resp.addAllLines(page);
            resp.setHasMore(req.getOffset() + page.size() < all.size());
            // complete：据本节点 registry 全量快照是否含 COMPLETE 标识（running job 无，恒 false）
            resp.setComplete(LogConstants.containsComplete(all));
        }
        responseObserver.onNext(resp.build());
        responseObserver.onCompleted();
    }
}
