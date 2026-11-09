package io.coredge.health.claims.exception.app;

public class FacilityLevelMismatchException extends RuntimeException {
    public FacilityLevelMismatchException(String facilityId, String levelHie, String levelProvider) {
        super("Mismatch in Facility Level: HIE has '%s', but Provider has '%s'. [Facility ID: %s]"
                .formatted(levelHie, levelProvider, facilityId));
    }
}