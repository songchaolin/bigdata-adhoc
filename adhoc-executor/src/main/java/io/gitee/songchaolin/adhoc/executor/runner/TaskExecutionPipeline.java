package io.gitee.songchaolin.adhoc.executor.runner;

import io.gitee.songchaolin.adhoc.common.enums.FailReasonCategory;
import io.gitee.songchaolin.adhoc.common.enums.FailStage;
import io.gitee.songchaolin.adhoc.common.exception.AdhocErrorCode;
import io.gitee.songchaolin.adhoc.common.util.LogTiming;
import io.gitee.songchaolin.adhoc.common.util.SqlHashUtil;
import io.gitee.songchaolin.adhoc.common.util.SqlTypeUtils;
import io.gitee.songchaolin.adhoc.executor.engine.LogSink;
import io.gitee.songchaolin.adhoc.executor.engine.QueryResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Task 执行管道
 * <p>
 * 协调状态转换、日志记录、结果写入、复用检查
 */
@Component
public class TaskExecutionPipeline {

    private static final Logger log = LoggerFactory.getLogger(TaskExecutionPipeline.class);

    /** 日志SQL预览最大长度 */
    private static final int SQL_PREVIEW_MAX_LEN = 200;

    private final TaskStateTransition stateTransition;
    private final TaskLogger taskLogger;
    private final TaskResultWriter resultWriter;
    private final TaskReuseHandler reuseHandler;
    private final CancelChecker cancelChecker;
    private final SqlLimitEnforcer limitEnforcer;

    public TaskExecutionPipeline(TaskStateTransition stateTransition, TaskLogger taskLogger,
                                 TaskResultWriter resultWriter, TaskReuseHandler reuseHandler,
                                 CancelChecker cancelChecker, SqlLimitEnforcer limitEnforcer) {
        this.stateTransition = stateTransition;
        this.taskLogger = taskLogger;
        this.resultWriter = resultWriter;
        this.reuseHandler = reuseHandler;
        this.cancelChecker = cancelChecker;
        this.limitEnforcer = limitEnforcer;
    }

    /** 执行结果枚举 */
    public enum Result {
        SUCCESS,    // 执行成功
        CACHED,     // 复用命中
        FAILED,     // 执行失败
        CANCELED    // 被取消
    }

    /**
     * 执行单个 Task
     *
     * @param ctx 执行上下文
     * @return 执行结果
     */
    public Result execute(TaskExecutionContext ctx) {
        ctx.setStartTime(System.currentTimeMillis());

        try {
            String originalSql = ctx.segment.getSql();
            String executedSql = limitEnforcer.enforce(originalSql, ctx.segment.getSqlType());
            ctx.setExecutedSql(executedSql);

            // 日志记录：prefix/submitted/executed（截断SQL避免刷屏）
            taskLogger.info(ctx.jobLog, ctx.jobId, ctx.taskId,
                    "prefix(set/use): " + (ctx.segment.getPrefixSql() != null && !ctx.segment.getPrefixSql().isEmpty()
                            ? truncateSqlForLog(ctx.segment.getPrefixSql()) : "(none)"));
            taskLogger.info(ctx.jobLog, ctx.jobId, ctx.taskId, "submitted sql: " + truncateSqlForLog(originalSql));
            taskLogger.info(ctx.jobLog, ctx.jobId, ctx.taskId, "executed sql: " + truncateSqlForLog(executedSql));
            log.info("【Task执行】jobId={} taskId={} 段#{} SQL长度={}", ctx.jobId, ctx.taskId, ctx.segmentIndex + 1, executedSql.length());
            log.debug("【Task执行详情】jobId={} taskId={} 段#{} 执行SQL={}", ctx.jobId, ctx.taskId, ctx.segmentIndex + 1, executedSql);

            // 结果复用检查：按结果指纹查（engine+instance+prefix+规整后SQL），与 TaskCreator 写入口径一致
            if (SqlTypeUtils.hasResultSet(ctx.segment.getSqlType())) {
                String executedSqlHash = SqlHashUtil.resultFingerprintHash(
                        ctx.request.getEngineType(), ctx.engine.engineInstance,
                        ctx.segment.getPrefixSql(), executedSql);
                String reusedFrom = reuseHandler.checkReuse(ctx.request.getUserId(), executedSqlHash);
                if (reusedFrom != null) {
                    return executeReuse(ctx, reusedFrom, executedSqlHash);
                }
            }

            // 执行新查询
            return executeFresh(ctx);

        } catch (Exception e) {
            ctx.setEndTime(System.currentTimeMillis());
            return handleException(ctx, e);
        }
    }

