package io.gitee.songchaolin.adhoc.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.gitee.songchaolin.adhoc.common.exception.AdhocErrorCode;
import io.gitee.songchaolin.adhoc.common.exception.AdhocException;
import io.gitee.songchaolin.adhoc.dao.entity.AdhocQueryJob;
import io.gitee.songchaolin.adhoc.dao.entity.AdhocQueryTask;
import io.gitee.songchaolin.adhoc.dao.entity.AdhocResultSummary;
import io.gitee.songchaolin.adhoc.dao.mapper.AdhocQueryJobMapper;
import io.gitee.songchaolin.adhoc.dao.mapper.AdhocQueryTaskMapper;
import io.gitee.songchaolin.adhoc.dao.mapper.AdhocResultSummaryMapper;
import io.gitee.songchaolin.adhoc.server.auth.OwnershipChecker;
import io.gitee.songchaolin.adhoc.common.dto.JobDetailResponse;
import io.gitee.songchaolin.adhoc.common.dto.JobProgressResponse;
import io.gitee.songchaolin.adhoc.common.dto.request.JobPageQueryRequest;
import io.gitee.songchaolin.adhoc.common.dto.JobStatusResponse;
import io.gitee.songchaolin.adhoc.common.dto.JobVO;
import io.gitee.songchaolin.adhoc.common.dto.TaskSummary;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Job 查询服务：分页列表 + 详情（Job + Task 列表，segment_index ASC）+ 状态。 */
@Service
public class JobQueryService {

    private final AdhocQueryJobMapper jobMapper;
    private final AdhocQueryTaskMapper taskMapper;
    private final AdhocResultSummaryMapper resultSummaryMapper;
    private final JobProgressAssembler progressAssembler;
    private final OwnershipChecker ownershipChecker;

    public JobQueryService(AdhocQueryJobMapper jobMapper, AdhocQueryTaskMapper taskMapper,
                           AdhocResultSummaryMapper resultSummaryMapper,
                           JobProgressAssembler progressAssembler,
                           OwnershipChecker ownershipChecker) {
        this.jobMapper = jobMapper;
        this.taskMapper = taskMapper;
        this.resultSummaryMapper = resultSummaryMapper;
        this.progressAssembler = progressAssembler;
        this.ownershipChecker = ownershipChecker;
    }

    /** 分页查询 Job 列表（对齐公司模版 selectPage：Page + LambdaQueryWrapper + entity->VO）。 */
    public IPage<JobVO> selectPage(JobPageQueryRequest request, String userId) {
        Page<AdhocQueryJob> page = new Page<>(request.getCurrent(), request.getSize());
        LambdaQueryWrapper<AdhocQueryJob> wrapper = new LambdaQueryWrapper<AdhocQueryJob>()
                .eq(AdhocQueryJob::getUserId, userId)
                .eq(StringUtils.hasText(request.getStatus()), AdhocQueryJob::getStatus, request.getStatus())
                .eq(StringUtils.hasText(request.getEngineType()), AdhocQueryJob::getEngineType, request.getEngineType())
                .eq(StringUtils.hasText(request.getFileNodeId()), AdhocQueryJob::getSourceFileNodeId, request.getFileNodeId())
                .orderByDesc(AdhocQueryJob::getSubmitTime);
        IPage<AdhocQueryJob> entityPage = jobMapper.selectPage(page, wrapper);
        Page<JobVO> voPage = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
        voPage.setRecords(entityPage.getRecords().stream().map(JobQueryService::toVO).collect(Collectors.toList()));
        return voPage;
    }

    /** entity -> VO（JobVO 已下沉 common 为纯 POJO，映射在 service 侧完成）。 */
    private static JobVO toVO(AdhocQueryJob j) {
        JobVO vo = new JobVO();
        vo.setJobId(j.getJobId());
        vo.setUserId(j.getUserId());
        vo.setUserName(j.getUserName());
        vo.setEngineType(j.getEngineType());
        vo.setStatus(j.getStatus());
        vo.setSubmitTime(j.getSubmitTime());
        vo.setStartTime(j.getStartTime());
        vo.setFinishTime(j.getFinishTime());
        vo.setDurationMs(j.getDurationMs());
        vo.setExecutorInstance(j.getExecutorInstance());
        return vo;
    }

