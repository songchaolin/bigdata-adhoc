package io.gitee.songchaolin.adhoc.executor.runner;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import io.gitee.songchaolin.adhoc.common.enums.JobStatus;
import io.gitee.songchaolin.adhoc.dao.entity.AdhocQueryJob;
import io.gitee.songchaolin.adhoc.dao.mapper.AdhocQueryJobMapper;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * Job DB 状态写（集中 LambdaUpdateWrapper）。
 * 所有方法以 jobId 为条件做单行 update，带 status 守卫防状态倒退/覆盖终态（多节点 TOCTOU）。
 */
@Component
public class JobStateWriter {

    private final AdhocQueryJobMapper jobMapper;

    public JobStateWriter(AdhocQueryJobMapper jobMapper) {
        this.jobMapper = jobMapper;
    }

    /** split 完成、开始执行：DISPATCHING -> RUNNING（CAS 防重复 dispatch 状态倒退）+ split_finish_time + start_time + executor_instance。 */
    public void markRunning(String jobId, String instanceId) {
        Date now = new Date();
        jobMapper.update(null, new LambdaUpdateWrapper<AdhocQueryJob>()
                .eq(AdhocQueryJob::getJobId, jobId)
                .eq(AdhocQueryJob::getStatus, JobStatus.DISPATCHING.name())
                .set(AdhocQueryJob::getStatus, JobStatus.RUNNING.name())
                .set(AdhocQueryJob::getSplitFinishTime, now)
                .set(AdhocQueryJob::getStartTime, now)
                .set(AdhocQueryJob::getExecutorInstance, instanceId));
    }

    /** 无可执行段：FAILED + finish_time（DISPATCHING -> FAILED）。 */
    public void markNoSegments(String jobId) {
        markFailed(jobId);
    }

    /** 外层异常：FAILED + finish_time（PENDING/DISPATCHING/RUNNING -> FAILED，带 status 守卫防覆盖终态）。 */
    public void markFailed(String jobId) {
        jobMapper.update(null, new LambdaUpdateWrapper<AdhocQueryJob>()
                .eq(AdhocQueryJob::getJobId, jobId)
                .in(AdhocQueryJob::getStatus,
                        JobStatus.PENDING.name(), JobStatus.DISPATCHING.name(), JobStatus.RUNNING.name())
                .set(AdhocQueryJob::getStatus, JobStatus.FAILED.name())
                .set(AdhocQueryJob::getFinishTime, new Date()));
    }

    /** 终态汇总：RUNNING -> SUCCESS/FAILED/PARTIAL_FAILED/CANCELED（带 status 守卫防覆盖已被外部标记的终态）。 */
    public void markTerminal(String jobId, String jobStatus, Date finishTime) {
        jobMapper.update(null, new LambdaUpdateWrapper<AdhocQueryJob>()
                .eq(AdhocQueryJob::getJobId, jobId)
                .eq(AdhocQueryJob::getStatus, JobStatus.RUNNING.name())
                .set(AdhocQueryJob::getStatus, jobStatus)
                .set(AdhocQueryJob::getFinishTime, finishTime));
    }
}
