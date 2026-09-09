package io.gitee.songchaolin.adhoc.sdk.grpc;

import io.gitee.songchaolin.adhoc.common.dto.*;
import io.gitee.songchaolin.adhoc.common.dto.request.JobSubmitRequest;
import io.gitee.songchaolin.adhoc.sdk.AdhocClient;
import io.gitee.songchaolin.adhoc.sdk.AdhocClientException;
import io.gitee.songchaolin.adhoc.sdk.grpc.v1.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * gRPC 协议 SDK 实现，基于 gRPC blocking stub。
 * <p>
 * 用户身份通过 gRPC Metadata 传递：
 * - x-adhoc-user-id: 用户 ID（必填）
 * - x-adhoc-user-name: 用户名（选填）
 */
public class GrpcAdhocClient implements AdhocClient {

    private static final String USER_ID_KEY = "x-adhoc-user-id";
    private static final String USER_NAME_KEY = "x-adhoc-user-name";

    private final ManagedChannel channel;
    private final AdhocServiceGrpc.AdhocServiceBlockingStub blockingStub;
    private final int timeoutMillis;

    public GrpcAdhocClient(String grpcEndpoint, int connectTimeout, int readTimeout) {
        String[] parts = grpcEndpoint.split(":");
        String host = parts[0];
        int port = parts.length > 1 ? Integer.parseInt(parts[1]) : 9090;

        this.timeoutMillis = readTimeout;
        this.channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build();

        this.blockingStub = AdhocServiceGrpc.newBlockingStub(channel);
    }

    // ==================== Job 提交与查询 ====================

    @Override
    public JobSubmitResponse submitJob(JobSubmitRequest request) {
        SubmitJobRequest grpcRequest = SubmitJobRequest.newBuilder()
                .setSqlContent(request.getSqlContent() != null ? request.getSqlContent() : "")
                .setEngineType(request.getEngineType() != null ? request.getEngineType() : "")
                .setClientRequestId(request.getClientRequestId() != null ? request.getClientRequestId() : "")
                .setFileId(request.getFileId() != null ? request.getFileId() : "")
                .setEngineInstance(request.getEngineInstance() != null ? request.getEngineInstance() : "")
                .build();

        Metadata metadata = createMetadata(request.getUserId(), request.getUserName());
        AdhocServiceGrpc.AdhocServiceBlockingStub stubWithMetadata = attachMetadata(metadata);

        SubmitJobResponse response = stubWithMetadata.submitJob(grpcRequest);
        handleError(response.getError());

        return new JobSubmitResponse(response.getJobId());
    }

    @Override
    public JobDetailResponse getJob(String jobId, String userId) {
        GetJobRequest grpcRequest = GetJobRequest.newBuilder()
                .setJobId(jobId)
                .build();

        Metadata metadata = createMetadata(userId, null);
        AdhocServiceGrpc.AdhocServiceBlockingStub stubWithMetadata = attachMetadata(metadata);

        GetJobResponse response = stubWithMetadata.getJob(grpcRequest);
        handleError(response.getError());

        JobDetailResponse result = new JobDetailResponse();
        result.setJobId(response.getJobId());
        result.setStatus(response.getStatus());
        result.setSqlContent(response.getSqlContent());
        result.setEngineType(response.getEngineType());
        result.setSubmitTime(response.getSubmitTime() > 0 ? new Date(response.getSubmitTime()) : null);

        List<io.gitee.songchaolin.adhoc.common.dto.TaskSummary> tasks = new ArrayList<>();
        for (io.gitee.songchaolin.adhoc.sdk.grpc.v1.TaskSummary taskSummary : response.getTasksList()) {
            io.gitee.songchaolin.adhoc.common.dto.TaskSummary task = new io.gitee.songchaolin.adhoc.common.dto.TaskSummary();
            task.setTaskId(taskSummary.getTaskId());
            task.setSegmentIndex(taskSummary.getSegmentIndex());
            task.setSqlContent(taskSummary.getSqlContent());
            task.setSqlType(taskSummary.getSqlType());
            task.setStatus(taskSummary.getStatus());
            task.setResultRows(taskSummary.getResultRows());
            task.setHasResultSet(taskSummary.getHasResultSet()); // 添加 hasResultSet
            tasks.add(task);
        }
        result.setTasks(tasks);

        return result;
    }

    @Override
    public JobStatusResponse getJobStatus(String jobId, String userId) {
        GetJobStatusRequest grpcRequest = GetJobStatusRequest.newBuilder()
                .setJobId(jobId)
                .build();

        Metadata metadata = createMetadata(userId, null);
        AdhocServiceGrpc.AdhocServiceBlockingStub stubWithMetadata = attachMetadata(metadata);

        GetJobStatusResponse response = stubWithMetadata.getJobStatus(grpcRequest);
        handleError(response.getError());

        return new JobStatusResponse(response.getJobId(), response.getStatus());
    }

    @Override
    public boolean cancelJob(String jobId, String userId) {
        CancelJobRequest grpcRequest = CancelJobRequest.newBuilder()
                .setJobId(jobId)
                .build();

        Metadata metadata = createMetadata(userId, null);
        AdhocServiceGrpc.AdhocServiceBlockingStub stubWithMetadata = attachMetadata(metadata);

        CancelJobResponse response = stubWithMetadata.cancelJob(grpcRequest);
        handleError(response.getError());

        return response.getCanceled();
    }

    // ==================== 结果与日志 ====================

