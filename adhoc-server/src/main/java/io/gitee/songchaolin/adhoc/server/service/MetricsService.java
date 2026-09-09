package io.gitee.songchaolin.adhoc.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.gitee.songchaolin.adhoc.common.dto.JobVO;
import io.gitee.songchaolin.adhoc.common.dto.JvmSamplePoint;
import io.gitee.songchaolin.adhoc.common.dto.MetricsExecutorVO;
import io.gitee.songchaolin.adhoc.common.dto.MetricsInstanceLoadVO;
import io.gitee.songchaolin.adhoc.common.dto.MetricsOverviewVO;
import io.gitee.songchaolin.adhoc.common.dto.MetricsServerVO;
import io.gitee.songchaolin.adhoc.common.dto.MetricsTaskFailVO;
import io.gitee.songchaolin.adhoc.common.dto.MetricsTopNVO;
import io.gitee.songchaolin.adhoc.common.dto.MetricsTrendPoint;
import io.gitee.songchaolin.adhoc.dao.entity.AdhocJvmMetricSample;
import io.gitee.songchaolin.adhoc.dao.mapper.AdhocExecutorInstanceMapper;
import io.gitee.songchaolin.adhoc.dao.mapper.AdhocJvmMetricSampleMapper;
import io.gitee.songchaolin.adhoc.dao.mapper.AdhocQueryJobMapper;
import io.gitee.songchaolin.adhoc.dao.mapper.AdhocQueryTaskMapper;
import io.gitee.songchaolin.adhoc.dao.mapper.AdhocServerInstanceMapper;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 指标大盘服务：job/task/executor 业务指标 on-the-fly 实时聚合（每次请求 GROUP BY 查表，限时间窗 + 走索引，不建汇总表不加定时任务）；
 * JVM/OS 指标时间曲线走采样历史表 {@code adhoc_jvm_metric_sample}（心跳 5s 采样、server 侧清理，瞬时态无法事后重算故建表）。
 * <p>异常统一由 {@link io.gitee.songchaolin.adhoc.server.aspect.AdhocResponseAdvice} 兜底。
 */
@Service
public class MetricsService {

    private static final String[] DURATION_BUCKET_KEYS = {"0-1s", "1-10s", "10-60s", ">60s"};

    private final AdhocQueryJobMapper jobMapper;
    private final AdhocQueryTaskMapper taskMapper;
    private final AdhocExecutorInstanceMapper executorMapper;
    private final AdhocServerInstanceMapper serverMapper;
    private final AdhocJvmMetricSampleMapper sampleMapper;

    public MetricsService(AdhocQueryJobMapper jobMapper,
                          AdhocQueryTaskMapper taskMapper,
                          AdhocExecutorInstanceMapper executorMapper,
                          AdhocServerInstanceMapper serverMapper,
                          AdhocJvmMetricSampleMapper sampleMapper) {
        this.jobMapper = jobMapper;
        this.taskMapper = taskMapper;
        this.executorMapper = executorMapper;
        this.serverMapper = serverMapper;
        this.sampleMapper = sampleMapper;
    }

