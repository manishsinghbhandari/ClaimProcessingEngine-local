package io.coredge.health.claims.models.category;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateCategoryRequest(
        @NotBlank String categoryName,
        @NotBlank String prefix
        ) {}