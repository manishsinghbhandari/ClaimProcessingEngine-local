package io.coredge.health.claims.exception.category;

import io.coredge.health.claims.exception.app.DataAlreadyExistsException;
import io.coredge.health.claims.exception.models.ErrorAttribute;
import io.coredge.health.claims.exception.models.ErrorCode;

import static io.coredge.health.claims.exception.models.ErrorCode.DUPLICATE_REJECTION_CATEGORY;

public class ReasonCategoryAlreadyExistsException extends DataAlreadyExistsException {

    private static final ErrorCode CODE = DUPLICATE_REJECTION_CATEGORY;

    private static final String CATEGORY = "category";
    private static final String CATEGORY_MSG = "Reason category already exists: ";

    public ReasonCategoryAlreadyExistsException(String category) {
        super(CODE, CATEGORY_MSG + category,
                ErrorAttribute.builder()
                        .key(CATEGORY)
                        .value(category)
                        .build());
    }
}
