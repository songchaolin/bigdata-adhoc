package io.gitee.songchaolin.adhoc.server.config;

import io.gitee.songchaolin.adhoc.common.config.ConfigItem;

/**
 * server 配置项（{@code adhoc.*}，server-only）：limit/server/schedule/healthcheck/compensation/reconcile/cleanup/log.collect/jvm-metric/dashboard/admin。
 * 共享项（oss/status）见 {@code AdhocCommonConfig}。平铺 {@code public static final ConfigItem} 常量，
 * 每项 = 完整 key + 默认值 + 中文描述 + 效果，一目了然。Apollo 配了听 Apollo，没配用默认值。
 */
public final class AdhocServerConfig {

    // ===== 限流（adhoc.limit.*） =====
    public static final ConfigItem<Integer> MAX_TASKS_PER_JOB = ConfigItem.of(
            "adhoc.limit.max-tasks-per-job", 20,
            "单 Job SQL 段数上限（按 ';' 拆，含 SET/USE）。提交时校验",
            "调大允许长脚本但单 Job 占 executor 久；调小防大脚本");
    public static final ConfigItem<Integer> MAX_PENDING_JOBS_PER_USER = ConfigItem.of(
            "adhoc.limit.max-pending-jobs-per-user", 1000,
            "单用户 PENDING Job 数。提交时校验",
            "调大允许用户多排队；调小防一人占满队列");
    public static final ConfigItem<Integer> MAX_PENDING_JOBS_GLOBAL = ConfigItem.of(
            "adhoc.limit.max-pending-jobs-global", 1000,
            "全局 PENDING Job 总数。提交时校验",
            "集群排队总闸");
    public static final ConfigItem<Integer> MAX_RUNNING_JOBS_GLOBAL = ConfigItem.of(
            "adhoc.limit.max-running-jobs-global", 5000,
            "全局 RUNNING Job 总数。调度时校验（满了不领）",
            "调大集群并发高但挤引擎；调小保护引擎");
    public static final ConfigItem<Integer> MAX_RUNNING_JOBS_PER_USER = ConfigItem.of(
            "adhoc.limit.max-running-jobs-per-user", 3000,
            "单用户 RUNNING Job 数。调度时校验",
            "用户并发公平性");
    public static final ConfigItem<Integer> MAX_RUNNING_JOBS_PER_SERVER = ConfigItem.of(
            "adhoc.limit.max-running-jobs-per-server", 300000,
            "单 server processing（DISPATCHING+RUNNING）Job 数。调度时校验",
            "防单 server 过载、对等集群负载均衡");

    // ===== server 实例与心跳（adhoc.server.*） =====
    public static final ConfigItem<Long> SERVER_HEARTBEAT_INTERVAL_MS = ConfigItem.of(
            "adhoc.server.heartbeat-interval-ms", 5000L,
            "server 心跳间隔(ms)",
            "调小发现宕机更快但 RPC 多；调大省资源但感知慢");
    public static final ConfigItem<String> SERVER_INSTANCE_ID = ConfigItem.of(
            "adhoc.server.instance-id", "",
            "server 实例标识（空则取 host:httpPort）",
            "多实例显式指定便于追溯");
    public static final ConfigItem<String> SERVER_VERSION = ConfigItem.of(
            "adhoc.server.version", "1.0.0",
            "server 版本",
            "灰度/排查");

    // ===== 调度（adhoc.schedule.*） =====
    public static final ConfigItem<Long> SCHEDULE_INTERVAL_MS = ConfigItem.of(
            "adhoc.schedule.interval-ms", 2000L,
            "QueueWorker 扫 PENDING 抢占间隔(ms)",
            "调小调度更及时但 DB 压力大；调大省 DB 但排队久");
    public static final ConfigItem<Integer> SCHEDULE_RECENT_WINDOW_HOURS = ConfigItem.of(
            "adhoc.schedule.recent-window-hours", 6,
            "调度回溯窗口(小时)：只扫最近 N 小时内提交的 PENDING",
            "调大捞更老 job 但扫描多；调小快但老 job 靠 reconcile 兜底");
    public static final ConfigItem<Boolean> SCHEDULE_EVENT_TRIGGER_ENABLED = ConfigItem.of(
            "adhoc.schedule.event-trigger-enabled", true,
            "是否启用事件驱动派发（submit 后立即 triggerDispatch）",
            "生产 true 更及时；测试 false 走纯轮询避免异步竞态");
    public static final ConfigItem<Long> SCHEDULE_PING_BLACKLIST_MS = ConfigItem.of(
            "adhoc.schedule.ping-blacklist-ms", 10000L,
            "ping 失败 executor 的短期熔断时长(ms)：TTL 内跳过，不重复 2s ping 死 executor",
            "调小恢复探测更勤但死 executor 多耗 2s/次；调大省 ping 但恢复慢");

