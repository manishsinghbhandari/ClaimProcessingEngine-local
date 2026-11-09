package io.coredge.health.claims.enums;

public enum ClaimOutComesStatus {
    QUEUED("queued", "Queued", "The Claim/Pre-authorization/Pre-determination has been received but processing has not begun."),
    COMPLETE("complete", "Processing Complete", "The processing has completed without errors"),
    ERROR("error", "Error", "One or more errors have been detected in the Claim"),
    PARTIAL("partial", "Partial Processing", "No errors have been detected in the Claim and some of the adjudication has been performed.");

    private final String code;
    private final String displayName;
    private final String description;

    ClaimOutComesStatus(String code, String displayName, String description) {
        this.code = code;
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public String getCode() {
        return code;
    }
}