    public JobDetailResponse getJobDetail(String jobId, String accessUserId) {
        AdhocQueryJob job = jobMapper.selectById(jobId);
        requireJobExists(job);
        // 归属校验：本人或管理员放行（metric token/gRPC 传 null=系统级放行）
        ownershipChecker.requireAccess(accessUserId, job.getUserId());
        List<AdhocQueryTask> tasks = taskMapper.selectList(new LambdaQueryWrapper<AdhocQueryTask>()
                .eq(AdhocQueryTask::getJobId, jobId)
                .orderByAsc(AdhocQueryTask::getSegmentIndex));
        List<TaskSummary> summaries = tasks.stream()
                .map(JobQueryService::toTaskSummary)
                .collect(Collectors.toList());
        // 批量填结果行数（DQL 查出的行数；DDL/DML 无 summary -> null）
        if (!tasks.isEmpty()) {
            List<String> taskIds = tasks.stream().map(AdhocQueryTask::getQueryId).collect(Collectors.toList());
            Map<String, Long> rowsById = new HashMap<>();
            resultSummaryMapper.selectBatchIds(taskIds).forEach(
                    rs -> rowsById.put(rs.getQueryId(), rs.getResultRows()));
            summaries.forEach(s -> s.setResultRows(rowsById.get(s.getTaskId())));
        }
        return new JobDetailResponse(job.getJobId(), job.getStatus(), job.getSqlContent(),
                job.getEngineType(), job.getSubmitTime(), summaries);
    }

    private static TaskSummary toTaskSummary(AdhocQueryTask t) {
        TaskSummary s = new TaskSummary();
        s.setTaskId(t.getQueryId());
        s.setSegmentIndex(t.getSegmentIndex());
        s.setStatus(t.getStatus());
        s.setFailStage(t.getFailStage());
        s.setSqlType(t.getSqlType());
        s.setHasResultSet(t.getHasResultSet() != null && t.getHasResultSet() == 1);
        s.setSqlContent(t.getSqlContent());
        s.setPrefixSql(t.getPrefixSql());
        s.setAffectedRows(t.getAffectedRows());
        s.setDurationMs(t.getDurationMs());
        s.setErrorMessage(t.getErrorMessage());
        return s;
    }

    public JobStatusResponse getJobStatus(String jobId, String accessUserId) {
        AdhocQueryJob job = jobMapper.selectById(jobId);
        requireJobExists(job);
        // 归属校验：本人或管理员放行（metric token/gRPC 传 null=系统级放行）
        ownershipChecker.requireAccess(accessUserId, job.getUserId());
        return new JobStatusResponse(job.getJobId(), job.getStatus());
    }

    /** Job 执行进度：阶段时间线（DAG）+ 各阶段耗时（Job 级 + Task 级）。 */
    public JobProgressResponse getJobProgress(String jobId, String accessUserId) {
        AdhocQueryJob job = jobMapper.selectById(jobId);
        requireJobExists(job);
        // 归属校验：本人或管理员放行（metric token/gRPC 传 null=系统级放行）
        ownershipChecker.requireAccess(accessUserId, job.getUserId());
        List<AdhocQueryTask> tasks = taskMapper.selectList(new LambdaQueryWrapper<AdhocQueryTask>()
                .eq(AdhocQueryTask::getJobId, jobId)
                .orderByAsc(AdhocQueryTask::getSegmentIndex));
        return progressAssembler.assemble(job, tasks);
    }

    /** 存在性校验：job 不存在 -> ADHOC_JOB_NOT_FOUND。 */
    private void requireJobExists(AdhocQueryJob job) {
        if (job == null) {
            throw new AdhocException(AdhocErrorCode.ADHOC_JOB_NOT_FOUND);
        }
    }
}
