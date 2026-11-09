package io.coredge.health.claims.models.AI;

import io.coredge.health.claims.enums.Decision;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AIValidationScoreResponse {

    private Double minScore;

    private Double maxScore;

    private String message;

    private Decision decision;

    private String claimType;
}