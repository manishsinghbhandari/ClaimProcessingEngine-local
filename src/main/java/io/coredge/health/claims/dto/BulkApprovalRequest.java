package io.coredge.health.claims.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Request DTO for bulk approval of claims.
 * Used by eClaimPortal to submit bulk approval requests.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BulkApprovalRequest {

    /**
     * SHA code to filter claims (e.g., "SHA-16-001").
     * All claims with intervention codes starting with this code will be selected.
     */
    @NotBlank(message = "SHA code is required")
    private String shaCode;

    /**
     * Optional status filter for claims (e.g., "APPROVED").
     * If provided, only claims with this status will be processed.
     * If not provided, claims with any of the 3 default statuses will be processed.
     */
    private String status;

    /**
     * If true, approve all matching claims.
     * If false, only validate and return count without approving.
     */
    private boolean approveAll;

    /**
     * Start date for filtering claims (inclusive).
     * Claims with date_from >= startDate will be included.
     * Accepts datetime format "yyyy-MM-dd HH:mm:ss" but uses LocalDate internally.
     */
    @NotNull(message = "Start date is required")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDate startDate;

    /**
     * End date for filtering claims (inclusive).
     * Claims with date_from <= endDate will be included.
     * Accepts datetime format "yyyy-MM-dd HH:mm:ss" but uses LocalDate internally.
     */
    @NotNull(message = "End date is required")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDate endDate;
}

