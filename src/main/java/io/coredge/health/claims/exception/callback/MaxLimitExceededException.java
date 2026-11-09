package io.coredge.health.claims.exception.callback;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class MaxLimitExceededException extends RuntimeException {
    public MaxLimitExceededException(String message) {
        super(message);
    }
}
