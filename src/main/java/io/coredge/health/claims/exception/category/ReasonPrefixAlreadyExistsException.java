package io.coredge.health.claims.exception.category;

import io.coredge.health.claims.exception.app.DataAlreadyExistsException;
import io.coredge.health.claims.exception.models.ErrorAttribute;
import io.coredge.health.claims.exception.models.ErrorCode;

import static io.coredge.health.claims.exception.models.ErrorCode.DUPLICATE_REASON_PREFIX;

public class ReasonPrefixAlreadyExistsException extends DataAlreadyExistsException {

    private static final ErrorCode CODE = DUPLICATE_REASON_PREFIX;

    private static final String PREFIX = "prefix";
    private static final String PREFIX_MSG = "Reason prefix already exists: ";

    public ReasonPrefixAlreadyExistsException(String prefix) {
        super(CODE, PREFIX_MSG + PREFIX,
                ErrorAttribute.builder()
                        .key(PREFIX)
                        .value(prefix)
                        .build());
    }
}
