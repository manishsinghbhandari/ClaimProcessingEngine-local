package io.coredge.health.claims.service.benefits;

import io.coredge.health.claims.constants.benefits.BenefitsMasterFieldKeys;
import io.coredge.health.claims.db.entity.AI.AIValidation;
import io.coredge.health.claims.db.entity.AI.AiValidationMapping;
import io.coredge.health.claims.db.entity.AiRoute;
import io.coredge.health.claims.db.entity.BenefitsMaster;
import io.coredge.health.claims.db.entity.benefits.DocumentType;
import io.coredge.health.claims.db.repositories.AiNotes.AIValidationRepository;
import io.coredge.health.claims.db.repositories.AiNotes.AiValidationMappingRepository;
import io.coredge.health.claims.db.repositories.AiRouteRepository;
import io.coredge.health.claims.db.repositories.BenefitsMasterRepository;
import io.coredge.health.claims.db.repositories.benefits.DocumentTypeRepository;
import io.coredge.health.claims.exception.benefits.AiConfigurationException;
import io.coredge.health.claims.exception.benefits.RequestedAIValidationNotExist;
import io.coredge.health.claims.exception.benefits.RequestedDocTypeNotExist;
import io.coredge.health.claims.models.AI.BulkValidationMappingRequest;
import io.coredge.health.claims.models.benefits.BenefitsMasterRequestDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static io.coredge.health.claims.constants.benefits.BenefitsMasterFieldKeys.*;

@Component
@RequiredArgsConstructor
public class BenefitsMasterFieldRegistry {

        private final DocumentTypeRepository documentTypeRepository;
        private final BenefitsMasterRepository benefitsMasterRepository;
        private final AIValidationRepository aiValidationRepository;
        private final AiValidationMappingRepository aiValidationMappingRepository;
        private final AiRouteRepository aiRouteRepository;

        public void updateFieldsToEntity(BenefitsMaster entity, BenefitsMasterRequestDTO req,
                        Map<String, Object> prev, Map<String, Object> curr) {
                updateValueIfChanged(BenefitsMasterFieldKeys.ACCESS_CODE,
                                entity.getAccessCode(),
                                req.getAccessCode(),
                                entity::setAccessCode, prev, curr);

                updateValueIfChanged(BenefitsMasterFieldKeys.GENDER,
                                entity.getGender(), req.getGender(),
                                entity::setGender, prev, curr);

                updateValueIfChanged(BenefitsMasterFieldKeys.NEEDS_PRE_AUTH,
                                entity.isNeedsPreAuth(),
                                req.getNeedsPreAuth(),
                                entity::setNeedsPreAuth, prev, curr);

                updateValueIfChanged(BenefitsMasterFieldKeys.PROVIDER_PAYMENT_MECHANISM,
                                entity.getProviderPaymentMechanism(),
                                req.getProviderPaymentMechanism(),
                                entity::setProviderPaymentMechanism, prev, curr);

                updateValueIfChanged(BenefitsMasterFieldKeys.LIMITS_INDIVIDUAL_HOUSEHOLD,
                                entity.getLimitsIndividualHousehold(),
                                req.getLimitsIndividualHousehold(),
                                entity::setLimitsIndividualHousehold, prev, curr);

                updateValueIfChanged(BenefitsMasterFieldKeys.INTERVENTION_QUANTITY_YEAR,
                                entity.getInterventionQuantityYear(),
                                req.getInterventionQuantityYear(),
                                entity::setInterventionQuantityYear, prev, curr);

                updateValueIfChanged(BenefitsMasterFieldKeys.APPLIES_TO_SCHEMES,
                                entity.getAppliesToSchemes(),
                                req.getAppliesToSchemes(),
                                entity::setAppliesToSchemes, prev, curr);

                updateValueIfChanged(BenefitsMasterFieldKeys.TARIFFS,
                                entity.getTariffs(),
                                req.getTariffs(),
                                entity::setTariffs, prev, curr);

                updateValueIfChanged(BenefitsMasterFieldKeys.LEVEL_2_TARIFF,
                                entity.getLevel2Tariff(),
                                req.getLevel2Tariff(),
                                entity::setLevel2Tariff, prev, curr);

                updateValueIfChanged(BenefitsMasterFieldKeys.LEVEL_3_TARIFF,
                                entity.getLevel3Tariff(), req.getLevel3Tariff(),
                                entity::setLevel3Tariff, prev, curr);

                updateValueIfChanged(BenefitsMasterFieldKeys.LEVEL_4_TARIFF,
                                entity.getLevel4Tariff(), req.getLevel4Tariff(),
                                entity::setLevel4Tariff, prev, curr);

                updateValueIfChanged(BenefitsMasterFieldKeys.LEVEL_5_TARIFF,
                                entity.getLevel5Tariff(),
                                req.getLevel5Tariff(),
                                entity::setLevel5Tariff, prev, curr);

                updateValueIfChanged(BenefitsMasterFieldKeys.LEVEL_6_TARIFF,
                                entity.getLevel6Tariff(),
                                req.getLevel6Tariff(),
                                entity::setLevel6Tariff, prev, curr);

                updateValueIfChanged(BenefitsMasterFieldKeys.BED_TYPE,
                                entity.getBedType(),
                                req.getBedType(),
                                entity::setBedType, prev, curr);

                // --- New: Update documentTypes --- EPORTAL-762
                updateDocumentTypes(entity, req, prev, curr);

                // --- New: Update AI Validation mappings ---
                updateAiValidations(entity, req, prev, curr);

                // --- New: Update AI Configuration (efwaCheckEnabled and routeId) ---
                updateAiConfiguration(entity, req, prev, curr);
        }

