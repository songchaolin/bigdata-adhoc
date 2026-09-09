package io.gitee.songchaolin.adhoc.executor.runner;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import io.gitee.songchaolin.adhoc.common.enums.OssUploadStatus;
import io.gitee.songchaolin.adhoc.common.enums.ResultStatus;
import io.gitee.songchaolin.adhoc.common.enums.StorageType;
import io.gitee.songchaolin.adhoc.common.util.ByteFormats;
import io.gitee.songchaolin.adhoc.common.util.LogTiming;
import io.gitee.songchaolin.adhoc.dao.entity.AdhocResultSummary;
import io.gitee.songchaolin.adhoc.dao.mapper.AdhocResultSummaryMapper;
import io.gitee.songchaolin.adhoc.executor.engine.QueryResult;
import io.gitee.songchaolin.adhoc.storage.format.AdhocResultSerializer;
import io.gitee.songchaolin.adhoc.storage.spi.StorageClient;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 结果持久化：AdhocResultSerializer.serialize + StorageClient.upload 一次性上传 OSS +
 * insert/update AdhocResultSummary。返回 job 日志行（rows/size/elapsed）。
 * storage_type=PERSISTENT（OSS redesign）。task 结果元数据（has_result_set/scan_rows/oss_upload_time）
 * 由 TaskExecutionPipeline 合并进 markSuccess 单次 UPDATE，不在此写。
 *
 * <p>写顺序防 OSS 孤儿：先 DB 占位（WRITING + path=null）-> upload OSS -> update summary（path+PERSISTENT+COMPLETE）。
 * OSS 失败时 summary 保持 WRITING path=null（无孤儿）；update 失败时 OSS 有但 summary WRITING（孤儿，二期对账清理）。
 */
@Component
public class TaskResultWriter {

    private final StorageClient storageClient;
    private final AdhocResultSummaryMapper resultSummaryMapper;

    public TaskResultWriter(StorageClient storageClient, AdhocResultSummaryMapper resultSummaryMapper) {
        this.storageClient = storageClient;
        this.resultSummaryMapper = resultSummaryMapper;
    }

    /**
     * 写结果集到 OSS + DB（summary + task meta）。返回 job 日志行：
     * "[executor] [INFO] [job=...][task=...] result: rows=N, size=XB (elapsed)"。
     */
    public String write(String taskId, String jobId, QueryResult qr, long taskStartMs) throws IOException {
        byte[] data = AdhocResultSerializer.serialize(qr.getSchema(), qr.getRows());
        String ossKey = jobId + "/" + taskId + "/result.part-0";

        // 1. 先 DB 占位（WRITING + path=null）-> OSS 失败不产生孤儿（summary 行存在，path null）
        AdhocResultSummary rs = new AdhocResultSummary();
        rs.setQueryId(taskId);
        rs.setResultRows((long) qr.getRows().size());
        rs.setResultBytes((long) data.length);
        rs.setPersistentPath(null);
        rs.setStorageType(StorageType.NONE.name());
        rs.setResultStatus(ResultStatus.WRITING.name());
        rs.setOssUploadStatus(OssUploadStatus.PENDING.name());
        resultSummaryMapper.insert(rs);

        // 2. upload OSS
        String fullKey = storageClient.uploadResult(ossKey, data);

        // 3. update summary（path + PERSISTENT + COMPLETE）-- 失败则 OSS 孤儿（summary WRITING，二期对账清理）
        resultSummaryMapper.update(null, new LambdaUpdateWrapper<AdhocResultSummary>()
                .eq(AdhocResultSummary::getQueryId, taskId)
                .set(AdhocResultSummary::getPersistentPath, fullKey)
                .set(AdhocResultSummary::getStorageType, StorageType.PERSISTENT.name())
                .set(AdhocResultSummary::getResultStatus, ResultStatus.COMPLETE.name())
                .set(AdhocResultSummary::getOssUploadStatus, OssUploadStatus.SUCCESS.name()));

        // task 结果元数据（has_result_set/scan_rows/oss_upload_time）合并到 markSuccess 一次 UPDATE，见 TaskExecutionPipeline

        return "[executor] [INFO] [job=" + jobId + "][task=" + taskId + "] result: rows=" + qr.getRows().size()
                + ", size=" + ByteFormats.formatSize(data.length) + " " + LogTiming.elapsed(taskStartMs);
    }
}
