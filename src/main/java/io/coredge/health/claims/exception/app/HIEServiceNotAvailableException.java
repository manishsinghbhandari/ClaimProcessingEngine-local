package io.coredge.health.claims.exception.app;

public class HIEServiceNotAvailableException extends RuntimeException {
    public HIEServiceNotAvailableException(String uri, Throwable cause) {
        super("SAFARICOM HIE is unavailable. Failed to reach URI: " + uri, cause);
    }
}