package io.gitee.songchaolin.adhoc.common.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 单个阶段的时间线项：阶段码 + 中文名 + 状态 + 起止时间 + 耗时。
 * 前端据此渲染进度条/时间线；{@code durationMs=null} 表示未开始，进行中阶段由 Assembler 补实时耗时(now-start)。
 */
@Data
@NoArgsConstructor
public class StageTimeline {
    private String stage;        // 阶段码（JobPhase/TaskPhase 的 name()）
    private String name;         // 中文展示名
    private String status;       // StageState 的 name()
    private Date startTime;
    private Date endTime;
    private Long durationMs;     // = endTime - startTime；进行中阶段由 Assembler 补 now-start（值未固定）
    private String duration;     // 人类可读时长（如 "34.77s"/"5m 23s"/"2h 15m"），由 Assembler 格式化；未采集时为 null

    public StageTimeline(String stage, String name, String status,
                         Date startTime, Date endTime, Long durationMs) {
        this.stage = stage;
        this.name = name;
        this.status = status;
        this.startTime = startTime;
        this.endTime = endTime;
        this.durationMs = durationMs;
    }
}