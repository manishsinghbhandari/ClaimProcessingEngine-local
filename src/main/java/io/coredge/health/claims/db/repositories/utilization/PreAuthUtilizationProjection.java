package io.coredge.health.claims.db.repositories.utilization;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public interface PreAuthUtilizationProjection {

    UUID getPreAuthId();
    LocalDate getPreAuthServiceDate();
    BigDecimal getPreAuthAmount();
    String getPreAuthStatus();
    LocalDateTime getPreauthCreatedDate();


    UUID getClaimId();
    LocalDate getClaimServiceDate();
    BigDecimal getClaimAmount();
    String getClaimStatus();
    LocalDateTime getClaimCreatedDate();

    BigDecimal getShifAmount();
    BigDecimal getEccifAmount();
    BigDecimal getPmfAmount();
}