    private Result executeReuse(TaskExecutionContext ctx, String sourceTaskId, String sqlHash) {
        taskLogger.cacheHit(ctx.jobLog, ctx.jobId, ctx.taskId, sourceTaskId);
        reuseHandler.markReused(ctx.taskId, sourceTaskId, sqlHash, ctx.getStartTimeMs());
        taskLogger.info(ctx.jobLog, ctx.jobId, ctx.taskId,
                "timing: " + LogTiming.taskTiming(ctx.tasksEnqueueMs, ctx.getStartTimeMs(), ctx.getEndTimeMs()));
        taskLogger.success(ctx.jobLog, ctx.jobId, ctx.taskId, true);
        log.info("【Task复用】jobId={} taskId={} 复用自={}", ctx.jobId, ctx.taskId, sourceTaskId);
        return Result.CACHED;
    }

    private Result executeFresh(TaskExecutionContext ctx) throws Exception {
        stateTransition.toRunning(ctx.taskId, ctx.getStartTimeMs());
        taskLogger.info(ctx.jobLog, ctx.jobId, ctx.taskId, "start, sql=" + truncateSqlForLog(ctx.getExecutedSql()));

        // 执行 prefix
        executePrefix(ctx);

        // 执行主查询
        stateTransition.toFetching(ctx.taskId);
        long queryStartMs = System.currentTimeMillis();
        // 引擎诊断日志透传：加 [job=X][task=Y] 前缀，使 /api/task/log 能按 task 过滤
        LogSink serverLogSink = line -> ctx.jobLog.append(
                "[executor] [INFO] [job=" + ctx.jobId + "][task=" + ctx.taskId + "] [Kyuubi] " + line);
        QueryResult qr = ctx.engine.executor.executeQuery(ctx.connection, ctx.jobId, ctx.getExecutedSql(), serverLogSink);
        long queryCostMs = System.currentTimeMillis() - queryStartMs;

        // 结果写入
        boolean hasResult = qr.isHasResultSet() && SqlTypeUtils.hasResultSet(ctx.segment.getSqlType());
        long writeStartMs = 0L;  // 仅 hasResult 时有意义，传给 toWriting 落 write_start_time
        if (hasResult) {
            writeStartMs = System.currentTimeMillis();
            stateTransition.toWriting(ctx.taskId, writeStartMs);
            ctx.jobLog.append(resultWriter.write(ctx.taskId, ctx.jobId, qr, ctx.getStartTimeMs()));
            long writeCostMs = System.currentTimeMillis() - writeStartMs;
            taskLogger.info(ctx.jobLog, ctx.jobId, ctx.taskId,
                    String.format("result write cost=%dms", writeCostMs));
            log.info("【结果写入耗时】jobId={} taskId={} cost={}ms", ctx.jobId, ctx.taskId, writeCostMs);
        } else {
            long uc = qr.getUpdateCount();
            stateTransition.updateAffectedRows(ctx.taskId, uc < 0 ? null : uc);
            taskLogger.info(ctx.jobLog, ctx.jobId, ctx.taskId,
                    "executed (no result set, DDL/DML) affected=" + (uc < 0 ? 0 : uc)
                            + " " + LogTiming.elapsed(ctx.getStartTimeMs()));
        }

        // 查询耗时
        taskLogger.info(ctx.jobLog, ctx.jobId, ctx.taskId,
                String.format("query executed, cost=%dms", queryCostMs));
        log.info("【查询耗时】jobId={} taskId={} cost={}ms", ctx.jobId, ctx.taskId, queryCostMs);

        // 标记成功（有结果集：合并 has_result_set/scan_rows/oss_upload_time/write_start_time 到一次 UPDATE）
        ctx.setEndTime(System.currentTimeMillis());
        try {
            if (hasResult) {
                stateTransition.toSuccess(ctx.taskId, ctx.getDurationMs(), qr.getRows().size());
            } else {
                stateTransition.toSuccess(ctx.taskId, ctx.getDurationMs());
            }
        } catch (Exception me) {
            log.error("【Task状态写失败】jobId={} taskId={} 结果已落盘，不回退 FAILED", ctx.jobId, ctx.taskId, me);
        }

        taskLogger.info(ctx.jobLog, ctx.jobId, ctx.taskId,
                "timing: " + LogTiming.taskTiming(ctx.tasksEnqueueMs, ctx.getStartTimeMs(), ctx.getEndTimeMs()));
        taskLogger.success(ctx.jobLog, ctx.jobId, ctx.taskId, false);
        log.info("【Task成功】jobId={} taskId={}", ctx.jobId, ctx.taskId);

        return Result.SUCCESS;
    }

