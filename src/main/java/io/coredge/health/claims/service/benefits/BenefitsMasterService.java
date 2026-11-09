package io.coredge.health.claims.service.benefits;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.coredge.health.claims.db.entity.BenefitsMaster;
import io.coredge.health.claims.db.repositories.AiRouteRepository;
import io.coredge.health.claims.db.repositories.BenefitsMasterRepository;
import io.coredge.health.claims.db.repositories.benefits.DocumentTypeRepository;
import io.coredge.health.claims.exception.benefits.BenefitsNotFoundException;
import io.coredge.health.claims.exception.benefits.NoChangesDetectedException;
import io.coredge.health.claims.mapper.benefits.BenefitsMasterMapper;
import io.coredge.health.claims.models.benefits.BenefitsMasterDropDownResponse;
import io.coredge.health.claims.models.benefits.BenefitsMasterProjection;
import io.coredge.health.claims.models.benefits.BenefitsMasterRequestDTO;
import io.coredge.health.claims.models.benefits.BenefitsMasterResponseDTO;
import io.coredge.health.claims.service.AI.AIValidationService;
import io.coredge.health.claims.utils.benefits.BenefitsMasterToJson;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class BenefitsMasterService {

    private final ObjectMapper objectMapper;
    private final BenefitsMasterAuditService benefitsMasterAuditService;
    private final PagedResourcesAssembler<BenefitsMasterResponseDTO> pagedResourcesAssembler;
    private final BenefitsMasterMapper benefitsMasterMapper;
    private final BenefitsMasterFieldRegistry benefitsMasterFieldRegistry;
    private final DocumentTypeRepository documentTypeRepository;
    private final BenefitsMasterRepository benefitsMasterRepository;
    private final AIValidationService aIValidationService;
    private final AiRouteRepository aiRouteRepository;

    public CollectionModel<EntityModel<BenefitsMasterResponseDTO>> getAllBenefits(Pageable pageable) {
        Page<BenefitsMaster> benefitsPage = benefitsMasterRepository.findAll(pageable);
        Page<BenefitsMasterResponseDTO> dtoPage = benefitsPage.map(benefitsMasterMapper::convertToDTO);
        return pagedResourcesAssembler.toModel(dtoPage);
    }

    public BenefitsMasterDropDownResponse getBenefitsDropdown() {
        List<BenefitsMasterProjection> projections = benefitsMasterRepository.findAllDropdownValues();

        Set<String> providerPaymentMechanisms = new HashSet<>();
        Set<String> limitsIndividualHouseholds = new HashSet<>();
        Set<String> interventionQuantityYears = new HashSet<>();
        Set<String> appliesToSchemes = new HashSet<>();
        Set<String> bedTypes = new HashSet<>();
        Set<String> accessCodes = new HashSet<>();
        Set<String> genders = new HashSet<>();
        Set<String> needsPreAuths = new HashSet<>();

        List<BenefitsMasterDropDownResponse.KeyValueResponse> aiValidationResponse = aIValidationService
                .getAIValidationDropdown();

        // Single iteration over the list
        projections.forEach(benefitsMasterProjection -> {
            Optional.ofNullable(benefitsMasterProjection.getProviderPaymentMechanism())
                    .ifPresent(providerPaymentMechanisms::add);
            Optional.ofNullable(benefitsMasterProjection.getLimitsIndividualHousehold())
                    .ifPresent(limitsIndividualHouseholds::add);
            Optional.ofNullable(benefitsMasterProjection.getInterventionQuantityYear())
                    .ifPresent(interventionQuantityYears::add);
            Optional.ofNullable(benefitsMasterProjection.getAppliesToSchemes()).ifPresent(appliesToSchemes::add);
            Optional.ofNullable(benefitsMasterProjection.getBedType()).ifPresent(bedTypes::add);
            Optional.ofNullable(benefitsMasterProjection.getAccessCode()).ifPresent(accessCodes::add);
            Optional.ofNullable(benefitsMasterProjection.getGender()).ifPresent(genders::add);
            Optional.ofNullable(benefitsMasterProjection.getNeedsPreAuth())
                    .map(b -> b ? "True" : "False")
                    .ifPresent(needsPreAuths::add);

        });

        List<BenefitsMasterDropDownResponse.KeyValueResponse> documentTypes = documentTypeRepository.findAll()
                .stream()
                .map(doc -> new BenefitsMasterDropDownResponse.KeyValueResponse(doc.getId(), doc.getName()))
                .toList();

        // AI Routes dropdown
        List<BenefitsMasterDropDownResponse.KeyValueResponse> aiRoutes = aiRouteRepository.findAll()
                .stream()
                .map(route -> new BenefitsMasterDropDownResponse.KeyValueResponse(route.getId(), route.getName()))
                .toList();

        return new BenefitsMasterDropDownResponse(
                new ArrayList<>(providerPaymentMechanisms),
                new ArrayList<>(limitsIndividualHouseholds),
                new ArrayList<>(interventionQuantityYears),
                new ArrayList<>(appliesToSchemes),
                new ArrayList<>(bedTypes),
                new ArrayList<>(accessCodes),
                new ArrayList<>(genders),
                new ArrayList<>(needsPreAuths),
                new ArrayList<>(documentTypes),
                new ArrayList<>(aiValidationResponse),
                new ArrayList<>(aiRoutes));
    }

    @Transactional
    public BenefitsMasterResponseDTO updateAllowedFields(Long id, BenefitsMasterRequestDTO req) {

        // Step 1. Fetch the existing entity from database by ID, or throw exception if not found
        BenefitsMaster entity = benefitsMasterRepository.findById(id)
                .orElseThrow(() -> new BenefitsNotFoundException("No benefits record found with ID: " + id));

        // Step 2. Prepare containers to capture field changes
        // 'prev' → holds old values before update
        Map<String, Object> prev = new LinkedHashMap<>();
        // 'curr' → holds new values after update
        Map<String, Object> curr = new LinkedHashMap<>();

        // Step 3. Update entity fields based on FIELD_UPDATERS defined in BenefitsMasterFieldRegistry.
        benefitsMasterFieldRegistry.updateFieldsToEntity(entity, req, prev, curr);

            // Step 4. If no fields were changed (curr map is empty), throw an exception indicating that there are no updatable fields.
        if (curr.isEmpty()) {
            log.warn("No updatable fields detected for BenefitsMaster with id={}", id);
            throw new NoChangesDetectedException("No changes detected, record not updated for ID: " + id);
        }

        // step 5. log the entry previous record and new record
        benefitsMasterAuditService.saveLog(entity.getId(),
                BenefitsMasterToJson.toJson(objectMapper, prev),
                BenefitsMasterToJson.toJson(objectMapper, curr));

        // Step 6. Convert the entity into a DTO to be returned in the API response.
        return benefitsMasterMapper.convertToDTO(entity);
    }

    public BenefitsMasterResponseDTO getBenefit(Long id) {
        return benefitsMasterRepository.findById(id)
                .map(benefitsMasterMapper::convertToDTO)
                .orElseThrow(() -> new BenefitsNotFoundException("No benefits record found with ID: " + id));
    }

    public CollectionModel<EntityModel<BenefitsMasterResponseDTO>> filterByInterventionOrCode(
            String interventionCode,
            String code,
            Pageable pageable) {

        Page<BenefitsMaster> benefitsPage;

        if (code != null && !code.isBlank()) {
            benefitsPage = benefitsMasterRepository
                    .findByCodeIgnoreCaseStartingWith(code.toLowerCase(), pageable);
        } else if (interventionCode != null && !interventionCode.isBlank()) {
            benefitsPage = benefitsMasterRepository
                    .findByInterventionCodeIgnoreCaseStartingWith(interventionCode.toLowerCase(), pageable);
        } else {
            benefitsPage = benefitsMasterRepository.findAll(pageable);
        }
        Page<BenefitsMasterResponseDTO> dtoPage = benefitsPage.map(benefitsMasterMapper::convertToDTO);

        return pagedResourcesAssembler.toModel(dtoPage);
    }
}
