package io.coredge.health.claims.exception.app;

import io.coredge.health.claims.exception.base.BusinessException;
import io.coredge.health.claims.exception.models.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.io.Serial;

@ResponseStatus(HttpStatus.ACCEPTED)
public class DataProcessingException extends BusinessException {

    @Serial private static final long serialVersionUID = 1L;

    static final ErrorCode CODE = ErrorCode.DATA_PROCESSING;

    public DataProcessingException() {
        super(CODE, "Data processing");
    }

    public DataProcessingException(String message) {
        super(CODE, message);
    }

}
