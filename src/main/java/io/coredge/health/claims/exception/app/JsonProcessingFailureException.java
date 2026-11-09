package io.coredge.health.claims.exception.app;

public class JsonProcessingFailureException extends RuntimeException {
    public JsonProcessingFailureException(String message, Throwable cause) {
        super(message, cause);
    }
}
