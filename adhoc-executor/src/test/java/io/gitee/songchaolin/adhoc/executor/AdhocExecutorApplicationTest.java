package io.gitee.songchaolin.adhoc.executor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;

// 上下文含 MySQL 数据源（druid 启动期建连），无 DB 环境时跳过（ADHOC_MYSQL_* 提供连接）
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "ADHOC_MYSQL_HOST", matches = ".+")
class AdhocExecutorApplicationTest {

    @Test
    void contextLoads() {
        // 启动 Spring 上下文 + gRPC server（测试 yml 用 port=0 随机端口），不抛异常即通过。
    }
}
