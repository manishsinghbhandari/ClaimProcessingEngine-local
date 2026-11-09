package io.coredge.health.claims.exception.app;

public class FacilityOwnerShipNotFoundException extends RuntimeException {
    public FacilityOwnerShipNotFoundException(String facilityId) {
        super("Facility OwnerShip not found for Facility ID: %s".formatted(facilityId));
    }
}