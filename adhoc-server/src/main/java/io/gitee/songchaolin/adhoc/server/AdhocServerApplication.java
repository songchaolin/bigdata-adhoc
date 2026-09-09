package io.gitee.songchaolin.adhoc.server;

import com.ctrip.framework.apollo.spring.annotation.EnableApolloConfig;
import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("io.gitee.songchaolin.adhoc.dao.mapper")
@EnableScheduling
@ConfigurationPropertiesScan(basePackages = "io.gitee.songchaolin.adhoc")
@ComponentScan(basePackages = "io.gitee.songchaolin.adhoc")
@EnableApolloConfig(value = {"application", "application.yml"})
@Slf4j
public class AdhocServerApplication {

    public static void main(String[] args) {
        log.info(">>>>>>>Server服务启动<<<<<<<");
        SpringApplication.run(AdhocServerApplication.class, args);
        log.info(">>>>>>>Server服务启动完成<<<<<<<");
    }
}
