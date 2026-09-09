package io.gitee.songchaolin.adhoc.server.service;

import io.gitee.songchaolin.adhoc.common.dto.JobProgressResponse;
import io.gitee.songchaolin.adhoc.common.dto.StageTimeline;
import io.gitee.songchaolin.adhoc.common.dto.TaskProgress;
import io.gitee.songchaolin.adhoc.common.enums.JobPhase;
import io.gitee.songchaolin.adhoc.common.enums.JobStatus;
import io.gitee.songchaolin.adhoc.common.enums.StageState;
import io.gitee.songchaolin.adhoc.common.enums.TaskPhase;
import io.gitee.songchaolin.adhoc.common.enums.TaskStage;
import io.gitee.songchaolin.adhoc.common.enums.TaskStatus;
import io.gitee.songchaolin.adhoc.dao.entity.AdhocQueryJob;
import io.gitee.songchaolin.adhoc.dao.entity.AdhocQueryTask;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JobProgressAssembler 单测（纯函数，无 Spring/DB）：覆盖 Job/Task 各状态的阶段状态、currentStage、耗时判定。
 */
class JobProgressAssemblerTest {

    private final JobProgressAssembler assembler = new JobProgressAssembler();

    // ==================== Job 级 ====================

    @Test
    void job_pending_queueRunning() {
        AdhocQueryJob job = job("j1", JobStatus.PENDING.name(), t(1000), null, null, null, null);
        JobProgressResponse r = assembler.assemble(job, Collections.emptyList());
        assertThat(r.getCurrentStage()).isEqualTo(JobPhase.QUEUE.name());
        assertThat(stage(r, JobPhase.SUBMIT).getStatus()).isEqualTo(StageState.DONE.name());
        assertThat(stage(r, JobPhase.QUEUE).getStatus()).isEqualTo(StageState.RUNNING.name());
        assertThat(stage(r, JobPhase.QUEUE).getDurationMs()).isGreaterThan(0L); // PENDING 进行中：now-submit 实时
        assertThat(stage(r, JobPhase.DISPATCH).getStatus()).isEqualTo(StageState.PENDING.name());
        assertThat(stage(r, JobPhase.FINISH).getStatus()).isEqualTo(StageState.PENDING.name());
    }

    @Test
    void job_dispatching_dispatchRunning() {
        AdhocQueryJob job = job("j1", JobStatus.DISPATCHING.name(), t(1000), t(2000), null, null, null);
        JobProgressResponse r = assembler.assemble(job, Collections.emptyList());
        assertThat(r.getCurrentStage()).isEqualTo(JobPhase.DISPATCH.name());
        assertThat(stage(r, JobPhase.QUEUE).getStatus()).isEqualTo(StageState.DONE.name());
        assertThat(stage(r, JobPhase.QUEUE).getDurationMs()).isEqualTo(1000L);
        assertThat(stage(r, JobPhase.DISPATCH).getStatus()).isEqualTo(StageState.RUNNING.name());
        assertThat(stage(r, JobPhase.RUNNING).getStatus()).isEqualTo(StageState.PENDING.name());
    }

    @Test
    void job_running_runningStageRunning() {
        // splitFinishTime == startTime（markRunning 同时写）
        AdhocQueryJob job = job("j1", JobStatus.RUNNING.name(), t(1000), t(2000), t(3000), t(3000), null);
        JobProgressResponse r = assembler.assemble(job, Collections.emptyList());
        assertThat(r.getCurrentStage()).isEqualTo(JobPhase.RUNNING.name());
        assertThat(stage(r, JobPhase.DISPATCH).getStatus()).isEqualTo(StageState.DONE.name());
        assertThat(stage(r, JobPhase.RUNNING).getStatus()).isEqualTo(StageState.RUNNING.name());
        assertThat(stage(r, JobPhase.RUNNING).getDurationMs()).isGreaterThan(0L); // RUNNING 进行中：now-start 实时
        assertThat(stage(r, JobPhase.RUNNING).getDuration()).isNotNull();
    }

    @Test
    void job_success_allDone() {
        AdhocQueryJob job = job("j1", JobStatus.SUCCESS.name(), t(1000), t(2000), t(3000), t(3000), t(9000));
        JobProgressResponse r = assembler.assemble(job, Collections.emptyList());
        assertThat(r.getCurrentStage()).isEqualTo(JobPhase.FINISH.name());
        for (JobPhase p : JobPhase.values()) {
            assertThat(stage(r, p).getStatus()).isEqualTo(StageState.DONE.name());
        }
        assertThat(stage(r, JobPhase.RUNNING).getDurationMs()).isEqualTo(6000L);
        assertThat(stage(r, JobPhase.RUNNING).getDuration()).isEqualTo("6s");
    }

