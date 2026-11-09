package io.coredge.health.claims.exception.app;

import static io.coredge.health.claims.exception.models.ErrorCode.INVALID_DATA;

import io.coredge.health.claims.exception.base.BusinessException;
import io.coredge.health.claims.exception.models.ErrorAttribute;
import io.coredge.health.claims.exception.models.ErrorCode;
import java.io.Serial;
import java.util.List;

public class DataValidationException extends BusinessException {
  @Serial private static final long serialVersionUID = 427449390100719L;

  private static final ErrorCode CODE = INVALID_DATA;

  private static final String DEFAULT_MSG = " Invalid data ";

  public DataValidationException() {
    super(CODE, DEFAULT_MSG);
  }

  public DataValidationException(String message) {
    super(CODE, message);
  }

  public DataValidationException(ErrorCode error, String message) {
    super(error, message);
  }

  public DataValidationException(Throwable cause) {
    super(CODE, DEFAULT_MSG, cause);
  }

  public DataValidationException(String message, Throwable cause) {
    super(CODE, message, cause);
  }

  public DataValidationException(String message, ErrorAttribute attribute) {
    super(CODE, message, attribute);
  }

  // New constructor to accept invalid codes list
  public DataValidationException(List<String> invalidCodes) {
    super(CODE, DEFAULT_MSG + ": " + String.join(", ", invalidCodes));
  }
}
