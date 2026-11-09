package io.coredge.health.claims.service.AI;

import io.coredge.health.claims.db.entity.AI.AIValidation;
import io.coredge.health.claims.db.entity.AI.AIValidationScore;
import io.coredge.health.claims.db.repositories.AiNotes.AiValidationMappingRepository;
import io.coredge.health.claims.enums.Decision;
import io.coredge.health.claims.exception.app.DataValidationException;
import io.coredge.health.claims.models.AI.DecisionDetail;
import io.coredge.health.claims.models.AI.ScoreDetail;
import io.coredge.health.claims.models.claims.ClaimAINotesResponsePayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiValidationLogicService {

    private final AiValidationMappingRepository validationMappingRepository;

    public List<AIValidation> getAiValidationMapping(List<String> interventionCodes, LocalDate date, String use) {
        return validationMappingRepository.findAiValidationsByBenefitMasterCodeAndUse(interventionCodes, date, use);
    }

    public Decision calculateAiValidationScore(ClaimAINotesResponsePayload payload, List<AIValidation> validations,
            String use, List<String> remarks) {
        if (validations.isEmpty()) {
            return Decision.APPROVE;
        }

        validateCompulsoryScores(payload.getScores(), validations);

        Map<String, List<AIValidation>> scoreMap = validations.stream()
                .collect(Collectors.groupingBy(AIValidation::getValidationKey));
        log.info("--calculateAIValidationScore scoreMap {}", scoreMap);

        List<DecisionDetail> decisions = validateAIScore(scoreMap, payload, use);

        log.info("--calculateAIValidationScore decisions {}", decisions);

        Decision decision = determineOverallDecision(decisions);

        log.info("--calculateAIValidationScore decision {}", decision);
        if (decision != null && decision != Decision.APPROVE && remarks != null) {
            log.info("AI Decision [use: {}]: {}", use, decision);
            remarks.addAll(determineOverallRemarks(decisions, decision));
        }
        return decision;
    }

    private List<DecisionDetail> validateAIScore(Map<String, List<AIValidation>> scoreMap,
            ClaimAINotesResponsePayload payload, String use) {
        return scoreMap.entrySet().stream().map(entry -> determineDecision(entry.getKey(),
                payload.getScores().get(entry.getKey()), entry.getValue(), use)).toList();
    }

    private DecisionDetail determineDecision(String key, ScoreDetail scoreDetail, List<AIValidation> validations, String use) {
        if (scoreDetail == null) {
            throw new DataValidationException("No Valid Score found for key: " + key + " in AI response.");
        }
        return validations.stream()
                .map(validation -> determineDecision(key, scoreDetail, validation, use))
                .reduce((a, b) -> {
                    assert b != null;
                    return a.getDecision().getPriority() < b.getDecision().getPriority() ? a : b;
                }).orElse(null);
    }


    private DecisionDetail determineDecision(String key, ScoreDetail scoreDetail, AIValidation validationScore, String use) {
        log.debug("Validating score for key: {}, score: {} against decision from Table", key, scoreDetail);

        if (validationScore == null) {
            throw new DataValidationException(
                    "No Validation Score found for key: " + key + " in Database for AI score validation.");
        }
        Double score = extractScore(scoreDetail.getScore());
        for (AIValidationScore scoreBand : validationScore.getScores()) {

            if (use != null && !use.equals(scoreBand.getUse())) {
                log.info("-----score band not for current use {}", scoreBand);
                continue;
            }

            if (score.compareTo(scoreBand.getMinScore()) >= 0 &&
                    score.compareTo(scoreBand.getMaxScore()) <= 0) {

                List<String> remarks = scoreDetail.getRemarks() != null && !scoreDetail.getRemarks().isEmpty()
                        ? scoreDetail.getRemarks()
                        : List.of(scoreBand.getMessage());
                return new DecisionDetail(scoreBand.getDecision(), remarks);
            }
        }
        return null;
    }

    private Decision determineOverallDecision(Collection<DecisionDetail> decisions) {
        return decisions.stream()
                .map(DecisionDetail::getDecision)
                .min(Comparator.comparingInt(Decision::getPriority))
                .orElseThrow(() -> new DataValidationException("No decisions could be determined from AI scores."));
    }

    private List<String> determineOverallRemarks(Collection<DecisionDetail> decisions, Decision decision) {
        return decisions.stream()
                .filter(decisionDetail -> decision.equals(decisionDetail.getDecision()))
                .map(DecisionDetail::getRemarks)
                .flatMap(Collection::stream)
                .toList();
    }

    private double extractScore(String scoreStr) {
        return Double.parseDouble(scoreStr.replace("%", ""));
    }

    private void validateCompulsoryScores(Map<String, ScoreDetail> scores, List<AIValidation> validations) {
        validations.stream()
                .filter(validation -> !scores.containsKey(validation.getValidationKey()))
                .findAny()
                .ifPresent(validation -> {
                    // Logic to check if validation is compulsory could be added here if needed
                    throw new DataValidationException("No valid Score found for :" + validation.getValidationKey());
                });
    }
}
