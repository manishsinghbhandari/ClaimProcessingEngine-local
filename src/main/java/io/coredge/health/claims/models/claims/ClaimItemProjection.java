package io.coredge.health.claims.models.claims;


import lombok.Getter;
import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
public class ClaimItemProjection {
        private String code;
        private BigDecimal netValue;
        private LocalDate serviceDate;
        private LocalDate servicedPeriodStart;
        private LocalDate servicedPeriodEnd;
public ClaimItemProjection(String code , BigDecimal value,LocalDate serviceDate, LocalDate servicedPeriodStart, LocalDate servicedPeriodEnd) {
    this.code = code;
    this.netValue = value;
    this.serviceDate = serviceDate;
    this.servicedPeriodStart = servicedPeriodStart;
    this.servicedPeriodEnd = servicedPeriodEnd;
    }
}