    // ===== 健康检查（adhoc.healthcheck.*） =====
    public static final ConfigItem<Long> HEALTHCHECK_INTERVAL_MS = ConfigItem.of(
            "adhoc.healthcheck.interval-ms", 10000L,
            "健康检查（标 DOWN）扫描间隔(ms)",
            "调小更快标 DOWN；调大省资源但发现慢");

    // ===== 宕机补偿（adhoc.compensation.*） =====
    public static final ConfigItem<Long> COMPENSATION_INTERVAL_MS = ConfigItem.of(
            "adhoc.compensation.interval-ms", 10000L,
            "宕机补偿扫描间隔(ms)",
            "调小补偿更快；调大省资源但恢复慢");

    // ===== 状态对账（adhoc.reconcile.*） =====
    public static final ConfigItem<Long> RECONCILE_INTERVAL_MS = ConfigItem.of(
            "adhoc.reconcile.interval-ms", 300000L,
            "状态对账扫描间隔(ms)",
            "调小兜底更及时；调大省资源");
    public static final ConfigItem<Integer> RECONCILE_STUCK_THRESHOLD_MINUTES = ConfigItem.of(
            "adhoc.reconcile.stuck-threshold-minutes", 30,
            "Job 卡住判定阈值(分钟)",
            "超时未变 -> reconcile 标 FAILED");
    public static final ConfigItem<Integer> RECONCILE_ABSOLUTE_STUCK_THRESHOLD_MINUTES = ConfigItem.of(
            "adhoc.reconcile.absolute-stuck-threshold-minutes", 60,
            "Job 绝对卡住阈值(分钟)",
            "超此强制终态，防永久卡死");

    // ===== DOWN 实例清理（adhoc.cleanup.*） =====
    public static final ConfigItem<Long> CLEANUP_INTERVAL_MS = ConfigItem.of(
            "adhoc.cleanup.interval-ms", 3600000L,
            "DOWN 实例物理清理间隔(ms)",
            "调小清理快；调大省资源");

    // ===== 日志收集（adhoc.log.*，server 用 collect-interval-ms；executor 的 reconcile/finalize 在 AdhocExecutorConfig） =====
    public static final ConfigItem<Long> LOG_COLLECT_INTERVAL_MS = ConfigItem.of(
            "adhoc.log.collect-interval-ms", 2000L,
            "JobLogCollector 拉 executor 日志间隔(ms)",
            "调小日志更近实时但 RPC 多；调大省资源但滞后");
    public static final ConfigItem<Long> LOG_COMPLETE_GRACE_MS = ConfigItem.of(
            "adhoc.log.complete-grace-ms", 30000L,
            "终态 Job 缺 COMPLETE 标识时判定 complete 的兜底等待(ms)：finish_time 超此值仍无标识(executor 崩溃未写)即判 complete，避免客户端死循环轮询",
            "须 > executor adhoc.log.reconcile-scan-interval-ms(10s)，免 executor 兜底写标识前提前判 complete 漏读终态页；调大更保守但异常 Job 轮询更久");

    // ===== JVM 指标采样历史（adhoc.jvm-metric.*） =====
    public static final ConfigItem<Integer> JVM_METRIC_RETENTION_HOURS = ConfigItem.of(
            "adhoc.jvm-metric.retention-hours", 168,
            "JVM 采样历史保留时长(小时)，超期物理删除",
            "调大可看更久历史但占库；调小省库但曲线窗口变短");
    public static final ConfigItem<Long> JVM_METRIC_CLEANUP_INTERVAL_MS = ConfigItem.of(
            "adhoc.jvm-metric.cleanup-interval-ms", 300000L,
            "JVM 采样历史清理扫描间隔(ms)，server 侧单点跑",
            "调小清理更勤；调大省资源");

    // ===== Dashboard 访问鉴权（adhoc.dashboard.*） =====
    public static final ConfigItem<String> DASHBOARD_ACCESS_TOKEN = ConfigItem.of(
            "adhoc.dashboard.access-token", "",
            "dashboard/metrics 访问令牌（空=不启用鉴权；非空=请求须带 X-Dashboard-Token 头或 URL ?token= 参数匹配）",
            "生产填长随机串；泄露后 Apollo 改值秒级生效；本地开发留空免登录");

    // ===== 管理员（adhoc.admin.*） =====
    public static final ConfigItem<String> ADMIN_USER_IDS = ConfigItem.of(
            "adhoc.admin.user-ids", "",
            "管理员工号列表（逗号分隔，对应 user_id 字段即网关 userCode）",
            "读接口归属校验：本人或管理员放行；空=无管理员，仅本人可读");

    private AdhocServerConfig() {
    }
}
