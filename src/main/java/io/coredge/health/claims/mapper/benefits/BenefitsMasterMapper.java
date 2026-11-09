package io.coredge.health.claims.mapper.benefits;


import io.coredge.health.claims.db.entity.AI.AIValidation;
import io.coredge.health.claims.db.entity.AI.AIValidationScore;
import io.coredge.health.claims.db.entity.BenefitsMaster;
import io.coredge.health.claims.db.entity.benefits.DocumentType;
import io.coredge.health.claims.helpers.AIValidationScoreMapper;
import io.coredge.health.claims.models.benefits.BenefitsMasterRequestDTO;
import io.coredge.health.claims.models.benefits.BenefitsMasterResponseDTO;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Mapper for converting between BenefitsMaster entity and DTOs.
 */
@Component
public class BenefitsMasterMapper {

    private final ModelMapper modelMapper;
    private final AIValidationScoreMapper AiValidationScoreMapper;

    // Constructor injection of ModelMapper bean
    public BenefitsMasterMapper(ModelMapper modelMapper, AIValidationScoreMapper aiValidationScoreMapper) {
        this.modelMapper = modelMapper;
        this.AiValidationScoreMapper = aiValidationScoreMapper;
    }

    /**
     * Converts BenefitsMaster entity to BenefitsMasterResponseDTO
     *
     * @param entity the BenefitsMaster entity
     * @return mapped BenefitsMasterResponseDTO
     */
    public BenefitsMasterResponseDTO convertToDTO(BenefitsMaster entity) {
        return AiValidationScoreMapper.convertToDTO(entity);
    }


    /**
     * Converts BenefitsMasterRequestDTO to BenefitsMaster entity
     * Useful for creating/updating entities from DTO
     *
     * @param dto the BenefitsMasterRequestDTO
     * @return mapped BenefitsMaster entity
     */
    public BenefitsMaster convertToEntity(BenefitsMasterRequestDTO dto) {
        return modelMapper.map(dto, BenefitsMaster.class);
    }
}

