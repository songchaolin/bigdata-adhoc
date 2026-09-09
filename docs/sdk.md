# Java SDK 使用指南

`adhoc-api-sdk` 提供 HTTP / gRPC 双协议的 Java 客户端，封装了鉴权头注入（`X-Adhoc-User-Id` / `X-Adhoc-User-Name`，gRPC 走 metadata `x-adhoc-user-id`）、统一响应 `Result` 解包、超时与重试，调用方拿到的是直接可用的业务 DTO。

## 目录

- [依赖引入](#依赖引入)
- [构建客户端](#构建客户端)
- [接口方法](#接口方法)
- [典型使用流程](#典型使用流程)
- [错误处理](#错误处理)
- [与 REST 接口的对应关系](#与-rest-接口的对应关系)

## 依赖引入

```xml
<dependency>
    <groupId>io.gitee.songchaolin</groupId>
    <artifactId>adhoc-api-sdk</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

SDK 依赖 `adhoc-common`（公共 DTO）与 `adhoc-protocol`（gRPC stub），随传递依赖自动引入，无需额外声明。

## 构建客户端

通过 `AdhocClientBuilder` 构建，`AdhocClient` 实现 `AutoCloseable`，建议 try-with-resources 或在应用关闭时调用 `close()` 释放连接资源。

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `endpoint` | 必填（HTTP） | HTTP 端点（含 context-path），如 `http://server:8080` |
| `grpcEndpoint` | 必填（gRPC） | gRPC 端点，如 `server:9090` |
| `protocol` | 必填 | `Protocol.HTTP` 或 `Protocol.GRPC` |
| `connectTimeout` | 3000 | 连接超时（毫秒） |
| `readTimeout` | 30000 | 读超时（毫秒） |
| `maxRetries` | 3 | IO 失败重试次数（仅 HTTP 实现） |
| `retryBackoffMillis` | 1000 | 重试指数退避起始值（毫秒，1s → 2s → 4s） |

```java
// HTTP 协议
AdhocClient client = AdhocClientBuilder.builder()
        .endpoint("http://adhoc-server:8080")
        .protocol(Protocol.HTTP)
        .connectTimeout(3000)
        .readTimeout(30000)
        .build();

// gRPC 协议（连 server 的 gRPC 端口，默认 9090）
AdhocClient client = AdhocClientBuilder.builder()
        .grpcEndpoint("adhoc-server:9090")
        .protocol(Protocol.GRPC)
        .build();
```

## 接口方法

| 方法 | 签名 | 说明 |
|------|------|------|
| 提交 Job | `JobSubmitResponse submitJob(JobSubmitRequest request)` | 请求体含 `sqlContent`、`engineType`、`userId`、`userName`、可选 `clientRequestId` / `engineInstance` / `fileId` |
| Job 详情 | `JobDetailResponse getJob(String jobId, String userId)` | 含 Task 摘要列表 |
| Job 状态 | `JobStatusResponse getJobStatus(String jobId, String userId)` | 轮询用，轻量 |
| 取消 Job | `boolean cancelJob(String jobId, String userId)` | 返回是否取消成功 |
| Task 结果 | `ResultResponse getTaskResult(String taskId, long current, long size, String userId)` | 结果分页（页码从 1 起） |
| Job 结果聚合 | `JobResultResponse getJobResult(String jobId, long size, String userId)` | 所有 Task 的第一页 |
| Job 日志 | `LogResponse getJobLog(String jobId, long offset, int limit, String userId)` | 日志分页（行偏移） |
| 关闭 | `void close()` | 释放连接资源 |

用户身份说明：HTTP 实现在 `submitJob` 时同时携带 `X-Adhoc-User-Id` / `X-Adhoc-User-Name`（取自请求体的 `userId`/`userName`），其余方法只带 `X-Adhoc-User-Id`（取自方法入参）；gRPC 实现同理走 metadata。

## 典型使用流程

完整生命周期：提交 → 轮询状态到终态 → 取结果 → 取日志。与模块自带示例 `AdhocClientDemo`（`adhoc-api-sdk/src/test/java/.../sdk/AdhocClientDemo.java`）一致：

```java
AdhocClient client = AdhocClientBuilder.builder()
        .endpoint("http://adhoc-server:8080")
        .protocol(Protocol.HTTP)
        .build();

try {
    String userId = "user123";

    // 1. 提交
    JobSubmitRequest request = new JobSubmitRequest();
    request.setSqlContent("SELECT * FROM dwd_order LIMIT 10");
    request.setEngineType("KYUUBI");
    request.setUserId(userId);
    request.setUserName("张三");
    // request.setEngineInstance("kyuubi-02"); // 可选：指定引擎实例，不设则用默认实例
    String jobId = client.submitJob(request).getJobId();

    // 2. 轮询状态直到终态（SUCCESS/FAILED/PARTIAL_FAILED/CANCELED）
    String status;
    do {
        Thread.sleep(2000);
        status = client.getJobStatus(jobId, userId).getStatus();
    } while (!JobStatus.isTerminal(status));

    // 3. 取结果：先拿 Task 列表，再逐个分页取
    if (JobStatus.SUCCESS.is(status) || JobStatus.PARTIAL_FAILED.is(status)) {
        for (TaskSummary task : client.getJob(jobId, userId).getTasks()) {
            if (Boolean.TRUE.equals(task.getHasResultSet())) {
                ResultResponse result = client.getTaskResult(task.getTaskId(), 1, 100, userId);
                // result.getSchema()：列定义；result.getRows()：JSON 字符串行
            }
        }
    }

    // 4. 取日志
    LogResponse log = client.getJobLog(jobId, 0, 1000, userId);
    // log.getLines()：日志行；log.isComplete()：是否已完整（终态刷盘）
} finally {
    client.close();
}
```

## 错误处理

所有业务与 IO 失败统一抛 `AdhocClientException`（RuntimeException），通过 `getErrorCode()` 拿机器可读标识：

| 场景 | 行为 |
|------|------|
| 业务错误（`Result.code != 1`） | 抛 `AdhocClientException`，errorCode 为 `ADHOC_SERVER_ERROR`，message 含服务端错误码前缀（如 `ADHOC_JOB_NOT_FOUND: Job 不存在或已失效`），**不重试** |
| gRPC 响应 error 字段非空 | 同上，errorCode / errorMessage 取自响应 |
| HTTP IO 异常（连接失败等） | 按指数退避重试（1s → 2s → 4s），超过 `maxRetries` 次后抛 `ADHOC_RETRY_EXHAUSTED` |
| 其他未预期异常 | 包装为 `ADHOC_SDK_ERROR` 抛出 |

```java
try {
    client.submitJob(request);
} catch (AdhocClientException e) {
    if (AdhocErrorCode.ADHOC_JOB_LIMIT_EXCEEDED.name().startsWith(e.getErrorCode())
            || e.getMessage().contains(AdhocErrorCode.ADHOC_JOB_LIMIT_EXCEEDED.name())) {
        // 限流：稍后重试
    }
}
```

服务端业务错误码全集见 [接口文档 - 错误码](api/README.md#错误码)。

## 与 REST 接口的对应关系

SDK 方法与 REST 端点一一对应（`submitJob` ↔ `POST /api/job`、`getJobStatus` ↔ `POST /api/job/status` 等），协议细节与字段说明见 [接口文档](api/README.md)；gRPC 协议定义见 `adhoc-protocol/src/main/proto/sdk/adhoc_service.proto`。

SDK 未覆盖的接口（文件节点、元数据补全、进度时间线等）请直接调 REST API。
