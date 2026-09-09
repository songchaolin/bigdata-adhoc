package io.gitee.songchaolin.adhoc.common.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Job 执行进度：Job 级阶段时间线 + 各 Task 阶段时间线。
 * 前端据此渲染"提交->排队->调度->运行->完成"的进度 DAG 与各阶段耗时。
 */
@Data
@NoArgsConstructor
public class JobProgressResponse {
    private String jobId;
    private String status;              // JobStatus 的 name()
    private String currentStage;        // JobPhase 的 name()
    private List<StageTimeline> stages; // Job 级阶段时间线
    private List<TaskProgress> tasks;   // Task 级进度（按 segmentIndex）

    public JobProgressResponse(String jobId, String status, String currentStage,
                               List<StageTimeline> stages, List<TaskProgress> tasks) {
        this.jobId = jobId;
        this.status = status;
        this.currentStage = currentStage;
        this.stages = stages;
        this.tasks = tasks;
    }
}