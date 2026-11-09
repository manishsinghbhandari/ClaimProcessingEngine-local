package io.coredge.health.claims.models.category.response;


import io.coredge.health.claims.enums.category.ReasonCodeStatus;

public record ReasonCodeResponse(
        Long id,
        Long categoryId,
        String categoryName,
        String code,
        String description,
        boolean isActive,
        String prefix,
        ReasonCodeStatus status
) {}