package io.gitee.songchaolin.adhoc.server.service;

import io.gitee.songchaolin.adhoc.common.dto.JobProgressResponse;
import io.gitee.songchaolin.adhoc.common.dto.StageTimeline;
import io.gitee.songchaolin.adhoc.common.dto.TaskProgress;
import io.gitee.songchaolin.adhoc.common.enums.FailStage;
import io.gitee.songchaolin.adhoc.common.enums.JobPhase;
import io.gitee.songchaolin.adhoc.common.enums.JobStatus;
import io.gitee.songchaolin.adhoc.common.enums.StageState;
import io.gitee.songchaolin.adhoc.common.enums.TaskPhase;
import io.gitee.songchaolin.adhoc.common.enums.TaskStage;
import io.gitee.songchaolin.adhoc.common.enums.TaskStatus;
import io.gitee.songchaolin.adhoc.common.util.SqlTypeUtils;
import io.gitee.songchaolin.adhoc.dao.entity.AdhocQueryJob;
import io.gitee.songchaolin.adhoc.dao.entity.AdhocQueryTask;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Job 执行进度组装器：Job + Tasks -> 阶段时间线（DAG）+ 各阶段耗时。
 * <p>数据来源为 DB 权威时间戳：
 * <ul>
 *   <li>Job：submit/dispatch/splitFinish(=start)/start/finish</li>
 *   <li>Task：enqueue/start/fetchStart/writeStart/ossUpload/finish + stage/failStage</li>
 * </ul>
 * 阶段状态由 JobStatus/TaskStatus + stage/failStage 判定；耗时 = 相邻时间戳相减。
 */
@Component
public class JobProgressAssembler {

    public JobProgressResponse assemble(AdhocQueryJob job, List<AdhocQueryTask> tasks) {
        Date now = new Date();
        List<StageTimeline> jobStages = buildJobStages(job);
        List<TaskProgress> taskProgressList = new ArrayList<>();
        if (tasks != null) {
            for (AdhocQueryTask t : tasks) {
                taskProgressList.add(buildTaskProgress(t));
            }
        }
        // 后处理：进行中阶段补实时耗时（now - start），统一生成人类可读 duration
        finalizeStages(jobStages, now);
        for (TaskProgress tp : taskProgressList) {
            finalizeStages(tp.getStages(), now);
        }
        return new JobProgressResponse(job.getJobId(), job.getStatus(),
                jobCurrentStage(job.getStatus()), jobStages, taskProgressList);
    }

    // ==================== Job 阶段 ====================

    private List<StageTimeline> buildJobStages(AdhocQueryJob job) {
        Date submit = job.getSubmitTime();
        Date dispatch = job.getDispatchTime();
        Date split = job.getSplitFinishTime();
        Date start = job.getStartTime();
        Date finish = job.getFinishTime();
        String status = job.getStatus();

        List<StageTimeline> stages = new ArrayList<>();
        stages.add(point(JobPhase.SUBMIT, submit, StageState.DONE));
        stages.add(interval(JobPhase.QUEUE, submit, dispatch));
        stages.add(interval(JobPhase.DISPATCH, dispatch, split));
        stages.add(interval(JobPhase.RUNNING, start, finish));
        stages.add(jobFinishStage(status, finish));
        return stages;
    }

    private String jobCurrentStage(String status) {
        if (JobStatus.PENDING.is(status)) {
            return JobPhase.QUEUE.name();
        }
        if (JobStatus.DISPATCHING.is(status)) {
            return JobPhase.DISPATCH.name();
        }
        if (JobStatus.RUNNING.is(status)) {
            return JobPhase.RUNNING.name();
        }
        return JobPhase.FINISH.name();
    }

    private StageTimeline jobFinishStage(String status, Date finish) {
        StageState state;
        if (!JobStatus.isTerminal(status)) {
            state = StageState.PENDING;
        } else if (JobStatus.CANCELED.is(status)) {
            state = StageState.CANCELED;
        } else if (JobStatus.FAILED.is(status) || JobStatus.PARTIAL_FAILED.is(status)) {
            state = StageState.FAILED;
        } else {
            state = StageState.DONE;
        }
        return point(JobPhase.FINISH, finish, state);
    }

    /** Job 区间阶段：end 有值=DONE；仅 start 有值=RUNNING；都无=PENDING。 */
    private StageTimeline interval(JobPhase phase, Date start, Date end) {
        StageState state;
        if (end != null) {
            state = StageState.DONE;
        } else if (start != null) {
            state = StageState.RUNNING;
        } else {
            state = StageState.PENDING;
        }
        return new StageTimeline(phase.name(), phase.getDisplayName(), state.name(), start, end, duration(start, end));
    }

    // ==================== Task 阶段 ====================

    private TaskProgress buildTaskProgress(AdhocQueryTask t) {
        return new TaskProgress(t.getQueryId(), t.getSegmentIndex(), t.getStatus(),
                taskCurrentStage(t), buildTaskStages(t));
    }

