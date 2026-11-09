package io.coredge.health.claims.db.entity;

import io.coredge.health.claims.enums.Status;
import jakarta.persistence.*;
import lombok.*;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@EqualsAndHashCode(callSuper = true)
@Data
@Builder
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "system_utilization_charge")
@Slf4j
public class SystemUtilizationCharge extends AuditableBase<String> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "rate", nullable = false)
    private BigDecimal rate;

    @Column(name = "maximum_value")
    private BigDecimal maximumValue;

    @Column(name = "effective_date", nullable = false)
    private LocalDateTime effectiveDate;

    @Basic
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status;
}
