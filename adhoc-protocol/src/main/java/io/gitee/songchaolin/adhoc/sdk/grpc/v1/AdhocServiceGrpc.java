package io.gitee.songchaolin.adhoc.sdk.grpc.v1;

import static io.grpc.MethodDescriptor.generateFullMethodName;

/**
 * <pre>
 **
 * SDK -&gt; server gRPC 服务。
 * 用户身份通过 gRPC Metadata 传递：
 * - x-adhoc-user-id: 用户 ID（必填）
 * - x-adhoc-user-name: 用户名（选填）
 * </pre>
 */
@javax.annotation.Generated(
    value = "by gRPC proto compiler (version 1.54.0)",
    comments = "Source: sdk/adhoc_service.proto")
@io.grpc.stub.annotations.GrpcGenerated
public final class AdhocServiceGrpc {

  private AdhocServiceGrpc() {}

  public static final String SERVICE_NAME = "adhoc.sdk.v1.AdhocService";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<io.gitee.songchaolin.adhoc.sdk.grpc.v1.SubmitJobRequest,
      io.gitee.songchaolin.adhoc.sdk.grpc.v1.SubmitJobResponse> getSubmitJobMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "submitJob",
      requestType = io.gitee.songchaolin.adhoc.sdk.grpc.v1.SubmitJobRequest.class,
      responseType = io.gitee.songchaolin.adhoc.sdk.grpc.v1.SubmitJobResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<io.gitee.songchaolin.adhoc.sdk.grpc.v1.SubmitJobRequest,
      io.gitee.songchaolin.adhoc.sdk.grpc.v1.SubmitJobResponse> getSubmitJobMethod() {
    io.grpc.MethodDescriptor<io.gitee.songchaolin.adhoc.sdk.grpc.v1.SubmitJobRequest, io.gitee.songchaolin.adhoc.sdk.grpc.v1.SubmitJobResponse> getSubmitJobMethod;
    if ((getSubmitJobMethod = AdhocServiceGrpc.getSubmitJobMethod) == null) {
      synchronized (AdhocServiceGrpc.class) {
        if ((getSubmitJobMethod = AdhocServiceGrpc.getSubmitJobMethod) == null) {
          AdhocServiceGrpc.getSubmitJobMethod = getSubmitJobMethod =
              io.grpc.MethodDescriptor.<io.gitee.songchaolin.adhoc.sdk.grpc.v1.SubmitJobRequest, io.gitee.songchaolin.adhoc.sdk.grpc.v1.SubmitJobResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "submitJob"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  io.gitee.songchaolin.adhoc.sdk.grpc.v1.SubmitJobRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  io.gitee.songchaolin.adhoc.sdk.grpc.v1.SubmitJobResponse.getDefaultInstance()))
              .setSchemaDescriptor(new AdhocServiceMethodDescriptorSupplier("submitJob"))
              .build();
        }
      }
    }
    return getSubmitJobMethod;
  }

  private static volatile io.grpc.MethodDescriptor<io.gitee.songchaolin.adhoc.sdk.grpc.v1.GetJobRequest,
      io.gitee.songchaolin.adhoc.sdk.grpc.v1.GetJobResponse> getGetJobMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "getJob",
      requestType = io.gitee.songchaolin.adhoc.sdk.grpc.v1.GetJobRequest.class,
      responseType = io.gitee.songchaolin.adhoc.sdk.grpc.v1.GetJobResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<io.gitee.songchaolin.adhoc.sdk.grpc.v1.GetJobRequest,
      io.gitee.songchaolin.adhoc.sdk.grpc.v1.GetJobResponse> getGetJobMethod() {
    io.grpc.MethodDescriptor<io.gitee.songchaolin.adhoc.sdk.grpc.v1.GetJobRequest, io.gitee.songchaolin.adhoc.sdk.grpc.v1.GetJobResponse> getGetJobMethod;
    if ((getGetJobMethod = AdhocServiceGrpc.getGetJobMethod) == null) {
      synchronized (AdhocServiceGrpc.class) {
        if ((getGetJobMethod = AdhocServiceGrpc.getGetJobMethod) == null) {
          AdhocServiceGrpc.getGetJobMethod = getGetJobMethod =
              io.grpc.MethodDescriptor.<io.gitee.songchaolin.adhoc.sdk.grpc.v1.GetJobRequest, io.gitee.songchaolin.adhoc.sdk.grpc.v1.GetJobResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "getJob"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  io.gitee.songchaolin.adhoc.sdk.grpc.v1.GetJobRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  io.gitee.songchaolin.adhoc.sdk.grpc.v1.GetJobResponse.getDefaultInstance()))
              .setSchemaDescriptor(new AdhocServiceMethodDescriptorSupplier("getJob"))
              .build();
        }
      }
    }
    return getGetJobMethod;
  }

  private static volatile io.grpc.MethodDescriptor<io.gitee.songchaolin.adhoc.sdk.grpc.v1.GetJobStatusRequest,
      io.gitee.songchaolin.adhoc.sdk.grpc.v1.GetJobStatusResponse> getGetJobStatusMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "getJobStatus",
      requestType = io.gitee.songchaolin.adhoc.sdk.grpc.v1.GetJobStatusRequest.class,
      responseType = io.gitee.songchaolin.adhoc.sdk.grpc.v1.GetJobStatusResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<io.gitee.songchaolin.adhoc.sdk.grpc.v1.GetJobStatusRequest,
      io.gitee.songchaolin.adhoc.sdk.grpc.v1.GetJobStatusResponse> getGetJobStatusMethod() {
    io.grpc.MethodDescriptor<io.gitee.songchaolin.adhoc.sdk.grpc.v1.GetJobStatusRequest, io.gitee.songchaolin.adhoc.sdk.grpc.v1.GetJobStatusResponse> getGetJobStatusMethod;
    if ((getGetJobStatusMethod = AdhocServiceGrpc.getGetJobStatusMethod) == null) {
      synchronized (AdhocServiceGrpc.class) {
        if ((getGetJobStatusMethod = AdhocServiceGrpc.getGetJobStatusMethod) == null) {
          AdhocServiceGrpc.getGetJobStatusMethod = getGetJobStatusMethod =
              io.grpc.MethodDescriptor.<io.gitee.songchaolin.adhoc.sdk.grpc.v1.GetJobStatusRequest, io.gitee.songchaolin.adhoc.sdk.grpc.v1.GetJobStatusResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "getJobStatus"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  io.gitee.songchaolin.adhoc.sdk.grpc.v1.GetJobStatusRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  io.gitee.songchaolin.adhoc.sdk.grpc.v1.GetJobStatusResponse.getDefaultInstance()))
              .setSchemaDescriptor(new AdhocServiceMethodDescriptorSupplier("getJobStatus"))
              .build();
        }
      }
    }
    return getGetJobStatusMethod;
  }

  private static volatile io.grpc.MethodDescriptor<io.gitee.songchaolin.adhoc.sdk.grpc.v1.CancelJobRequest,
      io.gitee.songchaolin.adhoc.sdk.grpc.v1.CancelJobResponse> getCancelJobMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "cancelJob",
      requestType = io.gitee.songchaolin.adhoc.sdk.grpc.v1.CancelJobRequest.class,
      responseType = io.gitee.songchaolin.adhoc.sdk.grpc.v1.CancelJobResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<io.gitee.songchaolin.adhoc.sdk.grpc.v1.CancelJobRequest,
      io.gitee.songchaolin.adhoc.sdk.grpc.v1.CancelJobResponse> getCancelJobMethod() {
    io.grpc.MethodDescriptor<io.gitee.songchaolin.adhoc.sdk.grpc.v1.CancelJobRequest, io.gitee.songchaolin.adhoc.sdk.grpc.v1.CancelJobResponse> getCancelJobMethod;
    if ((getCancelJobMethod = AdhocServiceGrpc.getCancelJobMethod) == null) {
      synchronized (AdhocServiceGrpc.class) {
        if ((getCancelJobMethod = AdhocServiceGrpc.getCancelJobMethod) == null) {
          AdhocServiceGrpc.getCancelJobMethod = getCancelJobMethod =
              io.grpc.MethodDescriptor.<io.gitee.songchaolin.adhoc.sdk.grpc.v1.CancelJobRequest, io.gitee.songchaolin.adhoc.sdk.grpc.v1.CancelJobResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "cancelJob"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  io.gitee.songchaolin.adhoc.sdk.grpc.v1.CancelJobRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  io.gitee.songchaolin.adhoc.sdk.grpc.v1.CancelJobResponse.getDefaultInstance()))
              .setSchemaDescriptor(new AdhocServiceMethodDescriptorSupplier("cancelJob"))
              .build();
        }
      }
    }
    return getCancelJobMethod;
  }

  private static volatile io.grpc.MethodDescriptor<io.gitee.songchaolin.adhoc.sdk.grpc.v1.GetTaskResultRequest,
      io.gitee.songchaolin.adhoc.sdk.grpc.v1.GetTaskResultResponse> getGetTaskResultMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "getTaskResult",
      requestType = io.gitee.songchaolin.adhoc.sdk.grpc.v1.GetTaskResultRequest.class,
      responseType = io.gitee.songchaolin.adhoc.sdk.grpc.v1.GetTaskResultResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<io.gitee.songchaolin.adhoc.sdk.grpc.v1.GetTaskResultRequest,
      io.gitee.songchaolin.adhoc.sdk.grpc.v1.GetTaskResultResponse> getGetTaskResultMethod() {
    io.grpc.MethodDescriptor<io.gitee.songchaolin.adhoc.sdk.grpc.v1.GetTaskResultRequest, io.gitee.songchaolin.adhoc.sdk.grpc.v1.GetTaskResultResponse> getGetTaskResultMethod;
    if ((getGetTaskResultMethod = AdhocServiceGrpc.getGetTaskResultMethod) == null) {
      synchronized (AdhocServiceGrpc.class) {
        if ((getGetTaskResultMethod = AdhocServiceGrpc.getGetTaskResultMethod) == null) {
          AdhocServiceGrpc.getGetTaskResultMethod = getGetTaskResultMethod =
              io.grpc.MethodDescriptor.<io.gitee.songchaolin.adhoc.sdk.grpc.v1.GetTaskResultRequest, io.gitee.songchaolin.adhoc.sdk.grpc.v1.GetTaskResultResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "getTaskResult"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  io.gitee.songchaolin.adhoc.sdk.grpc.v1.GetTaskResultRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  io.gitee.songchaolin.adhoc.sdk.grpc.v1.GetTaskResultResponse.getDefaultInstance()))
              .setSchemaDescriptor(new AdhocServiceMethodDescriptorSupplier("getTaskResult"))
              .build();
        }
      }
    }
    return getGetTaskResultMethod;
  }

  private static volatile io.grpc.MethodDescriptor<io.gitee.songchaolin.adhoc.sdk.grpc.v1.GetJobResultRequest,
      io.gitee.songchaolin.adhoc.sdk.grpc.v1.GetJobResultResponse> getGetJobResultMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "getJobResult",
      requestType = io.gitee.songchaolin.adhoc.sdk.grpc.v1.GetJobResultRequest.class,
      responseType = io.gitee.songchaolin.adhoc.sdk.grpc.v1.GetJobResultResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<io.gitee.songchaolin.adhoc.sdk.grpc.v1.GetJobResultRequest,
      io.gitee.songchaolin.adhoc.sdk.grpc.v1.GetJobResultResponse> getGetJobResultMethod() {
    io.grpc.MethodDescriptor<io.gitee.songchaolin.adhoc.sdk.grpc.v1.GetJobResultRequest, io.gitee.songchaolin.adhoc.sdk.grpc.v1.GetJobResultResponse> getGetJobResultMethod;
    if ((getGetJobResultMethod = AdhocServiceGrpc.getGetJobResultMethod) == null) {
      synchronized (AdhocServiceGrpc.class) {
        if ((getGetJobResultMethod = AdhocServiceGrpc.getGetJobResultMethod) == null) {
          AdhocServiceGrpc.getGetJobResultMethod = getGetJobResultMethod =
              io.grpc.MethodDescriptor.<io.gitee.songchaolin.adhoc.sdk.grpc.v1.GetJobResultRequest, io.gitee.songchaolin.adhoc.sdk.grpc.v1.GetJobResultResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "getJobResult"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  io.gitee.songchaolin.adhoc.sdk.grpc.v1.GetJobResultRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  io.gitee.songchaolin.adhoc.sdk.grpc.v1.GetJobResultResponse.getDefaultInstance()))
              .setSchemaDescriptor(new AdhocServiceMethodDescriptorSupplier("getJobResult"))
              .build();
        }
      }
    }
    return getGetJobResultMethod;
  }

  private static volatile io.grpc.MethodDescriptor<io.gitee.songchaolin.adhoc.sdk.grpc.v1.GetJobLogRequest,
      io.gitee.songchaolin.adhoc.sdk.grpc.v1.GetJobLogResponse> getGetJobLogMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "getJobLog",
      requestType = io.gitee.songchaolin.adhoc.sdk.grpc.v1.GetJobLogRequest.class,
      responseType = io.gitee.songchaolin.adhoc.sdk.grpc.v1.GetJobLogResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<io.gitee.songchaolin.adhoc.sdk.grpc.v1.GetJobLogRequest,
      io.gitee.songchaolin.adhoc.sdk.grpc.v1.GetJobLogResponse> getGetJobLogMethod() {
    io.grpc.MethodDescriptor<io.gitee.songchaolin.adhoc.sdk.grpc.v1.GetJobLogRequest, io.gitee.songchaolin.adhoc.sdk.grpc.v1.GetJobLogResponse> getGetJobLogMethod;
    if ((getGetJobLogMethod = AdhocServiceGrpc.getGetJobLogMethod) == null) {
      synchronized (AdhocServiceGrpc.class) {
        if ((getGetJobLogMethod = AdhocServiceGrpc.getGetJobLogMethod) == null) {
          AdhocServiceGrpc.getGetJobLogMethod = getGetJobLogMethod =
              io.grpc.MethodDescriptor.<io.gitee.songchaolin.adhoc.sdk.grpc.v1.GetJobLogRequest, io.gitee.songchaolin.adhoc.sdk.grpc.v1.GetJobLogResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "getJobLog"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  io.gitee.songchaolin.adhoc.sdk.grpc.v1.GetJobLogRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  io.gitee.songchaolin.adhoc.sdk.grpc.v1.GetJobLogResponse.getDefaultInstance()))
              .setSchemaDescriptor(new AdhocServiceMethodDescriptorSupplier("getJobLog"))
              .build();
        }
      }
    }
    return getGetJobLogMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static AdhocServiceStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<AdhocServiceStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<AdhocServiceStub>() {
        @java.lang.Override
        public AdhocServiceStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new AdhocServiceStub(channel, callOptions);
        }
      };
    return AdhocServiceStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static AdhocServiceBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<AdhocServiceBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<AdhocServiceBlockingStub>() {
        @java.lang.Override
        public AdhocServiceBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new AdhocServiceBlockingStub(channel, callOptions);
        }
      };
    return AdhocServiceBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static AdhocServiceFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<AdhocServiceFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<AdhocServiceFutureStub>() {
        @java.lang.Override
        public AdhocServiceFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new AdhocServiceFutureStub(channel, callOptions);
        }
      };
    return AdhocServiceFutureStub.newStub(factory, channel);
  }

  /**
   * <pre>
   **
   * SDK -&gt; server gRPC 服务。
   * 用户身份通过 gRPC Metadata 传递：
   * - x-adhoc-user-id: 用户 ID（必填）
   * - x-adhoc-user-name: 用户名（选填）
   * </pre>
   */
  public interface AsyncService {

    /**
     * <pre>
     ** 提交查询 Job 
     * </pre>
     */
    default void submitJob(io.gitee.songchaolin.adhoc.sdk.grpc.v1.SubmitJobRequest request,
        io.grpc.stub.StreamObserver<io.gitee.songchaolin.adhoc.sdk.grpc.v1.SubmitJobResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getSubmitJobMethod(), responseObserver);
    }

    /**
     * <pre>
     ** 查询 Job 详情（含 Task 列表） 
     * </pre>
     */
    default void getJob(io.gitee.songchaolin.adhoc.sdk.grpc.v1.GetJobRequest request,
        io.grpc.stub.StreamObserver<io.gitee.songchaolin.adhoc.sdk.grpc.v1.GetJobResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getGetJobMethod(), responseObserver);
    }

    /**
     * <pre>
     ** 查询 Job 状态 
     * </pre>
     */
    default void getJobStatus(io.gitee.songchaolin.adhoc.sdk.grpc.v1.GetJobStatusRequest request,
        io.grpc.stub.StreamObserver<io.gitee.songchaolin.adhoc.sdk.grpc.v1.GetJobStatusResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getGetJobStatusMethod(), responseObserver);
    }

    /**
     * <pre>
     ** 取消 Job 
     * </pre>
     */
    default void cancelJob(io.gitee.songchaolin.adhoc.sdk.grpc.v1.CancelJobRequest request,
        io.grpc.stub.StreamObserver<io.gitee.songchaolin.adhoc.sdk.grpc.v1.CancelJobResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getCancelJobMethod(), responseObserver);
    }

    /**
     * <pre>
     ** 获取 Task 结果分页 
     * </pre>
     */
    default void getTaskResult(io.gitee.songchaolin.adhoc.sdk.grpc.v1.GetTaskResultRequest request,
        io.grpc.stub.StreamObserver<io.gitee.songchaolin.adhoc.sdk.grpc.v1.GetTaskResultResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getGetTaskResultMethod(), responseObserver);
    }

    /**
     * <pre>
     ** 获取 Job 结果聚合（所有 Task 第一页） 
     * </pre>
     */
    default void getJobResult(io.gitee.songchaolin.adhoc.sdk.grpc.v1.GetJobResultRequest request,
        io.grpc.stub.StreamObserver<io.gitee.songchaolin.adhoc.sdk.grpc.v1.GetJobResultResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getGetJobResultMethod(), responseObserver);
    }

    /**
     * <pre>
     ** 获取 Job 日志分页 
     * </pre>
     */
    default void getJobLog(io.gitee.songchaolin.adhoc.sdk.grpc.v1.GetJobLogRequest request,
        io.grpc.stub.StreamObserver<io.gitee.songchaolin.adhoc.sdk.grpc.v1.GetJobLogResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getGetJobLogMethod(), responseObserver);
    }
  }

  /**
   * Base class for the server implementation of the service AdhocService.
   * <pre>
   **
   * SDK -&gt; server gRPC 服务。
   * 用户身份通过 gRPC Metadata 传递：
   * - x-adhoc-user-id: 用户 ID（必填）
   * - x-adhoc-user-name: 用户名（选填）
   * </pre>
   */
  public static abstract class AdhocServiceImplBase
      implements io.grpc.BindableService, AsyncService {

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return AdhocServiceGrpc.bindService(this);
    }
  }

  /**
   * A stub to allow clients to do asynchronous rpc calls to service AdhocService.
   * <pre>
   **
   * SDK -&gt; server gRPC 服务。
   * 用户身份通过 gRPC Metadata 传递：
   * - x-adhoc-user-id: 用户 ID（必填）
   * - x-adhoc-user-name: 用户名（选填）
   * </pre>
   */
  public static final class AdhocServiceStub
      extends io.grpc.stub.AbstractAsyncStub<AdhocServiceStub> {
    private AdhocServiceStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected AdhocServiceStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new AdhocServiceStub(channel, callOptions);
    }

    /**
     * <pre>
     ** 提交查询 Job 
     * </pre>
     */
    public void submitJob(io.gitee.songchaolin.adhoc.sdk.grpc.v1.SubmitJobRequest request,
        io.grpc.stub.StreamObserver<io.gitee.songchaolin.adhoc.sdk.grpc.v1.SubmitJobResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getSubmitJobMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     ** 查询 Job 详情（含 Task 列表） 
     * </pre>
     */
    public void getJob(io.gitee.songchaolin.adhoc.sdk.grpc.v1.GetJobRequest request,
        io.grpc.stub.StreamObserver<io.gitee.songchaolin.adhoc.sdk.grpc.v1.GetJobResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getGetJobMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     ** 查询 Job 状态 
     * </pre>
     */
    public void getJobStatus(io.gitee.songchaolin.adhoc.sdk.grpc.v1.GetJobStatusRequest request,
        io.grpc.stub.StreamObserver<io.gitee.songchaolin.adhoc.sdk.grpc.v1.GetJobStatusResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getGetJobStatusMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     ** 取消 Job 
     * </pre>
     */
    public void cancelJob(io.gitee.songchaolin.adhoc.sdk.grpc.v1.CancelJobRequest request,
        io.grpc.stub.StreamObserver<io.gitee.songchaolin.adhoc.sdk.grpc.v1.CancelJobResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getCancelJobMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     ** 获取 Task 结果分页 
     * </pre>
     */
    public void getTaskResult(io.gitee.songchaolin.adhoc.sdk.grpc.v1.GetTaskResultRequest request,
        io.grpc.stub.StreamObserver<io.gitee.songchaolin.adhoc.sdk.grpc.v1.GetTaskResultResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getGetTaskResultMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     ** 获取 Job 结果聚合（所有 Task 第一页） 
     * </pre>
     */
    public void getJobResult(io.gitee.songchaolin.adhoc.sdk.grpc.v1.GetJobResultRequest request,
        io.grpc.stub.StreamObserver<io.gitee.songchaolin.adhoc.sdk.grpc.v1.GetJobResultResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getGetJobResultMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     ** 获取 Job 日志分页 
     * </pre>
     */
    public void getJobLog(io.gitee.songchaolin.adhoc.sdk.grpc.v1.GetJobLogRequest request,
        io.grpc.stub.StreamObserver<io.gitee.songchaolin.adhoc.sdk.grpc.v1.GetJobLogResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getGetJobLogMethod(), getCallOptions()), request, responseObserver);
    }
  }

  /**
   * A stub to allow clients to do synchronous rpc calls to service AdhocService.
   * <pre>
   **
   * SDK -&gt; server gRPC 服务。
   * 用户身份通过 gRPC Metadata 传递：
   * - x-adhoc-user-id: 用户 ID（必填）
   * - x-adhoc-user-name: 用户名（选填）
   * </pre>
   */
  public static final class AdhocServiceBlockingStub
      extends io.grpc.stub.AbstractBlockingStub<AdhocServiceBlockingStub> {
    private AdhocServiceBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected AdhocServiceBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new AdhocServiceBlockingStub(channel, callOptions);
    }

    /**
     * <pre>
     ** 提交查询 Job 
     * </pre>
     */
    public io.gitee.songchaolin.adhoc.sdk.grpc.v1.SubmitJobResponse submitJob(io.gitee.songchaolin.adhoc.sdk.grpc.v1.SubmitJobRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getSubmitJobMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     ** 查询 Job 详情（含 Task 列表） 
     * </pre>
     */
    public io.gitee.songchaolin.adhoc.sdk.grpc.v1.GetJobResponse getJob(io.gitee.songchaolin.adhoc.sdk.grpc.v1.GetJobRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getGetJobMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     ** 查询 Job 状态 
     * </pre>
     */
    public io.gitee.songchaolin.adhoc.sdk.grpc.v1.GetJobStatusResponse getJobStatus(io.gitee.songchaolin.adhoc.sdk.grpc.v1.GetJobStatusRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getGetJobStatusMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     ** 取消 Job 
     * </pre>
     */
    public io.gitee.songchaolin.adhoc.sdk.grpc.v1.CancelJobResponse cancelJob(io.gitee.songchaolin.adhoc.sdk.grpc.v1.CancelJobRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getCancelJobMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     ** 获取 Task 结果分页 
     * </pre>
     */
    public io.gitee.songchaolin.adhoc.sdk.grpc.v1.GetTaskResultResponse getTaskResult(io.gitee.songchaolin.adhoc.sdk.grpc.v1.GetTaskResultRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getGetTaskResultMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     ** 获取 Job 结果聚合（所有 Task 第一页） 
     * </pre>
     */
    public io.gitee.songchaolin.adhoc.sdk.grpc.v1.GetJobResultResponse getJobResult(io.gitee.songchaolin.adhoc.sdk.grpc.v1.GetJobResultRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getGetJobResultMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     ** 获取 Job 日志分页 
     * </pre>
     */
    public io.gitee.songchaolin.adhoc.sdk.grpc.v1.GetJobLogResponse getJobLog(io.gitee.songchaolin.adhoc.sdk.grpc.v1.GetJobLogRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getGetJobLogMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do ListenableFuture-style rpc calls to service AdhocService.
   * <pre>
   **
   * SDK -&gt; server gRPC 服务。
   * 用户身份通过 gRPC Metadata 传递：
   * - x-adhoc-user-id: 用户 ID（必填）
   * - x-adhoc-user-name: 用户名（选填）
   * </pre>
   */
  public static final class AdhocServiceFutureStub
      extends io.grpc.stub.AbstractFutureStub<AdhocServiceFutureStub> {
    private AdhocServiceFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected AdhocServiceFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new AdhocServiceFutureStub(channel, callOptions);
    }

    /**
     * <pre>
     ** 提交查询 Job 
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<io.gitee.songchaolin.adhoc.sdk.grpc.v1.SubmitJobResponse> submitJob(
        io.gitee.songchaolin.adhoc.sdk.grpc.v1.SubmitJobRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getSubmitJobMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     ** 查询 Job 详情（含 Task 列表） 
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<io.gitee.songchaolin.adhoc.sdk.grpc.v1.GetJobResponse> getJob(
        io.gitee.songchaolin.adhoc.sdk.grpc.v1.GetJobRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getGetJobMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     ** 查询 Job 状态 
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<io.gitee.songchaolin.adhoc.sdk.grpc.v1.GetJobStatusResponse> getJobStatus(
        io.gitee.songchaolin.adhoc.sdk.grpc.v1.GetJobStatusRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getGetJobStatusMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     ** 取消 Job 
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<io.gitee.songchaolin.adhoc.sdk.grpc.v1.CancelJobResponse> cancelJob(
        io.gitee.songchaolin.adhoc.sdk.grpc.v1.CancelJobRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getCancelJobMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     ** 获取 Task 结果分页 
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<io.gitee.songchaolin.adhoc.sdk.grpc.v1.GetTaskResultResponse> getTaskResult(
        io.gitee.songchaolin.adhoc.sdk.grpc.v1.GetTaskResultRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getGetTaskResultMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     ** 获取 Job 结果聚合（所有 Task 第一页） 
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<io.gitee.songchaolin.adhoc.sdk.grpc.v1.GetJobResultResponse> getJobResult(
        io.gitee.songchaolin.adhoc.sdk.grpc.v1.GetJobResultRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getGetJobResultMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     ** 获取 Job 日志分页 
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<io.gitee.songchaolin.adhoc.sdk.grpc.v1.GetJobLogResponse> getJobLog(
        io.gitee.songchaolin.adhoc.sdk.grpc.v1.GetJobLogRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getGetJobLogMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_SUBMIT_JOB = 0;
  private static final int METHODID_GET_JOB = 1;
  private static final int METHODID_GET_JOB_STATUS = 2;
  private static final int METHODID_CANCEL_JOB = 3;
  private static final int METHODID_GET_TASK_RESULT = 4;
  private static final int METHODID_GET_JOB_RESULT = 5;
  private static final int METHODID_GET_JOB_LOG = 6;

  private static final class MethodHandlers<Req, Resp> implements
      io.grpc.stub.ServerCalls.UnaryMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ServerStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ClientStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.BidiStreamingMethod<Req, Resp> {
    private final AsyncService serviceImpl;
    private final int methodId;

    MethodHandlers(AsyncService serviceImpl, int methodId) {
      this.serviceImpl = serviceImpl;
      this.methodId = methodId;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public void invoke(Req request, io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        case METHODID_SUBMIT_JOB:
          serviceImpl.submitJob((io.gitee.songchaolin.adhoc.sdk.grpc.v1.SubmitJobRequest) request,
              (io.grpc.stub.StreamObserver<io.gitee.songchaolin.adhoc.sdk.grpc.v1.SubmitJobResponse>) responseObserver);
          break;
        case METHODID_GET_JOB:
          serviceImpl.getJob((io.gitee.songchaolin.adhoc.sdk.grpc.v1.GetJobRequest) request,
              (io.grpc.stub.StreamObserver<io.gitee.songchaolin.adhoc.sdk.grpc.v1.GetJobResponse>) responseObserver);
          break;
        case METHODID_GET_JOB_STATUS:
          serviceImpl.getJobStatus((io.gitee.songchaolin.adhoc.sdk.grpc.v1.GetJobStatusRequest) request,
              (io.grpc.stub.StreamObserver<io.gitee.songchaolin.adhoc.sdk.grpc.v1.GetJobStatusResponse>) responseObserver);
          break;
        case METHODID_CANCEL_JOB:
          serviceImpl.cancelJob((io.gitee.songchaolin.adhoc.sdk.grpc.v1.CancelJobRequest) request,
              (io.grpc.stub.StreamObserver<io.gitee.songchaolin.adhoc.sdk.grpc.v1.CancelJobResponse>) responseObserver);
          break;
        case METHODID_GET_TASK_RESULT:
          serviceImpl.getTaskResult((io.gitee.songchaolin.adhoc.sdk.grpc.v1.GetTaskResultRequest) request,
              (io.grpc.stub.StreamObserver<io.gitee.songchaolin.adhoc.sdk.grpc.v1.GetTaskResultResponse>) responseObserver);
          break;
        case METHODID_GET_JOB_RESULT:
          serviceImpl.getJobResult((io.gitee.songchaolin.adhoc.sdk.grpc.v1.GetJobResultRequest) request,
              (io.grpc.stub.StreamObserver<io.gitee.songchaolin.adhoc.sdk.grpc.v1.GetJobResultResponse>) responseObserver);
          break;
        case METHODID_GET_JOB_LOG:
          serviceImpl.getJobLog((io.gitee.songchaolin.adhoc.sdk.grpc.v1.GetJobLogRequest) request,
              (io.grpc.stub.StreamObserver<io.gitee.songchaolin.adhoc.sdk.grpc.v1.GetJobLogResponse>) responseObserver);
          break;
        default:
          throw new AssertionError();
      }
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public io.grpc.stub.StreamObserver<Req> invoke(
        io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        default:
          throw new AssertionError();
      }
    }
  }

  public static final io.grpc.ServerServiceDefinition bindService(AsyncService service) {
    return io.grpc.ServerServiceDefinition.builder(getServiceDescriptor())
        .addMethod(
          getSubmitJobMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              io.gitee.songchaolin.adhoc.sdk.grpc.v1.SubmitJobRequest,
              io.gitee.songchaolin.adhoc.sdk.grpc.v1.SubmitJobResponse>(
                service, METHODID_SUBMIT_JOB)))
        .addMethod(
          getGetJobMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              io.gitee.songchaolin.adhoc.sdk.grpc.v1.GetJobRequest,
              io.gitee.songchaolin.adhoc.sdk.grpc.v1.GetJobResponse>(
                service, METHODID_GET_JOB)))
        .addMethod(
          getGetJobStatusMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              io.gitee.songchaolin.adhoc.sdk.grpc.v1.GetJobStatusRequest,
              io.gitee.songchaolin.adhoc.sdk.grpc.v1.GetJobStatusResponse>(
                service, METHODID_GET_JOB_STATUS)))
        .addMethod(
          getCancelJobMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              io.gitee.songchaolin.adhoc.sdk.grpc.v1.CancelJobRequest,
              io.gitee.songchaolin.adhoc.sdk.grpc.v1.CancelJobResponse>(
                service, METHODID_CANCEL_JOB)))
        .addMethod(
          getGetTaskResultMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              io.gitee.songchaolin.adhoc.sdk.grpc.v1.GetTaskResultRequest,
              io.gitee.songchaolin.adhoc.sdk.grpc.v1.GetTaskResultResponse>(
                service, METHODID_GET_TASK_RESULT)))
        .addMethod(
          getGetJobResultMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              io.gitee.songchaolin.adhoc.sdk.grpc.v1.GetJobResultRequest,
              io.gitee.songchaolin.adhoc.sdk.grpc.v1.GetJobResultResponse>(
                service, METHODID_GET_JOB_RESULT)))
        .addMethod(
          getGetJobLogMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              io.gitee.songchaolin.adhoc.sdk.grpc.v1.GetJobLogRequest,
              io.gitee.songchaolin.adhoc.sdk.grpc.v1.GetJobLogResponse>(
                service, METHODID_GET_JOB_LOG)))
        .build();
  }

  private static abstract class AdhocServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    AdhocServiceBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return io.gitee.songchaolin.adhoc.sdk.grpc.v1.AdhocServiceOuterClass.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("AdhocService");
    }
  }

  private static final class AdhocServiceFileDescriptorSupplier
      extends AdhocServiceBaseDescriptorSupplier {
    AdhocServiceFileDescriptorSupplier() {}
  }

  private static final class AdhocServiceMethodDescriptorSupplier
      extends AdhocServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final String methodName;

    AdhocServiceMethodDescriptorSupplier(String methodName) {
      this.methodName = methodName;
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.MethodDescriptor getMethodDescriptor() {
      return getServiceDescriptor().findMethodByName(methodName);
    }
  }

  private static volatile io.grpc.ServiceDescriptor serviceDescriptor;

  public static io.grpc.ServiceDescriptor getServiceDescriptor() {
    io.grpc.ServiceDescriptor result = serviceDescriptor;
    if (result == null) {
      synchronized (AdhocServiceGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new AdhocServiceFileDescriptorSupplier())
              .addMethod(getSubmitJobMethod())
              .addMethod(getGetJobMethod())
              .addMethod(getGetJobStatusMethod())
              .addMethod(getCancelJobMethod())
              .addMethod(getGetTaskResultMethod())
              .addMethod(getGetJobResultMethod())
              .addMethod(getGetJobLogMethod())
              .build();
        }
      }
    }
    return result;
  }
}
