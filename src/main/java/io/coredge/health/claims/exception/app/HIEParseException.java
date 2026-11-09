package io.coredge.health.claims.exception.app;

public class HIEParseException extends RuntimeException {
    public HIEParseException(String identifier, String rawJson, Throwable cause) {
        super("Failed to parse HIE response for identifier: " + identifier + ".\nRaw JSON: " + rawJson, cause);
    }
}
