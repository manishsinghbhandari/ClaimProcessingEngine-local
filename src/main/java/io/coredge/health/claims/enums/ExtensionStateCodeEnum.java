package io.coredge.health.claims.enums;


import lombok.Getter;

@Getter
public enum ExtensionStateCodeEnum {
    IN_REVIEW("In Review", "in-review"),
    PENDING("Pending", "pending"),
    APPROVED("Approved", "approved"),
    PARTIAL_APPROVAL("Partial Approval", "partial-approval"),
    REJECTED("Rejected", "rejected"),
    CLINICAL_REVIEW("Clinical Review", "clinical-review"),
    SUBMITTED("Submitted", "submitted"),
    QUEUED("Queued", "queued"),
    SENT_BACK("Returned Back", "sent-back"),
    UNDER_COMMITTEE_REVIEW("Under Committee Review", "under-committee-review"),
    CLARIFICATION_AFTER_PAYER_CHECKS("Clarification After Payer Checks", "clarification-after-payer-checks"),
    SENT_TO_SURVEILLANCE("Sent To Surveillance", "sent-to-surveillance"),
    UNKNOWN("Unknown", "unknown"),
    SENT_FOR_PAYMENT_PROCESSING("Sent for payment processing", "Sent-for-payment-processing"),
    PAYMENT_COMPLETED("Payment completed", "Payment-completed"),
    PAYMENT_PARTIAL("Payment partial", "Payment-partial"),
    PAYMENT_DECLINED("Payment declined", "Payment-declined"),
    MEDICAL_REVIEW("Medical Review", "medical-review"),
    CANCELED("Canceled", "canceled"),
    DECLINE("Decline", "decline"),
    PAID("Paid", "Payment-completed"),
    EXPIRED("Expired", "Expired"),
    CONSUMED("Consumed","Consumed"),
    UNDER_INVESTIGATION("Under Investigation", "under-investigation"),
    MISSING_DOCUMENTS("Missing Documents", "missing-documents"),
    RESUBMITTED_MISSING_DOCUMENTS("Resubmitted Missing Documents", "resubmitted-missing-documents"),
    MANUAL_REVIEW("Manual Review", "manual-review"),
    UNDER_SURVEILLANCE("Under Surveillance", "under-surveillance"),
    REPROCESS("Reprocess", "reprocess");

    private final String displayName;
    private final String stateCode;

    ExtensionStateCodeEnum(String displayName, String stateCode) {
        this.displayName = displayName;
        this.stateCode = stateCode;
    }

    public static ExtensionStateCodeEnum fromCode(String code) {
        if (code == null) {
            return UNKNOWN;  // Or handle null cases as you prefer
        }
        return switch (code.toLowerCase()) {
            case "in-review", "in_review" -> IN_REVIEW;
            case "pending" -> PENDING;
            case "approved" -> APPROVED;
            case "rejected" -> REJECTED;
            case "submitted" -> SUBMITTED;
            case "clinical-review" -> CLINICAL_REVIEW;
            case "queued" -> QUEUED;
            case "sent-back" -> SENT_BACK;
            case "under-committee-review" -> UNDER_COMMITTEE_REVIEW;
            case "clarification-after-payer-checks" -> CLARIFICATION_AFTER_PAYER_CHECKS;
            case "sent-to-surveillance" -> SENT_TO_SURVEILLANCE;
            case "medical-review" -> MEDICAL_REVIEW;
            case "canceled" -> CANCELED;
            case "decline" -> DECLINE;
            case "consumed" -> CONSUMED;
            case "expired" -> EXPIRED;
            case "under-investigation" -> UNDER_INVESTIGATION;
            case "missing-documents" -> MISSING_DOCUMENTS;
            case "resubmitted-missing-documents" -> RESUBMITTED_MISSING_DOCUMENTS;
            default -> UNKNOWN;
        };
    }
}
