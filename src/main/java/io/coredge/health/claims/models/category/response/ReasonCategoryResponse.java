package io.coredge.health.claims.models.category.response;

import lombok.Builder;

@Builder
public record ReasonCategoryResponse(
        Long id,
        String categoryName,
        String prefix
) {}