package io.coredge.health.claims.db.repositories;

import io.coredge.health.claims.db.entity.BenefitsMaster;
import io.coredge.health.claims.db.entity.Intervention;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InterventionsRepository extends JpaRepository<Intervention, Long> {

    Optional<List<BenefitsMaster>> findByInterventionCode(String interventionCode);

    Optional<List<BenefitsMaster>> findByInterventionNameContainingIgnoreCase(String interventionName);

    Optional<List<BenefitsMaster>> findByInterventionCodeAndInterventionNameContainingIgnoreCase(String code, String interventionName);

    Optional<List<BenefitsMaster>> findByShaCategory(String shaCategory);

    Optional<List<BenefitsMaster>> findBySubCategoryBenefitsPackage(String subCategoryBenefitsPackage);

    Optional<List<BenefitsMaster>> findByShaCategoryContainingIgnoreCase(String shaCategory);

    Optional<List<BenefitsMaster>> findByCategoryCode(String shaCode);

    Optional<List<BenefitsMaster>> findByInterventionCodeIn(List<String> interventionCodes);

    Optional<List<BenefitsMaster>> findByInterventionCodeNotIn(List<String> interventionCodes);

    boolean existsByInterventionCode(String interventionCode);

}
