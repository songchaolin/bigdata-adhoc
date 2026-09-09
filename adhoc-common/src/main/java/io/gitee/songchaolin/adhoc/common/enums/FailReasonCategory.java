package io.gitee.songchaolin.adhoc.common.enums;

public enum FailReasonCategory {
    ENGINE_ERROR,
    EXECUTOR_CRASHED,
    QUERY_TIMEOUT,
    TASK_LOST,
    SKIPPED_DUE_TO_PRIOR_FAILURE,
    SKIPPED_DUE_TO_SESSION_LOSS,
    WRITE_ERROR,
    FETCH_ERROR,
    SPLIT_ERROR,
    CANCELED;

    public boolean is(String value) {
        return name().equals(value);
    }

    public static FailReasonCategory fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (FailReasonCategory e : values()) {
            if (e.name().equals(value)) {
                return e;
            }
        }
        return null;
    }
}
