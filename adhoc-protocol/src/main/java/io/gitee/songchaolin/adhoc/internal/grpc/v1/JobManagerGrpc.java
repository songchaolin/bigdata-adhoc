package io.gitee.songchaolin.adhoc.internal.grpc.v1;

import static io.grpc.MethodDescriptor.generateFullMethodName;

/**
 */
@javax.annotation.Generated(
    value = "by gRPC proto compiler (version 1.54.0)",
    comments = "Source: internal/job_dispatch.proto")
@io.grpc.stub.annotations.GrpcGenerated
public final class JobManagerGrpc {

  private JobManagerGrpc() {}

  public static final String SERVICE_NAME = "adhoc.internal.v1.JobManager";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<io.gitee.songchaolin.adhoc.internal.grpc.v1.CancelJobRequest,
      io.gitee.songchaolin.adhoc.internal.grpc.v1.CancelJobResponse> getCancelJobMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "cancelJob",
      requestType = io.gitee.songchaolin.adhoc.internal.grpc.v1.CancelJobRequest.class,
      responseType = io.gitee.songchaolin.adhoc.internal.grpc.v1.CancelJobResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<io.gitee.songchaolin.adhoc.internal.grpc.v1.CancelJobRequest,
      io.gitee.songchaolin.adhoc.internal.grpc.v1.CancelJobResponse> getCancelJobMethod() {
    io.grpc.MethodDescriptor<io.gitee.songchaolin.adhoc.internal.grpc.v1.CancelJobRequest, io.gitee.songchaolin.adhoc.internal.grpc.v1.CancelJobResponse> getCancelJobMethod;
    if ((getCancelJobMethod = JobManagerGrpc.getCancelJobMethod) == null) {
      synchronized (JobManagerGrpc.class) {
        if ((getCancelJobMethod = JobManagerGrpc.getCancelJobMethod) == null) {
          JobManagerGrpc.getCancelJobMethod = getCancelJobMethod =
              io.grpc.MethodDescriptor.<io.gitee.songchaolin.adhoc.internal.grpc.v1.CancelJobRequest, io.gitee.songchaolin.adhoc.internal.grpc.v1.CancelJobResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "cancelJob"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  io.gitee.songchaolin.adhoc.internal.grpc.v1.CancelJobRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  io.gitee.songchaolin.adhoc.internal.grpc.v1.CancelJobResponse.getDefaultInstance()))
              .setSchemaDescriptor(new JobManagerMethodDescriptorSupplier("cancelJob"))
              .build();
        }
      }
    }
    return getCancelJobMethod;
  }

  private static volatile io.grpc.MethodDescriptor<io.gitee.songchaolin.adhoc.internal.grpc.v1.GetRunningJobsRequest,
      io.gitee.songchaolin.adhoc.internal.grpc.v1.GetRunningJobsResponse> getGetRunningJobsMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "getRunningJobs",
      requestType = io.gitee.songchaolin.adhoc.internal.grpc.v1.GetRunningJobsRequest.class,
      responseType = io.gitee.songchaolin.adhoc.internal.grpc.v1.GetRunningJobsResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<io.gitee.songchaolin.adhoc.internal.grpc.v1.GetRunningJobsRequest,
      io.gitee.songchaolin.adhoc.internal.grpc.v1.GetRunningJobsResponse> getGetRunningJobsMethod() {
    io.grpc.MethodDescriptor<io.gitee.songchaolin.adhoc.internal.grpc.v1.GetRunningJobsRequest, io.gitee.songchaolin.adhoc.internal.grpc.v1.GetRunningJobsResponse> getGetRunningJobsMethod;
    if ((getGetRunningJobsMethod = JobManagerGrpc.getGetRunningJobsMethod) == null) {
      synchronized (JobManagerGrpc.class) {
        if ((getGetRunningJobsMethod = JobManagerGrpc.getGetRunningJobsMethod) == null) {
          JobManagerGrpc.getGetRunningJobsMethod = getGetRunningJobsMethod =
              io.grpc.MethodDescriptor.<io.gitee.songchaolin.adhoc.internal.grpc.v1.GetRunningJobsRequest, io.gitee.songchaolin.adhoc.internal.grpc.v1.GetRunningJobsResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "getRunningJobs"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  io.gitee.songchaolin.adhoc.internal.grpc.v1.GetRunningJobsRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  io.gitee.songchaolin.adhoc.internal.grpc.v1.GetRunningJobsResponse.getDefaultInstance()))
              .setSchemaDescriptor(new JobManagerMethodDescriptorSupplier("getRunningJobs"))
              .build();
        }
      }
    }
    return getGetRunningJobsMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static JobManagerStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<JobManagerStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<JobManagerStub>() {
        @java.lang.Override
        public JobManagerStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new JobManagerStub(channel, callOptions);
        }
      };
    return JobManagerStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static JobManagerBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<JobManagerBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<JobManagerBlockingStub>() {
        @java.lang.Override
        public JobManagerBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new JobManagerBlockingStub(channel, callOptions);
        }
      };
    return JobManagerBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static JobManagerFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<JobManagerFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<JobManagerFutureStub>() {
        @java.lang.Override
        public JobManagerFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new JobManagerFutureStub(channel, callOptions);
        }
      };
    return JobManagerFutureStub.newStub(factory, channel);
  }

  /**
   */
  public interface AsyncService {

    /**
     */
    default void cancelJob(io.gitee.songchaolin.adhoc.internal.grpc.v1.CancelJobRequest request,
        io.grpc.stub.StreamObserver<io.gitee.songchaolin.adhoc.internal.grpc.v1.CancelJobResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getCancelJobMethod(), responseObserver);
    }

    /**
     * <pre>
     * 探查：返回本 executor 当前在跑的 jobId 集合（server 心跳到达时 + reconcile 兜底时调用，清理孤儿 job）
     * </pre>
     */
    default void getRunningJobs(io.gitee.songchaolin.adhoc.internal.grpc.v1.GetRunningJobsRequest request,
        io.grpc.stub.StreamObserver<io.gitee.songchaolin.adhoc.internal.grpc.v1.GetRunningJobsResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getGetRunningJobsMethod(), responseObserver);
    }
  }

  /**
   * Base class for the server implementation of the service JobManager.
   */
  public static abstract class JobManagerImplBase
      implements io.grpc.BindableService, AsyncService {

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return JobManagerGrpc.bindService(this);
    }
  }

  /**
   * A stub to allow clients to do asynchronous rpc calls to service JobManager.
   */
  public static final class JobManagerStub
      extends io.grpc.stub.AbstractAsyncStub<JobManagerStub> {
    private JobManagerStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected JobManagerStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new JobManagerStub(channel, callOptions);
    }

    /**
     */
    public void cancelJob(io.gitee.songchaolin.adhoc.internal.grpc.v1.CancelJobRequest request,
        io.grpc.stub.StreamObserver<io.gitee.songchaolin.adhoc.internal.grpc.v1.CancelJobResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getCancelJobMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * 探查：返回本 executor 当前在跑的 jobId 集合（server 心跳到达时 + reconcile 兜底时调用，清理孤儿 job）
     * </pre>
     */
    public void getRunningJobs(io.gitee.songchaolin.adhoc.internal.grpc.v1.GetRunningJobsRequest request,
        io.grpc.stub.StreamObserver<io.gitee.songchaolin.adhoc.internal.grpc.v1.GetRunningJobsResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getGetRunningJobsMethod(), getCallOptions()), request, responseObserver);
    }
  }

  /**
   * A stub to allow clients to do synchronous rpc calls to service JobManager.
   */
  public static final class JobManagerBlockingStub
      extends io.grpc.stub.AbstractBlockingStub<JobManagerBlockingStub> {
    private JobManagerBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected JobManagerBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new JobManagerBlockingStub(channel, callOptions);
    }

    /**
     */
    public io.gitee.songchaolin.adhoc.internal.grpc.v1.CancelJobResponse cancelJob(io.gitee.songchaolin.adhoc.internal.grpc.v1.CancelJobRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getCancelJobMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * 探查：返回本 executor 当前在跑的 jobId 集合（server 心跳到达时 + reconcile 兜底时调用，清理孤儿 job）
     * </pre>
     */
    public io.gitee.songchaolin.adhoc.internal.grpc.v1.GetRunningJobsResponse getRunningJobs(io.gitee.songchaolin.adhoc.internal.grpc.v1.GetRunningJobsRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getGetRunningJobsMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do ListenableFuture-style rpc calls to service JobManager.
   */
  public static final class JobManagerFutureStub
      extends io.grpc.stub.AbstractFutureStub<JobManagerFutureStub> {
    private JobManagerFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected JobManagerFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new JobManagerFutureStub(channel, callOptions);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<io.gitee.songchaolin.adhoc.internal.grpc.v1.CancelJobResponse> cancelJob(
        io.gitee.songchaolin.adhoc.internal.grpc.v1.CancelJobRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getCancelJobMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     * 探查：返回本 executor 当前在跑的 jobId 集合（server 心跳到达时 + reconcile 兜底时调用，清理孤儿 job）
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<io.gitee.songchaolin.adhoc.internal.grpc.v1.GetRunningJobsResponse> getRunningJobs(
        io.gitee.songchaolin.adhoc.internal.grpc.v1.GetRunningJobsRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getGetRunningJobsMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_CANCEL_JOB = 0;
  private static final int METHODID_GET_RUNNING_JOBS = 1;

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
        case METHODID_CANCEL_JOB:
          serviceImpl.cancelJob((io.gitee.songchaolin.adhoc.internal.grpc.v1.CancelJobRequest) request,
              (io.grpc.stub.StreamObserver<io.gitee.songchaolin.adhoc.internal.grpc.v1.CancelJobResponse>) responseObserver);
          break;
        case METHODID_GET_RUNNING_JOBS:
          serviceImpl.getRunningJobs((io.gitee.songchaolin.adhoc.internal.grpc.v1.GetRunningJobsRequest) request,
              (io.grpc.stub.StreamObserver<io.gitee.songchaolin.adhoc.internal.grpc.v1.GetRunningJobsResponse>) responseObserver);
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
          getCancelJobMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              io.gitee.songchaolin.adhoc.internal.grpc.v1.CancelJobRequest,
              io.gitee.songchaolin.adhoc.internal.grpc.v1.CancelJobResponse>(
                service, METHODID_CANCEL_JOB)))
        .addMethod(
          getGetRunningJobsMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              io.gitee.songchaolin.adhoc.internal.grpc.v1.GetRunningJobsRequest,
              io.gitee.songchaolin.adhoc.internal.grpc.v1.GetRunningJobsResponse>(
                service, METHODID_GET_RUNNING_JOBS)))
        .build();
  }

  private static abstract class JobManagerBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    JobManagerBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return io.gitee.songchaolin.adhoc.internal.grpc.v1.JobDispatch.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("JobManager");
    }
  }

  private static final class JobManagerFileDescriptorSupplier
      extends JobManagerBaseDescriptorSupplier {
    JobManagerFileDescriptorSupplier() {}
  }

  private static final class JobManagerMethodDescriptorSupplier
      extends JobManagerBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final String methodName;

    JobManagerMethodDescriptorSupplier(String methodName) {
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
      synchronized (JobManagerGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new JobManagerFileDescriptorSupplier())
              .addMethod(getCancelJobMethod())
              .addMethod(getGetRunningJobsMethod())
              .build();
        }
      }
    }
    return result;
  }
}
