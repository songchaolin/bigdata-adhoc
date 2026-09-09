package io.gitee.songchaolin.adhoc.common.enums;

public enum JobStatus {
    PENDING,
    DISPATCHING,   // QueueWorker claim 后、executor createTasks 前（前端可看到"调度中"）
    RUNNING,
    SUCCESS,
    PARTIAL_FAILED,
    FAILED,
    CANCELED;

    public boolean is(String value) {
        return name().equals(value);
    }

    public static JobStatus fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (JobStatus e : values()) {
            if (e.name().equals(value)) {
                return e;
            }
        }
        return null;
    }

    /** 终态：SUCCESS / FAILED / PARTIAL_FAILED / CANCELED。 */
    public static boolean isTerminal(String value) {
        return SUCCESS.name().equals(value)
                || FAILED.name().equals(value)
                || PARTIAL_FAILED.name().equals(value)
                || CANCELED.name().equals(value);
    }

    /** 运行中：PENDING / DISPATCHING / RUNNING。 */
    public static boolean isRunning(String value) {
        return PENDING.name().equals(value)
                || DISPATCHING.name().equals(value)
                || RUNNING.name().equals(value);
    }
}
