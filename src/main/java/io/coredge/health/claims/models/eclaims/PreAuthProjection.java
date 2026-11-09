package io.coredge.health.claims.models.eclaims;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public interface PreAuthProjection {
    UUID getClaimId();
    LocalDate getExpiryDate();
    BigDecimal getApprovedPrice();

}