package io.gitee.songchaolin.adhoc.server.service;

import io.gitee.songchaolin.adhoc.common.dto.TaskDetailResponse;
import io.gitee.songchaolin.adhoc.common.exception.AdhocErrorCode;
import io.gitee.songchaolin.adhoc.common.exception.AdhocException;
import io.gitee.songchaolin.adhoc.dao.entity.AdhocQueryTask;
import io.gitee.songchaolin.adhoc.dao.entity.AdhocResultSummary;
import io.gitee.songchaolin.adhoc.dao.mapper.AdhocQueryTaskMapper;
import io.gitee.songchaolin.adhoc.dao.mapper.AdhocResultSummaryMapper;
import io.gitee.songchaolin.adhoc.server.auth.OwnershipChecker;
import org.springframework.stereotype.Service;

/**
 * Task 查询服务
 * <p>
 * 提供 Task 详情查询功能
 */
@Service
public class TaskQueryService {

    private final AdhocQueryTaskMapper taskMapper;
    private final AdhocResultSummaryMapper resultSummaryMapper;
    private final OwnershipChecker ownershipChecker;

    public TaskQueryService(AdhocQueryTaskMapper taskMapper,
                            AdhocResultSummaryMapper resultSummaryMapper,
                            OwnershipChecker ownershipChecker) {
        this.taskMapper = taskMapper;
        this.resultSummaryMapper = resultSummaryMapper;
        this.ownershipChecker = ownershipChecker;
    }

    /**
     * 查询 Task 详情
     *
     * @param taskId        Task ID
     * @param accessUserId  访问者工号（业务接口传登录工号；metric token/gRPC 传 null=系统级放行）
     * @return Task 详情
     */
    public TaskDetailResponse getTaskDetail(String taskId, String accessUserId) {
        // 1. 查询 Task 基本信息
        AdhocQueryTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new AdhocException(AdhocErrorCode.ADHOC_JOB_NOT_FOUND);
        }
        // 1.1 归属校验：本人或管理员放行（metric token/gRPC 传 null=系统级放行）
        ownershipChecker.requireAccess(accessUserId, task.getUserId());

        // 2. 转换为响应对象
        TaskDetailResponse response = toTaskDetailResponse(task);

        // 3. 补充结果行数（DQL 查询）
        if (task.getHasResultSet() != null && task.getHasResultSet() == 1) {
            AdhocResultSummary summary = resultSummaryMapper.selectById(taskId);
            if (summary != null) {
                response.setResultRows(summary.getResultRows());
            }
        }

        return response;
    }

    /**
     * Entity -> DTO 转换
     */
    private TaskDetailResponse toTaskDetailResponse(AdhocQueryTask t) {
        TaskDetailResponse r = new TaskDetailResponse();

        // 基本信息
        r.setTaskId(t.getQueryId());
        r.setJobId(t.getJobId());
        r.setSegmentIndex(t.getSegmentIndex());
        r.setStatus(t.getStatus());
        r.setSqlType(t.getSqlType());
        r.setHasResultSet(t.getHasResultSet() != null && t.getHasResultSet() == 1);
        r.setSqlContent(t.getSqlContent());
        r.setPrefixSql(t.getPrefixSql());

        // 执行统计
        r.setAffectedRows(t.getAffectedRows());
        r.setScanRows(t.getScanRows());
        r.setScanBytes(t.getScanBytes());
        r.setDurationMs(t.getDurationMs());

        // 失败信息
        r.setFailStage(t.getFailStage());
        r.setFailReasonCategory(t.getFailReasonCategory());
        r.setErrorCode(t.getErrorCode());
        r.setErrorMessage(t.getErrorMessage());

        // 复用信息
        r.setReusedFromTaskId(t.getReusedFromTaskId());

        // 引擎信息
        r.setEngineType(t.getEngineType());
        r.setEngineInstance(t.getEngineInstance());
        r.setExecutorInstance(t.getExecutorInstance());

        // 时间信息
        r.setEnqueueTime(t.getEnqueueTime());
        r.setStartTime(t.getStartTime());
        r.setFetchStartTime(t.getFetchStartTime());
        r.setWriteStartTime(t.getWriteStartTime());
        r.setFinishTime(t.getFinishTime());

        return r;
    }
}