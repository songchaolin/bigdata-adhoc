package io.gitee.songchaolin.adhoc.common.enums;

public enum TaskStage {
    EXECUTING,
    FETCHING,
    WRITING,
    SUCCESS;

    public boolean is(String value) {
        return name().equals(value);
    }

    public static TaskStage fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (TaskStage e : values()) {
            if (e.name().equals(value)) {
                return e;
            }
        }
        return null;
    }
}
