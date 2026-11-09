package io.coredge.health.claims.exception.base;

import io.coredge.health.claims.exception.models.ErrorCode;

import java.io.Serial;

public class ExternalServiceException extends BusinessException{

    private static final ErrorCode CODE = ErrorCode.THIRD_PARTY_API_ERROR;

    @Serial
    private static final long serialVersionUID = 1L;

    public ExternalServiceException(String message) {
        super(CODE, message);
    }

    public ExternalServiceException(String message, Throwable cause) {
        super(CODE, message, cause);
    }

    public ExternalServiceException(ErrorCode code, String message, Throwable cause) {
        super(code, message, cause);
    }

    public ExternalServiceException(ErrorCode code, String message) {
        super(code, message);
    }
}