    @Test
    void job_failed_finishFailed() {
        AdhocQueryJob job = job("j1", JobStatus.FAILED.name(), t(1000), t(2000), t(3000), t(3000), t(9000));
        assertThat(stage(assembler.assemble(job, Collections.emptyList()), JobPhase.FINISH).getStatus())
                .isEqualTo(StageState.FAILED.name());
    }

    @Test
    void job_canceled_finishCanceled() {
        AdhocQueryJob job = job("j1", JobStatus.CANCELED.name(), t(1000), t(2000), t(3000), t(3000), t(9000));
        assertThat(stage(assembler.assemble(job, Collections.emptyList()), JobPhase.FINISH).getStatus())
                .isEqualTo(StageState.CANCELED.name());
    }

    @Test
    void job_nullTasks_notBlowUp() {
        AdhocQueryJob job = job("j1", JobStatus.PENDING.name(), t(1000), null, null, null, null);
        JobProgressResponse r = assembler.assemble(job, null);
        assertThat(r.getTasks()).isEmpty();
    }

    // ==================== Task 级 ====================

    @Test
    void task_success_dql_allStagesDone() {
        AdhocQueryTask task = task("t1", 0, TaskStatus.SUCCESS.name(), TaskStage.SUCCESS.name(),
                null, "DQL", t(1000), t(2000), t(5000), t(6000), t(7000), t(8000));
        TaskProgress p = singleTask(task);
        assertThat(p.getCurrentStage()).isEqualTo(TaskPhase.FINISH.name());
        assertThat(tstage(p, TaskPhase.ENQUEUE).getStatus()).isEqualTo(StageState.DONE.name());
        assertThat(tstage(p, TaskPhase.ENQUEUE).getDurationMs()).isEqualTo(1000L); // start(2000)-enqueue(1000)
        assertThat(tstage(p, TaskPhase.ENQUEUE).getDuration()).isEqualTo("1s");
        assertThat(tstage(p, TaskPhase.EXECUTING).getStatus()).isEqualTo(StageState.DONE.name());
        assertThat(tstage(p, TaskPhase.EXECUTING).getDurationMs()).isEqualTo(3000L); // fetch(5000)-start(2000)
        assertThat(tstage(p, TaskPhase.EXECUTING).getDuration()).isEqualTo("3s");
        assertThat(tstage(p, TaskPhase.FETCHING).getStatus()).isEqualTo(StageState.DONE.name());
        assertThat(tstage(p, TaskPhase.FETCHING).getDurationMs()).isEqualTo(1000L);   // write(6000)-fetch(5000)
        assertThat(tstage(p, TaskPhase.WRITING).getStatus()).isEqualTo(StageState.DONE.name());
        assertThat(tstage(p, TaskPhase.WRITING).getDurationMs()).isEqualTo(1000L);    // upload(7000)-write(6000)
        assertThat(tstage(p, TaskPhase.FINISH).getStatus()).isEqualTo(StageState.DONE.name());
    }

    @Test
    void task_success_ddl_writingSkipped_fetchEndsAtFinish() {
        AdhocQueryTask task = task("t1", 0, TaskStatus.SUCCESS.name(), TaskStage.SUCCESS.name(),
                null, "DDL", t(1000), t(2000), t(5000), null, null, t(8000));
        TaskProgress p = singleTask(task);
        // DDL 无 write_start：FETCHING 持续到 finish
        assertThat(tstage(p, TaskPhase.FETCHING).getStatus()).isEqualTo(StageState.DONE.name());
        assertThat(tstage(p, TaskPhase.FETCHING).getDurationMs()).isEqualTo(3000L); // finish(8000)-fetch(5000)
        assertThat(tstage(p, TaskPhase.WRITING).getStatus()).isEqualTo(StageState.SKIPPED.name());
        assertThat(tstage(p, TaskPhase.WRITING).getDurationMs()).isNull();
    }

