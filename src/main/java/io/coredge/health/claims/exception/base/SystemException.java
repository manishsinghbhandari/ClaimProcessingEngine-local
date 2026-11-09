package io.coredge.health.claims.exception.base;

import io.coredge.health.claims.exception.models.ErrorCode;

import java.io.Serial;

public class SystemException extends ApiException {

    private static final ErrorCode CODE = ErrorCode.SYSTEM_EXCEPTION;

    @Serial
    private static final long serialVersionUID = 3592923536345132037L;

    public SystemException(String message) {
        super(CODE, message);
    }

    public SystemException(String message, Throwable cause) {
        super(CODE, message, cause);
    }

    public SystemException(ErrorCode code, String message, Throwable cause) {
        super(code, message, cause);
    }

    public SystemException(ErrorCode code, String message) {
        super(code, message);
    }
}
