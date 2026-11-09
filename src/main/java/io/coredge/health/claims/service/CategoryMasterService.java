package io.coredge.health.claims.service;

import io.coredge.health.claims.db.entity.CategoryMaster;
import io.coredge.health.claims.db.repositories.CategoryRepository;
import io.coredge.health.claims.enums.CategorySearchType;
import io.coredge.health.claims.exception.app.BadRequestException;
import io.coredge.health.claims.exception.app.DataNotFoundException;
import io.coredge.health.claims.exception.models.ErrorAttribute;
import io.coredge.health.claims.models.master.CategoryMasterResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.PagedModel;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
public class CategoryMasterService {

    @Autowired
    private CategoryRepository repository;

    @Autowired
    private PagedResourcesAssembler<CategoryMasterResponse> pagedResourcesAssembler;


    /**
     * @param categoryMaster
     * @return
     */
    private static CategoryMasterResponse getCategoryMasterResponse(CategoryMaster categoryMaster) {
        CategoryMasterResponse response = new CategoryMasterResponse();
        BeanUtils.copyProperties(categoryMaster, response); // Copy properties
        return response;
    }

    public Optional<List<CategoryMaster>> findAll() {
        return Optional.of(repository.findAll());
    }

    public Optional<Page<CategoryMaster>> findAll(Pageable pageable) {
        return Optional.of(repository.findAll(pageable));
    }

    public Optional<CategoryMaster> findById(Long id) {
        return repository.findById(id);
    }

    public CategoryMaster save(CategoryMaster categoryMaster) {
        return repository.save(categoryMaster);
    }

    public void deleteById(Long id) {
        repository.deleteById(id);
    }

    public List<CategoryMasterResponse> fetchCategory(String searchType, String searchKey) {
        if (CategorySearchType.code.getValue().equals(searchType)) {
            return getByCategoryCode(searchKey);
        }
        ErrorAttribute attribute = ErrorAttribute.builder().key(searchType).value(searchKey).build();
        throw new BadRequestException(attribute);
    }

    public List<CategoryMasterResponse> fetchAllCategory() {
        return findAll().filter(list -> !list.isEmpty())
                .map(list -> list.stream() // If data is present, stream over the list
                        .map(CategoryMasterService::getCategoryMasterResponse) // Process each item
                        .collect(Collectors.toList())) // Collect the results into a list
                .orElseThrow(DataNotFoundException::new);// If no data is present, return an empty list or throw
    }

    public PagedModel<EntityModel<CategoryMasterResponse>> fetchAllPageableCategory(Pageable pageable) {
        Optional<Page<CategoryMaster>> allPractitioners = findAll(pageable);
        Page<CategoryMasterResponse> practitionerDetailsResponses = allPractitioners
                .filter(Page::hasContent)
                .map(page -> page.map(CategoryMasterService::getCategoryMasterResponse))
                .orElseThrow(DataNotFoundException::new);
        return pagedResourcesAssembler.toModel(practitionerDetailsResponses);
    }

    /**
     * @param categoryCode
     * @return
     */
    private List<CategoryMasterResponse> getByCategoryCode(String categoryCode) {
        return repository.findByCode(categoryCode)
                .filter(list -> !list.isEmpty())
                .map(list -> list.stream() // If data is present, stream over the list
                        .map(CategoryMasterService::getCategoryMasterResponse) // Process each item
                        .collect(Collectors.toList())) // Collect the results into a list
                .orElseThrow(DataNotFoundException::new);// If no data is present, return an empty list or throw

    }


}
