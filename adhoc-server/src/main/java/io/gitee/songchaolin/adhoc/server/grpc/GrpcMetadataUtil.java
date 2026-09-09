package io.gitee.songchaolin.adhoc.server.grpc;

import io.grpc.Context;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;

/**
 * gRPC Metadata 工具类：提取用户身份（x-adhoc-user-id、x-adhoc-user-name）。
 * SDK 通过 gRPC Metadata 传递用户身份，与 HTTP Header 保持一致。
 */
public class GrpcMetadataUtil {

    private static final ThreadLocal<String> USER_ID_HOLDER = new ThreadLocal<>();
    private static final ThreadLocal<String> USER_NAME_HOLDER = new ThreadLocal<>();

    public static final Metadata.Key<String> USER_ID_KEY =
            Metadata.Key.of("x-adhoc-user-id", Metadata.ASCII_STRING_MARSHALLER);

    public static final Metadata.Key<String> USER_NAME_KEY =
            Metadata.Key.of("x-adhoc-user-name", Metadata.ASCII_STRING_MARSHALLER);

    /**
     * Server 端拦截器：从 gRPC Metadata 提取用户身份存入 ThreadLocal。
     * 注意：必须在请求处理完成后清理 ThreadLocal，避免内存泄漏。
     */
    public static class ServerMetadataInterceptor implements ServerInterceptor {

        @Override
        public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
                ServerCall<ReqT, RespT> call,
                Metadata headers,
                ServerCallHandler<ReqT, RespT> next) {

            String userId = headers.get(USER_ID_KEY);
            String userName = headers.get(USER_NAME_KEY);

            // 设置 ThreadLocal
            USER_ID_HOLDER.set(userId);
            USER_NAME_HOLDER.set(userName);

            // 使用 Context 的 onClose 回调在请求完成后清理
            Context context = Context.current();
            context.addListener(context1 -> {
                USER_ID_HOLDER.remove();
                USER_NAME_HOLDER.remove();
            }, Runnable::run);

            return next.startCall(call, headers);
        }
    }

    /**
     * 获取当前请求的用户 ID。
     *
     * @return 用户 ID（必填）
     */
    public static String getUserId() {
        return USER_ID_HOLDER.get();
    }

    /**
     * 获取当前请求的用户名。
     *
     * @return 用户名（选填）
     */
    public static String getUserName() {
        return USER_NAME_HOLDER.get();
    }

    /**
     * 清理 ThreadLocal（一般在请求结束后由拦截器自动调用）。
     */
    public static void clear() {
        USER_ID_HOLDER.remove();
        USER_NAME_HOLDER.remove();
    }
}