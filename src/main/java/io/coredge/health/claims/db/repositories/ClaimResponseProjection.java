package io.coredge.health.claims.db.repositories;

import lombok.Getter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
public class ClaimResponseProjection {

    private final String code;
    private final BigDecimal netValue;
    private final UUID claimId;

    public ClaimResponseProjection(String code, BigDecimal netValue,UUID claimId) {
        this.code = code;
        this.netValue = netValue;
        this.claimId = claimId;
    }
}
