package io.coredge.health.claims.exception.app;

import java.util.Date;

public class PatientBirthDateMismatchException extends RuntimeException {
    public PatientBirthDateMismatchException(String patientId, String hieBirthDate, String providerBirthDate) {
        super("Mismatch in Patient BirthDate: HIE has '%s', but Provider has '%s'. [Patient ID: %s]"
                .formatted(hieBirthDate, providerBirthDate, patientId));
    }
}