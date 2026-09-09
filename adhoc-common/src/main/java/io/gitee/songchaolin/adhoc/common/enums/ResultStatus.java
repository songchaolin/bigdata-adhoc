package io.gitee.songchaolin.adhoc.common.enums;

public enum ResultStatus {
    WRITING,
    COMPLETE,
    INCOMPLETE;

    public boolean is(String value) {
        return name().equals(value);
    }

    public static ResultStatus fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (ResultStatus e : values()) {
            if (e.name().equals(value)) {
                return e;
            }
        }
        return null;
    }
}
