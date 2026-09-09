package io.gitee.songchaolin.adhoc.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import io.gitee.songchaolin.adhoc.common.enums.EngineType;
import io.gitee.songchaolin.adhoc.common.enums.JobStatus;
import io.gitee.songchaolin.adhoc.common.enums.SqlType;
import io.gitee.songchaolin.adhoc.common.exception.AdhocErrorCode;
import io.gitee.songchaolin.adhoc.common.exception.AdhocException;
import io.gitee.songchaolin.adhoc.dao.entity.AdhocQueryJob;
import io.gitee.songchaolin.adhoc.dao.mapper.AdhocQueryJobMapper;
import io.gitee.songchaolin.adhoc.common.config.ConfigHolder;
import io.gitee.songchaolin.adhoc.server.auth.OwnershipChecker;
import io.gitee.songchaolin.adhoc.server.config.AdhocServerConfig;
import io.gitee.songchaolin.adhoc.server.grpc.ExecutorChannelPool;
import io.gitee.songchaolin.adhoc.server.ha.JobLogRegistry;
import io.gitee.songchaolin.adhoc.server.schedule.QueueWorker;
import io.gitee.songchaolin.adhoc.common.dto.request.JobSubmitRequest;
import io.gitee.songchaolin.adhoc.common.dto.JobSubmitResponse;
import io.gitee.songchaolin.adhoc.sqlparser.engine.SmartFallbackParserEngine;
import io.gitee.songchaolin.adhoc.sqlparser.engine.StarRocksParserEngine;
import io.gitee.songchaolin.adhoc.sqlparser.model.ProcessedSqlSegment;
import io.gitee.songchaolin.adhoc.sqlparser.preprocess.SqlScriptProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Job 提交服务：前置校验（engine_type + SQL 非空 + STARROCKS 语句类型限制 + 限流）+ client_request_id 幂等 + Job 入库（PENDING）。
 * 限流（提交时，超限抛 ADHOC_JOB_LIMIT_EXCEEDED 不建 Job）：单 Job 段数(max-tasks-per-job) + PENDING 全局/per-user。
 * STARROCKS 仅允许 DQL/CTAS/SESSION_CONFIG/AUX，DDL/DML/DCL/UNKNOWN 拒绝（ADHOC_ENGINE_SQL_TYPE_NOT_SUPPORTED）。
 * KYUUBI 无此限制（支持完整 DDL/DML/DQL/DCL）。
 */
@Service
public class JobService {

    private static final Logger log = LoggerFactory.getLogger(JobService.class);

    /** STARROCKS 允许的 SqlType（仅查询相关；DDL/DML 走专用数仓通道，不走 adhoc）。 */
    private static final Set<String> STARROCKS_ALLOWED_SQL_TYPES =
            new HashSet<>(Arrays.asList(SqlType.DQL.name(), SqlType.CTAS.name(),
                    SqlType.SESSION_CONFIG.name(), SqlType.AUX.name()));

    private final AdhocQueryJobMapper jobMapper;
    private final JobLogRegistry jobLogRegistry;
    private final ConfigHolder cfg;
    private final ExecutorChannelPool executorChannelPool;
    private final QueueWorker queueWorker;
    private final OwnershipChecker ownershipChecker;

    public JobService(AdhocQueryJobMapper jobMapper, JobLogRegistry jobLogRegistry, ConfigHolder cfg,
                      ExecutorChannelPool executorChannelPool, QueueWorker queueWorker,
                      OwnershipChecker ownershipChecker) {
        this.jobMapper = jobMapper;
        this.jobLogRegistry = jobLogRegistry;
        this.cfg = cfg;
        this.executorChannelPool = executorChannelPool;
        this.queueWorker = queueWorker;
        this.ownershipChecker = ownershipChecker;
    }