        public void updateDocumentTypes(BenefitsMaster entity,
                        BenefitsMasterRequestDTO req,
                        Map<String, Object> prev,
                        Map<String, Object> curr) {

                if (req.getDocumentTypes() == null)
                        return;

                // Step 1: Extract existing and requested IDs
                Set<Long> existingDocIds = entity.getDocumentTypes().stream()
                                .map(DocumentType::getId)
                                .collect(Collectors.toSet());

                Set<Long> requestDocIds = new HashSet<>(req.getDocumentTypes());

                // Step 2: Check if there's any change
                if (!existingDocIds.equals(requestDocIds)) {
                        prev.put(DOCUMENT_TYPES, existingDocIds);
                        curr.put(DOCUMENT_TYPES, requestDocIds);

                        // Step 3: Fetch all requested document types
                        List<DocumentType> foundDocs = documentTypeRepository.findAllById(requestDocIds);
                        if (foundDocs.size() != requestDocIds.size()) {
                                throw new RequestedDocTypeNotExist(
                                                "Requested document types do not exist in the database.");
                        }

                        // Step 4: Update bidirectional mapping safely
                        entity.getDocumentTypes().clear();
                        for (DocumentType dt : foundDocs) {
                                entity.getDocumentTypes().add(dt);
                                dt.getBenefitsMasters().add(entity);
                        }

                        // Step 5: Save parent entity to persist mapping table updates
                        benefitsMasterRepository.save(entity);
                }
        }

        public void updateAiValidations(BenefitsMaster entity,
                        BenefitsMasterRequestDTO req,
                        Map<String, Object> prev,
                        Map<String, Object> curr) {

                if (req.getAiValidationScore() == null || req.getAiValidationScore().isEmpty())
                        return;

                // Step 1: Get existing mappings from DB
                List<AIValidation> existingValidations = aiValidationMappingRepository
                                .findAiValidationsByBenefitMasterId(entity.getId());

                Set<Long> existingIds = existingValidations.stream()
                                .map(AIValidation::getId)
                                .collect(Collectors.toSet());

                Set<Long> requestedIds = new HashSet<>(req.getAiValidationScore());

                // Step 2: Find only new IDs (to insert)
                Set<Long> newIds = requestedIds.stream()
                                .filter(id -> !existingIds.contains(id))
                                .collect(Collectors.toSet());

                Set<Long> removeIds = existingIds.stream()
                                .filter(id -> !requestedIds.contains(id))
                                .collect(Collectors.toSet());

                aiValidationMappingRepository.deleteAllByBenefitMasterIdAndValidationIds(entity.getId(), removeIds);

                // Nothing new to add
                if (newIds.isEmpty() && removeIds.isEmpty())
                        return;

                prev.put(AI_VALIDATION_SCORE, existingIds);
                curr.put(AI_VALIDATION_SCORE, newIds);

                // Step 3: Fetch all requested AI validations (only new)
                List<AIValidation> newValidations = aiValidationRepository.findAllById(newIds);
                if (newValidations.size() != newIds.size()) {
                        throw new RequestedAIValidationNotExist(
                                        "Requested AI validations do not exist in the database.");
                }

                // Step 4: Create new mapping entries
                List<AiValidationMapping> newMappings = newValidations.stream()
                                .map(v -> {
                                        AiValidationMapping mapping = new AiValidationMapping();
                                        mapping.setBenefitMaster(entity);
                                        mapping.setAiValidation(v);
                                        return mapping;
                                })
                                .collect(Collectors.toList());

                aiValidationMappingRepository.saveAll(newMappings);
        }

