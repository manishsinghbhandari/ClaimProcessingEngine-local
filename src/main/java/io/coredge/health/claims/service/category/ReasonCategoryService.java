package io.coredge.health.claims.service.category;

import io.coredge.health.claims.models.category.CreateCategoryRequest;
import io.coredge.health.claims.models.category.response.ReasonCategoryResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;

public interface ReasonCategoryService {

    ReasonCategoryResponse createCategory(CreateCategoryRequest request);
    CollectionModel<EntityModel<ReasonCategoryResponse>> getCategories(Long id, Pageable pageable
    );
}
