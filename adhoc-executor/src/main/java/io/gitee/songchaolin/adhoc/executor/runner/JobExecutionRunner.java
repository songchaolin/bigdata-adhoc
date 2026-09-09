package io.gitee.songchaolin.adhoc.executor.runner;

import io.gitee.songchaolin.adhoc.common.enums.FailReasonCategory;
import io.gitee.songchaolin.adhoc.common.enums.FailStage;
import io.gitee.songchaolin.adhoc.common.exception.AdhocErrorCode;
import io.gitee.songchaolin.adhoc.common.util.LogTiming;
import io.gitee.songchaolin.adhoc.dao.entity.AdhocQueryJob;
import io.gitee.songchaolin.adhoc.dao.mapper.AdhocQueryJobMapper;
import io.gitee.songchaolin.adhoc.executor.ha.ExecutorInstanceInfo;
import io.gitee.songchaolin.adhoc.executor.ha.RunningJobRegistry;
import io.gitee.songchaolin.adhoc.executor.ha.RunningTaskRegistry;
import io.gitee.songchaolin.adhoc.internal.grpc.v1.DispatchJobRequest;
import io.gitee.songchaolin.adhoc.sqlparser.model.ProcessedSqlSegment;
import org.apache.hadoop.security.UserGroupInformation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.security.PrivilegedExceptionAction;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Date;
import java.util.List;

/**
 * Job 执行 runner（重构版）：编排 job/task 生命周期，委托各协作类处理具体逻辑。
 * <ul>
 *   <li>Task 创建：{@link TaskCreator}</li>
 *   <li>Task 执行：{@link TaskExecutionPipeline}</li>
 *   <li>Job 聚合：{@link JobAggregator}</li>
 *   <li>取消检查：{@link CancelChecker}</li>
 * </ul>
 */
@Component
public class JobExecutionRunner {

    private static final Logger log = LoggerFactory.getLogger(JobExecutionRunner.class);

    private final AdhocQueryJobMapper jobMapper;
    private final RunningJobRegistry runningJobRegistry;
    private final LogBufferRegistry logBufferRegistry;
    private final ExecutorInstanceInfo instanceInfo;
    private final EngineSelector engineSelector;
    private final JobStateWriter jobStateWriter;
    private final RunningTaskRegistry runningTaskRegistry;

    // 协作类
    private final TaskCreator taskCreator;
    private final TaskExecutionPipeline taskPipeline;
    private final TaskLogger taskLogger;
    private final TaskStateTransition stateTransition;
    private final JobAggregator jobAggregator;
    private final CancelChecker cancelChecker;

    public JobExecutionRunner(AdhocQueryJobMapper jobMapper,
                              RunningJobRegistry runningJobRegistry,
                              LogBufferRegistry logBufferRegistry,
                              ExecutorInstanceInfo instanceInfo,
                              EngineSelector engineSelector,
                              JobStateWriter jobStateWriter,
                              RunningTaskRegistry runningTaskRegistry,
                              TaskCreator taskCreator,
                              TaskExecutionPipeline taskPipeline,
                              TaskLogger taskLogger,
                              TaskStateTransition stateTransition,
                              JobAggregator jobAggregator,
                              CancelChecker cancelChecker) {
        this.jobMapper = jobMapper;
        this.runningJobRegistry = runningJobRegistry;
        this.logBufferRegistry = logBufferRegistry;
        this.instanceInfo = instanceInfo;
        this.engineSelector = engineSelector;
        this.jobStateWriter = jobStateWriter;
        this.runningTaskRegistry = runningTaskRegistry;
        this.taskCreator = taskCreator;
        this.taskPipeline = taskPipeline;
        this.taskLogger = taskLogger;
        this.stateTransition = stateTransition;
        this.jobAggregator = jobAggregator;
        this.cancelChecker = cancelChecker;
    }

