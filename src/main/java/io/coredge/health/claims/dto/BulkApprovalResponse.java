package io.coredge.health.claims.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Response DTO for bulk approval of claims.
 * Returns detailed results of the bulk approval operation.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BulkApprovalResponse {

    /**
     * Total number of claims that were eligible for approval.
     */
    private int totalEligible;

    /**
     * Total number of claims successfully approved.
     */
    private int totalApproved;

    /**
     * Total number of claims that failed approval.
     */
    private int totalFailed;

    /**
     * List of approved claim details.
     */
    @Builder.Default
    private List<ApprovedClaimInfo> approvedClaims = new ArrayList<>();

    /**
     * List of failed claim details with reasons.
     */
    @Builder.Default
    private List<FailedClaimInfo> failedClaims = new ArrayList<>();

    /**
     * Unique processing ID for tracking this bulk operation.
     */
    private String processingId;

    /**
     * Batch ID linking to the batch_approval_request table.
     */
    private Long batchId;

    /**
     * Information about an approved claim.
     */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ApprovedClaimInfo {
        private String claimId;
        private String interventionCode;
        private String previousStatus;
    }

    /**
     * Information about a failed claim approval.
     */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class FailedClaimInfo {
        private String claimId;
        private String interventionCode;
        private String reason;
    }
}
