package io.coredge.health.claims.exception.app;

public class NoLimitException extends RuntimeException{
    public NoLimitException(String message) {
        super(message);
    }
    public NoLimitException(String message, Throwable cause) {
        super(message, cause);
    }
}
