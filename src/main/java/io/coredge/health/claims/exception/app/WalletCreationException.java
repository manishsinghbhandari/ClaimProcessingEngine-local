package io.coredge.health.claims.exception.app;

import io.coredge.health.claims.exception.base.BusinessException;
import io.coredge.health.claims.exception.models.ErrorCode;

import static io.coredge.health.claims.exception.models.ErrorCode.INVALID_WORK_FLOW;

public class WalletCreationException extends BusinessException {

    private static final ErrorCode CODE = INVALID_WORK_FLOW;

    public WalletCreationException(String message) {
        super(CODE, message);
    }

    public WalletCreationException(String message, Throwable cause) {
        super(CODE, message, cause);
    }
}