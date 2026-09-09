package io.gitee.songchaolin.adhoc.sdk.http;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.gitee.songchaolin.adhoc.common.dto.*;
import io.gitee.songchaolin.adhoc.common.dto.request.JobIdRequest;
import io.gitee.songchaolin.adhoc.common.dto.request.JobSubmitRequest;
import io.gitee.songchaolin.adhoc.common.dto.request.ResultRequest;
import io.gitee.songchaolin.adhoc.sdk.AdhocClient;
import io.gitee.songchaolin.adhoc.sdk.AdhocClientException;
import okhttp3.*;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * HTTP 协议 SDK 实现，基于 OkHttp。
 * <p>用户身份通过标准头 {@code X-Adhoc-User-Id} / {@code X-Adhoc-User-Name} 传递，
 * 由 server 端 {@code GatewayUserInterceptor} 解析。submitJob 携带 userId + 显示名，
 * 其余查询接口只携带 userId。
 */
public class HttpAdhocClient implements AdhocClient {

    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json; charset=utf-8");

    /** 用户 ID 标准头，由 server 端 GatewayUserInterceptor 解析 */
    private static final String USER_ID_HEADER = "X-Adhoc-User-Id";

    /** 用户显示名标准头（可选，中文名），由 server 端 GatewayUserInterceptor 解析 */
    private static final String USER_NAME_HEADER = "X-Adhoc-User-Name";

    private final OkHttpClient httpClient;
    private final String endpoint;
    private final ObjectMapper objectMapper;
    private final int maxRetries;
    private final long retryBackoffMillis;

    public HttpAdhocClient(String endpoint, int connectTimeout, int readTimeout,
                           int maxRetries, long retryBackoffMillis) {
        this.endpoint = endpoint;
        this.maxRetries = maxRetries;
        this.retryBackoffMillis = retryBackoffMillis;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(connectTimeout, TimeUnit.MILLISECONDS)
                .readTimeout(readTimeout, TimeUnit.MILLISECONDS)
                .writeTimeout(readTimeout, TimeUnit.MILLISECONDS)
                .retryOnConnectionFailure(true)
                .build();
    }

    // ==================== Job 提交与查询 ====================

    @Override
    public JobSubmitResponse submitJob(JobSubmitRequest request) {
        String url = endpoint + "/api/job";
        return executeWithRetry("submitJob", () -> {
            String json = objectMapper.writeValueAsString(request);
            String userId = request.getUserId() != null ? request.getUserId() : "";
            Request httpRequest = new Request.Builder()
                    .url(url)
                    .post(RequestBody.create(json, JSON_MEDIA_TYPE))
                    .header(USER_ID_HEADER, userId)
                    .header(USER_NAME_HEADER, request.getUserName() != null ? request.getUserName() : "")
                    .build();

            Response response = httpClient.newCall(httpRequest).execute();
            return handleResponse(response, JobSubmitResponse.class);
        });
    }

    @Override
    public JobDetailResponse getJob(String jobId, String userId) {
        String url = endpoint + "/api/job/detail";
        return executeWithRetry("getJob", () -> {
            JobIdRequest request = new JobIdRequest();
            request.setJobId(jobId);
            String json = objectMapper.writeValueAsString(request);
            Request httpRequest = new Request.Builder()
                    .url(url)
                    .post(RequestBody.create(json, JSON_MEDIA_TYPE))
                    .header(USER_ID_HEADER, userId)
                    .build();

            Response response = httpClient.newCall(httpRequest).execute();
            return handleResponse(response, JobDetailResponse.class);
        });
    }

    @Override
    public JobStatusResponse getJobStatus(String jobId, String userId) {
        String url = endpoint + "/api/job/status";
        return executeWithRetry("getJobStatus", () -> {
            JobIdRequest request = new JobIdRequest();
            request.setJobId(jobId);
            String json = objectMapper.writeValueAsString(request);
            Request httpRequest = new Request.Builder()
                    .url(url)
                    .post(RequestBody.create(json, JSON_MEDIA_TYPE))
                    .header(USER_ID_HEADER, userId)
                    .build();

            Response response = httpClient.newCall(httpRequest).execute();
            return handleResponse(response, JobStatusResponse.class);
        });
    }

    @Override
    public boolean cancelJob(String jobId, String userId) {
        String url = endpoint + "/api/job/cancel";
        return executeWithRetry("cancelJob", () -> {
            JobIdRequest request = new JobIdRequest();
            request.setJobId(jobId);
            String json = objectMapper.writeValueAsString(request);
            Request httpRequest = new Request.Builder()
                    .url(url)
                    .post(RequestBody.create(json, JSON_MEDIA_TYPE))
                    .header(USER_ID_HEADER, userId)
                    .build();

            Response response = httpClient.newCall(httpRequest).execute();
            return handleResponse(response, Boolean.class);
        });
    }

