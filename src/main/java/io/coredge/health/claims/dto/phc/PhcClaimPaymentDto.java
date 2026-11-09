package io.coredge.health.claims.dto.phc;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PhcClaimPaymentDto {
    private Long id;
    private PhcProviderDto provider;
    private String aggregatedClaimId;
    private BigDecimal amount;
    private BigDecimal approvedAmount;
    private BigDecimal paidAmount;
    private BigDecimal numberOfClaims;
    private LocalDate batchPreparationDate;
    private LocalDate paymentDate;
    private String status;
    private String remarks;
    private LocalDateTime createdAt;
    private LocalDateTime approvedAt;
    private String approvedBy;
    private Long batchPaymentId;
    private String monthYear;
}
