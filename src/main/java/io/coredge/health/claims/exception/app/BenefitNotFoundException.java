package io.coredge.health.claims.exception.app;

import io.coredge.health.claims.exception.base.BusinessException;
import io.coredge.health.claims.exception.models.ErrorCode;

import static io.coredge.health.claims.exception.models.ErrorCode.BENEFITS_NOT_FOUND;

public class BenefitNotFoundException extends BusinessException {
    private static final ErrorCode CODE = BENEFITS_NOT_FOUND;
    private static final String DEFAULT_MSG = "User does not exist for the given token.";
    private static final long serialVersionUID = 487703573561058809L;

    public BenefitNotFoundException() {
        super(CODE, DEFAULT_MSG);
    }

    public BenefitNotFoundException(String message) {
        super(CODE, message);
    }

    public BenefitNotFoundException(String message, Throwable cause) {
        super(CODE, message, cause);
    }

    public BenefitNotFoundException(Throwable cause) {
        super(CODE, DEFAULT_MSG, cause);
    }

    @Override
    public synchronized Throwable fillInStackTrace() {
        return this;
    }
}
