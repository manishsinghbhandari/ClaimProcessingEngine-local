package io.coredge.health.claims.exception.benefits;

import io.coredge.health.claims.exception.base.BusinessException;
import io.coredge.health.claims.exception.models.ErrorCode;

import java.io.Serial;

public class BenefitsNotFoundException extends BusinessException {

    @Serial
    private static final long serialVersionUID = 487703573561058809L;

    private static final ErrorCode CODE = ErrorCode.BENEFITS_NOT_FOUND;

    public BenefitsNotFoundException(String message) {
        super(CODE, message);
    }
}
