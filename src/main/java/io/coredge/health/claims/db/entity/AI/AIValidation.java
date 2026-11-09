package io.coredge.health.claims.db.entity.AI;

import io.coredge.health.claims.db.entity.AuditableBase;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.DynamicUpdate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

@EqualsAndHashCode(callSuper = true)
@Entity
@Data
@Builder
@EntityListeners(AuditingEntityListener.class)
@AllArgsConstructor
@NoArgsConstructor
@DynamicUpdate
@Table(name = "ai_validation", schema = "public")
public class AIValidation extends AuditableBase<String> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "validation_key", nullable = false)
    private String validationKey;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "version", nullable = false)
    private Integer version;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted;

    @Column(name = "effective_date_from")
    private LocalDate effectiveDateFrom;

    @Column(name = "effective_date_to")
    private LocalDate effectiveDateTo;

    // ElementCollection for validation score details
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "ai_validation_score",
            joinColumns = @JoinColumn(name = "ai_validation_id")
    )
    private Set<AIValidationScore> scores = new HashSet<>();

}