    @Override
    public ResultResponse getTaskResult(String taskId, long current, long size, String userId) {
        GetTaskResultRequest grpcRequest = GetTaskResultRequest.newBuilder()
                .setTaskId(taskId)
                .setCurrent(current)
                .setSize(size)
                .build();

        Metadata metadata = createMetadata(userId, null);
        AdhocServiceGrpc.AdhocServiceBlockingStub stubWithMetadata = attachMetadata(metadata);

        GetTaskResultResponse response = stubWithMetadata.getTaskResult(grpcRequest);
        handleError(response.getError());

        ResultResponse result = new ResultResponse();
        result.setRows(new ArrayList<>(response.getRowsList()));
        result.setCurrent(response.getCurrent());
        result.setSize(response.getSize());
        result.setTotalRows(response.getTotalRows());
        result.setHasMore(response.getHasMore());

        List<ResultResponse.ColumnDto> schema = new ArrayList<>();
        for (ColumnSchema colSchema : response.getSchemaList()) {
            ResultResponse.ColumnDto col = new ResultResponse.ColumnDto();
            col.setColIndex(colSchema.getColIndex());
            col.setColName(colSchema.getColName());
            col.setColType(colSchema.getColType());
            schema.add(col);
        }
        result.setSchema(schema);

        return result;
    }

    @Override
    public JobResultResponse getJobResult(String jobId, long size, String userId) {
        GetJobResultRequest grpcRequest = GetJobResultRequest.newBuilder()
                .setJobId(jobId)
                .setSize(size)
                .build();

        Metadata metadata = createMetadata(userId, null);
        AdhocServiceGrpc.AdhocServiceBlockingStub stubWithMetadata = attachMetadata(metadata);

        GetJobResultResponse response = stubWithMetadata.getJobResult(grpcRequest);
        handleError(response.getError());

        JobResultResponse result = new JobResultResponse();
        result.setJobId(jobId);
        List<TaskResultItem> taskResults = new ArrayList<>();

        for (TaskResult taskResult : response.getTaskResultsList()) {
            TaskResultItem item = new TaskResultItem();
            item.setTaskId(taskResult.getTaskId());
            item.setSegmentIndex(taskResult.getSegmentIndex());
            item.setRows(new ArrayList<>(taskResult.getRowsList()));
            item.setTotalRows(taskResult.getTotalRows());
            item.setHasMore(taskResult.getHasMore());

            List<ResultResponse.ColumnDto> schema = new ArrayList<>();
            for (ColumnSchema colSchema : taskResult.getSchemaList()) {
                ResultResponse.ColumnDto col = new ResultResponse.ColumnDto();
                col.setColIndex(colSchema.getColIndex());
                col.setColName(colSchema.getColName());
                col.setColType(colSchema.getColType());
                schema.add(col);
            }
            item.setSchema(schema);

            taskResults.add(item);
        }

        result.setTasks(taskResults);
        return result;
    }

    @Override
    public LogResponse getJobLog(String jobId, long offset, int limit, String userId) {
        GetJobLogRequest grpcRequest = GetJobLogRequest.newBuilder()
                .setJobId(jobId)
                .setOffset(offset)
                .setLimit(limit)
                .build();

        Metadata metadata = createMetadata(userId, null);
        AdhocServiceGrpc.AdhocServiceBlockingStub stubWithMetadata = attachMetadata(metadata);

        GetJobLogResponse response = stubWithMetadata.getJobLog(grpcRequest);
        handleError(response.getError());

        LogResponse result = new LogResponse();
        result.setLines(new ArrayList<>(response.getLinesList()));
        result.setOffset(response.getNextOffset());
        result.setLimit(limit);
        result.setHasMore(response.getHasMore());
        result.setComplete(response.getComplete());

        return result;
    }

    @Override
    public void close() {
        if (channel != null) {
            channel.shutdown();
            try {
                if (!channel.awaitTermination(5, TimeUnit.SECONDS)) {
                    channel.shutdownNow();
                }
            } catch (InterruptedException e) {
                channel.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 创建 gRPC Metadata，包含用户身份信息
     */
    private Metadata createMetadata(String userId, String userName) {
        Metadata metadata = new Metadata();
        Metadata.Key<String> userIdKey = Metadata.Key.of(USER_ID_KEY, Metadata.ASCII_STRING_MARSHALLER);
        metadata.put(userIdKey, userId != null ? userId : "");

        if (userName != null) {
            Metadata.Key<String> userNameKey = Metadata.Key.of(USER_NAME_KEY, Metadata.ASCII_STRING_MARSHALLER);
            metadata.put(userNameKey, userName);
        }

        return metadata;
    }

    /**
     * 将 Metadata 附加到 stub
     */
    private AdhocServiceGrpc.AdhocServiceBlockingStub attachMetadata(Metadata metadata) {
        return blockingStub.withInterceptors(new io.grpc.ClientInterceptor() {
            @Override
            public <ReqT, RespT> io.grpc.ClientCall<ReqT, RespT> interceptCall(
                    io.grpc.MethodDescriptor<ReqT, RespT> method,
                    io.grpc.CallOptions callOptions,
                    io.grpc.Channel next) {
                return new io.grpc.ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>(
                        next.newCall(method, callOptions)) {
                    @Override
                    public void start(io.grpc.ClientCall.Listener<RespT> responseListener, io.grpc.Metadata headers) {
                        headers.merge(metadata);
                        super.start(responseListener, headers);
                    }
                };
            }
        }).withDeadlineAfter(timeoutMillis, TimeUnit.MILLISECONDS);
    }

    /**
     * 处理错误响应
     */
    private void handleError(ErrorResponse error) {
        if (error != null && !error.getErrorCode().isEmpty()) {
            throw new AdhocClientException(error.getErrorCode(), error.getErrorMessage());
        }
    }
}