    /** 概览：Job/Task 状态分布 + 成功率 + 时长 + 引擎 + 实时态 + executor 汇总。
     *  时间区间：自定义 startMs/endMs 优先（前端 datetime-local），否则按 hours 预设（now-Nh..now）。 */
    public MetricsOverviewVO getOverview(Long startMs, Long endMs, int hours) {
        TimeRange tr = resolveRange(startMs, endMs, hours);

        // Job 维度
        Map<String, Long> jobByStatus = rowsToDist(jobMapper.selectStatusDistribution(tr.start, tr.end));
        Map<String, Long> engineDist = rowsToDist(jobMapper.selectEngineDistribution(tr.start, tr.end));
        Map<String, Object> dur = jobMapper.selectDurationStats(tr.start, tr.end);
        Map<String, Long> buckets = durationBuckets(dur);

        // 实时态
        Map<String, Long> realtime = rowsToDist(jobMapper.selectRealtimeStatusCount());
        List<MetricsTaskFailVO.NameCount> psDist = rowsToNameCount(jobMapper.selectProcessingServerDistribution(), "serverInstance");

        // executor 汇总（复用 executors 查询，避免二次全表）
        List<Map<String, Object>> executors = executorMapper.selectMetricsView();
        long total = executors.size();
        long up = executors.stream().filter(r -> "UP".equalsIgnoreCase(str(r.get("status")))).count();
        long down = executors.stream().filter(r -> "DOWN".equalsIgnoreCase(str(r.get("status")))).count();
        long accepting = executors.stream().filter(r -> toLong(r.get("accepting")) == 1).count();

        // server 汇总
        List<Map<String, Object>> servers = serverMapper.selectMetricsView();
        long sTotal = servers.size();
        long sUp = servers.stream().filter(r -> "UP".equalsIgnoreCase(str(r.get("status")))).count();

        // Task 维度
        Map<String, Long> taskByStatus = rowsToDist(taskMapper.selectTaskStatusDistribution(tr.start, tr.end));

        return new MetricsOverviewVO(
                sum(jobByStatus),
                jobByStatus,
                successRate(jobByStatus, "SUCCESS"),
                toDouble(dur.get("avgMs")),
                toLong(dur.get("maxMs")),
                buckets,
                engineDist,
                getOrDefault(realtime, "PENDING"),
                getOrDefault(realtime, "RUNNING"),
                getOrDefault(realtime, "DISPATCHING"),
                psDist,
                total, up, down, accepting,
                sTotal, sUp,
                sum(taskByStatus),
                taskByStatus,
                successRate(taskByStatus, "SUCCESS")
        );
    }

    /** Job 趋势：区间跨度 ≤96h 按小时、否则按天。DB 侧 DATE_FORMAT 字符串分桶 + Service 侧按 min..max 补空桶，
     *  全程基于 DB 返回的 label 运算（不比 JVM now），规避 JVM/MySQL 时区不一致导致的桶错位。
     *  SQL 注入区间起止桶 0,0 锚点（DATE_FORMAT #{startTime}/#{endTime}），保证 min..max 覆盖整个区间，
     *  横轴始终铺到区间右端（近 N 小时预设下 = 当前小时），近段时间无任务也不断尾。 */
    public List<MetricsTrendPoint> getTrends(Long startMs, Long endMs, int hours) {
        TimeRange tr = resolveRange(startMs, endMs, hours);
        boolean daily = (tr.end.getTime() - tr.start.getTime()) > 96L * 3_600_000L;
        List<Map<String, Object>> rows = daily ? jobMapper.selectDailyTrend(tr.start, tr.end) : jobMapper.selectHourlyTrend(tr.start, tr.end);
        return fillTrendPoints(rows, daily);
    }

    /** Task 趋势：与 {@link #getTrends} 同口径同分桶逻辑（submitted 按 enqueue_time，finished 按 finish_time+终态），
     *  便于 job/task 趋势同 X 轴对照。区间跨度 ≤96h 按小时、否则按天。 */
    public List<MetricsTrendPoint> getTaskTrends(Long startMs, Long endMs, int hours) {
        TimeRange tr = resolveRange(startMs, endMs, hours);
        boolean daily = (tr.end.getTime() - tr.start.getTime()) > 96L * 3_600_000L;
        List<Map<String, Object>> rows = daily ? taskMapper.selectDailyTrend(tr.start, tr.end) : taskMapper.selectHourlyTrend(tr.start, tr.end);
        return fillTrendPoints(rows, daily);
    }

