package io.coredge.health.claims.db.repositories.utilization;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public interface ClaimUtilizationProjection {

    UUID getClaimId();
    LocalDate getClaimServiceDate();
    BigDecimal getClaimAmount();
    String getClaimStatus();
    LocalDateTime getClaimCreatedDate();
    UUID getPreAuthId();

    BigDecimal getShifAmount();
    BigDecimal getEccifAmount();
    BigDecimal getPmfAmount();
}
