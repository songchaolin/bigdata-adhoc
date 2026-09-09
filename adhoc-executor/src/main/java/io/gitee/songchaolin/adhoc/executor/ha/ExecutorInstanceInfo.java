package io.gitee.songchaolin.adhoc.executor.ha;

import io.gitee.songchaolin.adhoc.common.config.ConfigHolder;
import io.gitee.songchaolin.adhoc.executor.config.AdhocExecutorConfig;
import org.apache.hadoop.yarn.webapp.hamlet.HamletSpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * executor 实例信息（instance-id / host / grpcPort）。
 * 注册（ExecutorInstanceRegistration）与 JobExecutionRunner（给 task/job 打 executor_instance）共用，
 * 保证 task.executor_instance == adhoc_executor_instance.instance_id，server 能按 task 反查 executor。
 * instance-id 优先取 adhoc.executor.instance-id 配置，否则用 host:grpcPort。
 * grpc.server.port 是 Spring 标准配置，不纳入 adhoc 配置类（Spring 自管）。
 */
@Component
public class ExecutorInstanceInfo {

    private final String instanceId;
    @Value("${grpc.server.port:9091}")
    private int grpcPort;

    private volatile String host;
    private volatile String id;

    public ExecutorInstanceInfo(ConfigHolder cfg) {
        this.instanceId = cfg.get(AdhocExecutorConfig.EXECUTOR_INSTANCE_ID);
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

    public int getGrpcPort() {
        return grpcPort;
    }

    public String getGrpcInstance() {
        return getHost() + ":" + getGrpcPort();
    }

    public String getId() {
        if (id == null) {
            id = (instanceId == null || instanceId.isEmpty()) ? (getHost() + ":" + grpcPort) : instanceId;
        }
        return id;
    }
}