    /** 趋势分桶装配：DB 侧 DATE_FORMAT 字符串分桶（hour/submitted/finished 列），Service 侧按 min..max 补空桶填 0。
     *  全程基于 DB 返回的 label 运算（不比 JVM now），规避 JVM/MySQL 时区不一致导致的桶错位。job/task 趋势共用此逻辑。 */
    private List<MetricsTrendPoint> fillTrendPoints(List<Map<String, Object>> rows, boolean daily) {
        String fullFmt = daily ? "yyyy-MM-dd" : "yyyy-MM-dd HH:00";
        TreeMap<String, long[]> byHour = new TreeMap<>();
        for (Map<String, Object> r : rows) {
            String hour = str(r.get("hour"));
            if (hour == null) continue;
            byHour.computeIfAbsent(hour, k -> new long[2])[0] = toLong(r.get("submitted"));
            byHour.computeIfAbsent(hour, k -> new long[2])[1] = toLong(r.get("finished"));
        }
        if (byHour.isEmpty()) {
            return new ArrayList<>();
        }
        // 补空桶：min..max 之间每个桶都出点，缺数据填 0
        SimpleDateFormat fmt = new SimpleDateFormat(fullFmt);
        List<MetricsTrendPoint> points = new ArrayList<>();
        try {
            Date cur = fmt.parse(byHour.firstKey());
            Date end = fmt.parse(byHour.lastKey());
            long stepMs = daily ? 86_400_000L : 3_600_000L;
            while (!cur.after(end)) {
                String key = fmt.format(cur);
                long[] v = byHour.getOrDefault(key, new long[]{0, 0});
                points.add(new MetricsTrendPoint(shortLabel(key, daily), v[0], v[1]));
                cur = new Date(cur.getTime() + stepMs);
            }
        } catch (Exception e) {
            // 解析失败退路：直接返回原始有序点（不补空桶）
            byHour.forEach((k, v) -> points.add(new MetricsTrendPoint(shortLabel(k, daily), v[0], v[1])));
        }
        return points;
    }

    /** 显示用短标签：小时 'MM-dd HH:00'，天 'MM-dd'（去年份，更紧凑）。 */
    private static String shortLabel(String full, boolean daily) {
        if (full == null || full.length() < 5) return full;
        return daily ? full.substring(5) : full.substring(5);
    }

    /** Executor 列表：含利用率（Service 侧算，maxConcurrent=0 时 0）。onlineOnly=true 仅返回在线（status=UP）。 */
    public List<MetricsExecutorVO> getExecutors(boolean onlineOnly) {
        List<Map<String, Object>> rows = executorMapper.selectMetricsView();
        List<MetricsExecutorVO> list = new ArrayList<>(rows.size());
        for (Map<String, Object> r : rows) {
            if (onlineOnly && !"UP".equalsIgnoreCase(str(r.get("status")))) {
                continue;
            }
            int running = toInt(r.get("runningTasks"));
            int max = toInt(r.get("maxConcurrent"));
            double util = max > 0 ? running * 100.0 / max : 0d;
            list.add(new MetricsExecutorVO(
                    str(r.get("instanceId")), str(r.get("host")), toInt(r.get("grpcPort")),
                    str(r.get("status")), toInt(r.get("accepting")), running, max,
                    round1(util), toLong(r.get("heartbeatAgeSec")),
                    toDouble(r.get("cpuUsagePct")), toDouble(r.get("memoryUsagePct")), toDouble(r.get("loadScore")),
                    toDouble(r.get("systemCpuUsagePct")), toLong(r.get("memoryUsedMb")), toLong(r.get("memoryMaxMb")),
                    toLong(r.get("heapCommittedMb")), toLong(r.get("nonHeapUsedMb")), toLong(r.get("nonHeapCommittedMb")),
                    toInt(r.get("threadCount")), toInt(r.get("daemonThreadCount")),
                    toLong(r.get("gcCount")), toLong(r.get("gcTimeMs")), toDouble(r.get("gcTimeRatioPct")),
                    toInt(r.get("loadedClassCount")), toLong(r.get("uptimeMs"))
            ));
        }
        return list;
    }

