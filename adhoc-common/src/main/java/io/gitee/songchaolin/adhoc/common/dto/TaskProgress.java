package io.gitee.songchaolin.adhoc.common.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 单个 Task 的执行进度：当前阶段 + 阶段时间线。
 */
@Data
@NoArgsConstructor
public class TaskProgress {
    private String taskId;
    private Integer segmentIndex;
    private String status;          // TaskStatus 的 name()
    private String currentStage;    // TaskPhase 的 name()
    private List<StageTimeline> stages;

    public TaskProgress(String taskId, Integer segmentIndex, String status,
                        String currentStage, List<StageTimeline> stages) {
        this.taskId = taskId;
        this.segmentIndex = segmentIndex;
        this.status = status;
        this.currentStage = currentStage;
        this.stages = stages;
    }
}