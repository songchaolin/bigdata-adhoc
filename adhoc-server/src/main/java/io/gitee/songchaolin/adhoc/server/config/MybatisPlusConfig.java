package io.gitee.songchaolin.adhoc.server.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置（对齐公司模版）：注册分页插件，使 {@code selectPage} 真正下推 LIMIT。
 * MP 3.5.3 用 MybatisPlusInterceptor + PaginationInnerInterceptor（旧 PaginationInterceptor 已废弃）。
 */
@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        PaginationInnerInterceptor page = new PaginationInnerInterceptor(DbType.MYSQL);
        page.setMaxLimit(50000L);  // 单页上限保护（防恶意大页）
        interceptor.addInnerInterceptor(page);
        return interceptor;
    }
}
