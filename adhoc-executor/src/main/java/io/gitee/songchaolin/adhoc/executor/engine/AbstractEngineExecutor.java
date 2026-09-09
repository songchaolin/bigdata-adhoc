package io.gitee.songchaolin.adhoc.executor.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * 引擎执行器抽象类：统一处理连接耗时打印、公共日志等。
 *
 * <p>子类只需实现 {@link #doConnect(String, String, String, String)} 真正连接逻辑，
 * 连接耗时由抽象类自动记录并打印。
 */
public abstract class AbstractEngineExecutor implements EngineExecutor {

    private static final Logger log = LoggerFactory.getLogger(AbstractEngineExecutor.class);

    /**
     * 子类实现真正的连接逻辑
     *
     * @param url JDBC URL
     * @param user 用户名
     * @param password 密码
     * @param proxyUser 代理用户（可为 null）
     * @return Connection 对象
     * @throws SQLException 连接异常
     */
    protected abstract Connection doConnect(String url, String user, String password, String proxyUser) throws SQLException;

    /**
     * 获取引擎名称（用于日志打印），子类可重写
     */
    protected String getEngineName() {
        return this.getClass().getSimpleName();
    }

    @Override
    public Connection connect(String url, String user, String password) throws SQLException {
        return connect(url, user, password, null);
    }

    @Override
    public Connection connect(String url, String user, String password, String proxyUser) throws SQLException {
        long startMs = System.currentTimeMillis();

        try {
            Connection conn = doConnect(url, user, password, proxyUser);
            long costMs = System.currentTimeMillis() - startMs;
            StringBuilder logMsg = new StringBuilder()
                    .append("【连接耗时】").append(getEngineName())
                    .append(" connect cost=").append(costMs).append(" ms")
                    .append(", user=").append(user)
                    .append(", url=").append(url);
            if (proxyUser != null && !proxyUser.isEmpty()) {
                logMsg.append(", proxyUser=").append(proxyUser);
            }
            log.info(logMsg.toString());
            return conn;
        } catch (SQLException e) {
            long costMs = System.currentTimeMillis() - startMs;
            log.warn("【连接失败】{} connect failed after {} ms, url={}, error={}",
                    getEngineName(), costMs, url, e.getMessage());
            throw e;
        }
    }
}