    private void executePrefix(TaskExecutionContext ctx) throws Exception {
        if (ctx.segment.getPrefixSql() != null && !ctx.segment.getPrefixSql().isEmpty()) {
            long prefixStartMs = System.currentTimeMillis();
            ctx.engine.executor.executeSessionSql(ctx.connection, ctx.segment.getPrefixSql());
            long prefixCostMs = System.currentTimeMillis() - prefixStartMs;
            taskLogger.info(ctx.jobLog, ctx.jobId, ctx.taskId,
                    String.format("prefix (SET/USE) executed, cost=%dms", prefixCostMs));
            log.info("【Prefix耗时】jobId={} taskId={} cost={}ms", ctx.jobId, ctx.taskId, prefixCostMs);
        }
    }

    private Result handleException(TaskExecutionContext ctx, Exception e) {
        taskLogger.info(ctx.jobLog, ctx.jobId, ctx.taskId,
                "timing: " + LogTiming.taskTiming(ctx.tasksEnqueueMs, ctx.getStartTimeMs(), ctx.getEndTimeMs()));

        if (cancelChecker.isCancelRequested(ctx.jobId)) {
            taskLogger.info(ctx.jobLog, ctx.jobId, ctx.taskId, "canceled by cancel request");
            stateTransition.toCanceled(ctx.taskId);
            log.info("【Task取消】jobId={} taskId={}", ctx.jobId, ctx.taskId);
            return Result.CANCELED;
        }

        // 错误时打印完整SQL用于排查
        log.error("【Task失败】jobId={} taskId={} 错误={}", ctx.jobId, ctx.taskId, e.getMessage(), e);
        log.error("【Task失败SQL】jobId={} taskId={} SQL={}", ctx.jobId, ctx.taskId, ctx.getExecutedSql());
        taskLogger.error(ctx.jobLog, ctx.jobId, ctx.taskId, "FAILED: " + e.getMessage());

        // 异常分类：查询超时单独标记（JDBC setQueryTimeout 或服务端 query_timeout 触发），其余归 ENGINE_ERROR
        if (isQueryTimeout(e)) {
            stateTransition.toFailed(ctx.taskId, FailStage.EXECUTING, FailReasonCategory.QUERY_TIMEOUT,
                    AdhocErrorCode.ADHOC_QUERY_TIMEOUT.name(), e.getMessage());
            log.warn("【Task超时】jobId={} taskId={}", ctx.jobId, ctx.taskId);
        } else {
            stateTransition.toFailed(ctx.taskId, FailStage.EXECUTING, FailReasonCategory.ENGINE_ERROR,
                    AdhocErrorCode.ADHOC_EXECUTOR_CRASHED.name(), e.getMessage());
        }
        return Result.FAILED;
    }

    /** 判断异常是否查询超时：遍历 cause 链，message 含 "timeout"（覆盖 JDBC setQueryTimeout 的
     *  "Statement cancelled due to timeout" 与服务端 "Query timeout"）。v1 启发式，后续可按 SQLState/vendorCode 细化。 */
    private static boolean isQueryTimeout(Throwable e) {
        Throwable t = e;
        while (t != null) {
            String msg = t.getMessage();
            if (msg != null && msg.toLowerCase().contains("timeout")) {
                return true;
            }
            t = t.getCause();
        }
        return false;
    }

    /**
     * 截断SQL用于日志输出（避免刷屏）
     *
     * @param sql 原始SQL
     * @return 截断后的SQL（最多200字符）
     */
    private String truncateSqlForLog(String sql) {
        if (sql == null) {
            return "(null)";
        }
        if (sql.length() <= SQL_PREVIEW_MAX_LEN) {
            return sql;
        }
        return sql.substring(0, SQL_PREVIEW_MAX_LEN) + "... (truncated, total " + sql.length() + " chars)";
    }
}