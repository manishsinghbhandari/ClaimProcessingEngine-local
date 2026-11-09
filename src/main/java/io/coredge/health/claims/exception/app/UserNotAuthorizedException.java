package io.coredge.health.claims.exception.app;


import io.coredge.health.claims.exception.base.AuthenticationException;
import io.coredge.health.claims.exception.models.ErrorCode;

import java.io.Serial;

public class UserNotAuthorizedException extends AuthenticationException {

	@Serial
	private static final long serialVersionUID = 1458621256250882707L;

	private static final ErrorCode CODE = ErrorCode.RESOURCE_NOT_AUTHORIZED;

	private static final String DEFAULT_MSG = "You're not authorized to perform this operation";

	public UserNotAuthorizedException() {
		super(CODE, DEFAULT_MSG);
	}

	public UserNotAuthorizedException(String message) {
		super(CODE, message);
	}

}
