package io.gitee.songchaolin.adhoc.common.enums;

public enum FailStage {
    /** server 侧派发/HA 层失败：executor 宕机时 PENDING task（未进入执行管线）、孤儿 job 清理。精确原因见 fail_reason_category（SKIPPED_DUE_TO_SESSION_LOSS 等）。 */
    DISPATCH,
    SPLIT,
    EXECUTING,
    FETCHING,
    WRITING,
    OSS_UPLOAD;

    public boolean is(String value) {
        return name().equals(value);
    }

    public static FailStage fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (FailStage e : values()) {
            if (e.name().equals(value)) {
                return e;
            }
        }
        return null;
    }
}
