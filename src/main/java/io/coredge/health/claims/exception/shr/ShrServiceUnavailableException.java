package io.coredge.health.claims.exception.shr;

public class ShrServiceUnavailableException extends RuntimeException {
    public ShrServiceUnavailableException(String message) {
        super(message);
    }
}