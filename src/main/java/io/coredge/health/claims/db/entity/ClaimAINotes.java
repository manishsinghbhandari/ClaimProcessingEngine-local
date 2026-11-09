package io.coredge.health.claims.db.entity;


import io.coredge.health.claims.enums.ClaimAIProcessStatusEnum;
import io.coredge.health.claims.models.AI.ScoreDetail;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Cascade;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;

@Data
@Builder
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "claim_ai_notes")
public class ClaimAINotes extends AuditableBase<String> {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne
    @Cascade(org.hibernate.annotations.CascadeType.SAVE_UPDATE)
    @JoinColumn(name = "claim_id", nullable = false, referencedColumnName = "claim_id")
    private InsuranceClaim claim;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "notes", columnDefinition = "jsonb")
    private String notes;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "user_review", columnDefinition = "jsonb")
    private String user_review;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar default 'SENT'")
    private ClaimAIProcessStatusEnum status;

    @Column(name = "overall_notes", columnDefinition = "TEXT")
    private String overallNotes;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "scores", nullable = false , columnDefinition = "jsonb")
    private Map<String , ScoreDetail> scores;
}
