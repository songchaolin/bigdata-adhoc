package io.gitee.songchaolin.adhoc.executor.runner;

import io.gitee.songchaolin.adhoc.dao.entity.AdhocQueryJob;
import io.gitee.songchaolin.adhoc.dao.mapper.AdhocQueryJobMapper;
import org.springframework.stereotype.Component;

/**
 * 取消检查器
 * <p>
 * 查 DB cancel_requested 字段，用于段间 cancel 检查
 */
@Component
public class CancelChecker {

    private final AdhocQueryJobMapper jobMapper;

    public CancelChecker(AdhocQueryJobMapper jobMapper) {
        this.jobMapper = jobMapper;
    }

    /**
     * 检查是否收到取消请求
     *
     * @param jobId Job ID
     * @return true 表示需要取消
     */
    public boolean isCancelRequested(String jobId) {
        AdhocQueryJob job = jobMapper.selectById(jobId);
        return job != null && job.getCancelRequested() != null && job.getCancelRequested() == 1;
    }
}