package io.coredge.health.claims.models.phccalculation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PHCClaimsSummaryDTO {
    private Long claimNo;
    private String claimId;
    private String patientId;
    private String patientName;
    private String patientGender;
    private int patientAge;
    private String nationalId;
    private BigDecimal totalAmount;
    private BigDecimal claimWeight;
    private String icdCodes;
}
