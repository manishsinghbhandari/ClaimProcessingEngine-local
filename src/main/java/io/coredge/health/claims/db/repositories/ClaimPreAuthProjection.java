package io.coredge.health.claims.db.repositories;

import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Getter
public class ClaimPreAuthProjection {

    private String code;
    private BigDecimal netValue;
    private UUID claimId;
    private LocalDate serviceDate;
    private LocalDate servicedPeriodStart;
    private LocalDate getServicedPeriodEnd;

    public ClaimPreAuthProjection(String code, BigDecimal netValue, UUID claimId,LocalDate serviceDate, LocalDate servicedPeriodStart, LocalDate getServicedPeriodEnd) {
        this.code = code;
        this.netValue = netValue;
        this.claimId = claimId;
        this.serviceDate = serviceDate;
        this.servicedPeriodStart = servicedPeriodStart;
        this.getServicedPeriodEnd = getServicedPeriodEnd;
    }
}
