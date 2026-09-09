package io.gitee.songchaolin.adhoc.executor.config;

import io.gitee.songchaolin.adhoc.common.config.ConfigItem;

/**
 * executor 配置项（{@code adhoc.*}，executor-only）：heartbeat/executor/kyuubi+starrocks 默认实例名与鉴权模式/log.reconcile+finalize。
 * 引擎连接（endpoints/user/password/database/params）全部在 yml/Apollo 每实例配（adhoc.engine.{ENGINE}.{instance}.*），
 * 无引擎级回退、无代码默认凭据，由 EngineInstanceConfigResolver 动态读。
 * 共享项（oss/status）见 {@code AdhocCommonConfig}。平铺 {@code public static final ConfigItem} 常量，
 * 每项 = 完整 key + 默认值 + 中文描述 + 效果，一目了然。Apollo 配了听 Apollo，没配用默认值。
 */
public final class AdhocExecutorConfig {

    // ===== executor 心跳（adhoc.heartbeat.*） =====
    public static final ConfigItem<Long> HEARTBEAT_INTERVAL_MS = ConfigItem.of(
            "adhoc.heartbeat.interval-ms", 5000L,
            "executor 心跳上报间隔(ms)",
            "调小发现宕机更快但 RPC 多；调大省资源但感知慢");

    // ===== executor 执行参数（adhoc.executor.*） =====
    public static final ConfigItem<Integer> EXECUTOR_RESULT_LIMIT = ConfigItem.of(
            "adhoc.executor.result-limit", 1000000,
            "单 task 结果行数硬上限（SQL rewrite LIMIT + 行 cap）",
            "调大能拉更多行但吃内存/OSS；调小防 OOM");
    public static final ConfigItem<Long> EXECUTOR_SERVER_LOG_INTERVAL_MS = ConfigItem.of(
            "adhoc.executor.server-log-interval-ms", 2000L,
            "Kyuubi operation log 轮询间隔(ms)",
            "调小日志更实时但 RPC 更频；调大省资源但滞后");
    public static final ConfigItem<String> EXECUTOR_ENGINE_TYPES = ConfigItem.of(
            "adhoc.executor.engine-types", "KYUUBI,STARROCKS",
            "executor 支持的引擎（逗号分隔）",
            "server 按此路由 job");
    public static final ConfigItem<String> EXECUTOR_INSTANCE_ID = ConfigItem.of(
            "adhoc.executor.instance-id", "",
            "executor 实例标识（空则取 host:grpcPort）",
            "多实例显式指定便于追溯");
    public static final ConfigItem<String> EXECUTOR_VERSION = ConfigItem.of(
            "adhoc.executor.version", "1.0.0",
            "executor 版本",
            "灰度/排查");
    public static final ConfigItem<Integer> EXECUTOR_MAX_CONCURRENT_TASKS = ConfigItem.of(
            "adhoc.executor.max-concurrent-tasks", 10000,
            "executor 同时执行 Job 数（Semaphore 强制，满则 reject accepted=false，server failover）",
            "调大单节点并发高；调小防节点过载");
    public static final ConfigItem<Integer> EXECUTOR_QUERY_TIMEOUT_SEC = ConfigItem.of(
            "adhoc.executor.query-timeout-sec", 3600,
            "单 task 查询超时秒数（JDBC setQueryTimeout 兜底，防死循环/卡死查询；0=不限制）",
            "目前 StarRocks 生效（MySQL 协议 setQueryTimeout 成熟），Kyuubi 待支持；调大允许长查询但卡死查询占连接久");

    // ===== Kyuubi 引擎连接（adhoc.engine.KYUUBI.*，全在 yml/Apollo 每实例配，无引擎级回退、无代码默认凭据） =====
    // 每实例独立配置（实例名作 key 中段），由 EngineInstanceConfigResolver 动态读：
    //   adhoc.engine.KYUUBI.{instance}.endpoints   host:port 列表（逗号分隔，HA 共用同一组账号）--必填
    //   adhoc.engine.KYUUBI.{instance}.user         该实例 JDBC 用户--每实例独立，无回退
    //   adhoc.engine.KYUUBI.{instance}.password     该实例 JDBC 密码--每实例独立，无回退
    // 实例未配 endpoints 抛 ADHOC_ENGINE_INSTANCE_NOT_FOUND，不静默回退。
    public static final ConfigItem<String> KYUUBI_DEFAULT_INSTANCE = ConfigItem.of(
            "adhoc.engine.KYUUBI.default_instance", "kyuubi-01",
            "Kyuubi 默认实例名",
            "job 未指定 engine_instance 时用此实例；配多实例时在此指定默认");
    public static final ConfigItem<String> KYUUBI_AUTH_MODE = ConfigItem.of(
            "adhoc.engine.KYUUBI.auth-mode", "FIXED",
            "Kyuubi 鉴权模式（FIXED=统一账号+proxyUser 代理，USER=用户工号直连无密码无代理）",
            "USER 模式忽略每实例 user/password，用户名=访问用户工号；切回 FIXED 即恢复统一账号（引擎级，全实例生效）");

    // ===== StarRocks 引擎连接（adhoc.engine.STARROCKS.*，全在 yml/Apollo 每实例配，无引擎级回退、无代码默认凭据） =====
    // 每实例独立配置（由 EngineInstanceConfigResolver 动态读）：
    //   adhoc.engine.STARROCKS.{instance}.endpoints   host:port 列表--必填
    //   adhoc.engine.STARROCKS.{instance}.user/password   该实例账号密码--每实例独立，无回退
    //   adhoc.engine.STARROCKS.{instance}.database   该实例默认库--可选，配了则追加默认库，未配不追加
    //   adhoc.engine.STARROCKS.{instance}.params     该实例连接参数--可选，未配则不带（如 useSSL/serverTimezone）
    // StarRocks JDBC scheme 固定 jdbc:mysql://（MySQL 协议），非配置项。
    public static final ConfigItem<String> STARROCKS_DEFAULT_INSTANCE = ConfigItem.of(
            "adhoc.engine.STARROCKS.default_instance", "starrocks-01",
            "StarRocks 默认实例名",
            "job 未指定 engine_instance 时用此实例");
    public static final ConfigItem<String> STARROCKS_AUTH_MODE = ConfigItem.of(
            "adhoc.engine.STARROCKS.auth-mode", "FIXED",
            "StarRocks 鉴权模式（FIXED=实例统一账号，USER=用户工号+实例统一密码）",
            "USER 模式用户名=访问用户工号，密码仍取每实例 password 配置；切回 FIXED 即恢复统一账号（引擎级，全实例生效）");

    // ===== 日志对账/补全（adhoc.log.*，executor 用 reconcile-scan/finalize-delay；server 的 collect 在 AdhocServerConfig） =====
    public static final ConfigItem<Long> LOG_RECONCILE_SCAN_INTERVAL_MS = ConfigItem.of(
            "adhoc.log.reconcile-scan-interval-ms", 10000L,
            "LogBuffer 兜底对账扫描间隔(ms)",
            "调小补全更快；调大省资源");
    public static final ConfigItem<Long> LOG_FINALIZE_DELAY_MS = ConfigItem.of(
            "adhoc.log.finalize-delay-ms", 10000L,
            "LogBuffer 终态补全延迟(ms)",
            "调小补全快但可能抢在正常流程前；调大更稳但滞后");

    private AdhocExecutorConfig() {
    }
}
