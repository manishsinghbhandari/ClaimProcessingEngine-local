package io.coredge.health.claims.payments.exception;

import io.coredge.health.claims.exception.app.DataNotFoundException;

public class PaymentBatchRecordNotFoundException extends DataNotFoundException {

    public PaymentBatchRecordNotFoundException(String batchId) {
        super(String.format("Payment processing record not found for batchId: %s", batchId));
    }
}
