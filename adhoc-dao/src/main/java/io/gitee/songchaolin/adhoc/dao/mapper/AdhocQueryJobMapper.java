package io.gitee.songchaolin.adhoc.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.gitee.songchaolin.adhoc.common.dto.JobVO;
import io.gitee.songchaolin.adhoc.common.model.TaskStats;
import io.gitee.songchaolin.adhoc.dao.entity.AdhocQueryJob;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;
import java.util.Map;

@Mapper
public interface AdhocQueryJobMapper extends BaseMapper<AdhocQueryJob> {

    /** 取一个可调度的 PENDING Job（按提交时间 ASC；带 running 限流子查询：global/per-user/per-server，任一满返回 null）。
     *  recentWindowHours：只扫最近 N 小时内提交的 PENDING（更老的交给 reconcile 兜底，防全表扫描）。
     *  MVP 无 FOR UPDATE SKIP LOCKED，靠 CAS UPDATE 保证不重复抢占。 */
    String selectOnePendingJobId(@Param("maxRunningGlobal") int maxRunningGlobal,
                                 @Param("maxRunningPerUser") int maxRunningPerUser,
                                 @Param("maxRunningPerServer") int maxRunningPerServer,
                                 @Param("serverInstance") String serverInstance,
                                 @Param("recentWindowHours") int recentWindowHours);

    /** CAS 抢占：PENDING -> RUNNING，设 processing_server_instance + dispatch_time。返回影响行数（1=抢到）。 */
    int claimJob(@Param("jobId") String jobId, @Param("serverInstanceId") String serverInstanceId,
                 @Param("dispatchTime") Date dispatchTime);

    /** 回退 RUNNING -> PENDING（无可用 executor 或 dispatch 失败时）。 */
    int revertToPending(@Param("jobId") String jobId);

    /** executor 宕机：该 executor 上 RUNNING 的 Job -> FAILED。 */
    int markJobsFailedForExecutor(@Param("exec") String exec);

    /** server 宕机补偿：DOWN server 承接的 RUNNING Job -> processing_server_instance 置 NULL（executor 继续，其它 server 可查/读结果）。 */
    int clearProcessingServerForDownServers();

    /** server 宕机补偿：DOWN server 承接的 DISPATCHING Job -> 回退 PENDING（claim 后 dispatch 前崩溃的重派）。 */
    int revertDispatchingForDownServers();

    /** 查询 Job 的 Task 统计摘要（单次查询返回多维度计数，比分别查更高效） */
    TaskStats selectTaskStatsByJobId(@Param("jobId") String jobId);

    // ==================== 指标大盘聚合查询（on-the-fly GROUP BY，绝对时间区间 [startTime,endTime] 透传，走 idx_status_submit） ====================
    // 绝对区间统一支持「近 N 小时」预设（Service 侧算 start=now-Nh,end=now）与「自定义区间」（前端传 startMs/endMs）。

    /** Job 按 status 分布（时间区间内）：返回 [{status, cnt}] */
    List<Map<String, Object>> selectStatusDistribution(@Param("startTime") Date startTime, @Param("endTime") Date endTime);

    /** Job 按 engine_type 分布（时间区间内）：返回 [{engineType, cnt}] */
    List<Map<String, Object>> selectEngineDistribution(@Param("startTime") Date startTime, @Param("endTime") Date endTime);

    /** 提交/完成趋势（按小时，label='yyyy-MM-dd HH:00'，DB 侧 DATE_FORMAT 字符串，避免 JVM/DB 时区错配）。提交按 submit_time、完成按 finish_time，均落在 [start,end] 内。 */
    List<Map<String, Object>> selectHourlyTrend(@Param("startTime") Date startTime, @Param("endTime") Date endTime);

    /** 提交/完成趋势（按天，label='yyyy-MM-dd'，宽区间用，粒度更可读） */
    List<Map<String, Object>> selectDailyTrend(@Param("startTime") Date startTime, @Param("endTime") Date endTime);

    /** Job 执行时长统计（时间区间内，终态）：返回 {avgMs, maxMs, total, bucket0to1s, bucket1to10s, bucket10to60s, bucketGt60s} */
    Map<String, Object> selectDurationStats(@Param("startTime") Date startTime, @Param("endTime") Date endTime);

    /** TopN 慢 Job（时间区间内，终态，duration DESC）：返回 [{jobId, userId, userName, engineType, status, durationMs, submitTime}] */
    List<Map<String, Object>> selectTopNSlow(@Param("startTime") Date startTime, @Param("endTime") Date endTime, @Param("limit") int limit);

    /** TopN 活跃用户（时间区间内，按 user_id 计数 DESC）：返回 [{userId, userName, cnt}] */
    List<Map<String, Object>> selectTopNUsers(@Param("startTime") Date startTime, @Param("endTime") Date endTime, @Param("limit") int limit);

    /** 实时 processing_server 分布（多 server 负载，RUNNING Job 按 processing_server_instance 聚合，无时间窗） */
    List<Map<String, Object>> selectProcessingServerDistribution();

    /** 实时状态计数（PENDING/RUNNING，无时间窗）：返回 [{status, cnt}] */
    List<Map<String, Object>> selectRealtimeStatusCount();

    /** 每实例 Job 提交数（时间区间内，按 processing_server_instance 聚合；NULL -> '(未分配)'）：返回 [{instance, cnt}] */
    List<Map<String, Object>> selectJobCountByServer(@Param("startTime") Date startTime, @Param("endTime") Date endTime);

    /** 每实例 Job 执行数（时间区间内，按 executor_instance 聚合；NULL -> '(未分配)'）：返回 [{instance, cnt}] */
    List<Map<String, Object>> selectJobCountByExecutor(@Param("startTime") Date startTime, @Param("endTime") Date endTime);

    // ==================== 大盘穿透：Job 分页明细（跨用户，无 user_id 过滤，运维视图） ====================
    // 与上面聚合查询同口径：耗时用 COALESCE(duration_ms, finish_time - submit_time)，走 idx_status_submit。

    /**
     * 大盘穿透 Job 分页：跨用户，按时间窗 + status + engineType + 耗时区间过滤，可配排序。
     * <p>耗时口径与 {@link #selectDurationStats} / {@link #selectTopNSlow} 一致（COALESCE(duration_ms, finish_time - submit_time)）；
     * 耗时过滤仅命中 finish_time 非空的 Job（与分桶查询一致）。count 由 MyBatis-Plus 分页插件自动生成。
     *
     * @param page         分页对象（current/size）
     * @param startTime    时间窗起（闭，submit_time）
     * @param endTime      时间窗止（闭，submit_time）
     * @param status       状态过滤（可空）
     * @param engineType   引擎过滤（可空）
     * @param minDurationMs 耗时下界 ms（闭，可空；非空时隐含 finish_time 非空）
     * @param maxDurationMs 耗时上界 ms（开，可空；与分桶上界一致，如 10-60s 桶传 max=60000）
     * @param orderBy      排序键（白名单：submit_desc/submit_asc/duration_desc/duration_asc）
     * @return Job 分页（records 为 {@link JobVO}）
     */
    IPage<JobVO> selectMetricsJobPage(Page<JobVO> page,
                                      @Param("startTime") Date startTime, @Param("endTime") Date endTime,
                                      @Param("status") String status, @Param("engineType") String engineType,
                                      @Param("minDurationMs") Long minDurationMs, @Param("maxDurationMs") Long maxDurationMs,
                                      @Param("orderBy") String orderBy);
}