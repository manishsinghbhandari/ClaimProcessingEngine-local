package io.coredge.health.claims.exception.intervention;

import io.coredge.health.claims.exception.base.BusinessException;
import io.coredge.health.claims.exception.models.ErrorCode;

public class RuleViolationException extends BusinessException {

    public RuleViolationException(ErrorCode code, String message) {
        super(code, message);
    }

}
