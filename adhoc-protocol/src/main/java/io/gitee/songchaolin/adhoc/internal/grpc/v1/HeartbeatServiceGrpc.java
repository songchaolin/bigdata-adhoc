package io.gitee.songchaolin.adhoc.internal.grpc.v1;

import static io.grpc.MethodDescriptor.generateFullMethodName;

/**
 */
@javax.annotation.Generated(
    value = "by gRPC proto compiler (version 1.54.0)",
    comments = "Source: internal/heartbeat.proto")
@io.grpc.stub.annotations.GrpcGenerated
public final class HeartbeatServiceGrpc {

  private HeartbeatServiceGrpc() {}

  public static final String SERVICE_NAME = "adhoc.internal.v1.HeartbeatService";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<io.gitee.songchaolin.adhoc.internal.grpc.v1.HeartbeatRequest,
      io.gitee.songchaolin.adhoc.internal.grpc.v1.HeartbeatResponse> getHeartbeatMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "heartbeat",
      requestType = io.gitee.songchaolin.adhoc.internal.grpc.v1.HeartbeatRequest.class,
      responseType = io.gitee.songchaolin.adhoc.internal.grpc.v1.HeartbeatResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<io.gitee.songchaolin.adhoc.internal.grpc.v1.HeartbeatRequest,
      io.gitee.songchaolin.adhoc.internal.grpc.v1.HeartbeatResponse> getHeartbeatMethod() {
    io.grpc.MethodDescriptor<io.gitee.songchaolin.adhoc.internal.grpc.v1.HeartbeatRequest, io.gitee.songchaolin.adhoc.internal.grpc.v1.HeartbeatResponse> getHeartbeatMethod;
    if ((getHeartbeatMethod = HeartbeatServiceGrpc.getHeartbeatMethod) == null) {
      synchronized (HeartbeatServiceGrpc.class) {
        if ((getHeartbeatMethod = HeartbeatServiceGrpc.getHeartbeatMethod) == null) {
          HeartbeatServiceGrpc.getHeartbeatMethod = getHeartbeatMethod =
              io.grpc.MethodDescriptor.<io.gitee.songchaolin.adhoc.internal.grpc.v1.HeartbeatRequest, io.gitee.songchaolin.adhoc.internal.grpc.v1.HeartbeatResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "heartbeat"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  io.gitee.songchaolin.adhoc.internal.grpc.v1.HeartbeatRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  io.gitee.songchaolin.adhoc.internal.grpc.v1.HeartbeatResponse.getDefaultInstance()))
              .setSchemaDescriptor(new HeartbeatServiceMethodDescriptorSupplier("heartbeat"))
              .build();
        }
      }
    }
    return getHeartbeatMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static HeartbeatServiceStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<HeartbeatServiceStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<HeartbeatServiceStub>() {
        @java.lang.Override
        public HeartbeatServiceStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new HeartbeatServiceStub(channel, callOptions);
        }
      };
    return HeartbeatServiceStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static HeartbeatServiceBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<HeartbeatServiceBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<HeartbeatServiceBlockingStub>() {
        @java.lang.Override
        public HeartbeatServiceBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new HeartbeatServiceBlockingStub(channel, callOptions);
        }
      };
    return HeartbeatServiceBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static HeartbeatServiceFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<HeartbeatServiceFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<HeartbeatServiceFutureStub>() {
        @java.lang.Override
        public HeartbeatServiceFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new HeartbeatServiceFutureStub(channel, callOptions);
        }
      };
    return HeartbeatServiceFutureStub.newStub(factory, channel);
  }

  /**
   */
  public interface AsyncService {

    /**
     */
    default void heartbeat(io.gitee.songchaolin.adhoc.internal.grpc.v1.HeartbeatRequest request,
        io.grpc.stub.StreamObserver<io.gitee.songchaolin.adhoc.internal.grpc.v1.HeartbeatResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getHeartbeatMethod(), responseObserver);
    }
  }

  /**
   * Base class for the server implementation of the service HeartbeatService.
   */
  public static abstract class HeartbeatServiceImplBase
      implements io.grpc.BindableService, AsyncService {

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return HeartbeatServiceGrpc.bindService(this);
    }
  }

  /**
   * A stub to allow clients to do asynchronous rpc calls to service HeartbeatService.
   */
  public static final class HeartbeatServiceStub
      extends io.grpc.stub.AbstractAsyncStub<HeartbeatServiceStub> {
    private HeartbeatServiceStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected HeartbeatServiceStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new HeartbeatServiceStub(channel, callOptions);
    }

    /**
     */
    public void heartbeat(io.gitee.songchaolin.adhoc.internal.grpc.v1.HeartbeatRequest request,
        io.grpc.stub.StreamObserver<io.gitee.songchaolin.adhoc.internal.grpc.v1.HeartbeatResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getHeartbeatMethod(), getCallOptions()), request, responseObserver);
    }
  }

  /**
   * A stub to allow clients to do synchronous rpc calls to service HeartbeatService.
   */
  public static final class HeartbeatServiceBlockingStub
      extends io.grpc.stub.AbstractBlockingStub<HeartbeatServiceBlockingStub> {
    private HeartbeatServiceBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected HeartbeatServiceBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new HeartbeatServiceBlockingStub(channel, callOptions);
    }

    /**
     */
    public io.gitee.songchaolin.adhoc.internal.grpc.v1.HeartbeatResponse heartbeat(io.gitee.songchaolin.adhoc.internal.grpc.v1.HeartbeatRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getHeartbeatMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do ListenableFuture-style rpc calls to service HeartbeatService.
   */
  public static final class HeartbeatServiceFutureStub
      extends io.grpc.stub.AbstractFutureStub<HeartbeatServiceFutureStub> {
    private HeartbeatServiceFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected HeartbeatServiceFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new HeartbeatServiceFutureStub(channel, callOptions);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<io.gitee.songchaolin.adhoc.internal.grpc.v1.HeartbeatResponse> heartbeat(
        io.gitee.songchaolin.adhoc.internal.grpc.v1.HeartbeatRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getHeartbeatMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_HEARTBEAT = 0;

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
        case METHODID_HEARTBEAT:
          serviceImpl.heartbeat((io.gitee.songchaolin.adhoc.internal.grpc.v1.HeartbeatRequest) request,
              (io.grpc.stub.StreamObserver<io.gitee.songchaolin.adhoc.internal.grpc.v1.HeartbeatResponse>) responseObserver);
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
          getHeartbeatMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              io.gitee.songchaolin.adhoc.internal.grpc.v1.HeartbeatRequest,
              io.gitee.songchaolin.adhoc.internal.grpc.v1.HeartbeatResponse>(
                service, METHODID_HEARTBEAT)))
        .build();
  }

  private static abstract class HeartbeatServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    HeartbeatServiceBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return io.gitee.songchaolin.adhoc.internal.grpc.v1.Heartbeat.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("HeartbeatService");
    }
  }

  private static final class HeartbeatServiceFileDescriptorSupplier
      extends HeartbeatServiceBaseDescriptorSupplier {
    HeartbeatServiceFileDescriptorSupplier() {}
  }

  private static final class HeartbeatServiceMethodDescriptorSupplier
      extends HeartbeatServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final String methodName;

    HeartbeatServiceMethodDescriptorSupplier(String methodName) {
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
      synchronized (HeartbeatServiceGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new HeartbeatServiceFileDescriptorSupplier())
              .addMethod(getHeartbeatMethod())
              .build();
        }
      }
    }
    return result;
  }
}
