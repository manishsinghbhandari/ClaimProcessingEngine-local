package io.coredge.health.claims.exception.app;

public class FacilityLevelNotFoundException extends RuntimeException {
    public FacilityLevelNotFoundException(String facilityId) {
        super("Facility Level not found for Facility ID: %s".formatted(facilityId));
    }
}