    // ==================== 结果与日志 ====================

    @Override
    public ResultResponse getTaskResult(String taskId, long current, long size, String userId) {
        String url = endpoint + "/api/task/result";
        return executeWithRetry("getTaskResult", () -> {
            ResultRequest request = new ResultRequest();
            request.setTaskId(taskId);
            request.setCurrent(current);
            request.setSize(size);
            String json = objectMapper.writeValueAsString(request);
            Request httpRequest = new Request.Builder()
                    .url(url)
                    .post(RequestBody.create(json, JSON_MEDIA_TYPE))
                    .header(USER_ID_HEADER, userId)
                    .build();

            Response response = httpClient.newCall(httpRequest).execute();
            return handleResponse(response, ResultResponse.class);
        });
    }

    @Override
    public JobResultResponse getJobResult(String jobId, long size, String userId) {
        String url = endpoint + "/api/job/result";
        return executeWithRetry("getJobResult", () -> {
            io.gitee.songchaolin.adhoc.common.dto.request.JobResultRequest request =
                    new io.gitee.songchaolin.adhoc.common.dto.request.JobResultRequest();
            request.setJobId(jobId);
            request.setSize((int) size);
            String json = objectMapper.writeValueAsString(request);
            Request httpRequest = new Request.Builder()
                    .url(url)
                    .post(RequestBody.create(json, JSON_MEDIA_TYPE))
                    .header(USER_ID_HEADER, userId)
                    .build();

            Response response = httpClient.newCall(httpRequest).execute();
            return handleResponse(response, JobResultResponse.class);
        });
    }

    @Override
    public LogResponse getJobLog(String jobId, long offset, int limit, String userId) {
        String url = endpoint + "/api/job/log";
        return executeWithRetry("getJobLog", () -> {
            io.gitee.songchaolin.adhoc.common.dto.request.JobLogRequest request =
                    new io.gitee.songchaolin.adhoc.common.dto.request.JobLogRequest();
            request.setJobId(jobId);
            request.setOffset(offset);
            request.setLimit(limit);
            String json = objectMapper.writeValueAsString(request);
            Request httpRequest = new Request.Builder()
                    .url(url)
                    .post(RequestBody.create(json, JSON_MEDIA_TYPE))
                    .header(USER_ID_HEADER, userId)
                    .build();

            Response response = httpClient.newCall(httpRequest).execute();
            return handleResponse(response, LogResponse.class);
        });
    }

    @Override
    public void close() {
        httpClient.dispatcher().executorService().shutdown();
        httpClient.connectionPool().evictAll();
    }

    private <T> T handleResponse(Response response, Class<T> responseType) throws IOException {
        if (!response.isSuccessful()) {
            String errorBody = response.body() != null ? response.body().string() : "";
            throw new AdhocClientException("ADHOC_HTTP_ERROR",
                    "HTTP " + response.code() + ": " + errorBody);
        }

        String body = response.body().string();

        // 处理 Result 包装（server 端用 Result 包裹）
        ResultWrapper result = objectMapper.readValue(body, new TypeReference<ResultWrapper>() {});
        // 统一 Result 格式：成功码=1，失败码!=1
        if (result.code != 1) {
            throw new AdhocClientException("ADHOC_SERVER_ERROR", result.msg);
        }

        if (responseType == Boolean.class) {
            return (T) Boolean.valueOf(result.data.toString());
        }

        return objectMapper.convertValue(result.data, responseType);
    }

    private <T> T executeWithRetry(String operation, ThrowingSupplier<T> supplier) {
        Exception lastException = null;
        for (int i = 0; i <= maxRetries; i++) {
            try {
                return supplier.get();
            } catch (IOException e) {
                lastException = e;
                if (i < maxRetries) {
                    sleep(retryBackoffMillis * (1L << i)); // 指数退避
                }
            } catch (AdhocClientException e) {
                throw e; // 业务错误不重试
            } catch (Exception e) {
                throw new AdhocClientException("ADHOC_SDK_ERROR", operation + " failed: " + e.getMessage(), e);
            }
        }
        throw new AdhocClientException("ADHOC_RETRY_EXHAUSTED",
                operation + " failed after " + maxRetries + " retries", lastException);
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }

    /**
     * Result 包装类（server 端返回格式）
     */
    private static class ResultWrapper {
        public int code;
        public String msg;
        public Object data;
    }
}
