package io.coredge.health.claims.payments.exception;

import io.coredge.health.claims.exception.base.BusinessException;
import io.coredge.health.claims.exception.models.ErrorCode;
import lombok.Getter;

@Getter
public class PaymentDataMismatchException extends BusinessException {

    private final String field;
    private final Object expected;
    private final Object actual;

    public PaymentDataMismatchException(String claimId, String field, Object expected, Object actual) {
        super(ErrorCode.MISMATCH_DATA,
                String.format("Data mismatch for claim %s on field '%s'. Expected: %s, Actual: %s",
                        claimId, field, expected, actual));
        this.field = field;
        this.expected = expected;
        this.actual = actual;
    }
}
