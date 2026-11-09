package io.coredge.health.claims.exception.app;

import io.coredge.health.claims.exception.models.ErrorCode;

import java.io.Serial;


public class UserNotFoundException extends DataNotFoundException {

	private static final ErrorCode CODE = ErrorCode.USER_NOT_FOUND;

	private static final String DEFAULT_MSG = "User does not exist.";
	@Serial
	private static final long serialVersionUID = 487703573561058809L;

	public UserNotFoundException() {
		super(CODE, DEFAULT_MSG);
	}

	public UserNotFoundException(String message) {
		super(CODE, message);
	}

	public UserNotFoundException(String message, Throwable cause) {
		super(CODE, message, cause);
	}

	public UserNotFoundException(Throwable cause) {
		super(CODE, DEFAULT_MSG, cause);
	}

	@Override
	public synchronized Throwable fillInStackTrace() {
		return this;
	}
}
