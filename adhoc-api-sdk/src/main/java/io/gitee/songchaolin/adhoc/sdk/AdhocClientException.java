package io.gitee.songchaolin.adhoc.sdk;

/**
 * SDK 客户端异常，携带错误码和错误信息。
 */
public class AdhocClientException extends RuntimeException {

    private final String errorCode;
    private final String errorMessage;

    public AdhocClientException(String errorCode, String errorMessage) {
        super(errorCode + ": " + errorMessage);
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    public AdhocClientException(String errorCode, String errorMessage, Throwable cause) {
        super(errorCode + ": " + errorMessage, cause);
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}