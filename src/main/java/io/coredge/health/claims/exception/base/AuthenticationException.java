package io.coredge.health.claims.exception.base;

import io.coredge.health.claims.exception.models.ErrorCode;

import java.io.Serial;

public class AuthenticationException extends ApiException {

    @Serial
    private static final long serialVersionUID = -2627475246514946725L;

    public AuthenticationException(ErrorCode code) {
        super(code);
    }

    public AuthenticationException(ErrorCode code, String message) {
        super(code, message);
    }

    public AuthenticationException(ErrorCode code, String message, Throwable cause) {
        super(code, message);
    }

    public AuthenticationException(ErrorCode code, Throwable cause) {
        super(code, cause);
    }
}
