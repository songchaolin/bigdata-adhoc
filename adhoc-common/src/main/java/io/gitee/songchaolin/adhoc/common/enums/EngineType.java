package io.gitee.songchaolin.adhoc.common.enums;

public enum EngineType {
    KYUUBI,
    STARROCKS;

    public boolean is(String value) {
        return name().equals(value);
    }

    public static EngineType fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (EngineType e : values()) {
            if (e.name().equals(value)) {
                return e;
            }
        }
        return null;
    }
}
