package io.gitee.songchaolin.adhoc.server.service;

import io.gitee.songchaolin.adhoc.common.config.ConfigHolder;
import io.gitee.songchaolin.adhoc.common.enums.JobStatus;
import io.gitee.songchaolin.adhoc.common.enums.TaskStatus;
import io.gitee.songchaolin.adhoc.common.exception.AdhocErrorCode;
import io.gitee.songchaolin.adhoc.common.exception.AdhocException;
import io.gitee.songchaolin.adhoc.common.util.LogConstants;
import io.gitee.songchaolin.adhoc.dao.entity.AdhocQueryJob;
import io.gitee.songchaolin.adhoc.dao.entity.AdhocQueryTask;
import io.gitee.songchaolin.adhoc.dao.mapper.AdhocQueryJobMapper;
import io.gitee.songchaolin.adhoc.dao.mapper.AdhocQueryTaskMapper;
import io.gitee.songchaolin.adhoc.internal.grpc.v1.FetchJobLogResponse;
import io.gitee.songchaolin.adhoc.internal.grpc.v1.FetchLogResponse;
import io.gitee.songchaolin.adhoc.internal.grpc.v1.ServerJobLogResponse;
import io.gitee.songchaolin.adhoc.server.config.AdhocServerConfig;
import io.gitee.songchaolin.adhoc.server.auth.OwnershipChecker;
import io.gitee.songchaolin.adhoc.server.grpc.ExecutorChannelPool;
import io.gitee.songchaolin.adhoc.server.grpc.ServerReconcileClient;
import io.gitee.songchaolin.adhoc.server.ha.JobLog;
import io.gitee.songchaolin.adhoc.server.ha.JobLogRegistry;
import io.gitee.songchaolin.adhoc.server.ha.ServerInstanceInfo;
import io.gitee.songchaolin.adhoc.server.ha.TerminalLogCache;
import io.gitee.songchaolin.adhoc.common.dto.LogResponse;
import io.gitee.songchaolin.adhoc.storage.spi.StorageClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Log 查询服务：
 * - task 日志：RUNNING 且有 executor_instance -> gRPC fetchLog 读 executor 内存（实时）；
 *   terminal 或 gRPC 失败兜底读 job 日志并按 taskId 过滤（task 日志不再单独写 OSS，已合并到 job 日志，
 *   各 task 行带 [job=X][task=Y] 标记，按 [task=ID] 过滤，只返回该 task 相关行）。
 * - job 日志：server 拥有。running 读内存 JobLogRegistry（本节点）；不在本节点则转发到 processing_server 节点；
 *   terminal / 转发失败或未找到 -> 读 OSS（job.persistent_log_path，server collector 刷）。
 *
 * <p><b>分页与完整信号分离</b>（参见 {@link LogResponse}）：
 * <ul>
 *   <li><b>hasMore</b>：纯分页信号--当前数据源里 offset 之后是否还有未读行（size-based），与日志是否结束无关。</li>
 *   <li><b>complete</b>：日志是否不再增长（客户端可停止轮询）。两路判定：
 *       ① 含 {@link LogConstants#COMPLETE_MARKER}（collector/executor 兜底已 finalize）-> true，正常路径无 race；
 *       ② 标识缺失但 Job 已终态且 finish_time 超 {@link AdhocServerConfig#LOG_COMPLETE_GRACE_MS}
 *          （executor 崩溃致标识始终未写）-> true，兜底防客户端死循环。grace 须 &gt; executor reconcile-scan-interval-ms，
 *          确保 executor 兜底有机会写标识前不提前判 complete。刚转终态但未 finalize（无标识、未超 grace）-> false。</li>
 * </ul>
 * 客户端轮询契约：hasMore=true 翻页；hasMore=false 且 complete=false 仍须继续轮询（日志尚未 finalize，后续会增长）；
 * hasMore=false 且 complete=true 停止。complete 由 Job 终态驱动（标识或 grace 兜底），不再仅靠日志文本，避免标识丢失致死循环。
 *
 * <p>terminal 日志经 {@link TerminalLogCache} 缓存（仅 finalize 日志），分页/轮询复用，避免每次整文件下载 OSS。
 */
@Service
public class LogQueryService {

    private static final Logger log = LoggerFactory.getLogger(LogQueryService.class);

    private final AdhocQueryTaskMapper taskMapper;
    private final AdhocQueryJobMapper jobMapper;
    private final StorageClient storageClient;
    private final ExecutorChannelPool executorChannelPool;
    private final JobLogRegistry jobLogRegistry;
    private final ServerReconcileClient serverReconcileClient;
    private final ServerInstanceInfo serverInstanceInfo;
    private final TerminalLogCache terminalLogCache;
    private final ConfigHolder cfg;
    private final OwnershipChecker ownershipChecker;

    public LogQueryService(AdhocQueryTaskMapper taskMapper, AdhocQueryJobMapper jobMapper,
                           StorageClient storageClient, ExecutorChannelPool executorChannelPool,
                           JobLogRegistry jobLogRegistry, ServerReconcileClient serverReconcileClient,
                           ServerInstanceInfo serverInstanceInfo, TerminalLogCache terminalLogCache,
                           ConfigHolder cfg, OwnershipChecker ownershipChecker) {
        this.taskMapper = taskMapper;
        this.jobMapper = jobMapper;
        this.storageClient = storageClient;
        this.executorChannelPool = executorChannelPool;
        this.jobLogRegistry = jobLogRegistry;
        this.serverReconcileClient = serverReconcileClient;
        this.serverInstanceInfo = serverInstanceInfo;
        this.terminalLogCache = terminalLogCache;
        this.cfg = cfg;
        this.ownershipChecker = ownershipChecker;
    }

    public LogResponse getTaskLog(String taskId, long offset, int limit, String accessUserId) {
        AdhocQueryTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new AdhocException(AdhocErrorCode.ADHOC_JOB_NOT_FOUND);
        }
        AdhocQueryJob job = jobMapper.selectById(task.getJobId());
        if (job == null) {
            throw new AdhocException(AdhocErrorCode.ADHOC_JOB_NOT_FOUND);
        }
        // 归属校验：本人或管理员放行（metric token/gRPC 传 null=系统级放行）
        ownershipChecker.requireAccess(accessUserId, task.getUserId());
        boolean running = TaskStatus.isRunning(task.getStatus());
        if (running && task.getExecutorInstance() != null) {
            try {
                FetchLogResponse resp = executorChannelPool.fetchLog(task.getExecutorInstance(), taskId, offset, limit);
                // running task：hasMore 按页大小判断；complete=false（task 日志不含 job 级 COMPLETE 标识，未 finalize）-> 客户端续轮询
                return new LogResponse(new ArrayList<>(resp.getLinesList()), offset, limit,
                        resp.getHasMore(), false);
            } catch (Exception e) {
                // executor 不可达，兜底读 job 日志按 task 过滤（task 日志已合并到 job 日志）
            }
        }
        // task 日志不再写 OSS（内容已合并到 job 日志）：terminal/兜底读 job 日志并按 taskId 过滤
        if (job.getPersistentLogPath() != null) {
            return readOssTaskLog(job, taskId, offset, limit);
        }
        // 无 job 日志可读（job 未刷 OSS）：complete 走 isJobLogComplete（task 日志已并入 job 日志，随 job 终态 + grace 判）
        return new LogResponse(new ArrayList<>(), offset, limit, running, isJobLogComplete(new ArrayList<>(), job));
    }

    public LogResponse getJobLog(String jobId, long offset, int limit, String accessUserId) {
        AdhocQueryJob job = jobMapper.selectById(jobId);
        if (job == null) {
            throw new AdhocException(AdhocErrorCode.ADHOC_JOB_NOT_FOUND);
        }
        // 归属校验：本人或管理员放行（metric token/gRPC 传 null=系统级放行）
        ownershipChecker.requireAccess(accessUserId, job.getUserId());
        boolean running = JobStatus.isRunning(job.getStatus());
        if (running) {
            // 本节点处理：读内存
            JobLog jobLog = jobLogRegistry.get(jobId);
            if (jobLog != null) {
                return pageFromLines(jobLog.snapshot(), offset, limit);
            }
            // 不在本节点：转发到 processing_server（异常/未找到则继续 fallback）
            String processingServer = job.getProcessingServerInstance();
            if (processingServer != null && !processingServer.isEmpty()
                    && !processingServer.equals(serverInstanceInfo.getId())) {
                try {
                    ServerJobLogResponse resp = serverReconcileClient.fetchJobLog(processingServer, jobId, offset, limit);
                    if (resp.getFound()) {
                        // hasMore 按上游分页；complete 信任上游（上游据其 registry 快照判断，running 时为 false）
                        return new LogResponse(new ArrayList<>(resp.getLinesList()), offset, limit,
                                resp.getHasMore(), resp.getComplete());
                    }
                } catch (Exception e) {
                    log.warn("forward getJobLog to server {} failed: {}", processingServer, e.getMessage());
                }
            }
            // processing_server=NULL（迁移中）或本节点异常：直读 executor 内存（迁移期间临时实时读）
            if (job.getExecutorInstance() != null) {
                try {
                    FetchJobLogResponse resp = executorChannelPool.fetchJobLog(job.getExecutorInstance(), jobId, offset, limit);
                    // hasMore 按页大小；complete 据 executor 返回行判断（executor 内存不含 job 级 COMPLETE，恒 false）-> running 须续轮询
                    return new LogResponse(new ArrayList<>(resp.getLinesList()), offset, limit,
                            resp.getHasMore(), isComplete(resp.getLinesList()));
                } catch (Exception e) {
                    log.warn("forward getJobLog to executor {} failed: {}", job.getExecutorInstance(), e.getMessage());
                }
            }
        }
        // terminal / 本节点 registry 无且无 processing_server / 转发失败或未找到 -> OSS
        if (job.getPersistentLogPath() == null) {
            // 无日志源（刚派发/迁移中/terminal 未刷日志）：complete = isJobLogComplete--非终态或刚终态未超 grace 均 false
            // （续轮询，防 collector 终态轮刷前误判完成漏读终态页）；终态超 grace 仍无日志才 true
            return new LogResponse(new ArrayList<>(), offset, limit, running, isJobLogComplete(new ArrayList<>(), job));
        }
        return readOssLog(job, offset, limit);
    }

    private LogResponse pageFromLines(List<String> all, long offset, int limit) {
        List<String> page = new ArrayList<>();
        for (long i = offset; i < all.size() && page.size() < limit; i++) {
            page.add(all.get((int) i));
        }
        // hasMore：当前快照是否还有未读行（size-based）。complete：据全量快照是否含 COMPLETE 标识（检查 all 而非 page，
        // job 已 finalize 且 offset 越界时 complete 仍正确，避免空页误判未完整）。
        boolean hasMore = offset + page.size() < all.size();
        boolean complete = isComplete(all);
        return new LogResponse(page, offset, limit, hasMore, complete);
    }

    /** 日志是否已完整：含 server 终态轮追加的 COMPLETE 标识（委托 {@link LogConstants#containsComplete}）。 */
    private static boolean isComplete(List<String> lines) {
        return LogConstants.containsComplete(lines);
    }

    /**
     * Job 日志 complete 信号（OSS / 终态读取路径用，解决"标识丢失致客户端死循环"）：
     * <ol>
     *   <li>含 {@link LogConstants#COMPLETE_MARKER}（collector/executor 兜底已 finalize）-> true，正常路径无 race；</li>
     *   <li>标识缺失但 Job 已终态且 finish_time 超 {@link AdhocServerConfig#LOG_COMPLETE_GRACE_MS}
     *       （executor 崩溃致标识始终未写）-> true，兜底防死循环。grace 须 &gt; executor reconcile-scan-interval-ms，
     *       确保 executor 兜底写标识前不提前判 complete 漏读终态页。</li>
     * </ol>
     * 刚转终态但未 finalize（无标识、未超 grace）-> false，客户端续轮询直至拉到终态页。
     */
    private boolean isJobLogComplete(List<String> lines, AdhocQueryJob job) {
        if (LogConstants.containsComplete(lines)) {
            return true;
        }
        if (job != null && JobStatus.isTerminal(job.getStatus())) {
            Date finish = job.getFinishTime();
            if (finish != null) {
                return System.currentTimeMillis() - finish.getTime() >= cfg.get(AdhocServerConfig.LOG_COMPLETE_GRACE_MS);
            }
        }
        return false;
    }

    private LogResponse readOssLog(AdhocQueryJob job, long offset, int limit) {
        String key = job.getPersistentLogPath();
        // 仅含 COMPLETE 标识（真 finalize、不可变）的日志走 TTL 缓存；缺标识（含 grace 兜底判 complete 的）不缓存--
        // 后者可能仍被 collector/executor 兜底追加标识，缓存即陈旧。
        List<String> all = terminalLogCache.get(key);
        if (all == null) {
            all = loadOssLogLines(key);
            if (LogConstants.containsComplete(all)) {
                terminalLogCache.put(key, all);
            }
        }
        List<String> page = new ArrayList<>();
        for (long i = offset; i < all.size() && page.size() < limit; i++) {
            page.add(all.get((int) i));
        }
        // hasMore：size-based（全量行是否还有未读）。complete：见 isJobLogComplete（标识优先，Job 终态 + grace 兜底）。
        boolean hasMore = offset + page.size() < all.size();
        boolean complete = isJobLogComplete(all, job);
        return new LogResponse(page, offset, limit, hasMore, complete);
    }

    /**
     * task 日志读取：从 job 日志中按 taskId 过滤行（task 日志不再单独写 OSS，已合并到 job 日志，
     * 按 [task=ID] 标记过滤）。复用 {@link TerminalLogCache} 缓存的整段 job 日志，不额外下载 OSS。
     * <p>分页作用在<b>过滤后</b>的行上（offset/limit 为该 task 行内的偏移，与 /api/job/log 的全量分页不同构）；
     * complete 仍据<b>全量</b> job 日志判定--COMPLETE 标识在 job 级行（不在过滤后的 task 行内），但 grace 兜底仍有效。
     */
    private LogResponse readOssTaskLog(AdhocQueryJob job, String taskId, long offset, int limit) {
        String key = job.getPersistentLogPath();
        List<String> all = terminalLogCache.get(key);
        if (all == null) {
            all = loadOssLogLines(key);
            if (LogConstants.containsComplete(all)) {
                terminalLogCache.put(key, all);
            }
        }
        List<String> filtered = filterTaskLines(all, taskId);
        List<String> page = new ArrayList<>();
        for (long i = offset; i < filtered.size() && page.size() < limit; i++) {
            page.add(filtered.get((int) i));
        }
        boolean hasMore = offset + page.size() < filtered.size();
        // complete 用全量 all 判定（COMPLETE 标识在 job 级行，不在 filtered 内；grace 兜底仍有效）
        boolean complete = isJobLogComplete(all, job);
        return new LogResponse(page, offset, limit, hasMore, complete);
    }

    /**
     * 按 taskId 过滤日志行：方案B 后所有 task 行统一 [job=X][task=Y] 格式，按 [task=ID] 精确匹配；
     * 兼容历史日志的 "task ID" 旧格式（error/success/separator）。job 级行（开始/结束/耗时/COMPLETE 标识）
     * 无 task 标记，被过滤掉--task 日志只含该 task 自身行。
     */
    private static List<String> filterTaskLines(List<String> lines, String taskId) {
        if (taskId == null || taskId.isEmpty()) {
            return new ArrayList<>(lines);
        }
        String marker = "[task=" + taskId + "]";
        String legacyMarker = "task " + taskId;
        List<String> result = new ArrayList<>();
        for (String line : lines) {
            if (line != null && (line.contains(marker) || line.contains(legacyMarker))) {
                result.add(line);
            }
        }
        return result;
    }

    /** 全量读入 OSS 日志行。terminal 日志有 JobLog MAX_LINES 上限，可控；running 降级场景偶发，可接受。 */
    private List<String> loadOssLogLines(String key) {
        List<String> all = new ArrayList<>();
        try (InputStream is = storageClient.download(key);
             BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                all.add(line);
            }
        } catch (Exception e) {
            throw new RuntimeException("OSS log read failed: " + key, e);
        }
        return all;
    }
}
