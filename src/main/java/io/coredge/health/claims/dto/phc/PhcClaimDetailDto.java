package io.coredge.health.claims.dto.phc;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO for individual claim details in payment summary
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PhcClaimDetailDto {
    
    private Long claimNumber;
    private String claimId;
    private String externalId;
    private String patientName;
    private String patientGender;
    private Integer patientAge;
    private String nationalId;
    private LocalDate dateFrom;
    private LocalDate dateTo;
    private BigDecimal totalAmount;
    private BigDecimal approvedAmount;
    private String icdCodes;
    private String status;
}
