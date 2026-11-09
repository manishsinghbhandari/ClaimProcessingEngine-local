package io.coredge.health.claims.models.category;

import io.coredge.health.claims.enums.category.ReasonCodeStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateReasonCodeRequest(

        @NotNull(message = "Category id is required")
        Long categoryId,

        @NotNull(message = "Status is required")
        ReasonCodeStatus status,

        @NotBlank(message = "Description cannot be null or blank")
        String description
) {}