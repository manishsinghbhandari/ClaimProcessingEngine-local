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
public class PhcBatchPaymentDto {
    private Long id;
    private String countyName;
    private String adminLevelCode;
    private Long population;
    private LocalDate date;
    private BigDecimal amount;
    private BigDecimal approvedAmount;
    private BigDecimal paidAmount;
    private String status;
    private String remarks;
    private LocalDateTime createdAt;
    private LocalDateTime approvedAt;
    private String approvedBy;
    private int numberOfFacilities;
    private int totalClaims;
    private String monthYear;
}
