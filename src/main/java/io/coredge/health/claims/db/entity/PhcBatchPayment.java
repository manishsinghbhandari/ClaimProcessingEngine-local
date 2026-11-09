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
@Table(name = "phc_batch_payments", schema = "public")
public class PhcBatchPayment extends AuditableBase<String> {
    public enum Status {
        PENDING,
        APPROVED,
        PARTIAL_APPROVAL,
        REJECTED,
        SENT_FOR_PAYMENT_PROCESSING,
        PAYMENT_COMPLETED,
        FAILED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "admin_level_pop_id", referencedColumnName = "id", foreignKey = @ForeignKey(name = "fk_phc_batch_admin_pop"))
    private AdminLevelPopulation adminLevelPopulation;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "amount", precision = 38, scale = 2, nullable = false)
    private BigDecimal amount;

    @Column(name = "approved_amount", precision = 38, scale = 2)
    private BigDecimal approvedAmount;

    @Column(name = "paid_amount", precision = 38, scale = 2)
    private BigDecimal paidAmount;

    @Column(name = "number_of_claims")
    private Long numberOfClaims;

    @Column(name = "global_budget", precision = 38, scale = 2)
    private BigDecimal globalBudget;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 50, nullable = false)
    private Status status;

    @Column(name = "remarks")
    private String remarks;

    // Approval workflow fields
    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "approved_by")
    private String approvedBy;

}

