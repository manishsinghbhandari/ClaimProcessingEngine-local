package io.coredge.health.claims.exception.app;


import io.coredge.health.claims.exception.base.BusinessException;
import io.coredge.health.claims.exception.models.ErrorCode;

import java.io.Serial;

public class ActionNotFoundException extends BusinessException {

	@Serial
	private static final long serialVersionUID = 487703573561058809L;

	private static final ErrorCode CODE = ErrorCode.RESOURCE_NOT_FOUND;

	public ActionNotFoundException(String message) {
		super(CODE, message);
	}

}
