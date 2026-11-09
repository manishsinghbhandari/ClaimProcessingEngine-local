package io.coredge.health.claims.audit;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "claim_reprocess_audit", 
       uniqueConstraints = @UniqueConstraint(columnNames = {"claim_id", "intervention_code"}))
public class ClaimAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "claim_id", nullable = false)
    private UUID claimId;

    @Column(name = "approved_los")
    private Integer approvedAuditLos;

    @Column(name = "approved_amount")
    private BigDecimal approvedAmount;

    @Column(name = "intervention_code")
    private String interventionCode;

    @Column(name = "sequence")
    private Integer sequence;

    @Column(length = 200)
    private String remarks;

    @Column(name = "extra_remarks", length = 200)
    private String extraRemarks;

    @Column(name = "audit_status", nullable = false, length = 30)
    private String auditStatus;

    @Builder.Default
    @Column(nullable = false)
    private boolean processed = false;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "process_remarks", length = 100)
    private String processRemarks;

    @Column(name = "approver_name")
    private String approverName;

    @Column(name = "bulk_processing_id", length = 100)
    private String bulkProcessingId;
}
