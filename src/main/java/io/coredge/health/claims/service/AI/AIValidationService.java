package io.coredge.health.claims.service.AI;

import io.coredge.health.claims.constants.AI.AIValidationFields;
import io.coredge.health.claims.context.HealthIdContextHolder;
import io.coredge.health.claims.db.entity.AI.AIValidation;
import io.coredge.health.claims.db.entity.AI.AIValidationLog;
import io.coredge.health.claims.db.entity.AI.AIValidationScore;
import io.coredge.health.claims.db.repositories.AiNotes.AIValidationLogRepository;
import io.coredge.health.claims.db.repositories.AiNotes.AIValidationRepository;

import io.coredge.health.claims.enums.Decision;
import io.coredge.health.claims.exception.ai.DuplicateRecordException;
import io.coredge.health.claims.exception.ai.EmptyScoresException;
import io.coredge.health.claims.exception.ai.InvalidScoreSequenceException;
import io.coredge.health.claims.exception.app.DataNotFoundException;
import io.coredge.health.claims.exception.benefits.NoChangesDetectedException;
import io.coredge.health.claims.models.AI.*;
import io.coredge.health.claims.models.benefits.BenefitsMasterDropDownResponse;
import io.coredge.health.claims.service.benefits.BenefitsMasterFieldRegistry;
import io.coredge.health.claims.utils.AI.JsonUtil;
import jakarta.persistence.EntityNotFoundException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
@Slf4j
public class AIValidationService {

    private static final double SERIES_MIN = 0;

    private final AIValidationRepository aiValidationRepository;

    private final AIValidationLogRepository aiValidationLogRepository;

    private PagedResourcesAssembler<AIValidationResponse> pagedResourcesAssembler;

    private final BenefitsMasterFieldRegistry benefitsMasterFieldRegistry;


    public AIValidationResponse createAIValidation(AIValidationRequest request) {


        validateNoDuplicateScores(request, null);

        validateScoresRange(request.getAiValidationScore()); // validate first

        // Map request to embeddable scores
        List<AIValidationScore> scores = mapScores(request.getAiValidationScore());


        AIValidation aiValidation = new AIValidation();
        aiValidation.setName(request.getName());
        aiValidation.setValidationKey(request.getValidationKey());
        aiValidation.setVersion(1); // default version
        aiValidation.setIsDeleted(false);
        aiValidation.setScores(new HashSet<>(scores));
        aiValidation.setCreatedBy(HealthIdContextHolder.userId());
        aiValidation.setIsDeleted(false);
        aiValidation.setCreationDate(LocalDateTime.now());
        aiValidation.setEffectiveDateFrom(request.getEffectiveDateFrom());
        aiValidation.setEffectiveDateTo(request.getEffectiveDateTo());

        AIValidation aiValidationReturnEntity = aiValidationRepository.save(aiValidation);
        // convert and return response DTO
        return mapToResponse(aiValidationReturnEntity);
    }

