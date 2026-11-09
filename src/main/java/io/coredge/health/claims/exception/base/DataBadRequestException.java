package io.coredge.health.claims.exception.base;

import io.coredge.health.claims.exception.models.ErrorAttribute;
import io.coredge.health.claims.exception.models.ErrorCode;
import lombok.Getter;

import java.io.Serial;

@Getter
public class DataBadRequestException extends ApiException {

    @Serial
    private static final long serialVersionUID = 4151569448693017560L;

    private ErrorAttribute attribute;

    public DataBadRequestException(ErrorCode code) {
        super(code);
    }

    public DataBadRequestException(ErrorCode code, ErrorAttribute attribute) {
        super(code);
        this.attribute = attribute;
    }

    public DataBadRequestException(ErrorCode code, ErrorAttribute attribute, Throwable cause) {
        super(code, cause);
        this.attribute = attribute;
    }

    public DataBadRequestException(ErrorCode code, String message) {
        super(code, message);
    }

    public DataBadRequestException(ErrorCode code, String message, ErrorAttribute attribute) {
        super(code, message);
        this.attribute = attribute;
    }

    public DataBadRequestException(ErrorCode code, String message, ErrorAttribute attribute, Throwable cause) {
        super(code, message, cause);
        this.attribute = attribute;
    }

    public DataBadRequestException(ErrorCode code, String message, Throwable cause) {
        super(code, message, cause);
    }

    public DataBadRequestException(ErrorCode code, Throwable cause) {
        super(code, cause);
    }
}
