package io.coredge.health.claims.db.entity.utilization;

import io.coredge.health.claims.enums.FundType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@Table(name = "fund_utilization_limit")
@Entity
public class FundUtilizationLimit {

    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "fund_type", nullable = false, length = 50)
    private FundType fundType;

    @Column(name = "utilised_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal utilisedAmount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "utilization", nullable = false)
    private PatientUtilization utilization;
}