    public void run(DispatchJobRequest req) {
        String jobId = req.getJobId();
        String sqlPreview = truncateSql(req.getSqlContent());
        log.info("【Job开始】jobId={} 用户={} 引擎={} SQL={}", jobId, req.getUserId(), req.getEngineType(), sqlPreview);
        log.debug("【提交SQL】jobId={} {}", jobId, req.getSqlContent());  // 完整 SQL 改为 debug 级别

        LogBuffer jobLog = new LogBuffer();
        logBufferRegistry.putJobLog(jobId, jobLog);

        AdhocQueryJob jobMeta = jobMapper.selectById(jobId);
        long jobStartMs = System.currentTimeMillis();
        Date submitTime = jobMeta != null ? jobMeta.getSubmitTime() : null;
        Date dispatchTime = jobMeta != null ? jobMeta.getDispatchTime() : null;

        jobLog.append("[executor] [INFO] job " + jobId + " start, engine=" + req.getEngineType() + ", user=" + req.getUserId()
                + (dispatchTime != null ? " (dispatch->running=" + LogTiming.fmtMs(jobStartMs - dispatchTime.getTime()) + ")" : ""));

        runningJobRegistry.add(jobId);
        // taskIds/failureCtx 提升到 try 外，使 catch 块能收敛已入库但未执行的孤儿 Task（如 connect 失败）
        List<String> taskIds = null;
        final FailureContext failureCtx = new FailureContext();
        try {
            // 1. 选引擎
            EngineSelector.EngineContext eng = engineSelector.select(req.getEngineType(), req.getEngineInstance(), req.getUserId());

            // 2. 拆分 SQL
            List<ProcessedSqlSegment> segments = eng.processor.process(req.getSqlContent());
            if (segments.isEmpty()) {
                jobLog.append("[executor] [ERROR] job " + jobId + " no executable segment");
                log.warn("【Job无段】jobId={} 无可执行段", jobId);
                jobStateWriter.markNoSegments(jobId);
                return;
            }
            jobLog.append("[executor] [INFO] job " + jobId + " split into " + segments.size() + " segment(s) " + LogTiming.elapsed(jobStartMs));
            log.info("【拆分】jobId={} 拆为 {} 段", jobId, segments.size());

            // 3. 创建 Task（Task 先入库，再更新 Job 状态）
            long tasksEnqueueMs = System.currentTimeMillis();
            taskIds = taskCreator.create(req, jobId, segments, eng.engineInstance);

            // 4. 更新 Job 状态为 RUNNING（此时 Task 已入库，前端轮询能看到 Task 列表）
            jobStateWriter.markRunning(jobId, instanceInfo.getId());

            // 5. 执行 Task（顺序）
            final List<String> taskIdsRef = taskIds;  // effectively final 供 lambda 捕获

            // 代理用户上下文（仅 Kyuubi）
            if (eng.proxyUser != null) {
                UserGroupInformation proxyUGI = UserGroupInformation.createRemoteUser(eng.proxyUser);
                proxyUGI.doAs((PrivilegedExceptionAction<Void>) () -> {
                    executeAllTasks(req, jobId, eng, taskIdsRef, segments, jobLog, tasksEnqueueMs, failureCtx);
                    return null;
                });
            } else {
                executeAllTasks(req, jobId, eng, taskIdsRef, segments, jobLog, tasksEnqueueMs, failureCtx);
            }

            // 6. 聚合 Job
            jobAggregator.aggregate(jobId, jobLog, jobStartMs, submitTime, dispatchTime);
            log.info("【Job完成】jobId={}", jobId);

        } catch (Exception e) {
            // doAs 会把 SQLException 等非 IOException/InterruptedException 包成 UndeclaredThrowableException
            Throwable t = (e instanceof java.lang.reflect.UndeclaredThrowableException && e.getCause() != null)
                    ? e.getCause() : e;
            log.error("【Job异常】jobId={} 错误={}", jobId, t.getMessage(), t);
            jobLog.append("[executor] [ERROR] job " + jobId + " failed: " + t.getMessage());
            // 收敛孤儿 Task：Task 已入库但异常发生在真正执行完成前（最典型=connect 失败/权限错误），
            // 这些 Task 仍停留在 PENDING。语义与正常失败传播一致：报错的 Task=FAILED，余下=SKIPPED。
            // 注意：executeTaskWithFailurePropagation 内部 catch 吞掉了 Task 级异常并自行收敛，
            // 故冒泡到这里的异常必发生在 Task 循环之外（connect/select/process/doAs/aggregate）。
            markOrphanTasksFailed(jobId, taskIds, failureCtx, t, jobLog);
            jobStateWriter.markFailed(jobId);
            Date finishTime = new Date();
            jobLog.append("[executor] [INFO] [job=" + jobId + "] timing: " + LogTiming.jobTiming(submitTime, dispatchTime, jobStartMs, finishTime));
        } finally {
            runningJobRegistry.remove(jobId);
        }
    }

