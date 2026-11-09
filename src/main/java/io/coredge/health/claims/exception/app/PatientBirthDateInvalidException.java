package io.coredge.health.claims.exception.app;

import java.util.Date;

public class PatientBirthDateInvalidException extends RuntimeException {
    public PatientBirthDateInvalidException(Date birthDate, String patientId) {
        super("Invalid Patient BirthDate %s for Patient ID: %s".formatted(birthDate, patientId));
    }
}