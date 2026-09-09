package io.gitee.songchaolin.adhoc.internal.grpc.v1;

import static io.grpc.MethodDescriptor.generateFullMethodName;

/**
 */
@javax.annotation.Generated(
    value = "by gRPC proto compiler (version 1.54.0)",
    comments = "Source: internal/job_dispatch.proto")
@io.grpc.stub.annotations.GrpcGenerated
public final class JobExecutorGrpc {

  private JobExecutorGrpc() {}

  public static final String SERVICE_NAME = "adhoc.internal.v1.JobExecutor";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<io.gitee.songchaolin.adhoc.internal.grpc.v1.DispatchJobRequest,
      io.gitee.songchaolin.adhoc.internal.grpc.v1.DispatchJobResponse> getDispatchJobMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "dispatchJob",
      requestType = io.gitee.songchaolin.adhoc.internal.grpc.v1.DispatchJobRequest.class,
      responseType = io.gitee.songchaolin.adhoc.internal.grpc.v1.DispatchJobResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<io.gitee.songchaolin.adhoc.internal.grpc.v1.DispatchJobRequest,
      io.gitee.songchaolin.adhoc.internal.grpc.v1.DispatchJobResponse> getDispatchJobMethod() {
    io.grpc.MethodDescriptor<io.gitee.songchaolin.adhoc.internal.grpc.v1.DispatchJobRequest, io.gitee.songchaolin.adhoc.internal.grpc.v1.DispatchJobResponse> getDispatchJobMethod;
    if ((getDispatchJobMethod = JobExecutorGrpc.getDispatchJobMethod) == null) {
      synchronized (JobExecutorGrpc.class) {
        if ((getDispatchJobMethod = JobExecutorGrpc.getDispatchJobMethod) == null) {
          JobExecutorGrpc.getDispatchJobMethod = getDispatchJobMethod =
              io.grpc.MethodDescriptor.<io.gitee.songchaolin.adhoc.internal.grpc.v1.DispatchJobRequest, io.gitee.songchaolin.adhoc.internal.grpc.v1.DispatchJobResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "dispatchJob"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  io.gitee.songchaolin.adhoc.internal.grpc.v1.DispatchJobRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  io.gitee.songchaolin.adhoc.internal.grpc.v1.DispatchJobResponse.getDefaultInstance()))
              .setSchemaDescriptor(new JobExecutorMethodDescriptorSupplier("dispatchJob"))
              .build();
        }
      }
    }
    return getDispatchJobMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static JobExecutorStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<JobExecutorStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<JobExecutorStub>() {
        @java.lang.Override
        public JobExecutorStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new JobExecutorStub(channel, callOptions);
        }
      };
    return JobExecutorStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static JobExecutorBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<JobExecutorBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<JobExecutorBlockingStub>() {
        @java.lang.Override
        public JobExecutorBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new JobExecutorBlockingStub(channel, callOptions);
        }
      };
    return JobExecutorBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static JobExecutorFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<JobExecutorFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<JobExecutorFutureStub>() {
        @java.lang.Override
        public JobExecutorFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new JobExecutorFutureStub(channel, callOptions);
        }
      };
    return JobExecutorFutureStub.newStub(factory, channel);
  }

  /**
   */
  public interface AsyncService {

    /**
     */
    default void dispatchJob(io.gitee.songchaolin.adhoc.internal.grpc.v1.DispatchJobRequest request,
        io.grpc.stub.StreamObserver<io.gitee.songchaolin.adhoc.internal.grpc.v1.DispatchJobResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getDispatchJobMethod(), responseObserver);
    }
  }

  /**
   * Base class for the server implementation of the service JobExecutor.
   */
  public static abstract class JobExecutorImplBase
      implements io.grpc.BindableService, AsyncService {

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return JobExecutorGrpc.bindService(this);
    }
  }

  /**
   * A stub to allow clients to do asynchronous rpc calls to service JobExecutor.
   */
  public static final class JobExecutorStub
      extends io.grpc.stub.AbstractAsyncStub<JobExecutorStub> {
    private JobExecutorStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected JobExecutorStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new JobExecutorStub(channel, callOptions);
    }

    /**
     */
    public void dispatchJob(io.gitee.songchaolin.adhoc.internal.grpc.v1.DispatchJobRequest request,
        io.grpc.stub.StreamObserver<io.gitee.songchaolin.adhoc.internal.grpc.v1.DispatchJobResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getDispatchJobMethod(), getCallOptions()), request, responseObserver);
    }
  }

  /**
   * A stub to allow clients to do synchronous rpc calls to service JobExecutor.
   */
  public static final class JobExecutorBlockingStub
      extends io.grpc.stub.AbstractBlockingStub<JobExecutorBlockingStub> {
    private JobExecutorBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected JobExecutorBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new JobExecutorBlockingStub(channel, callOptions);
    }

    /**
     */
    public io.gitee.songchaolin.adhoc.internal.grpc.v1.DispatchJobResponse dispatchJob(io.gitee.songchaolin.adhoc.internal.grpc.v1.DispatchJobRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getDispatchJobMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do ListenableFuture-style rpc calls to service JobExecutor.
   */
  public static final class JobExecutorFutureStub
      extends io.grpc.stub.AbstractFutureStub<JobExecutorFutureStub> {
    private JobExecutorFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected JobExecutorFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new JobExecutorFutureStub(channel, callOptions);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<io.gitee.songchaolin.adhoc.internal.grpc.v1.DispatchJobResponse> dispatchJob(
        io.gitee.songchaolin.adhoc.internal.grpc.v1.DispatchJobRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getDispatchJobMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_DISPATCH_JOB = 0;

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
        case METHODID_DISPATCH_JOB:
          serviceImpl.dispatchJob((io.gitee.songchaolin.adhoc.internal.grpc.v1.DispatchJobRequest) request,
              (io.grpc.stub.StreamObserver<io.gitee.songchaolin.adhoc.internal.grpc.v1.DispatchJobResponse>) responseObserver);
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
          getDispatchJobMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              io.gitee.songchaolin.adhoc.internal.grpc.v1.DispatchJobRequest,
              io.gitee.songchaolin.adhoc.internal.grpc.v1.DispatchJobResponse>(
                service, METHODID_DISPATCH_JOB)))
        .build();
  }

  private static abstract class JobExecutorBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    JobExecutorBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return io.gitee.songchaolin.adhoc.internal.grpc.v1.JobDispatch.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("JobExecutor");
    }
  }

  private static final class JobExecutorFileDescriptorSupplier
      extends JobExecutorBaseDescriptorSupplier {
    JobExecutorFileDescriptorSupplier() {}
  }

  private static final class JobExecutorMethodDescriptorSupplier
      extends JobExecutorBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final String methodName;

    JobExecutorMethodDescriptorSupplier(String methodName) {
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
      synchronized (JobExecutorGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new JobExecutorFileDescriptorSupplier())
              .addMethod(getDispatchJobMethod())
              .build();
        }
      }
    }
    return result;
  }
}