    @Transactional
    public AIValidationResponse updateAIValidation(Long id, AIValidationRequest request) {
        AIValidation existing = aiValidationRepository.findByIdAndIsDeletedIsFalse(id)
                .orElseThrow(() -> new EntityNotFoundException("AIValidation with ID " + id + " not found"));
      // Validate scores and ranges
       validateNoDuplicateScores(request, id);
       validateScoresRange(request.getAiValidationScore());
     // Map new scores
        // Maps to track changed fields
        Map<String, Object> prev = new LinkedHashMap<>();
        Map<String, Object> curr = new LinkedHashMap<>();

        // Update only changed fields and record previous/current
        benefitsMasterFieldRegistry.updateValueIfChanged(AIValidationFields.NAME, existing.getName(), request.getName(), existing::setName, prev, curr);
        benefitsMasterFieldRegistry.updateValueIfChanged(AIValidationFields.VALIDATION_KEY, existing.getValidationKey(), request.getValidationKey(), existing::setValidationKey, prev, curr);
        benefitsMasterFieldRegistry.updateValueIfChanged(AIValidationFields.SCORES, existing.getScores(), new HashSet<>(mapScores(request.getAiValidationScore())), existing::setScores, prev, curr);
        benefitsMasterFieldRegistry.updateValueIfChanged(AIValidationFields.EFFECTIVE_DATE_FROM, existing.getEffectiveDateFrom(), request.getEffectiveDateFrom(), existing::setEffectiveDateFrom, prev, curr);
        benefitsMasterFieldRegistry.updateValueIfChanged(AIValidationFields.EFFECTIVE_DATE_TO, existing.getEffectiveDateTo(), request.getEffectiveDateTo(), existing::setEffectiveDateTo, prev, curr);

        // Step 4. If no fields were changed (curr map is empty), throw an exception indicating that there are no updatable fields.
        if (curr.isEmpty()) {
            log.warn("No updatable fields detected for AIValidation with id={}", id);
            throw new NoChangesDetectedException("There is no any changes in the request to update");
        }
        // Always update lastModifiedDate/by and version
        existing.setLastModifiedBy(HealthIdContextHolder.userId());
        existing.setLastModifiedDate(LocalDateTime.now());
        existing.setVersion(existing.getVersion() + 1);

        AIValidation updated = aiValidationRepository.save(existing);

        // Save log with only changed fields
        AIValidationLog log = new AIValidationLog();
        log.setAiValidationId(updated.getId());
        log.setPreviousRecord(JsonUtil.convertToJson(prev));
        log.setCurrentRecord(JsonUtil.convertToJson(curr));
        log.setCreatedBy(HealthIdContextHolder.userId());
        log.setCreationDate(LocalDateTime.now());
        aiValidationLogRepository.save(log);

        return mapToResponse(updated);
    }


    @Transactional(readOnly = true)
    public CollectionModel<EntityModel<AIValidationResponse>> getAllAIValidations(Pageable pageable) {
        Page<AIValidation> aiValidations = aiValidationRepository.findByIsDeletedIsFalse(pageable);
        Page<AIValidationResponse> dtoPage = aiValidations.map(this::mapToResponse);
        return pagedResourcesAssembler.toModel(dtoPage);
    }


    @Transactional(readOnly = true)
    public AIValidationResponse getAIValidation(Long id) {
        return aiValidationRepository.findByIdAndIsDeletedIsFalse(id)
                .map(this::mapToResponse)
                .orElseThrow(DataNotFoundException::new);
    }


    public List<BenefitsMasterDropDownResponse.KeyValueResponse> getAIValidationDropdown() {
        List<AIValidation> validations = aiValidationRepository.findByIsDeletedIsFalse();

        return validations.stream()
                .map(validation -> {
                    StringBuilder keyBuilder = new StringBuilder(validation.getName());

                    if (validation.getScores() != null && !validation.getScores().isEmpty()) {
                        keyBuilder.append(" - ");
                        String scoreDesc = validation.getScores().stream()
                                .sorted(Comparator.comparing(AIValidationScore::getMinScore)) // optional
                                .map(score -> "(" + score.getMinScore().intValue() + "-" + score.getMaxScore().intValue() + ")-" + score.getDecision())
                                .collect(Collectors.joining(" , "));
                        keyBuilder.append(scoreDesc);
                    }

                    return new BenefitsMasterDropDownResponse.KeyValueResponse(validation.getId(), keyBuilder.toString());
                })
                .toList();
    }


    public boolean deleteAIValidation(Long id) {
        AIValidation aiValidation = aiValidationRepository.findById(id)
                .orElse(null);

        if (aiValidation == null || aiValidation.getIsDeleted()) {
            return false; // record not found or already deleted
        }

        aiValidation.setIsDeleted(true);
        aiValidationRepository.save(aiValidation);
        return true;
    }

    private List<AIValidationScore> mapScores(List<AIValidationScoreRequest> scoreRequests) {
        List<AIValidationScore> scores = new ArrayList<>();
        if (scoreRequests != null) {
            scoreRequests.forEach(s -> {
                AIValidationScore score = new AIValidationScore();
                score.setDecision(s.getDecision());
                score.setMinScore(s.getMin());
                score.setMaxScore(s.getMax());
                score.setMessage(s.getMessage());
                score.setUse(s.getClaimType());
                scores.add(score);
            });
        }
        return scores;
    }