    @Test
    void task_running_executing() {
        AdhocQueryTask task = task("t1", 0, TaskStatus.RUNNING.name(), TaskStage.EXECUTING.name(),
                null, "DQL", t(1000), t(2000), null, null, null, null);
        TaskProgress p = singleTask(task);
        assertThat(p.getCurrentStage()).isEqualTo(TaskPhase.EXECUTING.name());
        assertThat(tstage(p, TaskPhase.EXECUTING).getStatus()).isEqualTo(StageState.RUNNING.name());
        assertThat(tstage(p, TaskPhase.FETCHING).getStatus()).isEqualTo(StageState.PENDING.name());
        assertThat(tstage(p, TaskPhase.WRITING).getStatus()).isEqualTo(StageState.PENDING.name());
        assertThat(tstage(p, TaskPhase.FINISH).getStatus()).isEqualTo(StageState.PENDING.name());
    }

    @Test
    void task_running_fetching() {
        AdhocQueryTask task = task("t1", 0, TaskStatus.RUNNING.name(), TaskStage.FETCHING.name(),
                null, "DQL", t(1000), t(2000), t(5000), null, null, null);
        TaskProgress p = singleTask(task);
        assertThat(p.getCurrentStage()).isEqualTo(TaskPhase.FETCHING.name());
        assertThat(tstage(p, TaskPhase.EXECUTING).getStatus()).isEqualTo(StageState.DONE.name());
        assertThat(tstage(p, TaskPhase.FETCHING).getStatus()).isEqualTo(StageState.RUNNING.name());
        assertThat(tstage(p, TaskPhase.WRITING).getStatus()).isEqualTo(StageState.PENDING.name());
    }

    @Test
    void task_running_writing() {
        AdhocQueryTask task = task("t1", 0, TaskStatus.RUNNING.name(), TaskStage.WRITING.name(),
                null, "DQL", t(1000), t(2000), t(5000), t(6000), null, null);
        TaskProgress p = singleTask(task);
        assertThat(p.getCurrentStage()).isEqualTo(TaskPhase.WRITING.name());
        assertThat(tstage(p, TaskPhase.FETCHING).getStatus()).isEqualTo(StageState.DONE.name());
        assertThat(tstage(p, TaskPhase.WRITING).getStatus()).isEqualTo(StageState.RUNNING.name());
        assertThat(tstage(p, TaskPhase.FINISH).getStatus()).isEqualTo(StageState.PENDING.name());
    }

    @Test
    void task_failed_executing() {
        AdhocQueryTask task = task("t1", 0, TaskStatus.FAILED.name(), null,
                "EXECUTING", "DQL", t(1000), t(2000), null, null, null, t(9000));
        TaskProgress p = singleTask(task);
        assertThat(p.getCurrentStage()).isEqualTo(TaskPhase.FINISH.name());
        assertThat(tstage(p, TaskPhase.EXECUTING).getStatus()).isEqualTo(StageState.FAILED.name());
        assertThat(tstage(p, TaskPhase.FETCHING).getStatus()).isEqualTo(StageState.SKIPPED.name());
        assertThat(tstage(p, TaskPhase.WRITING).getStatus()).isEqualTo(StageState.SKIPPED.name());
        assertThat(tstage(p, TaskPhase.FINISH).getStatus()).isEqualTo(StageState.FAILED.name());
    }

    @Test
    void task_failed_split_mapsToExecuting() {
        AdhocQueryTask task = task("t1", 0, TaskStatus.FAILED.name(), null,
                "SPLIT", "DDL", t(1000), null, null, null, null, t(9000));
        assertThat(tstage(singleTask(task), TaskPhase.EXECUTING).getStatus()).isEqualTo(StageState.FAILED.name());
    }

    @Test
    void task_canceled_fetching() {
        AdhocQueryTask task = task("t1", 0, TaskStatus.CANCELED.name(), TaskStage.FETCHING.name(),
                null, "DQL", t(1000), t(2000), t(5000), null, null, t(9000));
        TaskProgress p = singleTask(task);
        assertThat(tstage(p, TaskPhase.EXECUTING).getStatus()).isEqualTo(StageState.DONE.name());
        assertThat(tstage(p, TaskPhase.FETCHING).getStatus()).isEqualTo(StageState.CANCELED.name());
        assertThat(tstage(p, TaskPhase.WRITING).getStatus()).isEqualTo(StageState.SKIPPED.name());
        assertThat(tstage(p, TaskPhase.FINISH).getStatus()).isEqualTo(StageState.CANCELED.name());
    }

