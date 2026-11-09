package io.coredge.health.claims.exception.app;

public class FacilityCountyNotFoundException extends RuntimeException {
    public FacilityCountyNotFoundException(String facilityId) {
        super("Facility County not found for Facility ID: %s".formatted(facilityId));
    }
}