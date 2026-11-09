package io.coredge.health.claims.db.entity;

import io.coredge.health.claims.enums.FundType;
import jakarta.persistence.*;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.annotations.Cascade;

import java.math.BigDecimal;

@EqualsAndHashCode(callSuper = true)
@Data
@Builder
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "claim_payment")
@Slf4j
public class ClaimPayment extends AuditableBase<String>  {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "claim_id", nullable = false, referencedColumnName = "claim_id")
    private InsuranceClaim claim;

    @Basic
    @Enumerated(EnumType.STRING)
    @Column(name = "fund_type", nullable = false)
    private FundType fundType;

    @Column(name = "claimed_amount", nullable = false)
    private BigDecimal claimedAmount;

    @Column(name = "facility_payable_amount", nullable = false)
    private BigDecimal facilityPayableAmount;

    @Column(name = "system_utilization_amount", nullable = false)
    private BigDecimal systemUtilizationAmount;
}
