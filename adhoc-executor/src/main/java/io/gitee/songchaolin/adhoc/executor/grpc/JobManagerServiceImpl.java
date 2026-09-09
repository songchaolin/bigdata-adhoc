package io.gitee.songchaolin.adhoc.executor.grpc;

import io.gitee.songchaolin.adhoc.executor.engine.EngineExecutor;
import io.gitee.songchaolin.adhoc.executor.ha.RunningJobRegistry;
import io.gitee.songchaolin.adhoc.internal.grpc.v1.CancelJobRequest;
import io.gitee.songchaolin.adhoc.internal.grpc.v1.CancelJobResponse;
import io.gitee.songchaolin.adhoc.internal.grpc.v1.GetRunningJobsRequest;
import io.gitee.songchaolin.adhoc.internal.grpc.v1.GetRunningJobsResponse;
import io.gitee.songchaolin.adhoc.internal.grpc.v1.JobManagerGrpc;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * executor 侧 JobManager：cancelJob 调所有引擎 {@link EngineExecutor#cancel(String)}（Statement.cancel 中断当前查询）。
 * <p>公共部分：遍历所有 EngineExecutor（只当前跑的引擎有 Statement 映射，其他 no-op）。
 * <p>专有部分：各 EngineExecutor.cancel 实现（Kyuubi HiveStatement.cancel / StarRocks MySQL Statement.cancel）。
 * <p>cancel 立即生效：Statement.cancel 使正在阻塞的 execute 抛 SQLException，runner catch 后 markCanceled。
 */
@Component
@GrpcService
public class JobManagerServiceImpl extends JobManagerGrpc.JobManagerImplBase {

    private static final Logger log = LoggerFactory.getLogger(JobManagerServiceImpl.class);
    private final List<EngineExecutor> engineExecutors;
    private final RunningJobRegistry runningJobRegistry;

    public JobManagerServiceImpl(List<EngineExecutor> engineExecutors, RunningJobRegistry runningJobRegistry) {
        this.engineExecutors = engineExecutors;
        this.runningJobRegistry = runningJobRegistry;
    }

    @Override
    public void cancelJob(CancelJobRequest req, StreamObserver<CancelJobResponse> responseObserver) {
        String jobId = req.getJobId();
        log.info("【接收取消】jobId={}", jobId);
        for (EngineExecutor exec : engineExecutors) {
            try {
                exec.cancel(jobId);  // 只当前跑的引擎有映射，其他 no-op
            } catch (Exception e) {
                log.warn("cancel by {} for {} failed: {}", exec.getClass().getSimpleName(), jobId, e.getMessage());
            }
        }
        responseObserver.onNext(CancelJobResponse.newBuilder().setCanceled(true).build());
        responseObserver.onCompleted();
    }

    /** 探查：返回本 executor 当前在跑的 jobId 集合（server 清理孤儿 job 用，executor 重启后此集合为空）。 */
    @Override
    public void getRunningJobs(GetRunningJobsRequest req, StreamObserver<GetRunningJobsResponse> responseObserver) {
        Set<String> ids = runningJobRegistry.getIds();
        responseObserver.onNext(GetRunningJobsResponse.newBuilder().addAllJobIds(ids).build());
        responseObserver.onCompleted();
    }
}
