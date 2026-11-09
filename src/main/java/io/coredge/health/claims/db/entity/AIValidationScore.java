package io.coredge.health.claims.db.entity;

import io.coredge.health.claims.enums.Decision;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Data
@Builder
@EntityListeners(AuditingEntityListener.class)
@AllArgsConstructor
@NoArgsConstructor
@Cacheable
@Table(name = "ai_validation_score", schema = "public")
public class AIValidationScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "validation_key", nullable = false)
    private String validationKey;

    @Column(name = "min_score", nullable = false)
    private Double minScore;

    @Column(name = "max_score", nullable = false)
    private Double maxScore;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "message", nullable = false)
    private String message;

    @Basic
    @Column(name = "decision" , nullable = false)
    @Enumerated(EnumType.STRING)
    private Decision decision;
}
