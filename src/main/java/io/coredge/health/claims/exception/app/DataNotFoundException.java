package io.coredge.health.claims.exception.app;

import static io.coredge.health.claims.exception.models.ErrorCode.CODE_DATA_NOT_FOUND;

import io.coredge.health.claims.exception.base.BusinessException;
import io.coredge.health.claims.exception.models.ErrorAttribute;
import io.coredge.health.claims.exception.models.ErrorCode;
import java.io.Serial;

public class DataNotFoundException extends BusinessException {

  @Serial private static final long serialVersionUID = 4274460749390100719L;

  private static final ErrorCode CODE = CODE_DATA_NOT_FOUND;

  private static final String DEFAULT_MSG = "No Data Found";

  public DataNotFoundException() {
    super(CODE, DEFAULT_MSG);
  }

  public DataNotFoundException(String message) {
    super(CODE, message);
  }

  public DataNotFoundException(Throwable cause) {
    super(CODE, DEFAULT_MSG, cause);
  }

  public DataNotFoundException(String message, Throwable cause) {
    super(CODE, message, cause);
  }

  public DataNotFoundException(String message, ErrorAttribute attribute) {
    super(CODE, message, attribute);
  }

  public DataNotFoundException(ErrorCode errorCode) {
      super(errorCode);
  }

  public DataNotFoundException(ErrorCode errorCode, String message) {
      super(errorCode, message);
  }

  public DataNotFoundException(ErrorCode errorCode, String message, Throwable cause) {
      super(errorCode, message, cause);
  }

  public DataNotFoundException(ErrorCode errorCode, Throwable cause) {
      super(errorCode, cause);
  }
}
