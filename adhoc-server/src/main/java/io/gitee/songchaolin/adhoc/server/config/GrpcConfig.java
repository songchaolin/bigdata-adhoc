package io.gitee.songchaolin.adhoc.server.config;

import io.gitee.songchaolin.adhoc.server.grpc.GrpcMetadataUtil;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;
import org.springframework.context.annotation.Configuration;

/**
 * gRPC 配置类：注册全局拦截器
 */
@Configuration
public class GrpcConfig {

    /**
     * 注册 gRPC 服务端拦截器：提取 Metadata 中的用户身份
     */
    @GrpcGlobalServerInterceptor
    public GrpcMetadataUtil.ServerMetadataInterceptor serverMetadataInterceptor() {
        return new GrpcMetadataUtil.ServerMetadataInterceptor();
    }
}