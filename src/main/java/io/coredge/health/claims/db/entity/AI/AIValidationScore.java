package io.coredge.health.claims.db.entity.AI;

import io.coredge.health.claims.enums.Decision;
import jakarta.persistence.*;
import lombok.*;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Embeddable
public class AIValidationScore {

    private Double minScore;

    private Double maxScore;

    private String message;

    @Enumerated(EnumType.STRING)
    private Decision decision;

    private String use;
}
