package io.coredge.health.claims.exception.benefits;

import io.coredge.health.claims.exception.base.BusinessException;
import io.coredge.health.claims.exception.models.ErrorCode;
import java.io.Serial;

public class RequestedAIValidationNotExist extends BusinessException {

    @Serial
    private static final long serialVersionUID = 487703573561058809L;

    private static final ErrorCode CODE = ErrorCode.SAME_VALUE_UPDATE_NOT_ALLOWED;

    public RequestedAIValidationNotExist(String message) {
        super(CODE, message);
    }
}
