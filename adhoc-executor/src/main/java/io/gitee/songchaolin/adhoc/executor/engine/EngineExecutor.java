package io.gitee.songchaolin.adhoc.executor.engine;

import java.sql.Connection;
import java.sql.SQLException;

/** 引擎执行器接口（KyuubiEngineExecutor / StarRocksEngineExecutor 实现）。JobExecutionRunner 按 engine_type 选择。
 *  <p>公共编排（拆分/状态机/日志/cancel 检查）在 JobExecutionRunner；引擎专有（连接/执行/cancel）在本接口。
 *  <p>executeQuery 接收 LogSink + jobId：Kyuubi 流式转发服务端 operation log，StarRocks 忽略；jobId 用于注册 Statement 供 cancel。
 *  后续 SR 等新引擎接入只需实现本接口（含 cancel），JobExecutionRunner 编排无需改动。 */
public interface EngineExecutor {
    Connection connect(String url, String user, String password) throws SQLException;
    Connection connect(String url, String user, String password, String proxyUser) throws SQLException;
    void executeSessionSql(Connection conn, String sql) throws SQLException;
    /** 执行 SQL：执行期注册 Statement 到 runningStatements（cancel 用）。jobId 标识当前 job。 */
    QueryResult executeQuery(Connection conn, String jobId, String sql, LogSink serverLogSink) throws SQLException;
    /** 取消当前执行的查询（Statement.cancel，JDBC 标准，Kyuubi HiveStatement / MySQL 均支持）。无在跑查询则 no-op。 */
    void cancel(String jobId);
    /** 注册 connection 供 cancel：覆盖整个 job（含 prefix executeSessionSql 执行期）。
     *  <p>prefix（如 use db）是冷启动第一个操作、阻塞在引擎 session 创建，期间无 Statement 注册，
     *  Statement.cancel 无法生效；故 runner 在 connect 后注册 connection，cancel 可做 connection 级中断。
     *  默认 no-op，需 connection 级 cancel 的引擎（Kyuubi）覆盖。 */
    default void registerCancelTarget(String jobId, Connection conn) {}
    /** job 结束注销 cancel target。默认 no-op。 */
    default void unregisterCancelTarget(String jobId) {}
}
