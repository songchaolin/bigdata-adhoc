package io.gitee.songchaolin.adhoc.common.enums;

/**
 * 阶段时间线上的状态：DONE(已完成) / RUNNING(进行中) / PENDING(未开始) /
 * SKIPPED(跳过，如无结果集 task 的 FETCHING/WRITING、被上游失败跳过的 task) /
 * FAILED(失败点) / CANCELED(取消点)。
 */
public enum StageState {
    DONE,
    RUNNING,
    PENDING,
    SKIPPED,
    FAILED,
    CANCELED;

    public boolean is(String value) {
        return name().equals(value);
    }

    public static StageState fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (StageState e : values()) {
            if (e.name().equals(value)) {
                return e;
            }
        }
        return null;
    }
}