package io.coredge.health.claims.exception.app;

/**
 * Exception for rate limit errors (HTTP 429 Too Many Requests).
 */
public class RateLimitException extends RuntimeException {
    public RateLimitException(String message, Throwable cause) {
        super(message, cause);
    }

    public RateLimitException(String message) {
        super(message);
    }
}