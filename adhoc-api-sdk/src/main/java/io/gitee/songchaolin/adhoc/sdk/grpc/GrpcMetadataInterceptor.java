package io.gitee.songchaolin.adhoc.sdk.grpc;

import io.grpc.*;

/**
 * gRPC 客户端 Metadata 拦截器：注入用户身份到 gRPC Metadata。
 */
public class GrpcMetadataInterceptor implements ClientInterceptor {

    private final String userId;
    private final String userName;

    public GrpcMetadataInterceptor(String userId, String userName) {
        this.userId = userId;
        this.userName = userName;
    }

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
            MethodDescriptor<ReqT, RespT> method,
            CallOptions callOptions,
            Channel next) {

        return new ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>(
                next.newCall(method, callOptions)) {

            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                // 注入用户身份到 Metadata
                headers.put(USER_ID_KEY, userId);
                if (userName != null && !userName.isEmpty()) {
                    headers.put(USER_NAME_KEY, userName);
                }
                super.start(responseListener, headers);
            }
        };
    }

    private static final Metadata.Key<String> USER_ID_KEY =
            Metadata.Key.of("x-adhoc-user-id", Metadata.ASCII_STRING_MARSHALLER);

    private static final Metadata.Key<String> USER_NAME_KEY =
            Metadata.Key.of("x-adhoc-user-name", Metadata.ASCII_STRING_MARSHALLER);
}