package io.gitee.songchaolin.adhoc.internal.grpc.v1;

import static io.grpc.MethodDescriptor.generateFullMethodName;

/**
 */
@javax.annotation.Generated(
    value = "by gRPC proto compiler (version 1.54.0)",
    comments = "Source: internal/data_fetcher.proto")
@io.grpc.stub.annotations.GrpcGenerated
public final class DataFetcherGrpc {

  private DataFetcherGrpc() {}

  public static final String SERVICE_NAME = "adhoc.internal.v1.DataFetcher";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<io.gitee.songchaolin.adhoc.internal.grpc.v1.FetchLogRequest,
      io.gitee.songchaolin.adhoc.internal.grpc.v1.FetchLogResponse> getFetchLogMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "fetchLog",
      requestType = io.gitee.songchaolin.adhoc.internal.grpc.v1.FetchLogRequest.class,
      responseType = io.gitee.songchaolin.adhoc.internal.grpc.v1.FetchLogResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<io.gitee.songchaolin.adhoc.internal.grpc.v1.FetchLogRequest,
      io.gitee.songchaolin.adhoc.internal.grpc.v1.FetchLogResponse> getFetchLogMethod() {
    io.grpc.MethodDescriptor<io.gitee.songchaolin.adhoc.internal.grpc.v1.FetchLogRequest, io.gitee.songchaolin.adhoc.internal.grpc.v1.FetchLogResponse> getFetchLogMethod;
    if ((getFetchLogMethod = DataFetcherGrpc.getFetchLogMethod) == null) {
      synchronized (DataFetcherGrpc.class) {
        if ((getFetchLogMethod = DataFetcherGrpc.getFetchLogMethod) == null) {
          DataFetcherGrpc.getFetchLogMethod = getFetchLogMethod =
              io.grpc.MethodDescriptor.<io.gitee.songchaolin.adhoc.internal.grpc.v1.FetchLogRequest, io.gitee.songchaolin.adhoc.internal.grpc.v1.FetchLogResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "fetchLog"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  io.gitee.songchaolin.adhoc.internal.grpc.v1.FetchLogRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  io.gitee.songchaolin.adhoc.internal.grpc.v1.FetchLogResponse.getDefaultInstance()))
              .setSchemaDescriptor(new DataFetcherMethodDescriptorSupplier("fetchLog"))
              .build();
        }
      }
    }
    return getFetchLogMethod;
  }

  private static volatile io.grpc.MethodDescriptor<io.gitee.songchaolin.adhoc.internal.grpc.v1.FetchJobLogRequest,
      io.gitee.songchaolin.adhoc.internal.grpc.v1.FetchJobLogResponse> getFetchJobLogMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "fetchJobLog",
      requestType = io.gitee.songchaolin.adhoc.internal.grpc.v1.FetchJobLogRequest.class,
      responseType = io.gitee.songchaolin.adhoc.internal.grpc.v1.FetchJobLogResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<io.gitee.songchaolin.adhoc.internal.grpc.v1.FetchJobLogRequest,
      io.gitee.songchaolin.adhoc.internal.grpc.v1.FetchJobLogResponse> getFetchJobLogMethod() {
    io.grpc.MethodDescriptor<io.gitee.songchaolin.adhoc.internal.grpc.v1.FetchJobLogRequest, io.gitee.songchaolin.adhoc.internal.grpc.v1.FetchJobLogResponse> getFetchJobLogMethod;
    if ((getFetchJobLogMethod = DataFetcherGrpc.getFetchJobLogMethod) == null) {
      synchronized (DataFetcherGrpc.class) {
        if ((getFetchJobLogMethod = DataFetcherGrpc.getFetchJobLogMethod) == null) {
          DataFetcherGrpc.getFetchJobLogMethod = getFetchJobLogMethod =
              io.grpc.MethodDescriptor.<io.gitee.songchaolin.adhoc.internal.grpc.v1.FetchJobLogRequest, io.gitee.songchaolin.adhoc.internal.grpc.v1.FetchJobLogResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "fetchJobLog"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  io.gitee.songchaolin.adhoc.internal.grpc.v1.FetchJobLogRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  io.gitee.songchaolin.adhoc.internal.grpc.v1.FetchJobLogResponse.getDefaultInstance()))
              .setSchemaDescriptor(new DataFetcherMethodDescriptorSupplier("fetchJobLog"))
              .build();
        }
      }
    }
    return getFetchJobLogMethod;
  }

  private static volatile io.grpc.MethodDescriptor<io.gitee.songchaolin.adhoc.internal.grpc.v1.ClearJobLogRequest,
      io.gitee.songchaolin.adhoc.internal.grpc.v1.ClearJobLogResponse> getClearJobLogMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "clearJobLog",
      requestType = io.gitee.songchaolin.adhoc.internal.grpc.v1.ClearJobLogRequest.class,
      responseType = io.gitee.songchaolin.adhoc.internal.grpc.v1.ClearJobLogResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<io.gitee.songchaolin.adhoc.internal.grpc.v1.ClearJobLogRequest,
      io.gitee.songchaolin.adhoc.internal.grpc.v1.ClearJobLogResponse> getClearJobLogMethod() {
    io.grpc.MethodDescriptor<io.gitee.songchaolin.adhoc.internal.grpc.v1.ClearJobLogRequest, io.gitee.songchaolin.adhoc.internal.grpc.v1.ClearJobLogResponse> getClearJobLogMethod;
    if ((getClearJobLogMethod = DataFetcherGrpc.getClearJobLogMethod) == null) {
      synchronized (DataFetcherGrpc.class) {
        if ((getClearJobLogMethod = DataFetcherGrpc.getClearJobLogMethod) == null) {
          DataFetcherGrpc.getClearJobLogMethod = getClearJobLogMethod =
              io.grpc.MethodDescriptor.<io.gitee.songchaolin.adhoc.internal.grpc.v1.ClearJobLogRequest, io.gitee.songchaolin.adhoc.internal.grpc.v1.ClearJobLogResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "clearJobLog"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  io.gitee.songchaolin.adhoc.internal.grpc.v1.ClearJobLogRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  io.gitee.songchaolin.adhoc.internal.grpc.v1.ClearJobLogResponse.getDefaultInstance()))
              .setSchemaDescriptor(new DataFetcherMethodDescriptorSupplier("clearJobLog"))
              .build();
        }
      }
    }
    return getClearJobLogMethod;
  }

  private static volatile io.grpc.MethodDescriptor<io.gitee.songchaolin.adhoc.internal.grpc.v1.PingRequest,
      io.gitee.songchaolin.adhoc.internal.grpc.v1.PingResponse> getPingMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "ping",
      requestType = io.gitee.songchaolin.adhoc.internal.grpc.v1.PingRequest.class,
      responseType = io.gitee.songchaolin.adhoc.internal.grpc.v1.PingResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<io.gitee.songchaolin.adhoc.internal.grpc.v1.PingRequest,
      io.gitee.songchaolin.adhoc.internal.grpc.v1.PingResponse> getPingMethod() {
    io.grpc.MethodDescriptor<io.gitee.songchaolin.adhoc.internal.grpc.v1.PingRequest, io.gitee.songchaolin.adhoc.internal.grpc.v1.PingResponse> getPingMethod;
    if ((getPingMethod = DataFetcherGrpc.getPingMethod) == null) {
      synchronized (DataFetcherGrpc.class) {
        if ((getPingMethod = DataFetcherGrpc.getPingMethod) == null) {
          DataFetcherGrpc.getPingMethod = getPingMethod =
              io.grpc.MethodDescriptor.<io.gitee.songchaolin.adhoc.internal.grpc.v1.PingRequest, io.gitee.songchaolin.adhoc.internal.grpc.v1.PingResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "ping"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  io.gitee.songchaolin.adhoc.internal.grpc.v1.PingRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  io.gitee.songchaolin.adhoc.internal.grpc.v1.PingResponse.getDefaultInstance()))
              .setSchemaDescriptor(new DataFetcherMethodDescriptorSupplier("ping"))
              .build();
        }
      }
    }
    return getPingMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static DataFetcherStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<DataFetcherStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<DataFetcherStub>() {
        @java.lang.Override
        public DataFetcherStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new DataFetcherStub(channel, callOptions);
        }
      };
    return DataFetcherStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static DataFetcherBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<DataFetcherBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<DataFetcherBlockingStub>() {
        @java.lang.Override
        public DataFetcherBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new DataFetcherBlockingStub(channel, callOptions);
        }
      };
    return DataFetcherBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static DataFetcherFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<DataFetcherFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<DataFetcherFutureStub>() {
        @java.lang.Override
        public DataFetcherFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new DataFetcherFutureStub(channel, callOptions);
        }
      };
    return DataFetcherFutureStub.newStub(factory, channel);
  }

  /**
   */
  public interface AsyncService {

    /**
     */
    default void fetchLog(io.gitee.songchaolin.adhoc.internal.grpc.v1.FetchLogRequest request,
        io.grpc.stub.StreamObserver<io.gitee.songchaolin.adhoc.internal.grpc.v1.FetchLogResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getFetchLogMethod(), responseObserver);
    }

    /**
     */
    default void fetchJobLog(io.gitee.songchaolin.adhoc.internal.grpc.v1.FetchJobLogRequest request,
        io.grpc.stub.StreamObserver<io.gitee.songchaolin.adhoc.internal.grpc.v1.FetchJobLogResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getFetchJobLogMethod(), responseObserver);
    }

    /**
     */
    default void clearJobLog(io.gitee.songchaolin.adhoc.internal.grpc.v1.ClearJobLogRequest request,
        io.grpc.stub.StreamObserver<io.gitee.songchaolin.adhoc.internal.grpc.v1.ClearJobLogResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getClearJobLogMethod(), responseObserver);
    }

    /**
     */
    default void ping(io.gitee.songchaolin.adhoc.internal.grpc.v1.PingRequest request,
        io.grpc.stub.StreamObserver<io.gitee.songchaolin.adhoc.internal.grpc.v1.PingResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getPingMethod(), responseObserver);
    }
  }

  /**
   * Base class for the server implementation of the service DataFetcher.
   */
  public static abstract class DataFetcherImplBase
      implements io.grpc.BindableService, AsyncService {

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return DataFetcherGrpc.bindService(this);
    }
  }

  /**
   * A stub to allow clients to do asynchronous rpc calls to service DataFetcher.
   */
  public static final class DataFetcherStub
      extends io.grpc.stub.AbstractAsyncStub<DataFetcherStub> {
    private DataFetcherStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected DataFetcherStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new DataFetcherStub(channel, callOptions);
    }

    /**
     */
    public void fetchLog(io.gitee.songchaolin.adhoc.internal.grpc.v1.FetchLogRequest request,
        io.grpc.stub.StreamObserver<io.gitee.songchaolin.adhoc.internal.grpc.v1.FetchLogResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getFetchLogMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     */
    public void fetchJobLog(io.gitee.songchaolin.adhoc.internal.grpc.v1.FetchJobLogRequest request,
        io.grpc.stub.StreamObserver<io.gitee.songchaolin.adhoc.internal.grpc.v1.FetchJobLogResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getFetchJobLogMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     */
    public void clearJobLog(io.gitee.songchaolin.adhoc.internal.grpc.v1.ClearJobLogRequest request,
        io.grpc.stub.StreamObserver<io.gitee.songchaolin.adhoc.internal.grpc.v1.ClearJobLogResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getClearJobLogMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     */
    public void ping(io.gitee.songchaolin.adhoc.internal.grpc.v1.PingRequest request,
        io.grpc.stub.StreamObserver<io.gitee.songchaolin.adhoc.internal.grpc.v1.PingResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getPingMethod(), getCallOptions()), request, responseObserver);
    }
  }

  /**
   * A stub to allow clients to do synchronous rpc calls to service DataFetcher.
   */
  public static final class DataFetcherBlockingStub
      extends io.grpc.stub.AbstractBlockingStub<DataFetcherBlockingStub> {
    private DataFetcherBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected DataFetcherBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new DataFetcherBlockingStub(channel, callOptions);
    }

    /**
     */
    public io.gitee.songchaolin.adhoc.internal.grpc.v1.FetchLogResponse fetchLog(io.gitee.songchaolin.adhoc.internal.grpc.v1.FetchLogRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getFetchLogMethod(), getCallOptions(), request);
    }

    /**
     */
    public io.gitee.songchaolin.adhoc.internal.grpc.v1.FetchJobLogResponse fetchJobLog(io.gitee.songchaolin.adhoc.internal.grpc.v1.FetchJobLogRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getFetchJobLogMethod(), getCallOptions(), request);
    }

    /**
     */
    public io.gitee.songchaolin.adhoc.internal.grpc.v1.ClearJobLogResponse clearJobLog(io.gitee.songchaolin.adhoc.internal.grpc.v1.ClearJobLogRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getClearJobLogMethod(), getCallOptions(), request);
    }

    /**
     */
    public io.gitee.songchaolin.adhoc.internal.grpc.v1.PingResponse ping(io.gitee.songchaolin.adhoc.internal.grpc.v1.PingRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getPingMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do ListenableFuture-style rpc calls to service DataFetcher.
   */
  public static final class DataFetcherFutureStub
      extends io.grpc.stub.AbstractFutureStub<DataFetcherFutureStub> {
    private DataFetcherFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected DataFetcherFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new DataFetcherFutureStub(channel, callOptions);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<io.gitee.songchaolin.adhoc.internal.grpc.v1.FetchLogResponse> fetchLog(
        io.gitee.songchaolin.adhoc.internal.grpc.v1.FetchLogRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getFetchLogMethod(), getCallOptions()), request);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<io.gitee.songchaolin.adhoc.internal.grpc.v1.FetchJobLogResponse> fetchJobLog(
        io.gitee.songchaolin.adhoc.internal.grpc.v1.FetchJobLogRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getFetchJobLogMethod(), getCallOptions()), request);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<io.gitee.songchaolin.adhoc.internal.grpc.v1.ClearJobLogResponse> clearJobLog(
        io.gitee.songchaolin.adhoc.internal.grpc.v1.ClearJobLogRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getClearJobLogMethod(), getCallOptions()), request);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<io.gitee.songchaolin.adhoc.internal.grpc.v1.PingResponse> ping(
        io.gitee.songchaolin.adhoc.internal.grpc.v1.PingRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getPingMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_FETCH_LOG = 0;
  private static final int METHODID_FETCH_JOB_LOG = 1;
  private static final int METHODID_CLEAR_JOB_LOG = 2;
  private static final int METHODID_PING = 3;

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
        case METHODID_FETCH_LOG:
          serviceImpl.fetchLog((io.gitee.songchaolin.adhoc.internal.grpc.v1.FetchLogRequest) request,
              (io.grpc.stub.StreamObserver<io.gitee.songchaolin.adhoc.internal.grpc.v1.FetchLogResponse>) responseObserver);
          break;
        case METHODID_FETCH_JOB_LOG:
          serviceImpl.fetchJobLog((io.gitee.songchaolin.adhoc.internal.grpc.v1.FetchJobLogRequest) request,
              (io.grpc.stub.StreamObserver<io.gitee.songchaolin.adhoc.internal.grpc.v1.FetchJobLogResponse>) responseObserver);
          break;
        case METHODID_CLEAR_JOB_LOG:
          serviceImpl.clearJobLog((io.gitee.songchaolin.adhoc.internal.grpc.v1.ClearJobLogRequest) request,
              (io.grpc.stub.StreamObserver<io.gitee.songchaolin.adhoc.internal.grpc.v1.ClearJobLogResponse>) responseObserver);
          break;
        case METHODID_PING:
          serviceImpl.ping((io.gitee.songchaolin.adhoc.internal.grpc.v1.PingRequest) request,
              (io.grpc.stub.StreamObserver<io.gitee.songchaolin.adhoc.internal.grpc.v1.PingResponse>) responseObserver);
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
          getFetchLogMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              io.gitee.songchaolin.adhoc.internal.grpc.v1.FetchLogRequest,
              io.gitee.songchaolin.adhoc.internal.grpc.v1.FetchLogResponse>(
                service, METHODID_FETCH_LOG)))
        .addMethod(
          getFetchJobLogMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              io.gitee.songchaolin.adhoc.internal.grpc.v1.FetchJobLogRequest,
              io.gitee.songchaolin.adhoc.internal.grpc.v1.FetchJobLogResponse>(
                service, METHODID_FETCH_JOB_LOG)))
        .addMethod(
          getClearJobLogMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              io.gitee.songchaolin.adhoc.internal.grpc.v1.ClearJobLogRequest,
              io.gitee.songchaolin.adhoc.internal.grpc.v1.ClearJobLogResponse>(
                service, METHODID_CLEAR_JOB_LOG)))
        .addMethod(
          getPingMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              io.gitee.songchaolin.adhoc.internal.grpc.v1.PingRequest,
              io.gitee.songchaolin.adhoc.internal.grpc.v1.PingResponse>(
                service, METHODID_PING)))
        .build();
  }

  private static abstract class DataFetcherBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    DataFetcherBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return io.gitee.songchaolin.adhoc.internal.grpc.v1.DataFetcherOuterClass.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("DataFetcher");
    }
  }

  private static final class DataFetcherFileDescriptorSupplier
      extends DataFetcherBaseDescriptorSupplier {
    DataFetcherFileDescriptorSupplier() {}
  }

  private static final class DataFetcherMethodDescriptorSupplier
      extends DataFetcherBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final String methodName;

    DataFetcherMethodDescriptorSupplier(String methodName) {
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
      synchronized (DataFetcherGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new DataFetcherFileDescriptorSupplier())
              .addMethod(getFetchLogMethod())
              .addMethod(getFetchJobLogMethod())
              .addMethod(getClearJobLogMethod())
              .addMethod(getPingMethod())
              .build();
        }
      }
    }
    return result;
  }
}
