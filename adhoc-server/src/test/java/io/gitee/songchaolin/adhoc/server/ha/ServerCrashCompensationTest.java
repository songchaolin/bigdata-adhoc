package io.gitee.songchaolin.adhoc.server.ha;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import io.gitee.songchaolin.adhoc.dao.entity.AdhocQueryJob;
import io.gitee.songchaolin.adhoc.dao.entity.AdhocServerInstance;
import io.gitee.songchaolin.adhoc.dao.mapper.AdhocQueryJobMapper;
import io.gitee.songchaolin.adhoc.dao.mapper.AdhocServerInstanceMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P4-T5 server 宕机补偿测试：DOWN server 承接的 RUNNING Job -> processing_server_instance 置 NULL。
 * 连真实 MySQL 库（ADHOC_MYSQL_* 环境变量提供连接，未设置自动跳过）。
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "ADHOC_MYSQL_HOST", matches = ".+")
class ServerCrashCompensationTest {

    @Autowired
    private ServerCrashCompensation compensation;
    @Autowired
    private AdhocServerInstanceMapper serverMapper;
    @Autowired
    private AdhocQueryJobMapper jobMapper;

    @Test
    void clearsProcessingServerForDownServer() {
        String serverId = "down-server-" + System.nanoTime();
        serverMapper.upsertOnRegister(serverId, "127.0.0.1", 8080, 9090, "1.0.0");
        serverMapper.update(null, new LambdaUpdateWrapper<AdhocServerInstance>()
                .eq(AdhocServerInstance::getInstanceId, serverId)
                .set(AdhocServerInstance::getStatus, "DOWN"));

        String jobId = UUID.randomUUID().toString().replace("-", "");
        AdhocQueryJob job = new AdhocQueryJob();
        job.setJobId(jobId);
        job.setUserId("test");
        job.setSqlContent("SELECT 1");
        job.setEngineType("KYUUBI");
        job.setStatus("RUNNING");
        job.setProcessingServerInstance(serverId);
        job.setSubmitTime(new Date());
        jobMapper.insert(job);

        compensation.compensate();

        AdhocQueryJob updated = jobMapper.selectById(jobId);
        assertThat(updated.getProcessingServerInstance()).isNull();
        assertThat(updated.getStatus()).isEqualTo("RUNNING"); // Job 不变终态（executor 继续）

        jobMapper.deleteById(jobId);
        AdhocServerInstance inst = serverMapper.selectOne(
                new LambdaQueryWrapper<AdhocServerInstance>().eq(AdhocServerInstance::getInstanceId, serverId));
        if (inst != null) {
            serverMapper.deleteById(inst.getId());
        }
    }
}
