package io.coredge.health.claims.payments.exception;

import io.coredge.health.claims.exception.base.BusinessException;
import io.coredge.health.claims.exception.models.ErrorCode;
import lombok.Getter;

@Getter
public class PaymentBatchInvalidStatusException extends BusinessException {

    private final String currentStatus;
    private final String requiredStatus;

    public PaymentBatchInvalidStatusException(String batchId, String currentStatus, String requiredStatus) {
        super(ErrorCode.INVALID_PAYMENT_STATUS,
                String.format(
                        "Payment status mismatch for batch %s. The current status is %s and it cannot be processed. It should have been %s.",
                        batchId, currentStatus, requiredStatus));
        this.currentStatus = currentStatus;
        this.requiredStatus = requiredStatus;
    }
}
