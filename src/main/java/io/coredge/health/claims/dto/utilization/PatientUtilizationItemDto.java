package io.coredge.health.claims.dto.utilization;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PatientUtilizationItemDto {
    private UUID preAuthId;
    private LocalDate preAuthServiceDate;
    private BigDecimal preAuthAmount;
    private String preAuthStatus;
    private LocalDateTime preAuthcreatedDate;

    private UUID claimId;
    private LocalDate claimServiceDate;
    private BigDecimal claimAmount;
    private String claimStatus;
    private LocalDateTime claimCreatedDate;

    private BigDecimal shifAmount;
    private BigDecimal eccifAmount;
    private BigDecimal pmfAmount;

    private BigDecimal utilizedAmount;

}
