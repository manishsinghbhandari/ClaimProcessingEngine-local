package io.coredge.health.claims.helpers;

import io.coredge.health.claims.db.entity.AI.AIValidation;
import io.coredge.health.claims.db.entity.AI.AIValidationScore;
import io.coredge.health.claims.db.entity.BenefitsMaster;
import io.coredge.health.claims.db.entity.benefits.DocumentType;
import io.coredge.health.claims.db.repositories.AiNotes.AiValidationMappingRepository;
import io.coredge.health.claims.models.benefits.BenefitsMasterResponseDTO;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class AIValidationScoreMapper {

    private final ModelMapper modelMapper;
    private final AiValidationMappingRepository aiValidationMappingRepository;

    public BenefitsMasterResponseDTO convertToDTO(BenefitsMaster entity) {
        BenefitsMasterResponseDTO dto = modelMapper.map(entity, BenefitsMasterResponseDTO.class);

        // --- Document Types ---
        if (entity.getDocumentTypes() != null && !entity.getDocumentTypes().isEmpty()) {
            List<String> docNames = entity.getDocumentTypes().stream()
                    .filter(Objects::nonNull)
                    .map(DocumentType::getName)
                    .sorted()
                    .toList();
            dto.setDocumentTypes(docNames);
        }

        // --- AI Validation Scores ---
        List<AIValidation> aiValidations = aiValidationMappingRepository
                .findAiValidationsByBenefitMasterId(entity.getId());
        if (aiValidations != null && !aiValidations.isEmpty()) {
            List<String> validationDescriptions = aiValidations.stream()
                    .map(validation -> {
                        StringBuilder builder = new StringBuilder(validation.getName());
                        if (validation.getScores() != null && !validation.getScores().isEmpty()) {
                            builder.append(" - ");
                            String scoreDesc = validation.getScores().stream()
                                    .sorted(Comparator.comparing(AIValidationScore::getMinScore))
                                    .map(score -> String.format("(%d-%d)-%s",
                                            score.getMinScore().intValue(),
                                            score.getMaxScore().intValue(),
                                            score.getDecision()))
                                    .collect(Collectors.joining(" , "));
                            builder.append(scoreDesc);
                        }
                        return builder.toString();
                    })
                    .toList();
            dto.setAiValidationScore(validationDescriptions);
        }

        dto.setEfwaCheckEnabled(entity.getEfwaCheckEnabled());
        if (entity.getAiRoute() != null) {
            dto.setRouteId(entity.getAiRoute().getId());
            dto.setRouteName(entity.getAiRoute().getName());
        }

        return dto;
    }
}
