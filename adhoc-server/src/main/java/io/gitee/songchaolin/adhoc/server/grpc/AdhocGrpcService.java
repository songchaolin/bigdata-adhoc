package io.gitee.songchaolin.adhoc.server.grpc;

import io.gitee.songchaolin.adhoc.common.dto.*;
import io.gitee.songchaolin.adhoc.common.dto.request.JobSubmitRequest;
import io.gitee.songchaolin.adhoc.common.dto.request.ResultRequest;
import io.gitee.songchaolin.adhoc.sdk.grpc.v1.*;
import io.gitee.songchaolin.adhoc.server.service.JobQueryService;
import io.gitee.songchaolin.adhoc.server.service.JobService;
import io.gitee.songchaolin.adhoc.server.service.LogQueryService;
import io.gitee.songchaolin.adhoc.server.service.ResultQueryService;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.stream.Collectors;

/**
 * SDK gRPC 服务实现，委托给 JobService、JobQueryService、ResultQueryService、LogQueryService。
 * 用户身份从 gRPC Metadata 提取（通过 {@link GrpcMetadataUtil}）。
 */
@GrpcService
public class AdhocGrpcService extends AdhocServiceGrpc.AdhocServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(AdhocGrpcService.class);

    private final JobService jobService;
    private final JobQueryService jobQueryService;
    private final ResultQueryService resultQueryService;
    private final LogQueryService logQueryService;

    public AdhocGrpcService(JobService jobService,
                            JobQueryService jobQueryService,
                            ResultQueryService resultQueryService,
                            LogQueryService logQueryService) {
        this.jobService = jobService;
        this.jobQueryService = jobQueryService;
        this.resultQueryService = resultQueryService;
        this.logQueryService = logQueryService;
    }

    // ==================== Job 提交与查询 ====================

    @Override
    public void submitJob(SubmitJobRequest request,
                          StreamObserver<SubmitJobResponse> responseObserver) {
        try {
            String userId = GrpcMetadataUtil.getUserId();
            String userName = GrpcMetadataUtil.getUserName();

            JobSubmitRequest submitRequest = new JobSubmitRequest();
            submitRequest.setSqlContent(request.getSqlContent());
            submitRequest.setEngineType(request.getEngineType());
            submitRequest.setClientRequestId(emptyToNull(request.getClientRequestId()));
            submitRequest.setFileId(emptyToNull(request.getFileId()));
            submitRequest.setEngineInstance(emptyToNull(request.getEngineInstance()));

            JobSubmitResponse response = jobService.submit(submitRequest, userId, userName);

            responseObserver.onNext(SubmitJobResponse.newBuilder()
                    .setJobId(response.getJobId())
                    .build());
            responseObserver.onCompleted();

            log.info("【gRPC submitJob】jobId={} userId={}", response.getJobId(), userId);

        } catch (Exception e) {
            log.error("【gRPC submitJob】error: {}", e.getMessage(), e);
            responseObserver.onError(toStatus(e));
        }
    }

    @Override
    public void getJob(GetJobRequest request,
                       StreamObserver<GetJobResponse> responseObserver) {
        try {
//            String userId = GrpcMetadataUtil.getUserId();
            JobDetailResponse detail = jobQueryService.getJobDetail(request.getJobId(), null);

            GetJobResponse.Builder builder = GetJobResponse.newBuilder()
                    .setJobId(detail.getJobId())
                    .setStatus(detail.getStatus())
                    .setSqlContent(detail.getSqlContent())
                    .setEngineType(detail.getEngineType())
                    .setSubmitTime(detail.getSubmitTime().getTime())
                    .addAllTasks(detail.getTasks().stream()
                            .map(this::toTaskSummary)
                            .collect(Collectors.toList()));

            responseObserver.onNext(builder.build());
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("【gRPC getJob】jobId={} error: {}", request.getJobId(), e.getMessage(), e);
            responseObserver.onError(toStatus(e));
        }
    }

    @Override
    public void getJobStatus(GetJobStatusRequest request,
                             StreamObserver<GetJobStatusResponse> responseObserver) {
        try {
            JobStatusResponse status = jobQueryService.getJobStatus(request.getJobId(), null);

            responseObserver.onNext(GetJobStatusResponse.newBuilder()
                    .setJobId(status.getJobId())
                    .setStatus(status.getStatus())
                    .build());
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("【gRPC getJobStatus】jobId={} error: {}", request.getJobId(), e.getMessage(), e);
            responseObserver.onError(toStatus(e));
        }
    }

    @Override
    public void cancelJob(CancelJobRequest request,
                          StreamObserver<CancelJobResponse> responseObserver) {
        try {
            String userId = GrpcMetadataUtil.getUserId();
            boolean canceled = jobService.cancel(request.getJobId(), userId);

            responseObserver.onNext(CancelJobResponse.newBuilder()
                    .setCanceled(canceled)
                    .build());
            responseObserver.onCompleted();

            log.info("【gRPC cancelJob】jobId={} userId={}", request.getJobId(), userId);

        } catch (Exception e) {
            log.error("【gRPC cancelJob】jobId={} error: {}", request.getJobId(), e.getMessage(), e);
            responseObserver.onError(toStatus(e));
        }
    }

    // ==================== 结果与日志 ====================

    @Override
    public void getTaskResult(GetTaskResultRequest request,
                               StreamObserver<GetTaskResultResponse> responseObserver) {
        try {
            ResultRequest resultRequest = new ResultRequest();
            resultRequest.setTaskId(request.getTaskId());
            resultRequest.setCurrent(request.getCurrent());
            resultRequest.setSize(request.getSize());

            ResultResponse result = resultQueryService.getResult(resultRequest, null);

            GetTaskResultResponse.Builder builder = GetTaskResultResponse.newBuilder()
                    .addAllSchema(result.getSchema().stream()
                            .map(this::toColumnSchema)
                            .collect(Collectors.toList()))
                    .addAllRows(result.getRows())
                    .setCurrent(result.getCurrent())
                    .setSize(result.getSize())
                    .setTotalRows(result.getTotalRows())
                    .setHasMore(result.isHasMore());

            responseObserver.onNext(builder.build());
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("【gRPC getTaskResult】taskId={} error: {}", request.getTaskId(), e.getMessage(), e);
            responseObserver.onError(toStatus(e));
        }
    }

    @Override
    public void getJobResult(GetJobResultRequest request,
                             StreamObserver<GetJobResultResponse> responseObserver) {
        try {
            JobResultResponse result = resultQueryService.getJobResult(request.getJobId(), (int) request.getSize(), null);

            GetJobResultResponse.Builder builder = GetJobResultResponse.newBuilder()
                    .addAllTaskResults(result.getTasks().stream()
                            .map(this::toTaskResult)
                            .collect(Collectors.toList()));

            responseObserver.onNext(builder.build());
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("【gRPC getJobResult】jobId={} error: {}", request.getJobId(), e.getMessage(), e);
            responseObserver.onError(toStatus(e));
        }
    }

    @Override
    public void getJobLog(GetJobLogRequest request,
                          StreamObserver<GetJobLogResponse> responseObserver) {
        try {
            LogResponse logResp = logQueryService.getJobLog(request.getJobId(), request.getOffset(), request.getLimit(), null);

            GetJobLogResponse.Builder builder = GetJobLogResponse.newBuilder()
                    .addAllLines(logResp.getLines())
                    .setNextOffset(logResp.getOffset() + logResp.getLines().size())
                    .setHasMore(logResp.isHasMore())
                    .setComplete(logResp.isComplete());

            responseObserver.onNext(builder.build());
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("【gRPC getJobLog】jobId={} error: {}", request.getJobId(), e.getMessage(), e);
            responseObserver.onError(toStatus(e));
        }
    }

    // ==================== 辅助方法 ====================

    private io.gitee.songchaolin.adhoc.sdk.grpc.v1.TaskSummary toTaskSummary(io.gitee.songchaolin.adhoc.common.dto.TaskSummary summary) {
        return io.gitee.songchaolin.adhoc.sdk.grpc.v1.TaskSummary.newBuilder()
                .setTaskId(summary.getTaskId())
                .setSegmentIndex(summary.getSegmentIndex())
                .setSqlContent(summary.getSqlContent() != null ? summary.getSqlContent() : "")
                .setSqlType(summary.getSqlType() != null ? summary.getSqlType() : "")
                .setStatus(summary.getStatus() != null ? summary.getStatus() : "")
                .setResultRows(summary.getResultRows() != null ? summary.getResultRows() : 0L)
                .setResultBytes(0) // TaskSummary 暂无 resultBytes 字段，设为 0
                .setHasResultSet(Boolean.TRUE.equals(summary.getHasResultSet())) // 添加 hasResultSet
                .build();
    }

    private ColumnSchema toColumnSchema(ResultResponse.ColumnDto col) {
        return ColumnSchema.newBuilder()
                .setColIndex(col.getColIndex())
                .setColName(col.getColName())
                .setColType(col.getColType())
                .build();
    }

    private TaskResult toTaskResult(TaskResultItem item) {
        TaskResult.Builder builder = TaskResult.newBuilder()
                .setTaskId(item.getTaskId())
                .setSegmentIndex(item.getSegmentIndex());

        if (item.getSchema() != null) {
            builder.addAllSchema(item.getSchema().stream()
                    .map(this::toColumnSchema)
                    .collect(Collectors.toList()));
        }

        if (item.getRows() != null) {
            builder.addAllRows(item.getRows());
        }

        if (item.getTotalRows() != null) {
            builder.setTotalRows(item.getTotalRows());
        }

        if (item.getHasMore() != null) {
            builder.setHasMore(item.getHasMore());
        }

        return builder.build();
    }

    private String emptyToNull(String s) {
        return (s == null || s.isEmpty()) ? null : s;
    }

    private StatusRuntimeException toStatus(Exception e) {
        if (e instanceof io.gitee.songchaolin.adhoc.common.exception.AdhocException) {
            io.gitee.songchaolin.adhoc.common.exception.AdhocException adhocEx =
                    (io.gitee.songchaolin.adhoc.common.exception.AdhocException) e;
            return Status.INVALID_ARGUMENT
                    .withDescription(adhocEx.getErrorCode().name() + ": " + adhocEx.getMessage())
                    .asRuntimeException();
        }
        return Status.INTERNAL
                .withDescription(e.getMessage())
                .asRuntimeException();
    }
}