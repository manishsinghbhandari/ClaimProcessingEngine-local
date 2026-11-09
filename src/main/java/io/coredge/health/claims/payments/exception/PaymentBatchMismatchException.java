package io.coredge.health.claims.payments.exception;

import io.coredge.health.claims.exception.base.BusinessException;
import io.coredge.health.claims.exception.models.ErrorCode;
import lombok.Getter;

@Getter
public class PaymentBatchMismatchException extends BusinessException {

    private final String field;
    private final Object expected;
    private final Object actual;

    public PaymentBatchMismatchException(String batchId, String field, Object expected, Object actual) {
        super(ErrorCode.MISMATCH_DATA,
                String.format("Batch data mismatch for batch %s in field '%s'. System value: %s, Gateway value: %s",
                        batchId, field, expected, actual));
        this.field = field;
        this.expected = expected;
        this.actual = actual;
    }
}
