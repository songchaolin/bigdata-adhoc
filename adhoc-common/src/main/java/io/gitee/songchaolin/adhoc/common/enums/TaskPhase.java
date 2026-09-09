package io.gitee.songchaolin.adhoc.common.enums;

/**
 * Task 执行阶段（进度时间线用）：
 * ENQUEUE(入队) -> EXECUTING(执行, start->fetchStart) -> FETCHING(拉取, fetchStart->writeStart)
 * -> WRITING(写入, writeStart->ossUpload) -> FINISH(完成)。
 * 无结果集(DDL/DML) task 的 FETCHING/WRITING 标 SKIPPED。
 */
public enum TaskPhase {
    ENQUEUE("入队"),
    EXECUTING("执行"),
    FETCHING("拉取"),
    WRITING("写入"),
    FINISH("完成");

    private final String displayName;

    TaskPhase(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean is(String value) {
        return name().equals(value);
    }

    public static TaskPhase fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (TaskPhase e : values()) {
            if (e.name().equals(value)) {
                return e;
            }
        }
        return null;
    }
}