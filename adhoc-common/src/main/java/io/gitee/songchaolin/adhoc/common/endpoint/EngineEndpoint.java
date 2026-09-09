package io.gitee.songchaolin.adhoc.common.endpoint;

/**
 * 引擎 endpoint 模型（host:port）
 */
public class EngineEndpoint {

    private final String host;
    private final int port;
    private volatile boolean healthy = true;
    private volatile int activeConnections = 0;

    public EngineEndpoint(String host, int port) {
        this.host = host;
        this.port = port;
    }

    /**
     * 解析 endpoint 字符串（格式：host:port）
     */
    public static EngineEndpoint fromString(String endpoint) {
        String[] parts = endpoint.trim().split(":");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid endpoint format: " + endpoint + ", expected host:port");
        }
        return new EngineEndpoint(parts[0], Integer.parseInt(parts[1]));
    }

    /**
     * 转换为 JDBC URL
     */
    public String toJdbcUrl() {
        return "jdbc:hive2://" + host + ":" + port;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public boolean isHealthy() {
        return healthy;
    }

    public void setHealthy(boolean healthy) {
        this.healthy = healthy;
    }

    public int getActiveConnections() {
        return activeConnections;
    }

    public void setActiveConnections(int activeConnections) {
        this.activeConnections = activeConnections;
    }

    public void incrementConnections() {
        this.activeConnections++;
    }

    public void decrementConnections() {
        this.activeConnections--;
    }

    @Override
    public String toString() {
        return host + ":" + port;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EngineEndpoint that = (EngineEndpoint) o;
        return port == that.port && host.equals(that.host);
    }

    @Override
    public int hashCode() {
        return 31 * host.hashCode() + port;
    }
}