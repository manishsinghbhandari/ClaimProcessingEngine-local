package io.coredge.health.claims.payments.exception;

import io.coredge.health.claims.exception.base.BusinessException;
import io.coredge.health.claims.exception.models.ErrorCode;

public class PaymentMissingDataException extends BusinessException {
    public PaymentMissingDataException(String message) {
        super(ErrorCode.VALIDATE_DATA_CANNOT_BLANK, message);
    }
}
