package io.gitee.songchaolin.adhoc.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.gitee.songchaolin.adhoc.common.enums.StorageType;
import io.gitee.songchaolin.adhoc.common.exception.AdhocErrorCode;
import io.gitee.songchaolin.adhoc.common.exception.AdhocException;
import io.gitee.songchaolin.adhoc.dao.entity.AdhocQueryJob;
import io.gitee.songchaolin.adhoc.dao.entity.AdhocQueryTask;
import io.gitee.songchaolin.adhoc.dao.entity.AdhocResultSummary;
import io.gitee.songchaolin.adhoc.dao.mapper.AdhocQueryJobMapper;
import io.gitee.songchaolin.adhoc.dao.mapper.AdhocQueryTaskMapper;
import io.gitee.songchaolin.adhoc.dao.mapper.AdhocResultSummaryMapper;
import io.gitee.songchaolin.adhoc.server.auth.OwnershipChecker;
import io.gitee.songchaolin.adhoc.common.dto.JobResultResponse;
import io.gitee.songchaolin.adhoc.common.dto.TaskResultItem;
import io.gitee.songchaolin.adhoc.common.dto.request.ResultRequest;
import io.gitee.songchaolin.adhoc.common.dto.ResultResponse;
import io.gitee.songchaolin.adhoc.storage.format.AdhocResultSerializer;
import io.gitee.songchaolin.adhoc.storage.spi.StorageClient;
import io.gitee.songchaolin.adhoc.storage.reader.AdhocResultReader;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 结果查询服务：server 直读 OSS（StorageClient.download -> AdhocResultReader 分页），不转发 executor。
 * - /task/result：单 task 分页（current/size，1-based）；
 * - /job/result：聚合 Job 下所有 task 的结果第一页（按 segmentIndex 排列），前端不必逐 task 循环。
 * 存在性校验：task/job 不存在统一抛 ADHOC_JOB_NOT_FOUND（capability 模型，知道 taskId/jobId 即可读，不做归属校验）。
 */
@Service
public class ResultQueryService {

    private final AdhocResultSummaryMapper resultSummaryMapper;
    private final AdhocQueryTaskMapper taskMapper;
    private final AdhocQueryJobMapper jobMapper;
    private final StorageClient storageClient;
    private final OwnershipChecker ownershipChecker;

    public ResultQueryService(AdhocResultSummaryMapper resultSummaryMapper, AdhocQueryTaskMapper taskMapper,
                              AdhocQueryJobMapper jobMapper, StorageClient storageClient,
                              OwnershipChecker ownershipChecker) {
        this.resultSummaryMapper = resultSummaryMapper;
        this.taskMapper = taskMapper;
        this.jobMapper = jobMapper;
        this.storageClient = storageClient;
        this.ownershipChecker = ownershipChecker;
    }

    /** 分页读取单个 task 结果。current/size 继承自 PageRequest（1-based，已 @Valid 校验）。 */
    public ResultResponse getResult(ResultRequest req, String accessUserId) {
        // 存在性校验：task 不存在 -> ADHOC_JOB_NOT_FOUND
        AdhocQueryTask task = taskMapper.selectById(req.getTaskId());
        if (task == null) {
            throw new AdhocException(AdhocErrorCode.ADHOC_JOB_NOT_FOUND);
        }
        // 归属校验：本人或管理员放行（metric token/gRPC 传 null=系统级放行）
        ownershipChecker.requireAccess(accessUserId, task.getUserId());
        AdhocResultSummary rs = resultSummaryMapper.selectById(req.getTaskId());
        if (rs == null || rs.getPersistentPath() == null || !StorageType.PERSISTENT.is(rs.getStorageType())) {
            throw new AdhocException(AdhocErrorCode.ADHOC_RESULT_NO_RESULT, "任务 " + req.getTaskId() + " 暂无结果数据");
        }
        long offset = (req.getCurrent() - 1) * req.getSize();
        ReadResult r = readPage(rs, offset, req.getSize());
        return new ResultResponse(r.cols, r.rows, req.getCurrent(), req.getSize(), r.totalRows, r.hasMore);
    }

