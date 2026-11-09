package io.coredge.health.claims.enums;

import lombok.Getter;

@Getter
public enum Decision {
    SENT_BACK("Sent Back" , 0 , false , false),
    MANUAL_REVIEW("Manual Review" , 1 , true , true),
    APPROVE("Approve" , 2 , true , false),
    CLINICAL_REVIEW("Clinical Review", 3, true, false);

    private final String displayName;
    private final int priority;
    private final boolean approved;
    private final boolean requiresDoctorReview;

    Decision(String displayName, int priority, boolean approved, boolean requiresDoctorReview) {
        this.displayName = displayName;
        this.priority = priority;
        this.approved = approved;
        this.requiresDoctorReview = requiresDoctorReview;
    }

    public static Decision fromDisplayName(String text) {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("Decision cannot be null or empty");
        }

        for (Decision d : Decision.values()) {
            // Check 1: Match against "Clinical Review" (Display Name)
            // Check 2: Match against "CLINICAL_REVIEW" (Enum Constant Name)
            if (d.displayName.equalsIgnoreCase(text) || d.name().equalsIgnoreCase(text)) {
                return d;
            }
        }
        throw new IllegalArgumentException("Unknown decision: " + text);
    }
}
