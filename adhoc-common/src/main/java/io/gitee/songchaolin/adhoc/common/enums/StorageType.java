package io.gitee.songchaolin.adhoc.common.enums;

public enum StorageType {
    NONE,
    PERSISTENT;

    public boolean is(String value) {
        return name().equals(value);
    }

    public static StorageType fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (StorageType e : values()) {
            if (e.name().equals(value)) {
                return e;
            }
        }
        return null;
    }
}
