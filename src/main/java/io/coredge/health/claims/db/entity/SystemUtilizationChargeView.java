package io.coredge.health.claims.db.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface SystemUtilizationChargeView {
    BigDecimal getRate();
    BigDecimal getMaximumValue();
    LocalDateTime getEffectiveDate();
}
