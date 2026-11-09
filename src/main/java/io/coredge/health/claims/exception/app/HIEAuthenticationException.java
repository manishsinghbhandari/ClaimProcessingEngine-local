package io.coredge.health.claims.exception.app;

public class HIEAuthenticationException extends RuntimeException {
    public HIEAuthenticationException(String message) {
        super("HIE Authentication Error: " + message);
    }
}