    /**
     * 聚合 Job 下所有 task 的结果第一页（按 segmentIndex 排列）。
     * 有结果集的 task 填 schema/rows/totalRows/hasMore；无结果集（DDL/DML/未成功）只填元信息，hasResultSet=false。
     * 前端要看某 task 完整结果再用 /task/result 翻页。
     */
    public JobResultResponse getJobResult(String jobId, int size, String accessUserId) {
        AdhocQueryJob job = jobMapper.selectById(jobId);
        if (job == null) {
            throw new AdhocException(AdhocErrorCode.ADHOC_JOB_NOT_FOUND);
        }
        // 归属校验：本人或管理员放行（metric token/gRPC 传 null=系统级放行）
        ownershipChecker.requireAccess(accessUserId, job.getUserId());
        List<AdhocQueryTask> tasks = taskMapper.selectList(new LambdaQueryWrapper<AdhocQueryTask>()
                .eq(AdhocQueryTask::getJobId, jobId)
                .orderByAsc(AdhocQueryTask::getSegmentIndex));
        List<TaskResultItem> items = new ArrayList<>();
        for (AdhocQueryTask t : tasks) {
            TaskResultItem item = new TaskResultItem();
            item.setTaskId(t.getQueryId());
            item.setSegmentIndex(t.getSegmentIndex());
            item.setStatus(t.getStatus());
            item.setFailStage(t.getFailStage());
            item.setSqlType(t.getSqlType());
            item.setSqlContent(t.getSqlContent());
            item.setPrefixSql(t.getPrefixSql());
            item.setAffectedRows(t.getAffectedRows());
            item.setDurationMs(t.getDurationMs());
            item.setErrorMessage(t.getErrorMessage());
            // 有结果集才读第一页
            AdhocResultSummary rs = resultSummaryMapper.selectById(t.getQueryId());
            if (rs != null && rs.getPersistentPath() != null && StorageType.PERSISTENT.is(rs.getStorageType())) {
                item.setHasResultSet(true);
                ReadResult r = readPage(rs, 0, size);
                item.setSchema(r.cols);
                item.setRows(r.rows);
                item.setTotalRows(r.totalRows);
                item.setHasMore(r.hasMore);
            } else {
                item.setHasResultSet(false);
            }
            items.add(item);
        }
        return new JobResultResponse(jobId, job.getStatus(), items);
    }

    /** 读 OSS 结果文件的指定页（流式 skip offset + 读 size 行，不全量加载防 OOM）。 */
    private ReadResult readPage(AdhocResultSummary rs, long offset, long size) {
        try (InputStream is = storageClient.download(rs.getPersistentPath());
             AdhocResultReader reader = new AdhocResultReader(is)) {
            List<ResultResponse.ColumnDto> cols = new ArrayList<>();
            for (io.gitee.songchaolin.adhoc.storage.model.ResultColumn c : reader.getSchema().getColumns()) {
                cols.add(new ResultResponse.ColumnDto(c.getColIndex(), c.getColName(), c.getColType()));
            }
            reader.skip(offset);
            List<String> rows = new ArrayList<>();
            int read = 0;
            while (read < size && reader.hasNext()) {
                rows.add(new String(AdhocResultSerializer.rowToJson(reader.next()), StandardCharsets.UTF_8));
                read++;
            }
            boolean hasMore = offset + read < rs.getResultRows();
            return new ReadResult(cols, rows, rs.getResultRows(), hasMore);
        } catch (Exception e) {
            throw new RuntimeException("OSS read failed for task " + rs.getQueryId(), e);
        }
    }

    /** readPage 内部结果。 */
    private static class ReadResult {
        final List<ResultResponse.ColumnDto> cols;
        final List<String> rows;
        final long totalRows;
        final boolean hasMore;
        ReadResult(List<ResultResponse.ColumnDto> cols, List<String> rows, long totalRows, boolean hasMore) {
            this.cols = cols;
            this.rows = rows;
            this.totalRows = totalRows;
            this.hasMore = hasMore;
        }
    }
}
