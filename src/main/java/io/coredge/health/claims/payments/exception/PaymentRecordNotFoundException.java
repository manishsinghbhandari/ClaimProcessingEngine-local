package io.coredge.health.claims.payments.exception;

import io.coredge.health.claims.exception.app.DataNotFoundException;

public class PaymentRecordNotFoundException extends DataNotFoundException {

    public PaymentRecordNotFoundException(String claimId) {
        super(String.format("Payment processing record not found for claimId: %s", claimId));
    }
}
