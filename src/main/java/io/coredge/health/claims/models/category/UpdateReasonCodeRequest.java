package io.coredge.health.claims.models.category;

import jakarta.validation.constraints.NotBlank;

public record UpdateReasonCodeRequest(
    @NotBlank String description,
    boolean isActive
) {}