    private String taskCurrentStage(AdhocQueryTask t) {
        String status = t.getStatus();
        if (TaskStatus.PENDING.is(status)) {
            return TaskPhase.EXECUTING.name();
        }
        if (TaskStatus.RUNNING.is(status)) {
            TaskPhase p = mapTaskStage(t.getStage());
            return p != null ? p.name() : TaskPhase.EXECUTING.name();
        }
        return TaskPhase.FINISH.name();
    }

    private List<StageTimeline> buildTaskStages(AdhocQueryTask t) {
        String status = t.getStatus();
        // DQL/CTAS 等有结果集类型才有 WRITING 阶段；DDL/DML 的 WRITING 永远 SKIPPED
        boolean isDql = t.getSqlType() != null && SqlTypeUtils.hasResultSet(t.getSqlType());
        Date enqueue = t.getEnqueueTime();
        Date start = t.getStartTime();
        Date fetch = t.getFetchStartTime();
        Date write = t.getWriteStartTime();
        Date upload = t.getOssUploadTime();
        Date finish = t.getFinishTime();
        // DDL/DML 无 write_start：FETCHING 阶段持续到 finish（executeQuery 发生在 FETCHING 阶段）
        Date fetchEnd = write != null ? write : finish;
        // ENQUEUE = 入队 -> 开始执行的排队等待区间（start 为 null 时耗时为 null，如 PENDING / 未执行的 FAILED·CANCELED）；
        // SKIPPED 例外——跳过的 task 不排队，ENQUEUE 保持瞬时点

        List<StageTimeline> stages = new ArrayList<>();

        if (TaskStatus.SKIPPED.is(status)) {
            // 上游失败跳过：仅入队，其余跳过
            stages.add(point(TaskPhase.ENQUEUE, enqueue, StageState.DONE));
            stages.add(simple(TaskPhase.EXECUTING, StageState.SKIPPED));
            stages.add(simple(TaskPhase.FETCHING, StageState.SKIPPED));
            stages.add(simple(TaskPhase.WRITING, StageState.SKIPPED));
            stages.add(point(TaskPhase.FINISH, finish, StageState.SKIPPED));
            return stages;
        }

        if (TaskStatus.FAILED.is(status)) {
            TaskPhase failPhase = mapFailStage(t.getFailStage());
            stages.add(done(TaskPhase.ENQUEUE, enqueue, start));
            stages.add(staged(TaskPhase.EXECUTING, start, fetch, failPhase, StageState.FAILED, StageState.SKIPPED));
            stages.add(staged(TaskPhase.FETCHING, fetch, fetchEnd, failPhase, StageState.FAILED, StageState.SKIPPED));
            stages.add(isDql ? staged(TaskPhase.WRITING, write, upload, failPhase, StageState.FAILED, StageState.SKIPPED)
                    : simple(TaskPhase.WRITING, StageState.SKIPPED));
            stages.add(point(TaskPhase.FINISH, finish, StageState.FAILED));
            return stages;
        }

        if (TaskStatus.CANCELED.is(status)) {
            TaskPhase cancelPhase = mapTaskStage(t.getStage());
            if (cancelPhase == null) {
                cancelPhase = TaskPhase.EXECUTING;
            }
            stages.add(done(TaskPhase.ENQUEUE, enqueue, start));
            stages.add(staged(TaskPhase.EXECUTING, start, fetch, cancelPhase, StageState.CANCELED, StageState.SKIPPED));
            stages.add(staged(TaskPhase.FETCHING, fetch, fetchEnd, cancelPhase, StageState.CANCELED, StageState.SKIPPED));
            stages.add(isDql ? staged(TaskPhase.WRITING, write, upload, cancelPhase, StageState.CANCELED, StageState.SKIPPED)
                    : simple(TaskPhase.WRITING, StageState.SKIPPED));
            stages.add(point(TaskPhase.FINISH, finish, StageState.CANCELED));
            return stages;
        }

        if (TaskStatus.SUCCESS.is(status)) {
            stages.add(done(TaskPhase.ENQUEUE, enqueue, start));
            stages.add(done(TaskPhase.EXECUTING, start, fetch));
            stages.add(done(TaskPhase.FETCHING, fetch, fetchEnd));
            stages.add(isDql ? done(TaskPhase.WRITING, write, upload) : simple(TaskPhase.WRITING, StageState.SKIPPED));
            stages.add(point(TaskPhase.FINISH, finish, StageState.DONE));
            return stages;
        }

        // PENDING / RUNNING
        TaskPhase active = TaskStatus.PENDING.is(status) ? TaskPhase.EXECUTING : mapTaskStage(t.getStage());
        // PENDING task 尚未开始：active 阶段也是 PENDING（仅入队完成）；RUNNING task 的 active 阶段=RUNNING
        StageState activeState = TaskStatus.PENDING.is(status) ? StageState.PENDING : StageState.RUNNING;
        if (active == null) {
            active = TaskPhase.EXECUTING;
        }
        stages.add(done(TaskPhase.ENQUEUE, enqueue, start));
        stages.add(staged(TaskPhase.EXECUTING, start, fetch, active, activeState, StageState.PENDING));
        stages.add(staged(TaskPhase.FETCHING, fetch, fetchEnd, active, activeState, StageState.PENDING));
        stages.add(isDql ? staged(TaskPhase.WRITING, write, upload, active, activeState, StageState.PENDING)
                : simple(TaskPhase.WRITING, StageState.SKIPPED));
        stages.add(point(TaskPhase.FINISH, finish, StageState.PENDING));
        return stages;
    }

