package io.coredge.health.claims.exception.app;

public class ProviderNotFoundException extends RuntimeException {
    public ProviderNotFoundException(String identifier) {
        super("Provider not found in SAFARICOM HIE for identifier: " + identifier);
    }
}
