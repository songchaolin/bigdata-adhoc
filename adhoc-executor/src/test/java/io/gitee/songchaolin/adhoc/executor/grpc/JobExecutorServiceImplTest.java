package io.gitee.songchaolin.adhoc.executor.grpc;

import io.gitee.songchaolin.adhoc.common.config.ConfigHolder;
import io.gitee.songchaolin.adhoc.executor.runner.JobExecutionRunner;
import io.gitee.songchaolin.adhoc.internal.grpc.v1.DispatchJobRequest;
import io.gitee.songchaolin.adhoc.internal.grpc.v1.DispatchJobResponse;
import io.gitee.songchaolin.adhoc.internal.grpc.v1.JobExecutorGrpc;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class JobExecutorServiceImplTest {

    private static ConfigHolder holder(int maxConcurrentTasks) {
        return ConfigHolder.forTest(java.util.Collections.singletonMap(
                "adhoc.executor.max-concurrent-tasks", String.valueOf(maxConcurrentTasks)));
    }

    @Test
    void dispatchJob_echoesAccepted() throws IOException {
        String name = "exec-dispatch-test";
        Server server = InProcessServerBuilder.forName(name)
                .directExecutor()
                .addService(new JobExecutorServiceImpl(Mockito.mock(JobExecutionRunner.class), holder(10)))
                .build()
                .start();
        ManagedChannel channel = InProcessChannelBuilder.forName(name).directExecutor().build();
        try {
            JobExecutorGrpc.JobExecutorBlockingStub stub = JobExecutorGrpc.newBlockingStub(channel);
            DispatchJobResponse response = stub.dispatchJob(
                    DispatchJobRequest.newBuilder()
                            .setJobId("job-1")
                            .setEngineType("KYUUBI")
                            .build());
            assertThat(response.getAccepted()).isTrue();
        } finally {
            channel.shutdownNow();
            server.shutdownNow();
        }
    }

    /** Semaphore 满（max-concurrent-tasks=1）：第 1 个 accepted=true 占住 permit，第 2 个 accepted=false 被拒。 */
    @Test
    void dispatchJob_rejectsWhenSemaphoreFull() throws Exception {
        CountDownLatch hold = new CountDownLatch(1);
        JobExecutionRunner mockRunner = Mockito.mock(JobExecutionRunner.class);
        // runner.run 阻塞占住 permit（直到 hold.countDown）
        Mockito.doAnswer(inv -> { hold.await(); return null; }).when(mockRunner).run(Mockito.any());

        String name = "exec-busy-test";
        Server server = InProcessServerBuilder.forName(name)
                .directExecutor()
                .addService(new JobExecutorServiceImpl(mockRunner, holder(1)))
                .build()
                .start();
        ManagedChannel channel = InProcessChannelBuilder.forName(name).directExecutor().build();
        try {
            JobExecutorGrpc.JobExecutorBlockingStub stub = JobExecutorGrpc.newBlockingStub(channel);
            DispatchJobResponse r1 = stub.dispatchJob(
                    DispatchJobRequest.newBuilder().setJobId("job-1").setEngineType("KYUUBI").build());
            assertThat(r1.getAccepted()).isTrue(); // 占住唯一 permit，runner 异步阻塞

            DispatchJobResponse r2 = stub.dispatchJob(
                    DispatchJobRequest.newBuilder().setJobId("job-2").setEngineType("KYUUBI").build());
            assertThat(r2.getAccepted()).isFalse(); // permit 满 -> 拒

            hold.countDown(); // 释放 r1 的 runner，让其线程退出（daemon 亦不阻止 JVM 退出）
        } finally {
            channel.shutdownNow();
            server.shutdownNow();
        }
    }
}