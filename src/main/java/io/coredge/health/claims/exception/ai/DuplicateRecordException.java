package io.coredge.health.claims.exception.ai;

import io.coredge.health.claims.exception.app.DataAlreadyExistsException;
import io.coredge.health.claims.exception.models.ErrorCode;

import java.io.Serial;

public class DuplicateRecordException extends DataAlreadyExistsException {

    @Serial
    private static final long serialVersionUID = 487703573561058809L;

    private static final ErrorCode CODE = ErrorCode.DUPLICATE_RECORD;

    public DuplicateRecordException(String message) {
        super(CODE, message);
    }
}
