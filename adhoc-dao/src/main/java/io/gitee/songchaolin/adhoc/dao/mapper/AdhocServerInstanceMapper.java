package io.gitee.songchaolin.adhoc.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.gitee.songchaolin.adhoc.common.model.JvmMetrics;
import io.gitee.songchaolin.adhoc.dao.entity.AdhocServerInstance;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface AdhocServerInstanceMapper extends BaseMapper<AdhocServerInstance> {

    /** 自注册：插入或重置为 UP（ON DUPLICATE KEY UPDATE by uk_instance_id）。 */
    int upsertOnRegister(@Param("instanceId") String instanceId, @Param("host") String host,
                         @Param("httpPort") int httpPort, @Param("grpcPort") int grpcPort,
                         @Param("version") String version);

    /** server 自写：刷新 heartbeat_time + 复位 status=UP + 写 JVM 指标（accepting 不动，由优雅停机控制）。 */
    int updateSelfWithMetrics(@Param("instanceId") String instanceId, @Param("metrics") JvmMetrics metrics);

    /** 查所有 UP server（executor 心跳按此列表逐个尝试，失败转移下一个）。 */
    List<AdhocServerInstance> selectUpInstances();

    /** 健康检查：心跳超时的 UP 实例标 DOWN（server 15s）。 */
    int markDownTimedOut(@Param("seconds") int seconds);

    /** 优雅停机：标记 accepting=0（不再承接新 Job）。 */
    int markAcceptingZero(@Param("instanceId") String instanceId);

    /** 实例清理：删除 DOWN 超过 N 小时的实例。 */
    int cleanupOldDownInstances(@Param("hours") int hours);

    // ==================== 指标大盘：server 实时视图（含 active_jobs + 心跳新鲜度 + 全套 JVM 指标） ====================

    /** server 大盘视图：含实例状态 + active_jobs + 心跳新鲜度 + 启动时间 + 全套 JVM 指标。 */
    List<Map<String, Object>> selectMetricsView();
}