package io.coredge.health.claims.exception.category;

import io.coredge.health.claims.exception.app.DataAlreadyExistsException;
import io.coredge.health.claims.exception.models.ErrorAttribute;
import io.coredge.health.claims.exception.models.ErrorCode;

import static io.coredge.health.claims.exception.models.ErrorCode.REASON_DESC;


public class ReasonCodeAlreadyExistsException extends DataAlreadyExistsException {

    private static final ErrorCode CODE = REASON_DESC;
    private static final String KEY_CODE = "code";
    private static final String CODE_MSG = "Reason code already exists: ";

    public ReasonCodeAlreadyExistsException(String code) {
        super(CODE, CODE_MSG + code,
                ErrorAttribute.builder()
                        .key(KEY_CODE)
                        .value(code)
                        .build()
        );
    }
}
