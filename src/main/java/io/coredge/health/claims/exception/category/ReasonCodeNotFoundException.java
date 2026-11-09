package io.coredge.health.claims.exception.category;

import io.coredge.health.claims.exception.app.DataNotFoundException;
import io.coredge.health.claims.exception.models.ErrorCode;

import static io.coredge.health.claims.exception.models.ErrorCode.REASON_CODE_NOT_FOUND;

public class ReasonCodeNotFoundException extends DataNotFoundException {

    private static final ErrorCode CODE = REASON_CODE_NOT_FOUND;
    private static final String MESSAGE = "Reason code does not exist";

    public ReasonCodeNotFoundException() {
        super(CODE, MESSAGE);
    }
}
