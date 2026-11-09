package io.coredge.health.claims.exception.category;

import io.coredge.health.claims.exception.app.DataAlreadyExistsException;
import io.coredge.health.claims.exception.models.ErrorAttribute;
import io.coredge.health.claims.exception.models.ErrorCode;

import static io.coredge.health.claims.exception.models.ErrorCode.REASON_DESC;
public class ReasonDescriptionAlreadyExistsException extends DataAlreadyExistsException {

    private static final ErrorCode CODE = REASON_DESC;


    private static final String DESC = "description";
    private static final String DESC_MSG = "Reason description already exists: ";

    public ReasonDescriptionAlreadyExistsException(String description) {
        super(CODE, DESC_MSG + description,
                ErrorAttribute.builder()
                        .key(DESC)
                        .value(description)
                        .build());
    }
}
