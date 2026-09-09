package io.gitee.songchaolin.adhoc.executor.runner;

import io.gitee.songchaolin.adhoc.common.enums.FailReasonCategory;
import io.gitee.songchaolin.adhoc.common.enums.FailStage;
import io.gitee.songchaolin.adhoc.common.enums.TaskStatus;
import io.gitee.songchaolin.adhoc.common.exception.AdhocErrorCode;
import io.gitee.songchaolin.adhoc.common.util.SqlHashUtil;
import io.gitee.songchaolin.adhoc.common.util.SqlTypeUtils;
import io.gitee.songchaolin.adhoc.dao.entity.AdhocQueryTask;
import io.gitee.songchaolin.adhoc.dao.mapper.AdhocQueryTaskMapper;
import io.gitee.songchaolin.adhoc.executor.ha.ExecutorInstanceInfo;
import io.gitee.songchaolin.adhoc.internal.grpc.v1.DispatchJobRequest;
import io.gitee.songchaolin.adhoc.sqlparser.model.ProcessedSqlSegment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Task 创建器
 * <p>
 * 负责创建 Task 实体并批量写入数据库
 */
@Component
public class TaskCreator {

    private static final Logger logger = LoggerFactory.getLogger(TaskCreator.class);

    private final AdhocQueryTaskMapper taskMapper;
    private final ExecutorInstanceInfo instanceInfo;
    private final SqlLimitEnforcer limitEnforcer;

    public TaskCreator(AdhocQueryTaskMapper taskMapper, ExecutorInstanceInfo instanceInfo,
                       SqlLimitEnforcer limitEnforcer) {
        this.taskMapper = taskMapper;
        this.instanceInfo = instanceInfo;
        this.limitEnforcer = limitEnforcer;
    }

    /**
     * 批量创建 Task
     *
     * @param req            调度请求
     * @param jobId          Job ID
     * @param segments       SQL 段列表
     * @param engineInstance 引擎实例名
     * @return Task ID 列表
     */
    public List<String> create(DispatchJobRequest req, String jobId,
                               List<ProcessedSqlSegment> segments, String engineInstance) {
        List<String> taskIds = new ArrayList<>();
        Date now = new Date();
        List<AdhocQueryTask> tasks = new ArrayList<>(segments.size());

        for (ProcessedSqlSegment seg : segments) {
            String taskId = generateTaskId();
            AdhocQueryTask task = buildTask(taskId, jobId, seg, req, engineInstance, now);
            tasks.add(task);
            taskIds.add(taskId);
        }

        taskMapper.insertBatch(tasks);
        return taskIds;
    }

    private AdhocQueryTask buildTask(String taskId, String jobId, ProcessedSqlSegment seg,
                                      DispatchJobRequest req, String engineInstance, Date now) {
        AdhocQueryTask task = new AdhocQueryTask();
        task.setQueryId(taskId);
        task.setJobId(jobId);
        task.setSegmentIndex(seg.getSegmentIndex());
        task.setUserId(req.getUserId());
        task.setUserName(req.getUserName());
        task.setPrefixSql(seg.getPrefixSql());
        task.setSqlContent(seg.getSql());
        // 结果指纹：engine+instance+prefix+规整后SQL，与 TaskExecutionPipeline 复用查询端同口径；
        // 任一维度不同则不误复用（跨引擎/跨实例/跨库都会导致结果不同）
        String executedSql = limitEnforcer.enforce(seg.getSql(), seg.getSqlType());
        task.setSqlHash(SqlHashUtil.resultFingerprintHash(
                req.getEngineType(), engineInstance, seg.getPrefixSql(), executedSql));
        task.setSqlType(seg.getSqlType());

        // 关键修改：使用增强版 hasResultSet，传入 SQL 文本和 valid 标志
        task.setHasResultSet(SqlTypeUtils.hasResultSet(seg.getSqlType(), seg.getSql(), seg.isValid()) ? 1 : 0);

        // 关键修改：不要因为 valid=false 就标记为 FAILED
        // 而是 PENDING，让下游引擎尝试执行
        task.setStatus(TaskStatus.PENDING.name());

        if (!seg.isValid()) {
            // 记录警告信息，但不拦截执行
            task.setFailStage(FailStage.SPLIT.name());
            task.setFailReasonCategory(FailReasonCategory.ENGINE_ERROR.name());
            task.setErrorCode(AdhocErrorCode.ADHOC_SQL_SYNTAX_ERROR.name());
            task.setErrorMessage("解析降级（允许执行）: " + seg.getErrorMessage());

            logger.warn("SQL 解析降级但允许执行: jobId={}, taskId={}, segmentIndex={}, sqlType={}, error={}",
                    jobId, taskId, seg.getSegmentIndex(), seg.getSqlType(), seg.getErrorMessage());
        }

        task.setEngineType(req.getEngineType());
        task.setEngineInstance(engineInstance);
        task.setExecutorInstance(instanceInfo.getGrpcInstance());
        task.setProcessingServerInstance(req.getServerInstanceId());
        task.setEnqueueTime(now);

        return task;
    }

    private String generateTaskId() {
        return "task_" + UUID.randomUUID().toString().replace("-", "");
    }
}