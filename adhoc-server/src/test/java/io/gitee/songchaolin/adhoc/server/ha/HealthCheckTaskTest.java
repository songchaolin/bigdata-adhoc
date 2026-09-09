package io.gitee.songchaolin.adhoc.server.ha;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import io.gitee.songchaolin.adhoc.dao.entity.AdhocExecutorInstance;
import io.gitee.songchaolin.adhoc.dao.mapper.AdhocExecutorInstanceMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P4-T2 健康检查测试：注册 executor + 把 heartbeat_time 设为 1h 前 -> check() -> 验证标 DOWN。
 * 连真实 MySQL 库（ADHOC_MYSQL_* 环境变量提供连接，未设置自动跳过）。
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "ADHOC_MYSQL_HOST", matches = ".+")
class HealthCheckTaskTest {

    @Autowired
    private HealthCheckTask healthCheckTask;
    @Autowired
    private AdhocExecutorInstanceMapper executorMapper;

    @Test
    void marksTimedOutExecutorDown() {
        String id = "hc-test-" + System.nanoTime();
        executorMapper.upsertOnRegister(id, "127.0.0.1", 9091, "1.0.0", "KYUUBI", 10);

        AdhocExecutorInstance inst = executorMapper.selectOne(
                new LambdaQueryWrapper<AdhocExecutorInstance>().eq(AdhocExecutorInstance::getInstanceId, id));
        // 把 heartbeat_time 设为 1 小时前（超 30s 阈值）
        executorMapper.update(null, new LambdaUpdateWrapper<AdhocExecutorInstance>()
                .eq(AdhocExecutorInstance::getId, inst.getId())
                .set(AdhocExecutorInstance::getHeartbeatTime, new Date(System.currentTimeMillis() - 3600_000)));

        healthCheckTask.check();

        AdhocExecutorInstance updated = executorMapper.selectById(inst.getId());
        assertThat(updated.getStatus()).isEqualTo("DOWN");
        assertThat(updated.getLastDownTime()).isNotNull();

        executorMapper.deleteById(inst.getId());
    }
}
