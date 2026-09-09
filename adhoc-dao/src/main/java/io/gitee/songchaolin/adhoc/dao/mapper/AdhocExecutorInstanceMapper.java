package io.gitee.songchaolin.adhoc.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.gitee.songchaolin.adhoc.common.model.JvmMetrics;
import io.gitee.songchaolin.adhoc.dao.entity.AdhocExecutorInstance;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface AdhocExecutorInstanceMapper extends BaseMapper<AdhocExecutorInstance> {

    /** 选首个 UP + accepting 的 executor（按 load_score ASC；MVP 单 executor 也走此路）。 */
    AdhocExecutorInstance selectFirstUpAccepting();

    /** 选首个 UP + accepting 且支持指定 engine_type 的 executor（FIND_IN_SET engine_types，按 load_score ASC）。 */
    AdhocExecutorInstance selectFirstUpAcceptingByEngineType(@Param("engineType") String engineType);

    /** 查所有 UP + accepting 且支持指定 engine_type 的 executor（FIND_IN_SET engine_types，按 load_score ASC）。QueueWorker 逐个探活+failover 用。 */
    List<AdhocExecutorInstance> selectAllUpAcceptingByEngineType(@Param("engineType") String engineType);

    /** 自注册：插入或重置为 UP（ON DUPLICATE KEY UPDATE by uk_instance_id）。 */
    int upsertOnRegister(@Param("instanceId") String instanceId, @Param("host") String host,
                         @Param("grpcPort") int grpcPort, @Param("version") String version,
                         @Param("engineTypes") String engineTypes, @Param("maxConcurrentTasks") int maxConcurrentTasks);

    /** executor 自写自己的实例表行（status=UP + 指标 + running_tasks + heartbeat_time）。 */
    int updateSelf(@Param("instanceId") String instanceId,
                   @Param("runningTasks") String runningTasks,
                   @Param("metrics") JvmMetrics metrics);

    /** 健康检查：心跳超时的 UP 实例标 DOWN（executor 30s）。返回标 DOWN 数。 */
    int markDownTimedOut(@Param("seconds") int seconds);

    /** 实例清理：删除 DOWN 超过 N 小时的实例。 */
    int cleanupOldDownInstances(@Param("hours") int hours);

    // ==================== 指标大盘：executor 实时视图（全表小，含 JSON_LENGTH(running_tasks) 取在跑数 + 心跳新鲜度） ====================

    /** executor 大盘视图：含实例状态 + 并发 + 心跳新鲜度 + 全套 JVM 指标（cpu/mem/heap/nonheap/thread/daemon/gc/classload/uptime/loadScore）。 */
    List<Map<String, Object>> selectMetricsView();
}