    public JobSubmitResponse submit(JobSubmitRequest req, String userId, String userName) {
        // 先生成 jobId，这样验证过程中可以写日志
        String jobId = "Job_" + UUID.randomUUID().toString().replace("-", "");

        // 提前注册 job 日志（验证过程中可能需要写日志）
        jobLogRegistry.register(jobId);

        try {
            validate(req, jobId);
        } catch (Exception e) {
            // 验证失败，清理 job 日志注册
            jobLogRegistry.remove(jobId);
            throw e;
        }

        // 幂等：同 client_request_id 已有 Job -> 直接返回已有 jobId（不重复入库）
        if (req.getClientRequestId() != null && !req.getClientRequestId().isEmpty()) {
            AdhocQueryJob existing = jobMapper.selectOne(new LambdaQueryWrapper<AdhocQueryJob>()
                    .eq(AdhocQueryJob::getClientRequestId, req.getClientRequestId()));
            if (existing != null) {
                jobLogRegistry.remove(jobId);  // 清理临时注册
                return new JobSubmitResponse(existing.getJobId());
            }
        }

        // 限流：提交时校验 PENDING（全局 + per-user），超限抛 ADHOC_JOB_LIMIT_EXCEEDED（不建 Job）
        checkPendingLimits(userId);

        AdhocQueryJob job = new AdhocQueryJob();
        job.setJobId(jobId);
        job.setUserId(userId);
        job.setUserName(userName);
        job.setSqlContent(req.getSqlContent());
        job.setSourceFileNodeId(req.getFileId());
        job.setEngineType(req.getEngineType());
        // engineInstance：空串 -> null（避免唯一约束/歧义；executor 空则走默认实例）
        String engineInstance = (req.getEngineInstance() == null || req.getEngineInstance().isEmpty())
                ? null : req.getEngineInstance();
        job.setEngineInstance(engineInstance);
        job.setStatus(JobStatus.PENDING.name());
        job.setSubmitTime(new Date());
        // 空字符串 -> null（避免 uk_client_request_id 唯一约束冲突，NULL 允许多值）
        String clientRequestId = (req.getClientRequestId() == null || req.getClientRequestId().isEmpty())
                ? null : req.getClientRequestId();
        job.setClientRequestId(clientRequestId);
        try {
            jobMapper.insert(job);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            // 并发提交同 client_request_id 触发唯一约束 -> re-select 返回已有 jobId（幂等）
            if (clientRequestId != null) {
                AdhocQueryJob existing = jobMapper.selectOne(new LambdaQueryWrapper<AdhocQueryJob>()
                        .eq(AdhocQueryJob::getClientRequestId, clientRequestId));
                if (existing != null) {
                    jobLogRegistry.remove(jobId);  // 清理临时注册
                    return new JobSubmitResponse(existing.getJobId());
                }
            }
            throw e;
        }
        log.info("【提交】jobId={} 用户={} 引擎={} SQL长度={}", jobId, userId, req.getEngineType(), req.getSqlContent().length());
        jobLogRegistry.append(jobId, "[server] [INFO] [job=" + jobId + "] submitted, engine=" + req.getEngineType() + ", user=" + userId);
        jobLogRegistry.append(jobId, "[server] [INFO] [job=" + jobId + "] validated");
        jobLogRegistry.append(jobId, "[server] [INFO] [job=" + jobId + "] inserted PENDING");
        queueWorker.triggerDispatch();  // 事件驱动立即派发（不等 2s 轮询；非阻塞，轮询仍兜底）
        return new JobSubmitResponse(jobId);
    }

    private void validate(JobSubmitRequest req, String jobId) {
        if (req.getEngineType() == null || req.getEngineType().isEmpty()) {
            throw new AdhocException(AdhocErrorCode.ADHOC_ENGINE_TYPE_REQUIRED);
        }
        if (!EngineType.KYUUBI.is(req.getEngineType()) && !EngineType.STARROCKS.is(req.getEngineType())) {
            throw new AdhocException(AdhocErrorCode.ADHOC_ENGINE_TYPE_INVALID);
        }
        if (req.getSqlContent() == null || req.getSqlContent().trim().isEmpty()) {
            throw new AdhocException(AdhocErrorCode.ADHOC_SQL_SYNTAX_ERROR, "SQL 内容不能为空");
        }
        // 所有引擎：语法校验（g4 parse 每段）+ STARROCKS：SqlType 限制
        validateSql(req.getEngineType(), req.getSqlContent(), jobId);
    }