    /**
     * 收敛孤儿 Task：Job 级异常（如引擎连接失败/权限错误）发生在 Task 已入库但未执行完成时，
     * Task 会停留在 PENDING。语义与正常失败传播一致：报错的 Task=FAILED，余下 Task=SKIPPED。
     * <p>
     * 分两种情况：
     * <ul>
     *   <li>connect/前置阶段异常（{@code failureCtx} 未失败）：无 Task 真正执行过，把<b>第一个</b> Task
     *       标记 FAILED 作为 Job 级异常的承载者，其余 Task 标记 SKIPPED 指向它。</li>
     *   <li>循环中途 Task 执行异常冒泡（罕见，正常被内部 catch 吞掉）：若 {@code failureCtx} 已标记失败，
     *       失败的 Task 已是 FAILED（不动），其余 Task 标记 SKIPPED 指向它。</li>
     * </ul>
     * 幂等安全：{@link TaskStateWriter#markFailed}/{@code markSkipped} 带 status 守卫（仅 PENDING/RUNNING），
     * 已被正常收敛为终态的 Task 不会被覆盖。
     *
     * @param jobId       Job ID
     * @param taskIds     已创建的 Task ID 列表（可能为 null：异常发生在 create 之前）
     * @param failureCtx  失败上下文（含已失败的 Task 信息）
     * @param cause        Job 级异常
     * @param jobLog       Job 日志流
     */
    private void markOrphanTasksFailed(String jobId, List<String> taskIds,
                                       FailureContext failureCtx, Throwable cause, LogBuffer jobLog) {
        if (taskIds == null || taskIds.isEmpty()) {
            return;
        }
        String errMsg = cause != null ? cause.getClass().getSimpleName() + ": " + cause.getMessage() : "job-level failure";

        if (!failureCtx.isFailed()) {
            // connect/前置阶段异常：第一个 Task 承载 Job 级失败，其余跳过
            String failedTaskId = taskIds.get(0);
            stateTransition.toFailed(failedTaskId, FailStage.EXECUTING, FailReasonCategory.ENGINE_ERROR,
                    AdhocErrorCode.ADHOC_EXECUTOR_CRASHED.name(), "job-level failure: " + errMsg);
            taskLogger.error(jobLog, jobId, failedTaskId, "FAILED (orphan): " + errMsg);
            for (int i = 1; i < taskIds.size(); i++) {
                String skippedTaskId = taskIds.get(i);
                stateTransition.toSkipped(skippedTaskId, failedTaskId, "job-level failure: " + errMsg);
                taskLogger.error(jobLog, jobId, skippedTaskId, "SKIPPED (orphan): upstream " + failedTaskId + " failed");
            }
            log.warn("【孤儿Task收敛】jobId={} 前置异常，首Task={} FAILED，余 {} 个 SKIPPED", jobId, failedTaskId, taskIds.size() - 1);
        } else {
            // 循环中途异常冒泡：失败 Task 已收敛为 FAILED，其余指向它 SKIPPED
            String failedTaskId = failureCtx.getFailedTaskId();
            String reason = failureCtx.getFailedReason();
            for (String taskId : taskIds) {
                if (taskId.equals(failedTaskId)) {
                    continue;  // 失败 Task 已是 FAILED
                }
                stateTransition.toSkipped(taskId, failedTaskId, reason);
                taskLogger.error(jobLog, jobId, taskId, "SKIPPED (orphan): upstream " + failedTaskId + " failed");
            }
            log.warn("【孤儿Task收敛】jobId={} Task={} 已失败，余 {} 个 SKIPPED", jobId, failedTaskId, taskIds.size() - 1);
        }
    }

