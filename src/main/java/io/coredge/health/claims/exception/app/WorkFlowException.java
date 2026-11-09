package io.coredge.health.claims.exception.app;

import io.coredge.health.claims.exception.base.BusinessException;
import io.coredge.health.claims.exception.models.ErrorAttribute;
import io.coredge.health.claims.exception.models.ErrorCode;

import java.io.Serial;
import java.util.List;

import static io.coredge.health.claims.exception.models.ErrorCode.INVALID_DATA;
import static io.coredge.health.claims.exception.models.ErrorCode.INVALID_WORK_FLOW;

public class WorkFlowException extends BusinessException {
  @Serial private static final long serialVersionUID = 427449390100719L;

  private static final ErrorCode CODE = INVALID_WORK_FLOW;

  private static final String DEFAULT_MSG = " Invalid data ";

  public WorkFlowException() {
    super(CODE, DEFAULT_MSG);
  }

  public WorkFlowException(String message) {
    super(CODE, message);
  }

  public WorkFlowException(ErrorCode error, String message) {
    super(error, message);
  }

  public WorkFlowException(Throwable cause) {
    super(CODE, DEFAULT_MSG, cause);
  }

  public WorkFlowException(String message, Throwable cause) {
    super(CODE, message, cause);
  }

  public WorkFlowException(String message, ErrorAttribute attribute) {
    super(CODE, message, attribute);
  }

  // New constructor to accept invalid codes list
  public WorkFlowException(List<String> invalidCodes) {
    super(CODE, DEFAULT_MSG + ": " + String.join(", ", invalidCodes));
  }
}
