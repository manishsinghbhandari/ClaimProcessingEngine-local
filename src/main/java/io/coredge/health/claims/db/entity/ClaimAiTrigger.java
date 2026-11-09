package io.coredge.health.claims.db.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Data
@Builder
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "claim_ai_trigger")
public class ClaimAiTrigger extends AuditableBase<String> {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;
    
    @Column(name = "package", nullable = false, length = 255)
    private String packageCode;
    
    @Column(name = "intervention_code", length = 100)
    private String interventionCode;
    
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;
    
    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;
    
    @Column(name = "status", nullable = false, length = 50, columnDefinition = "varchar default 'PENDING'")
    private String status;
    
    @Column(name = "last_processed_claim_id")
    private Long lastProcessedClaimId;
    
    @Column(name = "processed_claims_count", nullable = false, columnDefinition = "int default 0")
    private Integer processedClaimsCount;
    
    @Column(name = "ai_url", length = 255)
    private String aiUrl;
    
    @Column(name = "is_workflow", nullable = false, columnDefinition = "boolean default false")
    private Boolean isWorkflow;
}
