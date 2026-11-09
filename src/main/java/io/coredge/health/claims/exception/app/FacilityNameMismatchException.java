package io.coredge.health.claims.exception.app;

public class FacilityNameMismatchException extends RuntimeException {
    public FacilityNameMismatchException(String facilityId, String nameHie, String nameProvider) {
        super("Mismatch in Facility Name: HIE has '%s', but Provider has '%s'. [Facility ID: %s]"
                .formatted(nameHie, nameProvider, facilityId));
    }
}