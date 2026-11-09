package io.coredge.health.claims.enums;

import lombok.Getter;

@Getter
public enum ErpErrorCode {
    // Retryable errors
    ERP_SUBMISSION_FAILED("ERP_SUBMISSION_FAILED", "ERP submission failed due to network or temporary issues", true),
    DB_UPDATE_FAILED("DB_UPDATE_FAILED", "Database update failed after successful ERP submission", true),

    // Non-retryable errors
    ERP_VALIDATION_ERROR("ERP_VALIDATION_ERROR", "ERP validation error - invalid request data", false),
    ERP_AUTHENTICATION_ERROR("ERP_AUTHENTICATION_ERROR", "ERP authentication failed", false),
    UNEXPECTED_RESPONSE("UNEXPECTED_RESPONSE", "Unexpected ERP response status code", false),
    CLAIM_ALREADY_EXISTS("CLAIM_ALREADY_EXISTS", "Claim already exists in ERP", false);

    private final String code;
    private final String description;
    private final boolean retryable;

    ErpErrorCode(String code, String description, boolean retryable) {
        this.code = code;
        this.description = description;
        this.retryable = retryable;
    }
}