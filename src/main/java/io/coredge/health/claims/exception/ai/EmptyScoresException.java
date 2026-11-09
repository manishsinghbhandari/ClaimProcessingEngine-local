package io.coredge.health.claims.exception.ai;

import io.coredge.health.claims.exception.base.BusinessException;
import io.coredge.health.claims.exception.models.ErrorCode;

import java.io.Serial;


public class EmptyScoresException extends BusinessException {

    @Serial
    private static final long serialVersionUID = 487703573561058809L;

    private static final ErrorCode CODE = ErrorCode.INVALID_EMPTY_SCORES_EXCEPTION;

    public EmptyScoresException(String message) {
        super(CODE, message);
    }
}