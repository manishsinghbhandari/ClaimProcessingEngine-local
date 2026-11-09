package io.coredge.health.claims.models.eclaims;

import java.math.BigDecimal;
import java.util.UUID;

public interface ClaimProjection {
    UUID getClaimId();
    BigDecimal getApprovedPrice();
    UUID getPreAuthId();
}
