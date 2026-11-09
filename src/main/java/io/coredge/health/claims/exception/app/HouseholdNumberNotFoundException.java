package io.coredge.health.claims.exception.app;

public class HouseholdNumberNotFoundException extends RuntimeException {
    public HouseholdNumberNotFoundException(String msg) {
        super(msg);
    }
}
