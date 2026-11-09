package io.coredge.health.claims.dto.eligibility;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Simplified DTO containing only coverage period information from Finsprint eligibility check
 * Updated for API v5 with multiple schemes support
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CoveragePeriodDto {
    
    private LocalDate policyStartDate;
    private LocalDate policyEndDate;
    private LocalDate coverageStartDate;
    private LocalDate coverageEndDate;
    private String schemeName;
    private String employer;
    private String jobGroup;
}
