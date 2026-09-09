package io.gitee.songchaolin.adhoc.common.exception;

import lombok.Getter;

@Getter
public class AdhocException extends RuntimeException {
    private final AdhocErrorCode errorCode;

    public AdhocException(AdhocErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
    }

    public AdhocException(AdhocErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public AdhocException(AdhocErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
}
