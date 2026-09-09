package io.gitee.songchaolin.adhoc.common.enums;

public enum SqlType {
    DQL,
    DDL_CREATE,
    DDL_ALTER,
    DDL_DROP,
    DML_INSERT,
    DML_MODIFY,
    CTAS,
    AUX,
    DCL,
    SESSION_CONFIG,
    UNKNOWN;

    public boolean is(String value) {
        return name().equals(value);
    }

    public static SqlType fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (SqlType e : values()) {
            if (e.name().equals(value)) {
                return e;
            }
        }
        return null;
    }
}
