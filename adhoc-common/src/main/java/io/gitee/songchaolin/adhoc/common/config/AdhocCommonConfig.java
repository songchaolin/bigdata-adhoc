package io.gitee.songchaolin.adhoc.common.config;

/**
 * 共享配置项（server + executor 共用，Hadoop {@code core-default} 模式）：{@code adhoc.result.*}、{@code adhoc.status.*}。
 * 平铺 {@code public static final ConfigItem} 常量，每项 = 完整 key + 默认值 + 中文描述 + 效果，一目了然。
 */
public final class AdhocCommonConfig {

    // ===== 结果复用（adhoc.result.*） =====
    /** 结果复用 TTL（秒） */
    public static final ConfigItem<Integer> RESULT_REUSE_TTL_SECONDS = ConfigItem.of(
            "adhoc.result.reuse-ttl-seconds", 300,
            "结果复用 TTL（秒）",
            "同一用户 5 分钟内重复 SQL 直接复用结果，调大命中率增但数据可能不新鲜");

    // ===== 状态播报（adhoc.status.*） =====
    /** 状态播报间隔(ms)。 */
    public static final ConfigItem<Long> STATUS_INTERVAL_MS = ConfigItem.of(
            "adhoc.status.interval-ms", 60000L,
            "状态播报间隔(ms)：server 各状态 Job 计数+processing+executor UP/DOWN+限流阈值；executor 运行 Job 数/上限+task 数+CPU/内存/线程/load",
            "调小播报更频但日志更多；调大省资源但观测滞后");

    private AdhocCommonConfig() {
    }
}
