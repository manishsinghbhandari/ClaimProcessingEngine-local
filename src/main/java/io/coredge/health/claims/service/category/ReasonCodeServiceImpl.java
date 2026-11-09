package io.coredge.health.claims.service.category;

import io.coredge.health.claims.context.HealthIdContextHolder;
import io.coredge.health.claims.db.entity.category.ReasonCategory;
import io.coredge.health.claims.db.entity.category.ReasonCode;
import io.coredge.health.claims.db.repositories.category.ReasonCategoryRepository;
import io.coredge.health.claims.db.repositories.category.ReasonCodeRepository;
import io.coredge.health.claims.enums.category.ReasonCodeStatus;
import io.coredge.health.claims.exception.category.ReasonCategoryNotFoundException;
import io.coredge.health.claims.exception.category.ReasonCodeAlreadyExistsException;
import io.coredge.health.claims.exception.category.ReasonCodeNotFoundException;
import io.coredge.health.claims.exception.category.ReasonDescriptionAlreadyExistsException;
import io.coredge.health.claims.models.category.CreateReasonCodeRequest;
import io.coredge.health.claims.models.category.UpdateReasonCodeRequest;
import io.coredge.health.claims.models.category.response.ReasonCodeResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReasonCodeServiceImpl implements ReasonCodeService {

    private final ReasonCodeRepository reasonCodeRepository;
    private final ReasonCategoryRepository reasonCategoryRepository;
    private final PagedResourcesAssembler<ReasonCodeResponse> pagedResourcesAssembler;
    private final SequenceGeneratorService sequenceGenerator;


    @Override
    public ReasonCodeResponse createReasonCode(CreateReasonCodeRequest request) {
        // 1. Get category
        ReasonCategory category = reasonCategoryRepository.findById(request.categoryId())
                .orElseThrow(ReasonCategoryNotFoundException::new);

        if (reasonCodeRepository.existsByDescription(request.description())) {
            throw new ReasonDescriptionAlreadyExistsException(request.description());
        }

        // 2. Get the next GLOBAL sequence
        String nextSequence = sequenceGenerator.getNextFormattedSequence(category.getId());

        // 3. Generate code with a GLOBAL sequence
        String generatedCode = category.getPrefix() + "-" + nextSequence;
        log.info("Generated code: {}", generatedCode);

        // 4. Check uniqueness
        if (reasonCodeRepository.existsByCode(generatedCode)) {
            throw new ReasonCodeAlreadyExistsException(generatedCode);
        }

        // 5. Create and save rejection code
        ReasonCode reasonCode = new ReasonCode();
        reasonCode.setCategory(category);
        reasonCode.setCode(generatedCode);
        reasonCode.setDescription(request.description());
        reasonCode.setActive(true);
        reasonCode.setStatus(request.status());

        // Set audit fields
        String userId = HealthIdContextHolder.userId();
        reasonCode.setCreatedBy(userId);
        reasonCode.setLastModifiedBy(userId);

        ReasonCode savedCode = reasonCodeRepository.save(reasonCode);

        return mapToResponse(savedCode);
    }

    @Override
    @Transactional(readOnly = true)
    public CollectionModel<EntityModel<ReasonCodeResponse>> getAllReasonCodes(Pageable pageable) {

        Page<ReasonCode> rejectionCodes = reasonCodeRepository.findAll(pageable);
        Page<ReasonCodeResponse> dtoPage = rejectionCodes.map(this::mapToResponse);
        return pagedResourcesAssembler.toModel(dtoPage);
    }

    @Override
    @Transactional(readOnly = true)
    public CollectionModel<EntityModel<ReasonCodeResponse>> getReasonCodesByCategory(Long categoryId, Pageable pageable) {
        Page<ReasonCode> rejectionCodes = reasonCodeRepository.findByCategory_Id(categoryId, pageable);
        if (rejectionCodes.getContent().isEmpty()) {
            throw new ReasonCategoryNotFoundException();
        }
        Page<ReasonCodeResponse> dtoPage = rejectionCodes.map(this::mapToResponse);
        return pagedResourcesAssembler.toModel(dtoPage);
    }


    @Override
    @Transactional
    public ReasonCodeResponse updateReasonCode(Long id, UpdateReasonCodeRequest request) {

        ReasonCode code = reasonCodeRepository.findById(id).orElseThrow(ReasonCodeNotFoundException::new);

        if (reasonCodeRepository.existsByDescriptionAndIdNot(request.description(), id)) {
            throw new ReasonDescriptionAlreadyExistsException(request.description());
        }

        code.setDescription(request.description());
        code.setActive(request.isActive());
        code.setLastModifiedBy(HealthIdContextHolder.userId());
        return mapToResponse(code);
    }

    private ReasonCodeResponse mapToResponse(ReasonCode code) {
        return new ReasonCodeResponse(
                code.getId(),
                code.getCategory().getId(),
                code.getCategory().getCategoryName(),
                code.getCode(),
                code.getDescription(),
                code.isActive(),
                code.getCategory().getPrefix(),
                code.getStatus()
        );
    }

    @Override
    public List<ReasonCodeResponse> getReasonCodesByStatus(ReasonCodeStatus status) {
        List<ReasonCode> listOfCodes =  reasonCodeRepository.findByStatusAndIsActiveTrue(status);
       if (listOfCodes == null || listOfCodes.isEmpty()) {
           throw new ReasonCodeNotFoundException();
       }
       return listOfCodes.stream().map(this::mapToResponse)
                .toList();

    }

    @Override
    @Transactional(readOnly = true)
    public CollectionModel<EntityModel<ReasonCodeResponse>> getReasonCodes(Long categoryId, ReasonCodeStatus status, Pageable pageable) {

        if (status != null) {
            List<ReasonCodeResponse> reasonCodesByStatus = getReasonCodesByStatus(status);
            List<EntityModel<ReasonCodeResponse>> models = reasonCodesByStatus.stream().map(EntityModel::of).toList();
            return CollectionModel.of(models);

        } else if (categoryId != null) {
            return getReasonCodesByCategory(categoryId, pageable);

        } else {
            return getAllReasonCodes(pageable);
        }
    }
}
