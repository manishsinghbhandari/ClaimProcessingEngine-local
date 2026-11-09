package io.coredge.health.claims.db.entity;

import io.coredge.health.claims.enums.BatchApprovalStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "batch_approval_request")
public class BatchApprovalRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "batch_status", nullable = false, length = 30)
    @Builder.Default
    private BatchApprovalStatus batchStatus = BatchApprovalStatus.PENDING;

    @Column(name = "total_claims", nullable = false)
    @Builder.Default
    private int totalClaims = 0;

    @Column(name = "successful", nullable = false)
    @Builder.Default
    private int successful = 0;

    @Column(name = "failed", nullable = false)
    @Builder.Default
    private int failed = 0;

    @Column(name = "initiated_by", nullable = false, length = 100)
    private String initiatedBy;

    @Column(name = "intervention_code", nullable = false, length = 50)
    private String interventionCode;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "status_filter", length = 30)
    private String statusFilter;

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Builder.Default
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    private void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
