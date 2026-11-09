package io.coredge.health.claims.db.repositories.AiNotes;

import io.coredge.health.claims.db.entity.AI.AIValidation;
import io.coredge.health.claims.db.entity.AI.AiValidationMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@Repository
public interface AiValidationMappingRepository extends JpaRepository<AiValidationMapping, Long> {

    @Query("""
                SELECT DISTINCT(m.aiValidation)
                FROM AiValidationMapping m
                WHERE m.benefitMaster.id = :benefitMasterId
                AND m.aiValidation.isDeleted = false
            """)
    List<AIValidation> findAiValidationsByBenefitMasterId(@Param("benefitMasterId") Long benefitMasterId);

    @Query("""
    SELECT DISTINCT m.aiValidation
    FROM AiValidationMapping m
    WHERE m.benefitMaster.interventionCode IN :benefitMasterCode
      AND :providedDate BETWEEN m.aiValidation.effectiveDateFrom AND m.aiValidation.effectiveDateTo
      AND m.aiValidation.isDeleted = false
""")
    List<AIValidation> findAiValidationsByBenefitMasterCode(@Param("benefitMasterCode") List<String> benefitMasterCode ,  @Param("providedDate") LocalDate providedDate);


    @Modifying
    @Transactional
    @Query(value = """
    DELETE FROM ai_validation_mapping
    WHERE benefit_master_id = :benefitMasterId
    AND ai_validation_id IN (:validationIds)
    """, nativeQuery = true)
    void deleteAllByBenefitMasterIdAndValidationIds(
            @Param("benefitMasterId") Long benefitMasterId,
            @Param("validationIds") Set<Long> validationIds
    );

    @Query("""
                SELECT DISTINCT m.aiValidation
                FROM AiValidationMapping m
                WHERE m.benefitMaster.interventionCode IN :benefitMasterCode
                    AND m.use = :use
                    AND :providedDate BETWEEN m.aiValidation.effectiveDateFrom AND m.aiValidation.effectiveDateTo
                    AND m.aiValidation.isDeleted = false
            """)
    List<AIValidation> findAiValidationsByBenefitMasterCodeAndUse(
            @Param("benefitMasterCode") List<String> benefitMasterCode,
            @Param("providedDate") LocalDate providedDate,
            @Param("use") String use);

    @Modifying
    @Transactional
    @Query(value = """
    INSERT INTO ai_validation_mapping (benefit_master_id, ai_validation_id)
    SELECT UNNEST(:benefitIds), UNNEST(:validationIds)
    ON CONFLICT (benefit_master_id, ai_validation_id) DO NOTHING
    """, nativeQuery = true)
    void insertBatchIgnoreConflicts(
            @Param("benefitIds") Long[] benefitIds,
            @Param("validationIds") Long[] validationIds);

}
