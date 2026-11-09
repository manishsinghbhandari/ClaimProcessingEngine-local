package io.coredge.health.claims.exception.category;

import io.coredge.health.claims.exception.app.DataNotFoundException;
import io.coredge.health.claims.exception.models.ErrorCode;

import static io.coredge.health.claims.exception.models.ErrorCode.REASON_CATEGORY;

public class ReasonCategoryNotFoundException extends DataNotFoundException {

    private static final ErrorCode CODE = REASON_CATEGORY;
    private static final String MESSAGE = "Reason category does not exist";


    public ReasonCategoryNotFoundException() {
        super(CODE, MESSAGE);
    }
}
