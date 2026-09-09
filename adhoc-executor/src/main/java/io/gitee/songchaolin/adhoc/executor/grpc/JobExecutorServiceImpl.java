package io.gitee.songchaolin.adhoc.executor.grpc;

import io.gitee.songchaolin.adhoc.common.config.ConfigHolder;
import io.gitee.songchaolin.adhoc.executor.config.AdhocExecutorConfig;
import io.gitee.songchaolin.adhoc.executor.runner.JobExecutionRunner;
import io.gitee.songchaolin.adhoc.internal.grpc.v1.DispatchJobRequest;
import io.gitee.songchaolin.adhoc.internal.grpc.v1.DispatchJobResponse;
import io.gitee.songchaolin.adhoc.internal.grpc.v1.JobExecutorGrpc;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

/**
 * executor 侧 JobExecutor 服务。dispatchJob 立即回 accepted + 异步触发 JobExecutionRunner
 *（建 Kyuubi session、拆 Task、顺序执行、写结果、直写 DB、失败传播）。
 *
 * <p>容量强制（adhoc.executor.max-concurrent-tasks）：Semaphore 限制同时执行的 Job 数。
 * tryAcquire 失败 -> accepted=false（executor busy），server 侧 QueueWorker failover 换下一个 executor，
 * 全 busy -> revertToPending 下轮再试。permit 在 runner.run 完成后 release（finally）。
 *
 * <p>幂等：acceptedJobs 记录已接收的 jobId，重复 dispatch（响应丢失/超时重发）-> 直接回 accepted=true 不重复执行，
 * 配合 JobStateWriter.markRunning 的 DISPATCHING CAS 双保险防同 job 双跑。
 */
@GrpcService
public class JobExecutorServiceImpl extends JobExecutorGrpc.JobExecutorImplBase {

    private static final Logger log = LoggerFactory.getLogger(JobExecutorServiceImpl.class);

    private final JobExecutionRunner runner;
    private final int maxConcurrentTasks;
    private final Semaphore semaphore;
    /** 已接收正在执行的 jobId（防重复 dispatch 致同 job 双跑）。runner 完成后 remove。 */
    private final ConcurrentHashMap<String, Boolean> acceptedJobs = new ConcurrentHashMap<>();
    /** runner 执行池（daemon 线程，阻塞的 runner 不阻止 JVM 退出）。Semaphore 是并发闸，池只供线程。 */
    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "job-executor-runner");
        t.setDaemon(true);
        return t;
    });

    public JobExecutorServiceImpl(JobExecutionRunner runner, ConfigHolder cfg) {
        this.runner = runner;
        this.maxConcurrentTasks = cfg.get(AdhocExecutorConfig.EXECUTOR_MAX_CONCURRENT_TASKS);
        this.semaphore = new Semaphore(maxConcurrentTasks, true);
    }

    @Override
    public void dispatchJob(DispatchJobRequest request, StreamObserver<DispatchJobResponse> responseObserver) {
        log.info("【接收派发】jobId={} 用户={} 引擎={} SQL长度={}",
                request.getJobId(), request.getUserId(), request.getEngineType(), request.getSqlContent().length());
        // 幂等：同一 jobId 重复 dispatch（响应丢失/deadline 超时后 server revert+重发）-> 直接回 accepted=true 不重复执行
        if (acceptedJobs.putIfAbsent(request.getJobId(), Boolean.TRUE) != null) {
            log.warn("【重复派发】jobId={} 已在执行，忽略重复 dispatch", request.getJobId());
            responseObserver.onNext(DispatchJobResponse.newBuilder().setAccepted(true).build());
            responseObserver.onCompleted();
            return;
        }
        if (!semaphore.tryAcquire()) {
            log.warn("【拒绝派发】jobId={} executor繁忙(并发上限={})", request.getJobId(), maxConcurrentTasks);
            acceptedJobs.remove(request.getJobId());  // 未执行，清 acceptedJobs 让重发可再尝试
            responseObserver.onNext(DispatchJobResponse.newBuilder().setAccepted(false).build());
            responseObserver.onCompleted();
            return;
        }
        executor.submit(() -> {
            try {
                runner.run(request);
            } finally {
                semaphore.release();
                acceptedJobs.remove(request.getJobId());
            }
        });
        responseObserver.onNext(DispatchJobResponse.newBuilder().setAccepted(true).build());
        responseObserver.onCompleted();
    }

    /** 当前正在执行的 Job 数（已发 permit 未释放）。ExecutorStatusReporter 播报用。 */
    public int runningJobCount() {
        return maxConcurrentTasks - semaphore.availablePermits();
    }

    public int getMaxConcurrentTasks() {
        return maxConcurrentTasks;
    }
}