    public AIValidationResponse mapToResponse(AIValidation entity) {
        AIValidationResponse response = new AIValidationResponse();
        response.setId(entity.getId());
        response.setName(entity.getName());
        response.setValidationKey(entity.getValidationKey());

        response.setEffectiveDateFrom(entity.getEffectiveDateFrom());
        response.setEffectiveDateTo(entity.getEffectiveDateTo());

        List<AIValidationScoreResponse> scores = entity.getScores().stream().map(score -> {
            AIValidationScoreResponse aIValidationScoreResponse = new AIValidationScoreResponse();
            aIValidationScoreResponse.setMinScore(score.getMinScore());
            aIValidationScoreResponse.setMaxScore(score.getMaxScore());
            aIValidationScoreResponse.setMessage(score.getMessage());
            aIValidationScoreResponse.setDecision(score.getDecision());
            aIValidationScoreResponse.setClaimType(score.getUse());
            return aIValidationScoreResponse;
        }).toList();

        response.setAiValidationScore(scores);

        return response;
    }

    public void validateScoresRange(List<AIValidationScoreRequest> scores) {
        if (scores == null || scores.isEmpty()) {
            throw new EmptyScoresException("Scores list cannot be null  or empty");
        }

        // Sort by minScore (ascending)
        scores.sort(Comparator.comparingDouble(AIValidationScoreRequest::getMin));

        // Validate first and last boundaries
        double prevMax = SERIES_MIN - 1 ;

        for (AIValidationScoreRequest curr : scores) {
            double currMin = curr.getMin();

            if (currMin != prevMax + 1) {
                throw new InvalidScoreSequenceException(currMin, prevMax);
            }

            prevMax = curr.getMax();
        }

        // Check if any category covers the full range (0–100)
        boolean hasFullRange = scores.stream()
                .anyMatch(s -> s.getMin() == 0 && s.getMax() == 100);

        // If full range exists, then all must be full-range
        if (hasFullRange && scores.size() > 1) {
            throw new InvalidScoreSequenceException(
                    "If one category covers the full range (0–100), no other partial ranges are allowed."
            );
        }


        // If partial ranges are used, ensure the full 0–100 range is completely covered without overlap
        double minStart = scores.getFirst().getMin();
        double maxEnd = scores.getLast().getMax();

        if (minStart != 0 || maxEnd != 100) {
            throw new InvalidScoreSequenceException(
                    "Score ranges must start from 0 and end at 100."
            );
        }
    }

    /**
     * Validates that the given list of scores does not already exist in the DB
     * for the same validation key and name.
     *
     * @param request the incoming AIValidationRequest
     */

    private void validateNoDuplicateScores(AIValidationRequest request, Long excludeId) {
        List<AIValidation> validations = aiValidationRepository.findAllByValidationKey(request.getValidationKey());

        validations.stream()
                .filter(validation -> !validation.getId().equals(excludeId))
                .filter(validation -> !validation.getIsDeleted())
                .filter(validation -> checkDuplicateScores(validation.getScores(), request.getAiValidationScore()))
                .findAny()
                .ifPresent(validation -> {
                    throw new DuplicateRecordException(
                            "Duplicate record exists with same Validation Key " +
                                    validation.getValidationKey() + " and same Scores Range");
                });
    }

    private static boolean checkDuplicateScores(Set<AIValidationScore> scores, List<AIValidationScoreRequest> requests) {
        if (scores == null || scores.isEmpty() || requests == null || requests.isEmpty()) {
            return false;
        }

        Map<Decision, AIValidationScore> scoreMap =
                scores.stream().collect(Collectors.toMap(AIValidationScore::getDecision, Function.identity()));

        Map<Decision, AIValidationScoreRequest> requestMap =
                requests.stream().collect(Collectors.toMap(AIValidationScoreRequest::getDecision, Function.identity()));

        return checkDuplicateScores(scoreMap, requestMap);
    }

    private static boolean checkDuplicateScores(Map<Decision, AIValidationScore> scores,
                                                Map<Decision, AIValidationScoreRequest> requests) {

        return scores.entrySet().stream().anyMatch(entry -> {
            if (!requests.containsKey(entry.getKey())) {
                return false;
            }
            AIValidationScoreRequest requestScore = requests.get(entry.getKey());
            AIValidationScore storedScore = entry.getValue();

            return Double.compare(requestScore.getMin(), storedScore.getMinScore()) == 0 &&
                    Double.compare(requestScore.getMax(), storedScore.getMaxScore()) == 0;
        });
    }
}
