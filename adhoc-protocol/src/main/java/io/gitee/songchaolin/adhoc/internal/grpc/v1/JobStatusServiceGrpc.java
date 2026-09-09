package io.gitee.songchaolin.adhoc.internal.grpc.v1;

import static io.grpc.MethodDescriptor.generateFullMethodName;

/**
 */
@javax.annotation.Generated(
    value = "by gRPC proto compiler (version 1.54.0)",
    comments = "Source: internal/status_report.proto")
@io.grpc.stub.annotations.GrpcGenerated
public final class JobStatusServiceGrpc {

  private JobStatusServiceGrpc() {}

  public static final String SERVICE_NAME = "adhoc.internal.v1.JobStatusService";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<io.gitee.songchaolin.adhoc.internal.grpc.v1.ReportJobStatusRequest,
      io.gitee.songchaolin.adhoc.internal.grpc.v1.ReportJobStatusResponse> getReportJobStatusMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "reportJobStatus",
      requestType = io.gitee.songchaolin.adhoc.internal.grpc.v1.ReportJobStatusRequest.class,
      responseType = io.gitee.songchaolin.adhoc.internal.grpc.v1.ReportJobStatusResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<io.gitee.songchaolin.adhoc.internal.grpc.v1.ReportJobStatusRequest,
      io.gitee.songchaolin.adhoc.internal.grpc.v1.ReportJobStatusResponse> getReportJobStatusMethod() {
    io.grpc.MethodDescriptor<io.gitee.songchaolin.adhoc.internal.grpc.v1.ReportJobStatusRequest, io.gitee.songchaolin.adhoc.internal.grpc.v1.ReportJobStatusResponse> getReportJobStatusMethod;
    if ((getReportJobStatusMethod = JobStatusServiceGrpc.getReportJobStatusMethod) == null) {
      synchronized (JobStatusServiceGrpc.class) {
        if ((getReportJobStatusMethod = JobStatusServiceGrpc.getReportJobStatusMethod) == null) {
          JobStatusServiceGrpc.getReportJobStatusMethod = getReportJobStatusMethod =
              io.grpc.MethodDescriptor.<io.gitee.songchaolin.adhoc.internal.grpc.v1.ReportJobStatusRequest, io.gitee.songchaolin.adhoc.internal.grpc.v1.ReportJobStatusResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "reportJobStatus"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  io.gitee.songchaolin.adhoc.internal.grpc.v1.ReportJobStatusRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  io.gitee.songchaolin.adhoc.internal.grpc.v1.ReportJobStatusResponse.getDefaultInstance()))
              .setSchemaDescriptor(new JobStatusServiceMethodDescriptorSupplier("reportJobStatus"))
              .build();
        }
      }
    }
    return getReportJobStatusMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static JobStatusServiceStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<JobStatusServiceStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<JobStatusServiceStub>() {
        @java.lang.Override
        public JobStatusServiceStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new JobStatusServiceStub(channel, callOptions);
        }
      };
    return JobStatusServiceStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static JobStatusServiceBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<JobStatusServiceBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<JobStatusServiceBlockingStub>() {
        @java.lang.Override
        public JobStatusServiceBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new JobStatusServiceBlockingStub(channel, callOptions);
        }
      };
    return JobStatusServiceBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static JobStatusServiceFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<JobStatusServiceFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<JobStatusServiceFutureStub>() {
        @java.lang.Override
        public JobStatusServiceFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new JobStatusServiceFutureStub(channel, callOptions);
        }
      };
    return JobStatusServiceFutureStub.newStub(factory, channel);
  }

  /**
   */
  public interface AsyncService {

    /**
     */
    default void reportJobStatus(io.gitee.songchaolin.adhoc.internal.grpc.v1.ReportJobStatusRequest request,
        io.grpc.stub.StreamObserver<io.gitee.songchaolin.adhoc.internal.grpc.v1.ReportJobStatusResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getReportJobStatusMethod(), responseObserver);
    }
  }

  /**
   * Base class for the server implementation of the service JobStatusService.
   */
  public static abstract class JobStatusServiceImplBase
      implements io.grpc.BindableService, AsyncService {

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return JobStatusServiceGrpc.bindService(this);
    }
  }

  /**
   * A stub to allow clients to do asynchronous rpc calls to service JobStatusService.
   */
  public static final class JobStatusServiceStub
      extends io.grpc.stub.AbstractAsyncStub<JobStatusServiceStub> {
    private JobStatusServiceStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected JobStatusServiceStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new JobStatusServiceStub(channel, callOptions);
    }

    /**
     */
    public void reportJobStatus(io.gitee.songchaolin.adhoc.internal.grpc.v1.ReportJobStatusRequest request,
        io.grpc.stub.StreamObserver<io.gitee.songchaolin.adhoc.internal.grpc.v1.ReportJobStatusResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getReportJobStatusMethod(), getCallOptions()), request, responseObserver);
    }
  }

  /**
   * A stub to allow clients to do synchronous rpc calls to service JobStatusService.
   */
  public static final class JobStatusServiceBlockingStub
      extends io.grpc.stub.AbstractBlockingStub<JobStatusServiceBlockingStub> {
    private JobStatusServiceBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected JobStatusServiceBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new JobStatusServiceBlockingStub(channel, callOptions);
    }

    /**
     */
    public io.gitee.songchaolin.adhoc.internal.grpc.v1.ReportJobStatusResponse reportJobStatus(io.gitee.songchaolin.adhoc.internal.grpc.v1.ReportJobStatusRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getReportJobStatusMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do ListenableFuture-style rpc calls to service JobStatusService.
   */
  public static final class JobStatusServiceFutureStub
      extends io.grpc.stub.AbstractFutureStub<JobStatusServiceFutureStub> {
    private JobStatusServiceFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected JobStatusServiceFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new JobStatusServiceFutureStub(channel, callOptions);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<io.gitee.songchaolin.adhoc.internal.grpc.v1.ReportJobStatusResponse> reportJobStatus(
        io.gitee.songchaolin.adhoc.internal.grpc.v1.ReportJobStatusRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getReportJobStatusMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_REPORT_JOB_STATUS = 0;

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
        case METHODID_REPORT_JOB_STATUS:
          serviceImpl.reportJobStatus((io.gitee.songchaolin.adhoc.internal.grpc.v1.ReportJobStatusRequest) request,
              (io.grpc.stub.StreamObserver<io.gitee.songchaolin.adhoc.internal.grpc.v1.ReportJobStatusResponse>) responseObserver);
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
          getReportJobStatusMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              io.gitee.songchaolin.adhoc.internal.grpc.v1.ReportJobStatusRequest,
              io.gitee.songchaolin.adhoc.internal.grpc.v1.ReportJobStatusResponse>(
                service, METHODID_REPORT_JOB_STATUS)))
        .build();
  }

  private static abstract class JobStatusServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    JobStatusServiceBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return io.gitee.songchaolin.adhoc.internal.grpc.v1.StatusReport.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("JobStatusService");
    }
  }

  private static final class JobStatusServiceFileDescriptorSupplier
      extends JobStatusServiceBaseDescriptorSupplier {
    JobStatusServiceFileDescriptorSupplier() {}
  }

  private static final class JobStatusServiceMethodDescriptorSupplier
      extends JobStatusServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final String methodName;

    JobStatusServiceMethodDescriptorSupplier(String methodName) {
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
      synchronized (JobStatusServiceGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new JobStatusServiceFileDescriptorSupplier())
              .addMethod(getReportJobStatusMethod())
              .build();
        }
      }
    }
    return result;
  }
}