    /** Server 列表：含 active_jobs + 心跳新鲜度 + 全套 JVM 指标（与 executor 共用 JvmMetrics 采集，每 5s 自写）。onlineOnly=true 仅返回在线（status=UP）。 */
    public List<MetricsServerVO> getServers(boolean onlineOnly) {
        List<Map<String, Object>> rows = serverMapper.selectMetricsView();
        List<MetricsServerVO> list = new ArrayList<>(rows.size());
        for (Map<String, Object> r : rows) {
            if (onlineOnly && !"UP".equalsIgnoreCase(str(r.get("status")))) {
                continue;
            }
            list.add(new MetricsServerVO(
                    str(r.get("instanceId")), str(r.get("host")),
                    toInt(r.get("httpPort")), toInt(r.get("grpcPort")),
                    str(r.get("status")), toInt(r.get("accepting")),
                    toInt(r.get("activeJobs")), toLong(r.get("heartbeatAgeSec")),
                    toLongNull(r.get("startTimeSec")), str(r.get("version")),
                    toDouble(r.get("cpuUsagePct")), toDouble(r.get("systemCpuUsagePct")),
                    toDouble(r.get("memoryUsagePct")), toLong(r.get("memoryUsedMb")), toLong(r.get("memoryMaxMb")),
                    toLong(r.get("heapCommittedMb")), toLong(r.get("nonHeapUsedMb")), toLong(r.get("nonHeapCommittedMb")),
                    toInt(r.get("threadCount")), toInt(r.get("daemonThreadCount")),
                    toLong(r.get("gcCount")), toLong(r.get("gcTimeMs")), toDouble(r.get("gcTimeRatioPct")),
                    toInt(r.get("loadedClassCount")), toLong(r.get("uptimeMs")),
                    toDouble(r.get("loadScore"))
            ));
        }
        return list;
    }

    /** TopN：最慢 Job + 最活跃用户。 */
    public MetricsTopNVO getTopN(Long startMs, Long endMs, int hours, int limit) {
        TimeRange tr = resolveRange(startMs, endMs, hours);
        int lim = clampLimit(limit);
        List<MetricsTopNVO.SlowJobRow> slow = new ArrayList<>();
        for (Map<String, Object> r : jobMapper.selectTopNSlow(tr.start, tr.end, lim)) {
            slow.add(new MetricsTopNVO.SlowJobRow(
                    str(r.get("jobId")), str(r.get("userId")), str(r.get("userName")),
                    str(r.get("engineType")), str(r.get("status")),
                    toLong(r.get("durationMs")), toEpochMillis(r.get("submitTime"))));
        }
        List<MetricsTopNVO.UserRow> users = new ArrayList<>();
        for (Map<String, Object> r : jobMapper.selectTopNUsers(tr.start, tr.end, lim)) {
            users.add(new MetricsTopNVO.UserRow(str(r.get("userId")), str(r.get("userName")), toLong(r.get("cnt"))));
        }
        return new MetricsTopNVO(slow, users);
    }

    /** Task 失败维度：fail_stage / error_code / sql_type 分布。 */
    public MetricsTaskFailVO getTaskFailures(Long startMs, Long endMs, int hours) {
        TimeRange tr = resolveRange(startMs, endMs, hours);
        List<MetricsTaskFailVO.NameCount> byStage = rowsToNameCount(taskMapper.selectFailStageDistribution(tr.start, tr.end), "failStage");
        List<MetricsTaskFailVO.NameCount> byErrorCode = rowsToNameCount(taskMapper.selectErrorCodeDistribution(tr.start, tr.end, 10), "errorCode");
        List<MetricsTaskFailVO.NameCount> bySqlType = rowsToNameCount(taskMapper.selectSqlTypeDistribution(tr.start, tr.end), "sqlType");
        return new MetricsTaskFailVO(byStage, byErrorCode, bySqlType);
    }

    /** 每实例负载：时间窗内每 server / 每 executor 上提交的 Job 数与 Task 数聚合。
     *  Job 按 processing_server_instance（server）/ executor_instance（executor）；Task 同字段（task 表冗余此列，无需 join job）。
     *  Java 侧合并 job + task 计数，按 jobs+tasks DESC 排序。 */
    public MetricsInstanceLoadVO getInstanceLoad(Long startMs, Long endMs, int hours) {
        TimeRange tr = resolveRange(startMs, endMs, hours);
        // server 维度
        Map<String, long[]> serverMap = new LinkedHashMap<>();
        accumulateInstanceLoad(serverMap, jobMapper.selectJobCountByServer(tr.start, tr.end), 0);
        accumulateInstanceLoad(serverMap, taskMapper.selectTaskCountByServer(tr.start, tr.end), 1);
        // executor 维度
        Map<String, long[]> execMap = new LinkedHashMap<>();
        accumulateInstanceLoad(execMap, jobMapper.selectJobCountByExecutor(tr.start, tr.end), 0);
        accumulateInstanceLoad(execMap, taskMapper.selectTaskCountByExecutor(tr.start, tr.end), 1);
        return new MetricsInstanceLoadVO(toInstanceLoadRows(serverMap), toInstanceLoadRows(execMap));
    }

