package io.gitee.songchaolin.adhoc.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.gitee.songchaolin.adhoc.dao.entity.AdhocQueryTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;
import java.util.Map;

@Mapper
public interface AdhocQueryTaskMapper extends BaseMapper<AdhocQueryTask> {

    /** 批量插入 task（一次 INSERT 多行，替代逐条 insert，N 段 N 次 RPC -> 1 次）。列同 createTasks 构造。 */
    int insertBatch(@Param("list") List<AdhocQueryTask> list);

    /** 找有 RUNNING/PENDING Task 的 DOWN executor（待补偿）。 */
    List<String> selectDownExecutorsWithActiveTasks();

    /** executor 宕机：RUNNING Task -> FAILED(EXECUTOR_CRASHED)。 */
    int markRunningTasksFailedForExecutor(@Param("exec") String exec);

    /** executor 宕机：PENDING Task -> FAILED(SKIPPED_DUE_TO_SESSION_LOSS)。 */
    int markPendingTasksFailedForExecutor(@Param("exec") String exec);

    /** 心跳对账：DB=RUNNING 但 executor 心跳未上报的 Task -> FAILED(TASK_LOST)。 */
    int markTaskLost(@Param("queryId") String queryId);

    /**
     * 结果复用：查询最近成功的同 SQL Task
     *
     * @param userId     用户ID
     * @param sqlHash    SQL 哈希值
     * @param ttlSeconds TTL 秒数
     * @return 最近的 Task ID，未命中返回 null
     */
    String selectSuccessTaskBySqlHash(@Param("userId") String userId,
                                      @Param("sqlHash") String sqlHash,
                                      @Param("ttlSeconds") int ttlSeconds);

    /**
     * 聚合查询：按 Job ID 查询 Task 统计信息（只查 status 和 fail_reason_category）
     *
     * @param jobId Job ID
     * @return Task 统计信息列表
     */
    List<AdhocQueryTask> selectStatisticsByJobId(@Param("jobId") String jobId);

    // ==================== 指标大盘聚合查询（on-the-fly GROUP BY，绝对时间区间 [startTime,endTime] 透传） ====================

    /** Task 按 status 分布（时间区间内，按 enqueue_time）：返回 [{status, cnt}] */
    List<Map<String, Object>> selectTaskStatusDistribution(@Param("startTime") Date startTime, @Param("endTime") Date endTime);

    /** 失败 Task 按 fail_stage 分布（时间区间内）：返回 [{failStage, cnt}] */
    List<Map<String, Object>> selectFailStageDistribution(@Param("startTime") Date startTime, @Param("endTime") Date endTime);

    /** 失败 Task 按 error_code 分布 TopN（时间区间内，cnt DESC）：返回 [{errorCode, cnt}] */
    List<Map<String, Object>> selectErrorCodeDistribution(@Param("startTime") Date startTime, @Param("endTime") Date endTime, @Param("limit") int limit);

    /** Task 按 sql_type 分布（时间区间内）：返回 [{sqlType, cnt}] */
    List<Map<String, Object>> selectSqlTypeDistribution(@Param("startTime") Date startTime, @Param("endTime") Date endTime);

    /** 每实例 Task 数（时间区间内，按 processing_server_instance 聚合；NULL -> '(未分配)'）：返回 [{instance, cnt}] */
    List<Map<String, Object>> selectTaskCountByServer(@Param("startTime") Date startTime, @Param("endTime") Date endTime);

    /** 每实例 Task 数（时间区间内，按 executor_instance 聚合；NULL -> '(未分配)'）：返回 [{instance, cnt}] */
    List<Map<String, Object>> selectTaskCountByExecutor(@Param("startTime") Date startTime, @Param("endTime") Date endTime);

    // ==================== Task 提交/完成趋势（与 Job 趋势同口径，便于 job/task 对照） ====================

    /** Task 提交/完成趋势（按小时，label='yyyy-MM-dd HH:00'）：submitted 按 enqueue_time，finished 按 finish_time+终态。返回 [{hour, submitted, finished}] */
    List<Map<String, Object>> selectHourlyTrend(@Param("startTime") Date startTime, @Param("endTime") Date endTime);

    /** Task 提交/完成趋势（按天，label='yyyy-MM-dd'，宽区间用）。返回 [{hour, submitted, finished}] */
    List<Map<String, Object>> selectDailyTrend(@Param("startTime") Date startTime, @Param("endTime") Date endTime);
}