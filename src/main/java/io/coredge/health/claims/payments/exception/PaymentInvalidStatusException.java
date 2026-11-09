package io.coredge.health.claims.payments.exception;

import io.coredge.health.claims.exception.base.BusinessException;
import io.coredge.health.claims.exception.models.ErrorCode;
import lombok.Getter;

@Getter
public class PaymentInvalidStatusException extends BusinessException {

    private final String currentStatus;
    private final String requiredStatus;

    public PaymentInvalidStatusException(String claimId, String currentStatus, String requiredStatus) {
        super(ErrorCode.INVALID_PAYMENT_STATUS,
                String.format(
                        "Payment status mismatch for claim %s. The current status is %s and it cannot be processed. It should have been %s.",
                        claimId, currentStatus, requiredStatus));
        this.currentStatus = currentStatus;
        this.requiredStatus = requiredStatus;
    }
}
