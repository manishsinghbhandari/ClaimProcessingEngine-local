package io.coredge.health.claims.exception.base;

import io.coredge.health.claims.exception.models.ErrorCode;
import lombok.Getter;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.io.Serial;

/**
 * @author rajesh<>rajeshk@coredge.io</>
 */
public class ApiException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = -8262500809779681557L;

    private static final String API_EXP_FORMAT = "%s: Code: [%s]-[%s], Message: %s ";

    @Getter
    private final ErrorCode code;
    private boolean enableTrace = false;

    public ApiException(ErrorCode code) {
        this.code = code;
    }

    public ApiException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public ApiException(ErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.enableTrace = true;
    }

    public ApiException(ErrorCode code, Throwable cause) {
        super(cause);
        this.code = code;
        this.enableTrace = true;
    }

    @Override
    public String toString() {
        String trace = String.format(API_EXP_FORMAT, this.getClass().getSimpleName(), getCode().name(), getCode().code(), getMessage());
        if (enableTrace) {
            trace = trace.concat(", Trace : ").concat(ExceptionUtils.getStackTrace(getCause()));
        }
        return trace;
    }
}
