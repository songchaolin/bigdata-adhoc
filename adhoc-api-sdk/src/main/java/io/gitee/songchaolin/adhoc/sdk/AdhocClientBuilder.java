package io.gitee.songchaolin.adhoc.sdk;

import io.gitee.songchaolin.adhoc.sdk.grpc.GrpcAdhocClient;
import io.gitee.songchaolin.adhoc.sdk.http.HttpAdhocClient;

/**
 * SDK 客户端 Builder。
 * <p>
 * 用法示例：
 * <pre>
 * AdhocClient client = AdhocClientBuilder.builder()
 *     .endpoint("http://server:8080/adhoc")
 *     .protocol(Protocol.HTTP)
 *     .connectTimeout(3000)
 *     .readTimeout(30000)
 *     .maxRetries(3)
 *     .build();
 * </pre>
 */
public class AdhocClientBuilder {

    private String endpoint;
    private String grpcEndpoint;
    private Protocol protocol;
    private int connectTimeout = 3000;
    private int readTimeout = 30000;
    private int maxRetries = 3;
    private long retryBackoffMillis = 1000;

    private AdhocClientBuilder() {
    }

    public static AdhocClientBuilder builder() {
        return new AdhocClientBuilder();
    }

    /**
     * 设置 HTTP 端点（HTTP 协议必填）。
     *
     * @param endpoint HTTP 端点（含 context-path），如 http://server:8080/adhoc
     * @return this
     */
    public AdhocClientBuilder endpoint(String endpoint) {
        this.endpoint = endpoint;
        return this;
    }

    /**
     * 设置 gRPC 端点（gRPC 协议必填）。
     *
     * @param grpcEndpoint gRPC 端点，如 server:9090
     * @return this
     */
    public AdhocClientBuilder grpcEndpoint(String grpcEndpoint) {
        this.grpcEndpoint = grpcEndpoint;
        return this;
    }

    /**
     * 设置协议类型（必填）。
     *
     * @param protocol HTTP 或 GRPC
     * @return this
     */
    public AdhocClientBuilder protocol(Protocol protocol) {
        this.protocol = protocol;
        return this;
    }

    /**
     * 设置连接超时（毫秒，默认 3000）。
     *
     * @param connectTimeout 连接超时
     * @return this
     */
    public AdhocClientBuilder connectTimeout(int connectTimeout) {
        this.connectTimeout = connectTimeout;
        return this;
    }

    /**
     * 设置读超时（毫秒，默认 30000）。
     *
     * @param readTimeout 读超时
     * @return this
     */
    public AdhocClientBuilder readTimeout(int readTimeout) {
        this.readTimeout = readTimeout;
        return this;
    }

    /**
     * 设置重试次数（默认 3）。
     *
     * @param maxRetries 重试次数
     * @return this
     */
    public AdhocClientBuilder maxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
        return this;
    }

    /**
     * 设置重试退避起始值（毫秒，默认 1000，指数退避 1s, 2s, 4s）。
     *
     * @param retryBackoffMillis 重试退避起始值
     * @return this
     */
    public AdhocClientBuilder retryBackoffMillis(long retryBackoffMillis) {
        this.retryBackoffMillis = retryBackoffMillis;
        return this;
    }

    /**
     * 构建 AdhocClient 实例。
     *
     * @return AdhocClient 实例
     * @throws IllegalArgumentException 参数校验失败
     */
    public AdhocClient build() {
        validate();

        switch (protocol) {
            case HTTP:
                return new HttpAdhocClient(endpoint, connectTimeout, readTimeout, maxRetries, retryBackoffMillis);
            case GRPC:
                return new GrpcAdhocClient(grpcEndpoint, connectTimeout, readTimeout);
            default:
                throw new IllegalArgumentException("Unsupported protocol: " + protocol);
        }
    }

    private void validate() {
        if (protocol == null) {
            throw new IllegalArgumentException("protocol is required");
        }

        if (protocol == Protocol.HTTP && (endpoint == null || endpoint.isEmpty())) {
            throw new IllegalArgumentException("endpoint is required for HTTP protocol");
        }

        if (protocol == Protocol.GRPC && (grpcEndpoint == null || grpcEndpoint.isEmpty())) {
            throw new IllegalArgumentException("grpcEndpoint is required for GRPC protocol");
        }

        if (connectTimeout <= 0) {
            throw new IllegalArgumentException("connectTimeout must be positive");
        }

        if (readTimeout <= 0) {
            throw new IllegalArgumentException("readTimeout must be positive");
        }

        if (maxRetries < 0) {
            throw new IllegalArgumentException("maxRetries must be non-negative");
        }

        if (retryBackoffMillis <= 0) {
            throw new IllegalArgumentException("retryBackoffMillis must be positive");
        }
    }
}