package io.coredge.health.claims.exception.intervention;

public class InvalidFacilityLevelException extends RuntimeException {

    public InvalidFacilityLevelException(String facilityLevel, String interventionCode) {
        super(String.format("Facility level [%s] is not allowed for minor surgery [%s]", facilityLevel, interventionCode));
    }
}