    /** JVM 指标时间曲线：按 instanceId 列表 + 时间窗查 adhoc_jvm_metric_sample 采样历史，Java 侧按实例分组。
     *  时间窗：自定义 startMs/endMs 优先，否则按 minutes 预设（now-Nmin..now）；clamp 到保留期 7d（与 adhoc.jvm-metric.retention-hours 默认 168h 对齐）防超窗扫表。
     *  role 由 sample 表自带列（无需前端传），同一查询可跨 server+executor 实例。 */
    public Map<String, List<JvmSamplePoint>> getJvmSeries(List<String> instanceIds, Long startMs, Long endMs, int minutes) {
        if (instanceIds == null || instanceIds.isEmpty()) {
            return new LinkedHashMap<>();
        }
        long now = System.currentTimeMillis();
        long start;
        long end;
        if (startMs != null && endMs != null && startMs > 0 && endMs > startMs) {
            start = startMs;
            end = endMs;
        } else {
            int m = minutes <= 0 ? 60 : Math.min(minutes, 10080);  // 上限 10080min = 保留期 7d
            start = now - m * 60_000L;
            end = now;
        }
        // clamp 到保留期 7d（adhoc.jvm-metric.retention-hours 默认 168h）
        long maxSpan = 7L * 24 * 3_600_000L;
        if (end - start > maxSpan) {
            start = end - maxSpan;
        }
        List<AdhocJvmMetricSample> rows = sampleMapper.selectList(
                new LambdaQueryWrapper<AdhocJvmMetricSample>()
                        .in(AdhocJvmMetricSample::getInstanceId, instanceIds)
                        .between(AdhocJvmMetricSample::getSampleTime, new Date(start), new Date(end))
                        .orderByAsc(AdhocJvmMetricSample::getSampleTime));
        Map<String, List<JvmSamplePoint>> result = new LinkedHashMap<>();
        for (AdhocJvmMetricSample s : rows) {
            Long ts = s.getSampleTime() == null ? null : s.getSampleTime().getTime();
            JvmSamplePoint p = new JvmSamplePoint(ts,
                    s.getCpuUsagePct(), s.getSystemCpuUsagePct(), s.getSystemLoadAvg(), s.getPhysMemUsedPct(),
                    s.getHeapUsedMb(), s.getHeapUsedPct(), s.getHeapCommittedMb(), s.getHeapMaxMb(), s.getNonHeapUsedMb(),
                    s.getEdenUsedMb(), s.getOldUsedMb(),
                    s.getYoungGcCount(), s.getFullGcCount(), s.getGcTimeRatioPct(), s.getGcCount(), s.getGcTimeMs(),
                    s.getThreadCount(), s.getDaemonThreadCount(),
                    s.getDirectBufferUsedMb(), s.getLoadedClassCount());
            result.computeIfAbsent(s.getInstanceId(), k -> new ArrayList<>()).add(p);
        }
        return result;
    }

    /** 大盘穿透：Job 分页明细（跨用户，运维视图，不做 per-user 隔离）。
     *  时间窗复用 {@link #resolveRange}（与概览/分桶同口径）；size clamp 1..100（对齐 PageRequest 上限）；
     *  orderBy 白名单 {@link #mapOrderBy}（防注入，禁 ${}）。
     *  <p>耗时过滤口径与 {@link #getOverview} 的时长分桶一致（COALESCE(duration_ms, finish-submit)），故按桶穿透行数 = 桶计数。 */
    public IPage<JobVO> getJobPage(Long startMs, Long endMs, int hours, String status, String engineType,
                                   Long minDurationMs, Long maxDurationMs, long current, long size, String orderBy) {
        TimeRange tr = resolveRange(startMs, endMs, hours);
        long cur = current <= 0 ? 1 : current;
        long sz = size <= 0 ? 20 : Math.min(size, 100);
        // 仅当传了任一耗时边界时才下推耗时过滤（避免对无耗时过滤的查询强加 finish_time 非空限制）
        boolean hasDur = minDurationMs != null || maxDurationMs != null;
        Page<JobVO> page = new Page<>(cur, sz);
        return jobMapper.selectMetricsJobPage(page, tr.start, tr.end, status, engineType,
                hasDur ? minDurationMs : null, hasDur ? maxDurationMs : null, mapOrderBy(orderBy));
    }

