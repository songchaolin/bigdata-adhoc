package io.gitee.songchaolin.adhoc.common.enums;

public enum OssUploadStatus {
    PENDING,
    SUCCESS,
    FAILED;

    public boolean is(String value) {
        return name().equals(value);
    }

    public static OssUploadStatus fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (OssUploadStatus e : values()) {
            if (e.name().equals(value)) {
                return e;
            }
        }
        return null;
    }
}
