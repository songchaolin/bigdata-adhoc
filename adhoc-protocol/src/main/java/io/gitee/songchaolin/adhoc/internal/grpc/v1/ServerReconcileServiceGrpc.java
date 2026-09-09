package io.gitee.songchaolin.adhoc.internal.grpc.v1;

import static io.grpc.MethodDescriptor.generateFullMethodName;

/**
 */
@javax.annotation.Generated(
    value = "by gRPC proto compiler (version 1.54.0)",
    comments = "Source: internal/server_reconcile.proto")
@io.grpc.stub.annotations.GrpcGenerated
public final class ServerReconcileServiceGrpc {

  private ServerReconcileServiceGrpc() {}

  public static final String SERVICE_NAME = "adhoc.internal.v1.ServerReconcileService";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<io.gitee.songchaolin.adhoc.internal.grpc.v1.CheckJobRunningRequest,
      io.gitee.songchaolin.adhoc.internal.grpc.v1.CheckJobRunningResponse> getCheckJobRunningMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "checkJobRunning",
      requestType = io.gitee.songchaolin.adhoc.internal.grpc.v1.CheckJobRunningRequest.class,
      responseType = io.gitee.songchaolin.adhoc.internal.grpc.v1.CheckJobRunningResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<io.gitee.songchaolin.adhoc.internal.grpc.v1.CheckJobRunningRequest,
      io.gitee.songchaolin.adhoc.internal.grpc.v1.CheckJobRunningResponse> getCheckJobRunningMethod() {
    io.grpc.MethodDescriptor<io.gitee.songchaolin.adhoc.internal.grpc.v1.CheckJobRunningRequest, io.gitee.songchaolin.adhoc.internal.grpc.v1.CheckJobRunningResponse> getCheckJobRunningMethod;
    if ((getCheckJobRunningMethod = ServerReconcileServiceGrpc.getCheckJobRunningMethod) == null) {
      synchronized (ServerReconcileServiceGrpc.class) {
        if ((getCheckJobRunningMethod = ServerReconcileServiceGrpc.getCheckJobRunningMethod) == null) {
          ServerReconcileServiceGrpc.getCheckJobRunningMethod = getCheckJobRunningMethod =
              io.grpc.MethodDescriptor.<io.gitee.songchaolin.adhoc.internal.grpc.v1.CheckJobRunningRequest, io.gitee.songchaolin.adhoc.internal.grpc.v1.CheckJobRunningResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "checkJobRunning"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  io.gitee.songchaolin.adhoc.internal.grpc.v1.CheckJobRunningRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  io.gitee.songchaolin.adhoc.internal.grpc.v1.CheckJobRunningResponse.getDefaultInstance()))
              .setSchemaDescriptor(new ServerReconcileServiceMethodDescriptorSupplier("checkJobRunning"))
              .build();
        }
      }
    }
    return getCheckJobRunningMethod;
  }

  private static volatile io.grpc.MethodDescriptor<io.gitee.songchaolin.adhoc.internal.grpc.v1.ServerJobLogRequest,
      io.gitee.songchaolin.adhoc.internal.grpc.v1.ServerJobLogResponse> getFetchJobLogMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "fetchJobLog",
      requestType = io.gitee.songchaolin.adhoc.internal.grpc.v1.ServerJobLogRequest.class,
      responseType = io.gitee.songchaolin.adhoc.internal.grpc.v1.ServerJobLogResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<io.gitee.songchaolin.adhoc.internal.grpc.v1.ServerJobLogRequest,
      io.gitee.songchaolin.adhoc.internal.grpc.v1.ServerJobLogResponse> getFetchJobLogMethod() {
    io.grpc.MethodDescriptor<io.gitee.songchaolin.adhoc.internal.grpc.v1.ServerJobLogRequest, io.gitee.songchaolin.adhoc.internal.grpc.v1.ServerJobLogResponse> getFetchJobLogMethod;
    if ((getFetchJobLogMethod = ServerReconcileServiceGrpc.getFetchJobLogMethod) == null) {
      synchronized (ServerReconcileServiceGrpc.class) {
        if ((getFetchJobLogMethod = ServerReconcileServiceGrpc.getFetchJobLogMethod) == null) {
          ServerReconcileServiceGrpc.getFetchJobLogMethod = getFetchJobLogMethod =
              io.grpc.MethodDescriptor.<io.gitee.songchaolin.adhoc.internal.grpc.v1.ServerJobLogRequest, io.gitee.songchaolin.adhoc.internal.grpc.v1.ServerJobLogResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "fetchJobLog"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  io.gitee.songchaolin.adhoc.internal.grpc.v1.ServerJobLogRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  io.gitee.songchaolin.adhoc.internal.grpc.v1.ServerJobLogResponse.getDefaultInstance()))
              .setSchemaDescriptor(new ServerReconcileServiceMethodDescriptorSupplier("fetchJobLog"))
              .build();
        }
      }
    }
    return getFetchJobLogMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static ServerReconcileServiceStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<ServerReconcileServiceStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<ServerReconcileServiceStub>() {
        @java.lang.Override
        public ServerReconcileServiceStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new ServerReconcileServiceStub(channel, callOptions);
        }
      };
    return ServerReconcileServiceStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static ServerReconcileServiceBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<ServerReconcileServiceBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<ServerReconcileServiceBlockingStub>() {
        @java.lang.Override
        public ServerReconcileServiceBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new ServerReconcileServiceBlockingStub(channel, callOptions);
        }
      };
    return ServerReconcileServiceBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static ServerReconcileServiceFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<ServerReconcileServiceFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<ServerReconcileServiceFutureStub>() {
        @java.lang.Override
        public ServerReconcileServiceFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new ServerReconcileServiceFutureStub(channel, callOptions);
        }
      };
    return ServerReconcileServiceFutureStub.newStub(factory, channel);
  }

  /**
   */
  public interface AsyncService {

    /**
     */
    default void checkJobRunning(io.gitee.songchaolin.adhoc.internal.grpc.v1.CheckJobRunningRequest request,
        io.grpc.stub.StreamObserver<io.gitee.songchaolin.adhoc.internal.grpc.v1.CheckJobRunningResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getCheckJobRunningMethod(), responseObserver);
    }

    /**
     */
    default void fetchJobLog(io.gitee.songchaolin.adhoc.internal.grpc.v1.ServerJobLogRequest request,
        io.grpc.stub.StreamObserver<io.gitee.songchaolin.adhoc.internal.grpc.v1.ServerJobLogResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getFetchJobLogMethod(), responseObserver);
    }
  }

  /**
   * Base class for the server implementation of the service ServerReconcileService.
   */
  public static abstract class ServerReconcileServiceImplBase
      implements io.grpc.BindableService, AsyncService {

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return ServerReconcileServiceGrpc.bindService(this);
    }
  }

  /**
   * A stub to allow clients to do asynchronous rpc calls to service ServerReconcileService.
   */
  public static final class ServerReconcileServiceStub
      extends io.grpc.stub.AbstractAsyncStub<ServerReconcileServiceStub> {
    private ServerReconcileServiceStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected ServerReconcileServiceStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new ServerReconcileServiceStub(channel, callOptions);
    }

    /**
     */
    public void checkJobRunning(io.gitee.songchaolin.adhoc.internal.grpc.v1.CheckJobRunningRequest request,
        io.grpc.stub.StreamObserver<io.gitee.songchaolin.adhoc.internal.grpc.v1.CheckJobRunningResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getCheckJobRunningMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     */
    public void fetchJobLog(io.gitee.songchaolin.adhoc.internal.grpc.v1.ServerJobLogRequest request,
        io.grpc.stub.StreamObserver<io.gitee.songchaolin.adhoc.internal.grpc.v1.ServerJobLogResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getFetchJobLogMethod(), getCallOptions()), request, responseObserver);
    }
  }

  /**
   * A stub to allow clients to do synchronous rpc calls to service ServerReconcileService.
   */
  public static final class ServerReconcileServiceBlockingStub
      extends io.grpc.stub.AbstractBlockingStub<ServerReconcileServiceBlockingStub> {
    private ServerReconcileServiceBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected ServerReconcileServiceBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new ServerReconcileServiceBlockingStub(channel, callOptions);
    }

    /**
     */
    public io.gitee.songchaolin.adhoc.internal.grpc.v1.CheckJobRunningResponse checkJobRunning(io.gitee.songchaolin.adhoc.internal.grpc.v1.CheckJobRunningRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getCheckJobRunningMethod(), getCallOptions(), request);
    }

    /**
     */
    public io.gitee.songchaolin.adhoc.internal.grpc.v1.ServerJobLogResponse fetchJobLog(io.gitee.songchaolin.adhoc.internal.grpc.v1.ServerJobLogRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getFetchJobLogMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do ListenableFuture-style rpc calls to service ServerReconcileService.
   */
  public static final class ServerReconcileServiceFutureStub
      extends io.grpc.stub.AbstractFutureStub<ServerReconcileServiceFutureStub> {
    private ServerReconcileServiceFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected ServerReconcileServiceFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new ServerReconcileServiceFutureStub(channel, callOptions);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<io.gitee.songchaolin.adhoc.internal.grpc.v1.CheckJobRunningResponse> checkJobRunning(
        io.gitee.songchaolin.adhoc.internal.grpc.v1.CheckJobRunningRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getCheckJobRunningMethod(), getCallOptions()), request);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<io.gitee.songchaolin.adhoc.internal.grpc.v1.ServerJobLogResponse> fetchJobLog(
        io.gitee.songchaolin.adhoc.internal.grpc.v1.ServerJobLogRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getFetchJobLogMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_CHECK_JOB_RUNNING = 0;
  private static final int METHODID_FETCH_JOB_LOG = 1;

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
        case METHODID_CHECK_JOB_RUNNING:
          serviceImpl.checkJobRunning((io.gitee.songchaolin.adhoc.internal.grpc.v1.CheckJobRunningRequest) request,
              (io.grpc.stub.StreamObserver<io.gitee.songchaolin.adhoc.internal.grpc.v1.CheckJobRunningResponse>) responseObserver);
          break;
        case METHODID_FETCH_JOB_LOG:
          serviceImpl.fetchJobLog((io.gitee.songchaolin.adhoc.internal.grpc.v1.ServerJobLogRequest) request,
              (io.grpc.stub.StreamObserver<io.gitee.songchaolin.adhoc.internal.grpc.v1.ServerJobLogResponse>) responseObserver);
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
          getCheckJobRunningMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              io.gitee.songchaolin.adhoc.internal.grpc.v1.CheckJobRunningRequest,
              io.gitee.songchaolin.adhoc.internal.grpc.v1.CheckJobRunningResponse>(
                service, METHODID_CHECK_JOB_RUNNING)))
        .addMethod(
          getFetchJobLogMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              io.gitee.songchaolin.adhoc.internal.grpc.v1.ServerJobLogRequest,
              io.gitee.songchaolin.adhoc.internal.grpc.v1.ServerJobLogResponse>(
                service, METHODID_FETCH_JOB_LOG)))
        .build();
  }

  private static abstract class ServerReconcileServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    ServerReconcileServiceBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return io.gitee.songchaolin.adhoc.internal.grpc.v1.ServerReconcile.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("ServerReconcileService");
    }
  }

  private static final class ServerReconcileServiceFileDescriptorSupplier
      extends ServerReconcileServiceBaseDescriptorSupplier {
    ServerReconcileServiceFileDescriptorSupplier() {}
  }

  private static final class ServerReconcileServiceMethodDescriptorSupplier
      extends ServerReconcileServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final String methodName;

    ServerReconcileServiceMethodDescriptorSupplier(String methodName) {
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
      synchronized (ServerReconcileServiceGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new ServerReconcileServiceFileDescriptorSupplier())
              .addMethod(getCheckJobRunningMethod())
              .addMethod(getFetchJobLogMethod())
              .build();
        }
      }
    }
    return result;
  }
}