    /** 排序白名单：非法/空值退回 submit_desc（与大盘明细默认倒序一致）。XML 侧 <choose> 仅认这几个分支，禁 ${}。 */
    private static String mapOrderBy(String o) {
        if (o == null) {
            return "submit_desc";
        }
        switch (o) {
            case "submit_asc": return "submit_asc";
            case "duration_desc": return "duration_desc";
            case "duration_asc": return "duration_asc";
            default: return "submit_desc";
        }
    }

    // ==================== 装配辅助 ====================

    /** List<{name,cnt}> -> Map<nameValue,Long>：key 取 name 列的**值**（如 "RUNNING"），value 取 cnt 列。
     *  注意：name 是列值（status/engineType 字符串），cnt 是计数；二者不可颠倒（旧版误把列名当 key、对列值 toLong，导致全 0）。 */
    private Map<String, Long> rowsToDist(List<Map<String, Object>> rows) {
        Map<String, Long> map = new LinkedHashMap<>();
        for (Map<String, Object> r : rows) {
            Object nameVal = pickNameValue(r);
            map.put(nameVal == null ? "(unknown)" : nameVal.toString(), toLong(r.get("cnt")));
        }
        return map;
    }

    /** List<{nameKey,cnt}> -> List<NameCount>（name 取指定列的值） */
    private List<MetricsTaskFailVO.NameCount> rowsToNameCount(List<Map<String, Object>> rows, String nameKey) {
        List<MetricsTaskFailVO.NameCount> list = new ArrayList<>(rows.size());
        for (Map<String, Object> r : rows) {
            Object nameVal = r.get(nameKey);
            list.add(new MetricsTaskFailVO.NameCount(nameVal == null ? "(unknown)" : nameVal.toString(), toLong(r.get("cnt"))));
        }
        return list;
    }

    /** 取 Map 中第一个非 cnt 列的**值**（name 列的值，如 status 的 "RUNNING"）。 */
    private Object pickNameValue(Map<String, Object> r) {
        for (Map.Entry<String, Object> e : r.entrySet()) {
            if (!"cnt".equalsIgnoreCase(e.getKey())) {
                return e.getValue();
            }
        }
        return null;
    }

    /** 把 [{instance, cnt}] 累加进 dst 的 idx 列（0=jobs,1=tasks）。instance 由 SQL COALESCE 保证非空，这里兜底。 */
    private static void accumulateInstanceLoad(Map<String, long[]> dst, List<Map<String, Object>> rows, int idx) {
        for (Map<String, Object> r : rows) {
            String instance = str(r.get("instance"));
            if (instance == null || instance.isEmpty()) {
                instance = "(未分配)";
            }
            dst.computeIfAbsent(instance, k -> new long[2])[idx] = toLong(r.get("cnt"));
        }
    }

    /** Map<instance,[jobs,tasks]> -> List<InstanceLoadRow>，按 jobs+tasks DESC。 */
    private static List<MetricsInstanceLoadVO.InstanceLoadRow> toInstanceLoadRows(Map<String, long[]> map) {
        List<MetricsInstanceLoadVO.InstanceLoadRow> list = new ArrayList<>(map.size());
        for (Map.Entry<String, long[]> e : map.entrySet()) {
            list.add(new MetricsInstanceLoadVO.InstanceLoadRow(e.getKey(), e.getValue()[0], e.getValue()[1]));
        }
        list.sort((a, b) -> Long.compare(b.getJobs() + b.getTasks(), a.getJobs() + a.getTasks()));
        return list;
    }

