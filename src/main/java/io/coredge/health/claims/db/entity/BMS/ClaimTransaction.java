package io.coredge.health.claims.db.entity.BMS;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import io.coredge.health.claims.db.entity.BMS.common.AuditableEntity;
import io.coredge.health.claims.db.entity.InsuranceClaim;

@Entity
@Table(
        name = "claim_transactions",
        schema = "public",
        indexes = {
            @Index(name = "idx_claimtransaction_claim_id", columnList = "claim_id"),
            @Index(name = "idx_claimtransaction_claim_status_id", columnList = "claim_status_id"),
            @Index(name = "idx_claimtransaction_benefit_id", columnList = "benefit_id"),
            @Index(name = "idx_claimtransaction_status_id", columnList = "status_id"),
            @Index(name = "idx_claimtransaction_created_by", columnList = "created_by"),
            @Index(name = "idx_claimtransaction_updated_by", columnList = "updated_by")
        }
)
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ClaimTransaction extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "claim_id", referencedColumnName = "claim_id", nullable = false)
    @NotNull
    private InsuranceClaim claim;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "claim_status_id", referencedColumnName = "id", nullable = false)
    @NotNull
    private ClaimStatus claimStatus;

    @Column(name = "operation")
    private String operation;

    @Column(name = "amount")
    private Double amount;

    @Column(name = "limit_amount")
    private Double limitAmount;

    @Column(name = "limit_utilization")
    private Double limitUtilization;

    @Column(name = "limit_over_utilization")
    private Double limitOverUtilization;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "benefit_id", referencedColumnName = "id")
    private Benefit benefit;

}
