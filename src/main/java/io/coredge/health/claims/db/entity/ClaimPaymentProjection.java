package io.coredge.health.claims.db.entity;

import io.coredge.health.claims.enums.FundType;
import java.math.BigDecimal;

public interface ClaimPaymentProjection {
    BigDecimal getClaimedTotal();
    BigDecimal getApprovedTotal();
    BigDecimal getTotalPayableAmount();
    BigDecimal getTotalUtilizationAmount();
    FundType getFundType();
    BigDecimal getClaimedAmount();
    BigDecimal getFacilityPayableAmount();
    BigDecimal getUtilizationAmount();
}