    /**
     * 阶段状态：active 之前=DONE；active 阶段=activeState；active 之后=afterState
     * （终态失败/取消之后用 SKIPPED，运行中之后用 PENDING）。
     */
    private StageTimeline staged(TaskPhase phase, Date start, Date end,
                                 TaskPhase active, StageState activeState, StageState afterState) {
        int cmp = Integer.compare(phase.ordinal(), active.ordinal());
        if (cmp < 0) {
            return done(phase, start, end);
        }
        if (cmp == 0) {
            return stateInterval(phase, start, end, activeState);
        }
        return simple(phase, afterState);
    }

    // ==================== 映射 ====================

    /** TaskStage（DB 当前阶段）-> TaskPhase（进度阶段）。SUCCESS -> FINISH。 */
    private TaskPhase mapTaskStage(String stage) {
        if (stage == null) {
            return null;
        }
        if (TaskStage.EXECUTING.is(stage)) return TaskPhase.EXECUTING;
        if (TaskStage.FETCHING.is(stage)) return TaskPhase.FETCHING;
        if (TaskStage.WRITING.is(stage)) return TaskPhase.WRITING;
        if (TaskStage.SUCCESS.is(stage)) return TaskPhase.FINISH;
        return null;
    }

    /** FailStage（失败阶段）-> TaskPhase。DISPATCH（server HA 层失败，task 未进入执行管线）/SPLIT 归 EXECUTING，OSS_UPLOAD 归 WRITING。 */
    private TaskPhase mapFailStage(String failStage) {
        if (failStage == null) {
            return TaskPhase.EXECUTING;
        }
        if (FailStage.DISPATCH.is(failStage) || FailStage.SPLIT.is(failStage) || FailStage.EXECUTING.is(failStage)) return TaskPhase.EXECUTING;
        if (FailStage.FETCHING.is(failStage)) return TaskPhase.FETCHING;
        if (FailStage.WRITING.is(failStage) || FailStage.OSS_UPLOAD.is(failStage)) return TaskPhase.WRITING;
        return TaskPhase.EXECUTING;
    }

    // ==================== 基础构造 ====================

    private StageTimeline point(JobPhase phase, Date time, StageState state) {
        return new StageTimeline(phase.name(), phase.getDisplayName(), state.name(), time, time, time != null ? 0L : null);
    }

    private StageTimeline point(TaskPhase phase, Date time, StageState state) {
        return new StageTimeline(phase.name(), phase.getDisplayName(), state.name(), time, time, time != null ? 0L : null);
    }

    private StageTimeline done(TaskPhase phase, Date start, Date end) {
        return new StageTimeline(phase.name(), phase.getDisplayName(), StageState.DONE.name(), start, end, duration(start, end));
    }

    private StageTimeline stateInterval(TaskPhase phase, Date start, Date end, StageState state) {
        return new StageTimeline(phase.name(), phase.getDisplayName(), state.name(), start, end, duration(start, end));
    }

    private StageTimeline simple(TaskPhase phase, StageState state) {
        return new StageTimeline(phase.name(), phase.getDisplayName(), state.name(), null, null, null);
    }

    private Long duration(Date start, Date end) {
        return (start != null && end != null) ? end.getTime() - start.getTime() : null;
    }

    /**
     * 后处理：进行中（RUNNING）阶段 end 仍为 null，补实时耗时 now - start（值未固定，下次查询会变）；
     * 再统一把 durationMs 格式化为人类可读 duration。终态/未开始阶段不受影响。
     */
    private void finalizeStages(List<StageTimeline> stages, Date now) {
        for (StageTimeline s : stages) {
            if (StageState.RUNNING.is(s.getStatus()) && s.getStartTime() != null && s.getEndTime() == null) {
                s.setDurationMs(now.getTime() - s.getStartTime().getTime());
            }
            s.setDuration(formatDuration(s.getDurationMs()));
        }
    }

    /**
     * 毫秒 -> 标准化可读时长：&lt;1s 用 ms，&lt;1min 用秒(2 位小数去尾零)，&lt;1h 用 m+s，其余 h+m。null 透传。
     */
    private static String formatDuration(Long ms) {
        if (ms == null) {
            return null;
        }
        if (ms < 1000) {
            return ms + "ms";
        }
        if (ms < 60_000) {
            String sec = new BigDecimal(ms).movePointLeft(3)
                    .setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
            return sec + "s";
        }
        if (ms < 3_600_000) {
            long m = ms / 60_000;
            long s = (ms % 60_000) / 1000;
            return s == 0 ? m + "m" : m + "m " + s + "s";
        }
        long h = ms / 3_600_000;
        long m = (ms % 3_600_000) / 60_000;
        return m == 0 ? h + "h" : h + "h " + m + "m";
    }
}