package io.coredge.health.claims.models;

import io.coredge.health.claims.enums.LimitScope;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class InterventionUtilizationLimit {
    private String code;
    private Integer individualUtilizationCount;
    private Integer householdUtilizationCount;
    private BigDecimal utilizationLimit;
}
