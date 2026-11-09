package io.coredge.health.claims.exception.app;

import io.coredge.health.claims.exception.base.BusinessException;
import io.coredge.health.claims.exception.models.ErrorAttribute;
import io.coredge.health.claims.exception.models.ErrorCode;

import java.io.Serial;

public class DataAlreadyExistsException extends BusinessException {

    @Serial
    private static final long serialVersionUID = 1L;

    public DataAlreadyExistsException(ErrorCode errorCode) {
        super(errorCode);
    }

    public DataAlreadyExistsException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public DataAlreadyExistsException(ErrorCode errorCode, String message, ErrorAttribute attribute) {
        super(errorCode, message, attribute);
    }
}
