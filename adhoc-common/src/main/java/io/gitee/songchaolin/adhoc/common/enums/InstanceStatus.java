package io.gitee.songchaolin.adhoc.common.enums;

public enum InstanceStatus {
    UP,
    DOWN,
    UNKNOWN;

    public boolean is(String value) {
        return name().equals(value);
    }

    public static InstanceStatus fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (InstanceStatus e : values()) {
            if (e.name().equals(value)) {
                return e;
            }
        }
        return null;
    }

    public static boolean isUp(String value) {
        return UP.name().equals(value);
    }
}
