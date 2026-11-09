package io.coredge.health.claims.exception.app;

public class FacilityCountyInvalidException extends RuntimeException {
    public FacilityCountyInvalidException(String county, String facilityId) {
        super("Invalid Facility County %s for Facility ID: %s".formatted(county, facilityId));
    }
}