package io.coredge.health.claims.models;

import io.coredge.health.claims.enums.FundType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ClaimPaymentResponse{
    private FundType fundType;
    private BigDecimal claimedAmount;
    private BigDecimal facilityPayableAmount;
    private BigDecimal systemUtilizationAmount;
}
