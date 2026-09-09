package io.gitee.songchaolin.adhoc.executor.ha;

import io.gitee.songchaolin.adhoc.common.model.JvmMetrics;
import io.gitee.songchaolin.adhoc.dao.mapper.AdhocExecutorInstanceMapper;
import io.gitee.songchaolin.adhoc.dao.mapper.AdhocJvmMetricSampleMapper;
import io.gitee.songchaolin.adhoc.dao.mapper.AdhocServerInstanceMapper;
import io.gitee.songchaolin.adhoc.common.config.ConfigHolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)
class ExecutorHeartbeatTaskTest {

    @Mock private RunningTaskRegistry runningTaskRegistry;
    @Mock private AdhocExecutorInstanceMapper executorInstanceMapper;
    @Mock private AdhocServerInstanceMapper serverInstanceMapper;
    @Mock private AdhocJvmMetricSampleMapper sampleMapper;
    @Mock private ExecutorInstanceInfo instanceInfo;
    private ExecutorHeartbeatTask task;

    @BeforeEach
    void setup() {
        task = new ExecutorHeartbeatTask(runningTaskRegistry, executorInstanceMapper,
                serverInstanceMapper, sampleMapper, instanceInfo, ConfigHolder.forTest());
    }

    @Test
    void heartbeat_selfWritesEvenWhenNoUpServer() {
        when(instanceInfo.getId()).thenReturn("exec-1");
        when(runningTaskRegistry.getIds()).thenReturn(Collections.emptySet());
        when(serverInstanceMapper.selectUpInstances()).thenReturn(Collections.emptyList());

        task.heartbeat();

        // 无 UP server（gRPC 跳过）也必须自写库
        verify(executorInstanceMapper).updateSelf(eq("exec-1"), anyString(), any(JvmMetrics.class));
    }
}
