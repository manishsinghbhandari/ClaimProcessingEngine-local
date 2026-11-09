package io.coredge.health.claims.db.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

@Data
@Builder
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "claim_ai_reports")
public class ClaimAiReport extends AuditableBase<String> {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "claim_id", nullable = false, referencedColumnName = "claim_id")
    private InsuranceClaim claim;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trigger_id", referencedColumnName = "id")
    private ClaimAiTrigger trigger;
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "notes", columnDefinition = "jsonb")
    private String notes;
    
    @Column(name = "overall_notes", columnDefinition = "TEXT")
    private String overallNotes;
    
    @Column(name = "status", nullable = false, length = 50, columnDefinition = "varchar default 'PENDING'")
    private String status;
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "scores", columnDefinition = "jsonb")
    private String scores;
}