    private void executeAllTasks(DispatchJobRequest req, String jobId, EngineSelector.EngineContext eng,
                                 List<String> taskIds, List<ProcessedSqlSegment> segments,
                                 LogBuffer jobLog, long tasksEnqueueMs, FailureContext failureCtx) throws SQLException {
        // 优化：先检查是否所有 Task 都会被跳过（取消或失败传播）
        // 如果是，则不需要获取连接
        boolean anyTaskToExecute = false;
        for (int i = 0; i < segments.size(); i++) {
            // 检查取消
            if (cancelChecker.isCancelRequested(jobId)) {
                break;  // 所有剩余 Task 都会被取消
            }
            // 检查失败传播
            if (failureCtx.isFailed()) {
                continue;  // 当前 Task 会被跳过，但后续可能还有未失败的 Task
            }
            // 降级策略：只有 UNKNOWN 类型才标记为失败
            // valid=false 但 sqlType != UNKNOWN → 允许执行（降级解析成功）
            if (!segments.get(i).isValid() && "UNKNOWN".equals(segments.get(i).getSqlType())) {
                failureCtx.markFailed(taskIds.get(i), "syntax error: " + segments.get(i).getErrorMessage());
                continue;
            }
            // 有需要执行的 Task
            anyTaskToExecute = true;
            break;
        }

        // 如果没有需要执行的 Task，直接返回
        if (!anyTaskToExecute) {
            log.info("【Job无执行Task】jobId={} 所有Task被取消或跳过，不获取连接", jobId);
            // 执行跳过/取消逻辑（标记状态）
            for (int i = 0; i < segments.size(); i++) {
                executeTaskWithFailurePropagation(req, jobId, eng, null, taskIds.get(i), segments.get(i),
                        i, jobLog, tasksEnqueueMs, failureCtx);
            }
            return;
        }

        // 有需要执行的 Task，获取连接
        Connection conn = eng.executor.connect(eng.url, eng.user, eng.password, eng.proxyUser);
        try {
            eng.executor.registerCancelTarget(jobId, conn);
            for (int i = 0; i < segments.size(); i++) {
                executeTaskWithFailurePropagation(req, jobId, eng, conn, taskIds.get(i), segments.get(i),
                        i, jobLog, tasksEnqueueMs, failureCtx);
            }
        } finally {
            eng.executor.unregisterCancelTarget(jobId);
            closeQuietly(conn, jobId);
        }
    }

