package io.coredge.health.claims.models.AI;

import io.coredge.health.claims.enums.Decision;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AIValidationScoreRequest {
    @NotNull
    private Decision decision;

    @NotNull
    private Double min;

    @NotNull
    private Double max;

    @NotNull
    private String message;

    @NotNull
    private String claimType;
}
