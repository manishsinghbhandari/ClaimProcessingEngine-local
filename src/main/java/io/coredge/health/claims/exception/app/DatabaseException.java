package io.coredge.health.claims.exception.app;

import static io.coredge.health.claims.exception.models.ErrorCode.DATABASE_EXCEPTION;

import io.coredge.health.claims.exception.base.SystemException;
import io.coredge.health.claims.exception.models.ErrorCode;
import java.io.Serial;

public class DatabaseException extends SystemException {

  @Serial private static final long serialVersionUID = -4437131025229714979L;
  private static final ErrorCode CODE = DATABASE_EXCEPTION;
  private static final String DEFAULT_MESSAGE =
      "Exception occurred while calling this database exception.";

  public DatabaseException() {
    super(CODE, DEFAULT_MESSAGE);
  }

  public DatabaseException(String message) {
    super(CODE, message);
  }

  public DatabaseException(Throwable cause) {
    super(CODE, DEFAULT_MESSAGE, cause);
  }

  public DatabaseException(String message, Throwable cause) {
    super(CODE, message, cause);
  }
}
