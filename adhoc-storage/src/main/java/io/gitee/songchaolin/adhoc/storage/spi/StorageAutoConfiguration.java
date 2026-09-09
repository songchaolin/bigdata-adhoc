package io.gitee.songchaolin.adhoc.storage.spi;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 存储装配：adhoc.storage.type=local|aliyun 条件注册；第三方实现可直接注册 StorageClient Bean
 * 覆盖内置装配（@ConditionalOnMissingBean）。
 */
@Configuration
@ConditionalOnProperty(name = "adhoc.storage.type")
public class StorageAutoConfiguration {

    @Bean
    @ConditionalOnProperty(name = "adhoc.storage.type", havingValue = "local")
    @ConditionalOnMissingBean(StorageClient.class)
    public StorageClient localStorageClient(
            @Value("${adhoc.storage.local.base-dir:./data/storage}") String baseDir) {
        return new LocalStorageClient(baseDir);
    }

    @Bean
    @ConditionalOnProperty(name = "adhoc.storage.type", havingValue = "aliyun")
    @ConditionalOnMissingBean(StorageClient.class)
    public StorageClient aliyunStorageClient(
            @Value("${adhoc.storage.aliyun.endpoint}") String endpoint,
            @Value("${adhoc.storage.aliyun.bucket}") String bucket,
            @Value("${adhoc.storage.aliyun.access-key}") String accessKey,
            @Value("${adhoc.storage.aliyun.secret-key}") String secretKey) {
        return new AliyunStorageClient(endpoint, bucket, accessKey, secretKey);
    }
}