    private void executeTaskWithFailurePropagation(DispatchJobRequest req, String jobId,
                                                    EngineSelector.EngineContext eng, Connection conn,
                                                    String taskId, ProcessedSqlSegment segment, int index,
                                                    LogBuffer jobLog, long tasksEnqueueMs,
                                                    FailureContext failureCtx) {
        // 优先级1: 检查取消请求（最高优先级）
        if (cancelChecker.isCancelRequested(jobId)) {
            taskLogger.info(jobLog, jobId, taskId, "skipped due to cancel requested");
            stateTransition.toCanceled(taskId);
            return;
        }

        // 优先级2: 检查上游失败传播
        if (failureCtx.isFailed()) {
            skipDueToPriorFailure(jobId, taskId, jobLog, failureCtx);
            return;
        }

        // 优先级3: 检查语法错误（降级策略：只有 UNKNOWN 类型才标记为失败）
        // valid=false 但 sqlType != UNKNOWN → 允许执行（降级解析成功，记录警告）
        if (!segment.isValid()) {
            if ("UNKNOWN".equals(segment.getSqlType())) {
                // 降级解析也无法识别，标记为失败
                handleSyntaxError(jobId, taskId, segment, jobLog, failureCtx);
                return;
            }
            // 降级解析成功识别类型，记录警告但允许执行
            jobLog.append("[executor] [WARN] [job=" + jobId + "][task=" + taskId + "] SQL 解析降级但允许执行: sqlType=" + segment.getSqlType()
                    + ", error=" + segment.getErrorMessage());
        }

        // 优先级4: 执行 Task
        // 注意：如果 conn=null，说明所有 Task 都被跳过，不应该到达这里
        if (conn == null) {
            log.error("【Task执行异常】jobId={} taskId={} conn=null 但Task需要执行", jobId, taskId);
            taskLogger.error(jobLog, jobId, taskId, "INTERNAL ERROR: connection is null");
            stateTransition.toFailed(taskId, FailStage.EXECUTING, FailReasonCategory.ENGINE_ERROR,
                    AdhocErrorCode.ADHOC_EXECUTOR_CRASHED.name(), "connection is null");
            failureCtx.markFailed(taskId, "connection is null");
            return;
        }

        runningTaskRegistry.add(taskId);
        try {
            TaskExecutionContext ctx = new TaskExecutionContext(taskId, jobId, index, segment, conn, eng, req, jobLog, tasksEnqueueMs);
            TaskExecutionPipeline.Result result = taskPipeline.execute(ctx);

            // 记录分隔符
            taskLogger.separator(jobLog, jobId, taskId, segment.getSqlType(),
                    result == TaskExecutionPipeline.Result.SUCCESS || result == TaskExecutionPipeline.Result.CACHED);

            // 记录失败上下文
            if (result == TaskExecutionPipeline.Result.FAILED) {
                failureCtx.markFailed(taskId, "execution failed");
            }
        } catch (Exception e) {
            log.error("【Task异常】jobId={} taskId={} 未捕获异常={}", jobId, taskId, e.getMessage(), e);
            taskLogger.error(jobLog, jobId, taskId, "uncaught exception: " + e.getMessage());
            stateTransition.toFailed(taskId, FailStage.EXECUTING, FailReasonCategory.ENGINE_ERROR,
                    AdhocErrorCode.ADHOC_EXECUTOR_CRASHED.name(), e.getMessage());
            failureCtx.markFailed(taskId, e.getMessage());
        } finally {
            runningTaskRegistry.remove(taskId);
        }
    }

    private void skipDueToPriorFailure(String jobId, String taskId, LogBuffer jobLog, FailureContext failureCtx) {
        String reason = failureCtx.getFailedTaskId()
                + (failureCtx.getFailedReason() != null ? " (" + failureCtx.getFailedReason() + ")" : "");
        String msg = "skipped due to upstream task " + reason + " failure";
        stateTransition.toSkipped(taskId, failureCtx.getFailedTaskId(), failureCtx.getFailedReason());
        taskLogger.error(jobLog, jobId, taskId, "SKIPPED: " + msg);
        log.info("【Task跳过】jobId={} taskId={} 上游task={}", jobId, taskId, failureCtx.getFailedTaskId());
    }

    private void handleSyntaxError(String jobId, String taskId, ProcessedSqlSegment segment,
                                   LogBuffer jobLog, FailureContext failureCtx) {
        String err = segment.getErrorMessage();
        stateTransition.toSyntaxError(taskId, err);
        taskLogger.error(jobLog, jobId, taskId, "syntax error: " + err);
        log.warn("【Task语法错误】jobId={} taskId={} 错误={}", jobId, taskId, err);
        failureCtx.markFailed(taskId, "syntax error: " + err);
    }

    private void closeQuietly(Connection conn, String jobId) {
        try {
            conn.close();
        } catch (Exception ce) {
            log.debug("close connection for {} failed (expected after cancel): {}", jobId, ce.getMessage());
        }
    }

    /**
     * 截断 SQL 用于日志预览（避免刷屏）
     *
     * @param sql 原始 SQL
     * @return 截断后的 SQL（最多 200 字符）
     */
    private String truncateSql(String sql) {
        if (sql == null) {
            return "(null)";
        }
        if (sql.length() <= 200) {
            return sql;
        }
        return sql.substring(0, 200) + "... (truncated, total " + sql.length() + " chars)";
    }
}