        @Async
        public void bulkUploadValidationMapping(BulkValidationMappingRequest request) {

                // Determine which benefit IDs to use
                List<Long> benefitIdsFromDb;
                if ("all".equalsIgnoreCase(request.getBenefit())) {
                        benefitIdsFromDb = benefitsMasterRepository.findAllIds();
                } else {
                        List<String> codes = Arrays.stream(request.getBenefit().toLowerCase().split(","))
                                        .map(String::trim)
                                        .toList();

                        benefitIdsFromDb = benefitsMasterRepository.findAllIdsByCodeOrInterventionCodeIn(codes);
                }

                // Build the cross-product of Benefits × Validations
                List<Long> benefitIds = new ArrayList<>();
                List<Long> validationIds = new ArrayList<>();

                for (Long benefitId : benefitIdsFromDb) {
                        for (Long validationId : request.getValidation()) {
                                benefitIds.add(benefitId);
                                validationIds.add(validationId);
                        }
                }

                // Perform batch insert (ignore duplicates)
                aiValidationMappingRepository.insertBatchIgnoreConflicts(
                                benefitIds.toArray(new Long[0]),
                                validationIds.toArray(new Long[0]));

        }

        public <T> void updateValueIfChanged(String key, T oldValue, T newValue, Consumer<T> setter,
                        Map<String, Object> prev, Map<String, Object> curr) {
                if (newValue != null && !Objects.equals(oldValue, newValue)) {
                        prev.put(key, oldValue);
                        curr.put(key, newValue);
                        setter.accept(newValue);
                }
        }

        /**
         * Updates AI configuration fields (efwaCheckEnabled and routeId).
         * Validates that routeId cannot be set if efwaCheckEnabled is false.
         */
        public void updateAiConfiguration(BenefitsMaster entity,
                        BenefitsMasterRequestDTO req,
                        Map<String, Object> prev,
                        Map<String, Object> curr) {

                // Determine the effective efwaCheckEnabled value
                Boolean currentEfwaEnabled = entity.getEfwaCheckEnabled();
                Boolean newEfwaEnabled = req.getEfwaCheckEnabled();
                Boolean effectiveEfwaEnabled = (newEfwaEnabled != null) ? newEfwaEnabled : currentEfwaEnabled;

                // Update efwaCheckEnabled
                updateValueIfChanged(BenefitsMasterFieldKeys.EFWA_CHECK_ENABLED,
                                currentEfwaEnabled,
                                newEfwaEnabled,
                                entity::setEfwaCheckEnabled, prev, curr);

                // Validate and update routeId
                if (req.getRouteId() != null) {
                        // If trying to set a route but AI flag is false, reject
                        if (Boolean.FALSE.equals(effectiveEfwaEnabled)) {
                                throw new AiConfigurationException(
                                                "Cannot set AI Route when AI Flag (efwaCheckEnabled) is disabled");
                        }

                        Long currentRouteId = entity.getAiRoute() != null ? entity.getAiRoute().getId() : null;
                        if (!Objects.equals(currentRouteId, req.getRouteId())) {
                                prev.put(BenefitsMasterFieldKeys.ROUTE_ID, currentRouteId);
                                curr.put(BenefitsMasterFieldKeys.ROUTE_ID, req.getRouteId());

                                AiRoute route = aiRouteRepository.findById(req.getRouteId())
                                                .orElseThrow(() -> new AiConfigurationException(
                                                                "AI Route not found with id: " + req.getRouteId()));
                                entity.setAiRoute(route);
                        }
                }
        }
}
