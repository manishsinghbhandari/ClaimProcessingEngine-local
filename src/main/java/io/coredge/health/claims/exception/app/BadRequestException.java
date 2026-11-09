package io.coredge.health.claims.exception.app;

import static io.coredge.health.claims.exception.models.ErrorCode.BAD_REQUEST;

import io.coredge.health.claims.exception.base.DataBadRequestException;
import io.coredge.health.claims.exception.models.ErrorAttribute;
import io.coredge.health.claims.exception.models.ErrorCode;
import java.io.Serial;

public class BadRequestException extends DataBadRequestException {

  @Serial private static final long serialVersionUID = -6923402066434299227L;

  private static final ErrorCode CODE = BAD_REQUEST;

  private static final String DEFAULT_MSG = "Bad Request";

  public BadRequestException() {
    super(CODE, DEFAULT_MSG);
  }

  public BadRequestException(String message) {
    super(CODE, message);
  }

  public BadRequestException(Throwable cause) {
    super(CODE, DEFAULT_MSG, cause);
  }

  public BadRequestException(String message, Throwable cause) {
    super(CODE, message, cause);
  }

  public BadRequestException(ErrorAttribute attribute) {
    super(CODE, DEFAULT_MSG, attribute);
  }

  public BadRequestException(String message, ErrorAttribute attribute) {
    super(CODE, message, attribute);
  }
}