    /** 语法校验（所有引擎，g4 parse 每段）+ STARROCKS SqlType 限制（仅 DQL/CTAS/SESSION_CONFIG/AUX）。 */
    private void validateSql(String engineType, String sqlContent, String jobId) {
        SqlScriptProcessor processor = EngineType.STARROCKS.is(engineType)
                ? new SqlScriptProcessor(new SmartFallbackParserEngine(new StarRocksParserEngine()))
                : new SqlScriptProcessor();
        List<ProcessedSqlSegment> segments = processor.process(sqlContent);
        // 限流：单 Job 段数上限（按 ';' 拆，含 SET/USE）
        if (segments.size() > cfg.get(AdhocServerConfig.MAX_TASKS_PER_JOB)) {
            log.warn("submit rejected (limit): segments={} > max-tasks-per-job={}", segments.size(), cfg.get(AdhocServerConfig.MAX_TASKS_PER_JOB));
            throw new AdhocException(AdhocErrorCode.ADHOC_JOB_LIMIT_EXCEEDED,
                    "SQL 段数超限: " + segments.size() + " > max-tasks-per-job=" + cfg.get(AdhocServerConfig.MAX_TASKS_PER_JOB));
        }
        for (ProcessedSqlSegment seg : segments) {
            // 危险语句拦截（代码注入/文件系统/权限/系统管理/库级数据销毁），优先于语法校验
            String dangerous = io.gitee.songchaolin.adhoc.common.util.DangerousSqlChecker.check(seg.getSql());
            if (dangerous != null) {
                log.warn("submit rejected (dangerous): {}", dangerous);
                jobLogRegistry.append(jobId, "[server] [WARN] [job=" + jobId + "] 提交被拦截: SQL 包含危险操作 " + dangerous);
                throw new AdhocException(AdhocErrorCode.ADHOC_SQL_DANGEROUS_STATEMENT,
                        "SQL 包含危险操作已被拦截: " + dangerous);
            }

            // 降级策略：g4 解析失败但降级解析能识别类型 -> 允许执行
            // 只有 UNKNOWN 类型才拦截（真正的语法错误）
            if (!seg.isValid()) {
                if ("UNKNOWN".equals(seg.getSqlType())) {
                    // 降级解析也无法识别，拦截
                    log.warn("submit rejected (syntax): {}", seg.getErrorMessage());
                    jobLogRegistry.append(jobId, "[server] [WARN] [job=" + jobId + "] 提交被拦截: SQL 语法错误 " + seg.getErrorMessage());
                    throw new AdhocException(AdhocErrorCode.ADHOC_SQL_SYNTAX_ERROR,
                            "SQL 语法错误: " + seg.getErrorMessage());
                }
                // 降级解析成功识别类型，记录到 job 日志
                log.info("SQL 解析降级但允许执行: sqlType={}, error={}",
                        seg.getSqlType(), seg.getErrorMessage());
                jobLogRegistry.append(jobId, "[server] [WARN] [job=" + jobId + "][segment=" + seg.getSegmentIndex() + "] SQL 解析降级: " + seg.getErrorMessage());
                jobLogRegistry.append(jobId, "[server] [INFO] [job=" + jobId + "][segment=" + seg.getSegmentIndex() + "] 降级后识别为 " + seg.getSqlType() + " 类型，允许提交引擎执行");
            }

            if (EngineType.STARROCKS.is(engineType) && !STARROCKS_ALLOWED_SQL_TYPES.contains(seg.getSqlType())) {
                jobLogRegistry.append(jobId, "[server] [WARN] [job=" + jobId + "] 提交被拦截: StarRocks 不支持 " + seg.getSqlType() + " 类型");
                throw new AdhocException(AdhocErrorCode.ADHOC_ENGINE_SQL_TYPE_NOT_SUPPORTED,
                        "StarRocks 引擎不支持 " + seg.getSqlType() + " 语句类型，请使用 KYUUBI 引擎");
            }
        }
    }