    @Test
    void task_skipped_onlyEnqueueDone() {
        AdhocQueryTask task = task("t1", 0, TaskStatus.SKIPPED.name(), null,
                null, "DQL", t(1000), null, null, null, null, t(2000));
        TaskProgress p = singleTask(task);
        assertThat(tstage(p, TaskPhase.ENQUEUE).getStatus()).isEqualTo(StageState.DONE.name());
        assertThat(tstage(p, TaskPhase.EXECUTING).getStatus()).isEqualTo(StageState.SKIPPED.name());
        assertThat(tstage(p, TaskPhase.FETCHING).getStatus()).isEqualTo(StageState.SKIPPED.name());
        assertThat(tstage(p, TaskPhase.FINISH).getStatus()).isEqualTo(StageState.SKIPPED.name());
    }

    @Test
    void task_pending_executingPending() {
        AdhocQueryTask task = task("t1", 0, TaskStatus.PENDING.name(), null,
                null, "DQL", t(1000), null, null, null, null, null);
        TaskProgress p = singleTask(task);
        assertThat(p.getCurrentStage()).isEqualTo(TaskPhase.EXECUTING.name());
        assertThat(tstage(p, TaskPhase.ENQUEUE).getStatus()).isEqualTo(StageState.DONE.name());
        assertThat(tstage(p, TaskPhase.ENQUEUE).getDurationMs()).isNull(); // PENDING 未开始执行，排队耗时未知
        assertThat(tstage(p, TaskPhase.EXECUTING).getStatus()).isEqualTo(StageState.PENDING.name());
        assertThat(tstage(p, TaskPhase.WRITING).getStatus()).isEqualTo(StageState.PENDING.name());
    }

    @Test
    void task_running_ddl_writingSkipped() {
        // DDL 执行中（stage=EXECUTING）：WRITING 永远 SKIPPED（DDL 无结果集）
        AdhocQueryTask task = task("t1", 0, TaskStatus.RUNNING.name(), TaskStage.EXECUTING.name(),
                null, "DDL", t(1000), t(2000), null, null, null, null);
        TaskProgress p = singleTask(task);
        assertThat(tstage(p, TaskPhase.WRITING).getStatus()).isEqualTo(StageState.SKIPPED.name());
    }

    @Test
    void task_success_durationFormat_msAndMinutes() {
        // ENQUEUE 80ms（毫秒档）, EXECUTING 90000ms（分钟档 1m 30s）
        AdhocQueryTask task = task("t1", 0, TaskStatus.SUCCESS.name(), TaskStage.SUCCESS.name(),
                null, "DQL", t(0), t(80), t(90080), t(90080), t(90080), t(90080));
        TaskProgress p = singleTask(task);
        assertThat(tstage(p, TaskPhase.ENQUEUE).getDuration()).isEqualTo("80ms");
        assertThat(tstage(p, TaskPhase.EXECUTING).getDuration()).isEqualTo("1m 30s");
    }

    // ==================== helpers ====================

    private TaskProgress singleTask(AdhocQueryTask task) {
        return assembler.assemble(job("j1", JobStatus.RUNNING.name(), t(0), t(0), t(0), t(0), null),
                Collections.singletonList(task)).getTasks().get(0);
    }

    private StageTimeline stage(JobProgressResponse r, JobPhase p) {
        return r.getStages().stream().filter(s -> p.is(s.getStage()))
                .findFirst().orElseThrow(() -> new AssertionError("missing job stage " + p));
    }

    private StageTimeline tstage(TaskProgress p, TaskPhase ph) {
        return p.getStages().stream().filter(s -> ph.is(s.getStage()))
                .findFirst().orElseThrow(() -> new AssertionError("missing task stage " + ph));
    }

    private Date t(long ms) {
        return new Date(ms);
    }

    private AdhocQueryJob job(String id, String status, Date submit, Date dispatch, Date split, Date start, Date finish) {
        AdhocQueryJob j = new AdhocQueryJob();
        j.setJobId(id);
        j.setStatus(status);
        j.setSubmitTime(submit);
        j.setDispatchTime(dispatch);
        j.setSplitFinishTime(split);
        j.setStartTime(start);
        j.setFinishTime(finish);
        return j;
    }

    private AdhocQueryTask task(String id, int idx, String status, String stage, String failStage,
                                String sqlType, Date enqueue, Date start, Date fetch, Date write, Date upload, Date finish) {
        AdhocQueryTask t = new AdhocQueryTask();
        t.setQueryId(id);
        t.setSegmentIndex(idx);
        t.setStatus(status);
        t.setStage(stage);
        t.setFailStage(failStage);
        t.setSqlType(sqlType);
        t.setEnqueueTime(enqueue);
        t.setStartTime(start);
        t.setFetchStartTime(fetch);
        t.setWriteStartTime(write);
        t.setOssUploadTime(upload);
        t.setFinishTime(finish);
        return t;
    }
}