    private Map<String, Long> durationBuckets(Map<String, Object> dur) {
        Map<String, Long> m = new LinkedHashMap<>();
        m.put(DURATION_BUCKET_KEYS[0], toLong(dur.get("bucket0to1s")));
        m.put(DURATION_BUCKET_KEYS[1], toLong(dur.get("bucket1to10s")));
        m.put(DURATION_BUCKET_KEYS[2], toLong(dur.get("bucket10to60s")));
        m.put(DURATION_BUCKET_KEYS[3], toLong(dur.get("bucketGt60s")));
        return m;
    }

    /** 成功率 = successKey / 终态数 * 100。终态 = SUCCESS+FAILED+PARTIAL_FAILED+CANCELED。 */
    private Double successRate(Map<String, Long> dist, String successKey) {
        long terminal = getOrDefault(dist, "SUCCESS") + getOrDefault(dist, "FAILED")
                + getOrDefault(dist, "PARTIAL_FAILED") + getOrDefault(dist, "CANCELED");
        if (terminal == 0) {
            return 0d;
        }
        return round1(getOrDefault(dist, successKey) * 100.0 / terminal);
    }

    private static long sum(Map<String, Long> m) {
        return m.values().stream().mapToLong(Long::longValue).sum();
    }

    private static long getOrDefault(Map<String, Long> m, String k) {
        return m.getOrDefault(k, 0L);
    }

    /** 绝对时间区间（start/end 均闭区间）。统一支持「近 N 小时」预设与「自定义区间」。 */
    private static final class TimeRange {
        final Date start;
        final Date end;
        TimeRange(Date start, Date end) { this.start = start; this.end = end; }
    }

    /** 解析时间区间：自定义 startMs/endMs（前端 datetime-local 传 epoch 毫秒）优先，否则按 hours 预设（start=now-Nh, end=now）。
     *  clamp：区间上限 30d 防全表扫描；start>=end 或非法值退回 hours 预设。 */
    private static TimeRange resolveRange(Long startMs, Long endMs, int hours) {
        long now = System.currentTimeMillis();
        long start, end;
        if (startMs != null && endMs != null && startMs > 0 && endMs > startMs) {
            start = startMs;
            end = endMs;
        } else {
            int h = clampHours(hours);
            start = now - h * 3_600_000L;
            end = now;
        }
        long maxSpan = 30L * 86_400_000L;
        if (end - start > maxSpan) {
            start = end - maxSpan;
        }
        return new TimeRange(new Date(start), new Date(end));
    }

    private static int clampHours(int hours) {
        return hours <= 0 ? 24 : Math.min(hours, 720);
    }

    private static int clampLimit(int limit) {
        return limit <= 0 ? 10 : Math.min(limit, 100);
    }

    private static long toLong(Object o) {
        if (o == null) return 0L;
        if (o instanceof Number) return ((Number) o).longValue();
        try { return Long.parseLong(o.toString()); } catch (NumberFormatException e) { return 0L; }
    }

    /** 保留 null（toLong 会把 null 归 0；startTime 等"可能为空"字段用此，前端显示 -） */
    private static Long toLongNull(Object o) {
        if (o == null) return null;
        if (o instanceof Number) return ((Number) o).longValue();
        try { return Long.parseLong(o.toString()); } catch (NumberFormatException e) { return null; }
    }

    private static int toInt(Object o) {
        if (o == null) return 0;
        if (o instanceof Number) return ((Number) o).intValue();
        try { return Integer.parseInt(o.toString()); } catch (NumberFormatException e) { return 0; }
    }

    private static double toDouble(Object o) {
        if (o == null) return 0d;
        if (o instanceof Number) return ((Number) o).doubleValue();
        try { return Double.parseDouble(o.toString()); } catch (NumberFormatException e) { return 0d; }
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }

    private static double round1(double d) {
        return Math.round(d * 10.0) / 10.0;
    }

    /** submit_time 列转 epoch millis（Date / LocalDateTime / sql.Timestamp 兼容） */
    private static Long toEpochMillis(Object o) {
        if (o == null) return null;
        if (o instanceof java.util.Date) return ((java.util.Date) o).getTime();
        if (o instanceof Number) return ((Number) o).longValue();
        return null;
    }
}
