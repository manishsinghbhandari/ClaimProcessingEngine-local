package io.coredge.health.claims.exception.app;

import io.coredge.health.claims.exception.base.BusinessException;
import io.coredge.health.claims.exception.models.ErrorCode;

import java.io.Serial;

public class PatientGenderMismatchException extends BusinessException {

    private static final ErrorCode CODE = ErrorCode.PATIENT_GENDER_MISMATCH;

    private static final String DEFAULT_MSG = "Mismatch in patient gender information.";

    private static final String GENDER_MISMATCH_MSG =
            "Mismatch in Patient Gender: HIE has '%s', but Provider has '%s'. [Patient ID: %s]";

    @Serial
    private static final long serialVersionUID = 487703573561058809L;

    public PatientGenderMismatchException() {

        super(CODE, DEFAULT_MSG);
    }

    public PatientGenderMismatchException(String message) {

        super(CODE, message);
    }

    public PatientGenderMismatchException(String patientId, String hieGender, String providerGender) {
        super(CODE,GENDER_MISMATCH_MSG.formatted(hieGender, providerGender, patientId));
    }

    public PatientGenderMismatchException(String message, Throwable cause) {

        super(CODE, message, cause);
    }

    public PatientGenderMismatchException(Throwable cause) {

        super(CODE, DEFAULT_MSG, cause);
    }

    @Override
    public synchronized Throwable fillInStackTrace() {

        return this;
    }
}
