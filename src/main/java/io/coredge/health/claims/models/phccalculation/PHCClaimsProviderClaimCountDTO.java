package io.coredge.health.claims.models.phccalculation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PHCClaimsProviderClaimCountDTO {
    String providerName;
    Long providerId;
    Long count;
    String ownership;
    BigDecimal totalAmount;
}
