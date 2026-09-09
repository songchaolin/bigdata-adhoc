package io.gitee.songchaolin.adhoc.common.enums;

public enum TaskStatus {
    PENDING,
    RUNNING,
    SUCCESS,
    SKIPPED,
    FAILED,
    CANCELED;

    public boolean is(String value) {
        return name().equals(value);
    }

    public static TaskStatus fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (TaskStatus e : values()) {
            if (e.name().equals(value)) {
                return e;
            }
        }
        return null;
    }

    /** 终态：SUCCESS / FAILED / CANCELED。 */
    public static boolean isTerminal(String value) {
        return SUCCESS.name().equals(value)
                || FAILED.name().equals(value)
                || CANCELED.name().equals(value);
    }

    /** 运行中：RUNNING / PENDING。 */
    public static boolean isRunning(String value) {
        return RUNNING.name().equals(value)
                || PENDING.name().equals(value);
    }
}
