package io.coredge.health.claims.dto.phc;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PhcPaymentSummaryDto {
    private int totalCounties;
    private int totalFacilities;
    private BigDecimal totalAmount;
    private BigDecimal totalApprovedAmount;
    private BigDecimal totalPaidAmount;
    private int pendingBatchPayments;
    private int approvedBatchPayments;
    private int rejectedBatchPayments;
    private int completedBatchPayments;
    private int pendingFacilityPayments;
    private int approvedFacilityPayments;
    private int rejectedFacilityPayments;
    private int completedFacilityPayments;
}