    /** 限流：提交时校验 PENDING 计数（全局 + per-user）。count-then-insert 有 race，一期接受（二期 Redis 原子计数）。 */
    private void checkPendingLimits(String userId) {
        long pendingUser = jobMapper.selectCount(new LambdaQueryWrapper<AdhocQueryJob>()
                .eq(AdhocQueryJob::getUserId, userId)
                .eq(AdhocQueryJob::getStatus, JobStatus.PENDING.name()));
        if (pendingUser >= cfg.get(AdhocServerConfig.MAX_PENDING_JOBS_PER_USER)) {
            log.warn("submit rejected (limit): user={} pending={} >= max-pending-jobs-per-user={}",
                    userId, pendingUser, cfg.get(AdhocServerConfig.MAX_PENDING_JOBS_PER_USER));
            throw new AdhocException(AdhocErrorCode.ADHOC_JOB_LIMIT_EXCEEDED,
                    "用户 " + userId + " PENDING Job 超限: " + pendingUser
                            + " >= max-pending-jobs-per-user=" + cfg.get(AdhocServerConfig.MAX_PENDING_JOBS_PER_USER));
        }
        long pendingGlobal = jobMapper.selectCount(new LambdaQueryWrapper<AdhocQueryJob>()
                .eq(AdhocQueryJob::getStatus, JobStatus.PENDING.name()));
        if (pendingGlobal >= cfg.get(AdhocServerConfig.MAX_PENDING_JOBS_GLOBAL)) {
            log.warn("submit rejected (limit): global pending={} >= max-pending-jobs-global={}",
                    pendingGlobal, cfg.get(AdhocServerConfig.MAX_PENDING_JOBS_GLOBAL));
            throw new AdhocException(AdhocErrorCode.ADHOC_JOB_LIMIT_EXCEEDED,
                    "全局 PENDING Job 超限: " + pendingGlobal
                            + " >= max-pending-jobs-global=" + cfg.get(AdhocServerConfig.MAX_PENDING_JOBS_GLOBAL));
        }
    }

    /**
     * 取消 Job：置 cancel_requested=1（RUNNING job 靠 executor 每段前检查跳过剩余 task + aggregateJob 落 CANCELED）；
     * 未派发/派发中的（PENDING/DISPATCHING）直接 CAS CANCELED（不会被调度执行）。
     * 归属校验：仅创建人或管理员（{@link OwnershipChecker}，userId=null 视为系统级放行）；操作人记入 job 日志审计。
     */
    public boolean cancel(String jobId, String userId) {
        AdhocQueryJob job = jobMapper.selectById(jobId);
        if (job == null) {
            throw new AdhocException(AdhocErrorCode.ADHOC_JOB_NOT_FOUND);
        }
        ownershipChecker.requireAccess(userId, job.getUserId());
        jobMapper.update(null, new LambdaUpdateWrapper<AdhocQueryJob>()
                .eq(AdhocQueryJob::getJobId, jobId)
                .set(AdhocQueryJob::getCancelRequested, 1)
                .set(AdhocQueryJob::getCancelRequestedTime, new Date()));
        int canceled = jobMapper.update(null, new LambdaUpdateWrapper<AdhocQueryJob>()
                .eq(AdhocQueryJob::getJobId, jobId)
                .in(AdhocQueryJob::getStatus, JobStatus.PENDING.name(), JobStatus.DISPATCHING.name())
                .set(AdhocQueryJob::getStatus, JobStatus.CANCELED.name())
                .set(AdhocQueryJob::getFinishTime, new Date()));
        if (canceled > 0) {
            jobLogRegistry.append(jobId, "[server] [INFO] [job=" + jobId + "] canceled (pre-dispatch) by user=" + userId);
        } else {
            // RUNNING: gRPC 通知 executor cancelJob（Statement.cancel 立即中断当前段）
            if (JobStatus.RUNNING.is(job.getStatus()) && job.getExecutorInstance() != null) {
                try {
                    executorChannelPool.cancelJob(job.getExecutorInstance(), jobId);
                    jobLogRegistry.append(jobId, "[server] [INFO] [job=" + jobId + "] cancel notified to executor " + job.getExecutorInstance() + " (Statement.cancel) by user=" + userId);
                } catch (Exception e) {
                    log.warn("notify cancelJob to executor {} failed: {}", job.getExecutorInstance(), e.getMessage());
                    jobLogRegistry.append(jobId, "[server] [WARN] [job=" + jobId + "] cancel notify failed, executor will check cancel_requested between segments by user=" + userId);
                }
            } else {
                jobLogRegistry.append(jobId, "[server] [INFO] [job=" + jobId + "] cancel requested (running, executor will skip remaining tasks) by user=" + userId);
            }
        }
        return true;
    }
}