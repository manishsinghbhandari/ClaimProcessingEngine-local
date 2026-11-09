package io.coredge.health.claims.rest.admin;

import io.coredge.health.claims.context.HealthIdContextHolder;
import io.coredge.health.claims.enums.category.ReasonCodeStatus;
import io.coredge.health.claims.enums.permission.ActionEnum;
import io.coredge.health.claims.enums.permission.ResourceEnum;
import io.coredge.health.claims.exception.app.ResourceNotAuthorizedException;
import io.coredge.health.claims.helpers.Helper;
import io.coredge.health.claims.models.category.CreateCategoryRequest;
import io.coredge.health.claims.models.category.CreateReasonCodeRequest;
import io.coredge.health.claims.models.category.UpdateReasonCodeRequest;
import io.coredge.health.claims.models.category.response.ReasonCategoryResponse;
import io.coredge.health.claims.models.category.response.ReasonCodeResponse;
import io.coredge.health.claims.service.PermissionService;
import io.coredge.health.claims.service.category.ReasonCategoryService;
import io.coredge.health.claims.service.category.ReasonCodeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static io.coredge.health.claims.constants.Constants.CATEGORY.*;

@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class ReasonCategoryAndCodeController {

    private final ReasonCodeService reasonCodeService;
    private final ReasonCategoryService reasonCategoryService;
    private final PermissionService permissionService;

    @PostMapping(CREATE_CATEGORY)
    public ResponseEntity<ReasonCategoryResponse> createCategory(@RequestBody @Valid CreateCategoryRequest request) {
        boolean hasPermission = permissionService.hasPermission(HealthIdContextHolder.userId(), ResourceEnum.REASON_MODULE, ActionEnum.EDIT);
        if (!hasPermission) {
            throw new ResourceNotAuthorizedException("User is not authorized to perform this action");
        }
        return ResponseEntity.ok(reasonCategoryService.createCategory(request));
    }

    @PostMapping(CREATE_CODE)
    public ResponseEntity<ReasonCodeResponse> createRejectionCode(@RequestBody @Valid CreateReasonCodeRequest request) {
        boolean hasPermission = permissionService.hasPermission(HealthIdContextHolder.userId(), ResourceEnum.REASON_MODULE, ActionEnum.EDIT);
        if (!hasPermission) {
            throw new ResourceNotAuthorizedException("User is not authorized to perform this action");
        }
        return ResponseEntity.ok(reasonCodeService.createReasonCode(request));
    }

    @GetMapping(GET_ALL_REASON_CODES)
    public ResponseEntity<CollectionModel<EntityModel<ReasonCodeResponse>>> listRejectionCodes(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) ReasonCodeStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "ASC") String direction) {

        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(Helper.getSortDirection(direction), sortBy)
        );

        CollectionModel<EntityModel<ReasonCodeResponse>> response =
                reasonCodeService.getReasonCodes(categoryId, status, pageable);

        return ResponseEntity.ok(response);
    }

    @GetMapping(GET_CATEGORIES)
    public ResponseEntity<CollectionModel<EntityModel<ReasonCategoryResponse>>> listCategories(
            @RequestParam(required = false) Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "ASC") String direction) {

        PageRequest pageable = PageRequest.of(page, size, Sort.by(Helper.getSortDirection(direction), sortBy));
        return ResponseEntity.ok(reasonCategoryService.getCategories(id, pageable));
    }

    @PutMapping(FIND_BY_ID)
    public ResponseEntity<ReasonCodeResponse> updateReasonCode(@PathVariable Long id, @RequestBody @Valid UpdateReasonCodeRequest request) {
        return ResponseEntity.ok(reasonCodeService.updateReasonCode(id, request));
    }

    @GetMapping(GET_REASON_CODES)
    public ResponseEntity<List<ReasonCodeResponse>> getReasonCodesByStatus(@PathVariable ReasonCodeStatus status) {
        return ResponseEntity.ok(reasonCodeService.getReasonCodesByStatus(status));
    }
}