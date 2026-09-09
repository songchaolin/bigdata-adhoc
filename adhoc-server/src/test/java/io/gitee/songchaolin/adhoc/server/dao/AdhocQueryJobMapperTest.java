package io.gitee.songchaolin.adhoc.server.dao;

import io.gitee.songchaolin.adhoc.dao.entity.AdhocQueryJob;
import io.gitee.songchaolin.adhoc.dao.mapper.AdhocQueryJobMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P2 Mapper 冒烟测试：对真实 MySQL（平台元数据库） insert/select/delete 一条 adhoc_query_job，
 * 验证 MyBatis-Plus Entity/Mapper 与库表对齐。需 DB 可达 + DDL 已执行
 * （连接由 ADHOC_MYSQL_* 环境变量提供，未设置自动跳过）。
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "ADHOC_MYSQL_HOST", matches = ".+")
class AdhocQueryJobMapperTest {

    @Autowired
    private AdhocQueryJobMapper jobMapper;

    @Test
    void insertSelectDelete() {
        AdhocQueryJob job = new AdhocQueryJob();
        job.setJobId("smoke-" + System.nanoTime());
        job.setUserId("smoke-user");
        job.setSqlContent("SELECT 1");
        job.setEngineType("KYUUBI");
        job.setStatus("PENDING");
        job.setSubmitTime(new Date());

        assertThat(jobMapper.insert(job)).isEqualTo(1);

        AdhocQueryJob found = jobMapper.selectById(job.getJobId());
        assertThat(found).isNotNull();
        assertThat(found.getSqlContent()).isEqualTo("SELECT 1");
        assertThat(found.getStatus()).isEqualTo("PENDING");
        assertThat(found.getEngineType()).isEqualTo("KYUUBI");

        assertThat(jobMapper.deleteById(job.getJobId())).isEqualTo(1);
        assertThat(jobMapper.selectById(job.getJobId())).isNull();
    }
}
