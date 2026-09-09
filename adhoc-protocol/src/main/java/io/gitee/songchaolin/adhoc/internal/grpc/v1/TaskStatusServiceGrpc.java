package io.gitee.songchaolin.adhoc.internal.grpc.v1;

import static io.grpc.MethodDescriptor.generateFullMethodName;

/**
 */
@javax.annotation.Generated(
    value = "by gRPC proto compiler (version 1.54.0)",
    comments = "Source: internal/status_report.proto")
@io.grpc.stub.annotations.GrpcGenerated
public final class TaskStatusServiceGrpc {

  private TaskStatusServiceGrpc() {}

  public static final String SERVICE_NAME = "adhoc.internal.v1.TaskStatusService";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<io.gitee.songchaolin.adhoc.internal.grpc.v1.ReportTaskStatusRequest,
      io.gitee.songchaolin.adhoc.internal.grpc.v1.ReportTaskStatusResponse> getReportTaskStatusMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "reportTaskStatus",
      requestType = io.gitee.songchaolin.adhoc.internal.grpc.v1.ReportTaskStatusRequest.class,
      responseType = io.gitee.songchaolin.adhoc.internal.grpc.v1.ReportTaskStatusResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<io.gitee.songchaolin.adhoc.internal.grpc.v1.ReportTaskStatusRequest,
      io.gitee.songchaolin.adhoc.internal.grpc.v1.ReportTaskStatusResponse> getReportTaskStatusMethod() {
    io.grpc.MethodDescriptor<io.gitee.songchaolin.adhoc.internal.grpc.v1.ReportTaskStatusRequest, io.gitee.songchaolin.adhoc.internal.grpc.v1.ReportTaskStatusResponse> getReportTaskStatusMethod;
    if ((getReportTaskStatusMethod = TaskStatusServiceGrpc.getReportTaskStatusMethod) == null) {
      synchronized (TaskStatusServiceGrpc.class) {
        if ((getReportTaskStatusMethod = TaskStatusServiceGrpc.getReportTaskStatusMethod) == null) {
          TaskStatusServiceGrpc.getReportTaskStatusMethod = getReportTaskStatusMethod =
              io.grpc.MethodDescriptor.<io.gitee.songchaolin.adhoc.internal.grpc.v1.ReportTaskStatusRequest, io.gitee.songchaolin.adhoc.internal.grpc.v1.ReportTaskStatusResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "reportTaskStatus"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  io.gitee.songchaolin.adhoc.internal.grpc.v1.ReportTaskStatusRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  io.gitee.songchaolin.adhoc.internal.grpc.v1.ReportTaskStatusResponse.getDefaultInstance()))
              .setSchemaDescriptor(new TaskStatusServiceMethodDescriptorSupplier("reportTaskStatus"))
              .build();
        }
      }
    }
    return getReportTaskStatusMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static TaskStatusServiceStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<TaskStatusServiceStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<TaskStatusServiceStub>() {
        @java.lang.Override
        public TaskStatusServiceStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new TaskStatusServiceStub(channel, callOptions);
        }
      };
    return TaskStatusServiceStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static TaskStatusServiceBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<TaskStatusServiceBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<TaskStatusServiceBlockingStub>() {
        @java.lang.Override
        public TaskStatusServiceBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new TaskStatusServiceBlockingStub(channel, callOptions);
        }
      };
    return TaskStatusServiceBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static TaskStatusServiceFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<TaskStatusServiceFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<TaskStatusServiceFutureStub>() {
        @java.lang.Override
        public TaskStatusServiceFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new TaskStatusServiceFutureStub(channel, callOptions);
        }
      };
    return TaskStatusServiceFutureStub.newStub(factory, channel);
  }

  /**
   */
  public interface AsyncService {

    /**
     */
    default void reportTaskStatus(io.gitee.songchaolin.adhoc.internal.grpc.v1.ReportTaskStatusRequest request,
        io.grpc.stub.StreamObserver<io.gitee.songchaolin.adhoc.internal.grpc.v1.ReportTaskStatusResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getReportTaskStatusMethod(), responseObserver);
    }
  }

  /**
   * Base class for the server implementation of the service TaskStatusService.
   */
  public static abstract class TaskStatusServiceImplBase
      implements io.grpc.BindableService, AsyncService {

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return TaskStatusServiceGrpc.bindService(this);
    }
  }

  /**
   * A stub to allow clients to do asynchronous rpc calls to service TaskStatusService.
   */
  public static final class TaskStatusServiceStub
      extends io.grpc.stub.AbstractAsyncStub<TaskStatusServiceStub> {
    private TaskStatusServiceStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected TaskStatusServiceStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new TaskStatusServiceStub(channel, callOptions);
    }

    /**
     */
    public void reportTaskStatus(io.gitee.songchaolin.adhoc.internal.grpc.v1.ReportTaskStatusRequest request,
        io.grpc.stub.StreamObserver<io.gitee.songchaolin.adhoc.internal.grpc.v1.ReportTaskStatusResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getReportTaskStatusMethod(), getCallOptions()), request, responseObserver);
    }
  }

  /**
   * A stub to allow clients to do synchronous rpc calls to service TaskStatusService.
   */
  public static final class TaskStatusServiceBlockingStub
      extends io.grpc.stub.AbstractBlockingStub<TaskStatusServiceBlockingStub> {
    private TaskStatusServiceBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected TaskStatusServiceBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new TaskStatusServiceBlockingStub(channel, callOptions);
    }

    /**
     */
    public io.gitee.songchaolin.adhoc.internal.grpc.v1.ReportTaskStatusResponse reportTaskStatus(io.gitee.songchaolin.adhoc.internal.grpc.v1.ReportTaskStatusRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getReportTaskStatusMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do ListenableFuture-style rpc calls to service TaskStatusService.
   */
  public static final class TaskStatusServiceFutureStub
      extends io.grpc.stub.AbstractFutureStub<TaskStatusServiceFutureStub> {
    private TaskStatusServiceFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected TaskStatusServiceFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new TaskStatusServiceFutureStub(channel, callOptions);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<io.gitee.songchaolin.adhoc.internal.grpc.v1.ReportTaskStatusResponse> reportTaskStatus(
        io.gitee.songchaolin.adhoc.internal.grpc.v1.ReportTaskStatusRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getReportTaskStatusMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_REPORT_TASK_STATUS = 0;

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
        case METHODID_REPORT_TASK_STATUS:
          serviceImpl.reportTaskStatus((io.gitee.songchaolin.adhoc.internal.grpc.v1.ReportTaskStatusRequest) request,
              (io.grpc.stub.StreamObserver<io.gitee.songchaolin.adhoc.internal.grpc.v1.ReportTaskStatusResponse>) responseObserver);
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
          getReportTaskStatusMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              io.gitee.songchaolin.adhoc.internal.grpc.v1.ReportTaskStatusRequest,
              io.gitee.songchaolin.adhoc.internal.grpc.v1.ReportTaskStatusResponse>(
                service, METHODID_REPORT_TASK_STATUS)))
        .build();
  }

  private static abstract class TaskStatusServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    TaskStatusServiceBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return io.gitee.songchaolin.adhoc.internal.grpc.v1.StatusReport.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("TaskStatusService");
    }
  }

  private static final class TaskStatusServiceFileDescriptorSupplier
      extends TaskStatusServiceBaseDescriptorSupplier {
    TaskStatusServiceFileDescriptorSupplier() {}
  }

  private static final class TaskStatusServiceMethodDescriptorSupplier
      extends TaskStatusServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final String methodName;

    TaskStatusServiceMethodDescriptorSupplier(String methodName) {
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
      synchronized (TaskStatusServiceGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new TaskStatusServiceFileDescriptorSupplier())
              .addMethod(getReportTaskStatusMethod())
              .build();
        }
      }
    }
    return result;
  }
}
