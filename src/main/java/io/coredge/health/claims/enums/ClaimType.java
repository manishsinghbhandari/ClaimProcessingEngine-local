package io.coredge.health.claims.enums;

import lombok.Getter;

@Getter
public enum ClaimType {
    PREAUTHORIZATION("Preauthorization"),
    CLAIM("Claim"),
    PHC_CLAIM("Claim_Phc"),
    UNKNOWN("Unknown");

    private final String displayName;

    ClaimType(String displayName) {
        this.displayName = displayName;
    }

    public static ClaimType fromCode(String code) {
        if (code == null) {
            return UNKNOWN;  // Or handle null cases as you prefer
        }
        return switch (code.toLowerCase()) {
            case "preauthorization", "preauthorizations" -> PREAUTHORIZATION;
            case "claim" -> CLAIM;
            case "claim_phc" -> PHC_CLAIM;
            default -> UNKNOWN;
        };
    }

    public enum BucketStatus {
        PENDING, COMPLETED , RELEASED
    }
}
