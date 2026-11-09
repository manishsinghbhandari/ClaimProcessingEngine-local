package io.coredge.health.claims.exception.app;

public class PatientNotFoundException extends RuntimeException {
    public PatientNotFoundException(String identifier) {
        super("Beneficiary Details not found in HIE for identifier: " + identifier);
    }
}
