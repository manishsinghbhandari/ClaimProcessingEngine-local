package io.coredge.health.claims.service.category;


import io.coredge.health.claims.enums.category.ReasonCodeStatus;
import io.coredge.health.claims.models.category.CreateReasonCodeRequest;
import io.coredge.health.claims.models.category.UpdateReasonCodeRequest;
import io.coredge.health.claims.models.category.response.ReasonCodeResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;

import java.util.List;


public interface ReasonCodeService {

    ReasonCodeResponse createReasonCode(CreateReasonCodeRequest request);

    ReasonCodeResponse updateReasonCode(Long id, UpdateReasonCodeRequest request);

    CollectionModel<EntityModel<ReasonCodeResponse>> getAllReasonCodes(Pageable pageable);

    CollectionModel<EntityModel<ReasonCodeResponse>> getReasonCodesByCategory(Long categoryId, Pageable pageable);

    List<ReasonCodeResponse> getReasonCodesByStatus(ReasonCodeStatus status);

    CollectionModel<EntityModel<ReasonCodeResponse>> getReasonCodes(Long categoryId, ReasonCodeStatus status, Pageable pageable);

}