package io.coredge.health.claims.db.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "phc_claim_payments", schema = "public",
    uniqueConstraints = {
        @UniqueConstraint(name = "phc_claim_payments_aggregated_claim_id_key", columnNames = "aggregated_claim_id"),
        @UniqueConstraint(name = "phc_claim_payments_bank_reference_number_key", columnNames = "bank_reference_number")
    }
)
public class PhcClaimPayment extends AuditableBase<String> {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "aggregated_claim_id", length = 100, nullable = false)
    private String aggregatedClaimId;

    @Column(name = "bank_reference_number", length = 100)
    private String bankReferenceNumber;

    @Column(name = "provider_id", nullable = false)
    private Long providerId;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Column(name = "number_of_claims", nullable = false)
    private Double numberOfClaims;

    @Column(name = "batch_preparation_date", nullable = false)
    private LocalDate batchPreparationDate;

    @Column(name = "payment_date")
    private LocalDate paymentDate;

    @Column(name = "status", length = 30, nullable = false)
    private String status;

    @Column(name = "remarks")
    private String remarks;


    @ManyToOne
    @JoinColumn(name = "phc_batch_payment_id", referencedColumnName = "id", foreignKey = @ForeignKey(name = "phc_claim_payments_batch_fk"))
    private PhcBatchPayment phcBatchPayment;

    @Column(name = "approved_amount", precision = 38, scale = 2)
    private BigDecimal approvedAmount;

    @Column(name = "paid_amount", precision = 38, scale = 2)
    private BigDecimal paidAmount;

    // Approval workflow fields
    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "approved_by")
    private String approvedBy;

}

