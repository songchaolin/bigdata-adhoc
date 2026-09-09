package io.gitee.songchaolin.adhoc.server.web;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.gitee.songchaolin.adhoc.common.dto.JobDetailResponse;
import io.gitee.songchaolin.adhoc.common.dto.JobProgressResponse;
import io.gitee.songchaolin.adhoc.common.dto.JobVO;
import io.gitee.songchaolin.adhoc.common.dto.JvmSamplePoint;
import io.gitee.songchaolin.adhoc.common.dto.LogResponse;
import io.gitee.songchaolin.adhoc.common.dto.MetricsExecutorVO;
import io.gitee.songchaolin.adhoc.common.dto.MetricsInstanceLoadVO;
import io.gitee.songchaolin.adhoc.common.dto.MetricsOverviewVO;
import io.gitee.songchaolin.adhoc.common.dto.MetricsServerVO;
import io.gitee.songchaolin.adhoc.common.dto.MetricsTaskFailVO;
import io.gitee.songchaolin.adhoc.common.dto.MetricsTopNVO;
import io.gitee.songchaolin.adhoc.common.dto.MetricsTrendPoint;
import io.gitee.songchaolin.adhoc.common.dto.TaskDetailResponse;
import io.gitee.songchaolin.adhoc.server.aspect.AdhocResponseAdvice;
import io.gitee.songchaolin.adhoc.server.service.JobQueryService;
import io.gitee.songchaolin.adhoc.server.service.LogQueryService;
import io.gitee.songchaolin.adhoc.server.service.MetricsService;
import io.gitee.songchaolin.adhoc.server.service.TaskQueryService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 集群指标大盘端点：on-the-fly 实时聚合 job/task/executor 表，供内嵌前端 {@code /dashboard/} 调用。
 * GET + @RequestParam（对齐 MetadataController 风格）；裸返回 VO 由 {@link AdhocResponseAdvice} 统一包 Result。
 * <p>运维视图，不做 per-user 隔离（对齐 ConfigController，内部运维）。
 *
 * <p><b>穿透下钻端点</b>（{@code /jobs} / {@code /job-detail} / {@code /job-progress} /
 * {@code /task-detail} / {@code /job-log} / {@code /task-log}）：薄包装复用 {@link MetricsService} 聚合查询与
 * {@link JobQueryService}/{@link LogQueryService}/{@link TaskQueryService} 读服务，均只读、按 jobId/taskId 查、
 * 无归属校验（与现有读类接口 capability 模型一致），落在已免登录的 {@code /api/metrics/**} 下。
 */
@Api(tags = "Adhoc Metrics")
@RestController
@RequestMapping("/api/metrics")
public class MetricsController {

    private final MetricsService metricsService;
    private final JobQueryService jobQueryService;
    private final LogQueryService logQueryService;
    private final TaskQueryService taskQueryService;

    public MetricsController(MetricsService metricsService, JobQueryService jobQueryService,
                             LogQueryService logQueryService, TaskQueryService taskQueryService) {
        this.metricsService = metricsService;
        this.jobQueryService = jobQueryService;
        this.logQueryService = logQueryService;
        this.taskQueryService = taskQueryService;
    }

    @ApiOperation("概览（Job/Task 状态分布+成功率+时长+引擎+实时态+executor 汇总）。hours=预设窗口；startMs/endMs=自定义区间(epoch 毫秒，优先) ")
    @GetMapping("/overview")
    public MetricsOverviewVO overview(@RequestParam(required = false) Long startMs,
                                      @RequestParam(required = false) Long endMs,
                                      @RequestParam(defaultValue = "24") int hours) {
        return metricsService.getOverview(startMs, endMs, hours);
    }

    @ApiOperation("小时/天提交完成趋势。startMs/endMs 优先，否则按 hours。")
    @GetMapping("/trends")
    public List<MetricsTrendPoint> trends(@RequestParam(required = false) Long startMs,
                                           @RequestParam(required = false) Long endMs,
                                           @RequestParam(defaultValue = "24") int hours) {
        return metricsService.getTrends(startMs, endMs, hours);
    }

    @ApiOperation("Task 小时/天提交完成趋势（与 Job 趋势同口径，便于 job/task 对照）。startMs/endMs 优先，否则按 hours。")
    @GetMapping("/task-trends")
    public List<MetricsTrendPoint> taskTrends(@RequestParam(required = false) Long startMs,
                                               @RequestParam(required = false) Long endMs,
                                               @RequestParam(defaultValue = "24") int hours) {
        return metricsService.getTaskTrends(startMs, endMs, hours);
    }

    @ApiOperation("Executor 集群列表（状态/并发/利用率/心跳/JVM指标）。onlineOnly=true(默认) 仅返回在线实例")
    @GetMapping("/executors")
    public List<MetricsExecutorVO> executors(@RequestParam(defaultValue = "true") boolean onlineOnly) {
        return metricsService.getExecutors(onlineOnly);
    }

    @ApiOperation("Server 集群列表（状态/承接/active_jobs/心跳）。onlineOnly=true(默认) 仅返回在线实例")
    @GetMapping("/servers")
    public List<MetricsServerVO> servers(@RequestParam(defaultValue = "true") boolean onlineOnly) {
        return metricsService.getServers(onlineOnly);
    }

    @ApiOperation("TopN 慢 Job + 活跃用户。startMs/endMs 优先，否则按 hours。")
    @GetMapping("/topn")
    public MetricsTopNVO topn(@RequestParam(required = false) Long startMs,
                              @RequestParam(required = false) Long endMs,
                              @RequestParam(defaultValue = "24") int hours,
                              @RequestParam(defaultValue = "10") int limit) {
        return metricsService.getTopN(startMs, endMs, hours, limit);
    }

    @ApiOperation("Task 失败维度（fail_stage / error_code / sql_type 分布）。startMs/endMs 优先，否则按 hours。")
    @GetMapping("/task-failures")
    public MetricsTaskFailVO taskFailures(@RequestParam(required = false) Long startMs,
                                         @RequestParam(required = false) Long endMs,
                                         @RequestParam(defaultValue = "24") int hours) {
        return metricsService.getTaskFailures(startMs, endMs, hours);
    }

    @ApiOperation("每实例负载：时间窗内每 server/executor 上提交的 Job 数与 Task 数聚合。startMs/endMs 优先，否则按 hours。")
    @GetMapping("/instance-load")
    public MetricsInstanceLoadVO instanceLoad(@RequestParam(required = false) Long startMs,
                                              @RequestParam(required = false) Long endMs,
                                              @RequestParam(defaultValue = "24") int hours) {
        return metricsService.getInstanceLoad(startMs, endMs, hours);
    }

    @ApiOperation("JVM 指标时间曲线：按实例 id 列表 + 时间窗查采样历史（ad-hoc 5s 采样）。" +
            "minutes 默认 30、上限 120（=保留期 2h）；startMs/endMs 优先。返回 {instanceId: [{t,指标...}]}。")
    @GetMapping("/jvm-series")
    public Map<String, List<JvmSamplePoint>> jvmSeries(@RequestParam(required = false) List<String> instanceIds,
                                                        @RequestParam(required = false) Long startMs,
                                                        @RequestParam(required = false) Long endMs,
                                                        @RequestParam(defaultValue = "30") int minutes) {
        return metricsService.getJvmSeries(instanceIds, startMs, endMs, minutes);
    }

    // ==================== 穿透下钻端点（薄包装，复用现有读服务；均免登录，对齐大盘运维视图） ====================

    @ApiOperation("穿透：Job 分页明细（跨用户，按 status/引擎/耗时区间/时间窗过滤，可配排序）。" +
            "耗时区间与概览时长分桶同口径，按桶穿透行数 = 桶计数。")
    @GetMapping("/jobs")
    public IPage<JobVO> jobs(@RequestParam(required = false) Long startMs,
                             @RequestParam(required = false) Long endMs,
                             @RequestParam(defaultValue = "24") int hours,
                             @RequestParam(required = false) String status,
                             @RequestParam(required = false) String engineType,
                             @RequestParam(required = false) Long minDurationMs,
                             @RequestParam(required = false) Long maxDurationMs,
                             @RequestParam(defaultValue = "1") long current,
                             @RequestParam(defaultValue = "20") long size,
                             @RequestParam(defaultValue = "submit_desc") String orderBy) {
        return metricsService.getJobPage(startMs, endMs, hours, status, engineType,
                minDurationMs, maxDurationMs, current, size, orderBy);
    }

    @ApiOperation("穿透：Job 详情（含 Task 摘要列表，segmentIndex ASC）。入参仅 jobId，系统级访问（dashboard token 鉴权）。")
    @GetMapping("/job-detail")
    public JobDetailResponse jobDetail(@RequestParam String jobId) {
        return jobQueryService.getJobDetail(jobId, null);
    }

    @ApiOperation("穿透：Job 执行进度（阶段时间线 DAG + 各阶段耗时）。入参仅 jobId，系统级访问。")
    @GetMapping("/job-progress")
    public JobProgressResponse jobProgress(@RequestParam String jobId) {
        return jobQueryService.getJobProgress(jobId, null);
    }

    @ApiOperation("穿透：Task 详情（全量字段：执行统计/失败明细/时间线）。入参仅 taskId，系统级访问。")
    @GetMapping("/task-detail")
    public TaskDetailResponse taskDetail(@RequestParam String taskId) {
        return taskQueryService.getTaskDetail(taskId, null);
    }

    @ApiOperation("穿透：Job 日志（offset/limit 增量；hasMore=分页未完，complete=日志已完整可停轮询）。" +
            "running 读内存/转发，terminal 读 OSS。系统级访问。")
    @GetMapping("/job-log")
    public LogResponse jobLog(@RequestParam String jobId,
                              @RequestParam(defaultValue = "0") long offset,
                              @RequestParam(defaultValue = "1000") int limit) {
        return logQueryService.getJobLog(jobId, offset, limit, null);
    }

    @ApiOperation("穿透：Task 日志（offset/limit 增量）。running 走 executor gRPC，terminal 兜底读 job 日志。系统级访问。")
    @GetMapping("/task-log")
    public LogResponse taskLog(@RequestParam String taskId,
                               @RequestParam(defaultValue = "0") long offset,
                               @RequestParam(defaultValue = "100") int limit) {
        return logQueryService.getTaskLog(taskId, offset, limit, null);
    }
}
