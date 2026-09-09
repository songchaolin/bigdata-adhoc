package io.gitee.songchaolin.adhoc.common.enums;

/**
 * 删除状态枚举
 */
public enum DeletedStatus {
    NOT_DELETED(0, "未删除"),
    DELETED(1, "已删除");

    private final int code;
    private final String desc;

    DeletedStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    public boolean is(Integer value) {
        return value != null && code == value;
    }

    public static DeletedStatus fromValue(Integer value) {
        if (value == null) {
            return null;
        }
        for (DeletedStatus e : values()) {
            if (e.code == value) {
                return e;
            }
        }
        return null;
    }
}
