package io.coredge.health.claims.payments.exception;

import io.coredge.health.claims.exception.base.BusinessException;
import io.coredge.health.claims.exception.models.ErrorCode;
import lombok.Getter;

@Getter
public class PaymentValidationException extends BusinessException {
    private final String errorMessage;

    public PaymentValidationException(String errorMessage) {
        super(ErrorCode.INVALID_DATA, errorMessage);
        this.errorMessage = errorMessage;
    }
}
