package io.coredge.health.claims.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PaymentDetailResponse {
  private BigDecimal claimedTotal;
  private BigDecimal approvedTotal;
  private BigDecimal systemUtilizationAmount;
  private BigDecimal claimPayableAmount;
  private List<ClaimPaymentResponse> claimPaymentResponses;
}
