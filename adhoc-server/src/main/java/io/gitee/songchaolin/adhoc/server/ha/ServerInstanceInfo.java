package io.gitee.songchaolin.adhoc.server.ha;

import io.gitee.songchaolin.adhoc.common.config.ConfigHolder;
import io.gitee.songchaolin.adhoc.server.config.AdhocServerConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * server 实例信息（instance-id / host / httpPort）。
 * 注册（ServerInstanceRegistration）与自心跳（ServerHeartbeatTask）共用，保证写同一 instance_id。
 * instance-id 优先取 adhoc.server.instance-id 配置，否则用 host:httpPort。
 * server.port / grpc.server.port 是 Spring 标准配置，不纳入 adhoc 配置类（Spring 自管）。
 */
@Component
public class ServerInstanceInfo {

    private final ConfigHolder cfg;
    @Value("${server.port:8080}")
    private int httpPort;
    @Value("${grpc.server.port:9090}")
    private int grpcPort;

    private volatile String host;
    private volatile String id;

    public ServerInstanceInfo(ConfigHolder cfg) {
        this.cfg = cfg;
    }

    public String getHost() {
        if (host == null) {
            try {
                host = InetAddress.getLocalHost().getHostAddress();
            } catch (UnknownHostException e) {
                host = "127.0.0.1";
            }
        }
        return host;
    }

    public int getHttpPort() {
        return httpPort;
    }

    public int getGrpcPort() {
        return grpcPort;
    }

    public String getGrpcInstance() {
        return getHost() + ":" + getGrpcPort();
    }

    public String getId() {
        if (id == null) {
            String instanceId = cfg.get(AdhocServerConfig.SERVER_INSTANCE_ID);
            id = (instanceId == null || instanceId.isEmpty()) ? (getHost() + ":" + httpPort) : instanceId;
        }
        return id;
    }
}
