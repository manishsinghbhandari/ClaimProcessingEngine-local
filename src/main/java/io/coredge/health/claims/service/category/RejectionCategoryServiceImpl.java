package io.coredge.health.claims.service.category;

import io.coredge.health.claims.db.entity.category.ReasonCategory;
import io.coredge.health.claims.db.repositories.category.ReasonCategoryRepository;
import io.coredge.health.claims.exception.category.ReasonCategoryAlreadyExistsException;
import io.coredge.health.claims.exception.category.ReasonCategoryNotFoundException;
import io.coredge.health.claims.exception.category.ReasonPrefixAlreadyExistsException;
import io.coredge.health.claims.models.category.CreateCategoryRequest;
import io.coredge.health.claims.models.category.response.ReasonCategoryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;



@Service
@RequiredArgsConstructor
public class RejectionCategoryServiceImpl implements ReasonCategoryService {

    private final ReasonCategoryRepository categoryRepository;

    private final PagedResourcesAssembler<ReasonCategoryResponse> pagedResourcesAssembler;


    @Override
    public ReasonCategoryResponse createCategory(CreateCategoryRequest request) {
        // Check for duplicates
        if (categoryRepository.existsByCategoryName(request.categoryName())) {
            throw new ReasonCategoryAlreadyExistsException(request.categoryName());
        }

        if (categoryRepository.existsByPrefix(request.prefix())) {
            throw new ReasonPrefixAlreadyExistsException(request.prefix());
        }

        // Create category
        String SEQUENCE = "000";
        ReasonCategory category = ReasonCategory.builder()
                .categoryName(request.categoryName())
                .prefix(request.prefix())
                .lastSequence(SEQUENCE)
                .build();

        ReasonCategory saved = categoryRepository.save(category);

        return toDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public CollectionModel<EntityModel<ReasonCategoryResponse>> getCategories(Long id, Pageable pageable) {

        Page<ReasonCategory> categories = id != null ? categoryRepository.findById(id, pageable) : categoryRepository.findAll(pageable);

        if (id != null && categories.isEmpty()) {
            throw new ReasonCategoryNotFoundException();
        }

        Page<ReasonCategoryResponse> dtoPage = categories.map(this::toDto);
        return pagedResourcesAssembler.toModel(dtoPage);
    }

    private ReasonCategoryResponse toDto(ReasonCategory category) {
        return ReasonCategoryResponse.builder()
                .id(category.getId())
                .categoryName(category.getCategoryName())
                .prefix(category.getPrefix())
                .build();
    }
}