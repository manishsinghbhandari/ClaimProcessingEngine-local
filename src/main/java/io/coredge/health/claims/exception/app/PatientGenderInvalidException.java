package io.coredge.health.claims.exception.app;

public class PatientGenderInvalidException extends RuntimeException {
    public PatientGenderInvalidException(String gender, String patientId) {
        super("Invalid Patient Gender %s for Patient ID: %s".formatted(gender, patientId));
    }
}