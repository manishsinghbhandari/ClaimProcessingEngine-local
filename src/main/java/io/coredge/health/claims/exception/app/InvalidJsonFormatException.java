package io.coredge.health.claims.exception.app;

public class InvalidJsonFormatException extends RuntimeException {
    public InvalidJsonFormatException(String message) {
        super(message);
    }

    public InvalidJsonFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
