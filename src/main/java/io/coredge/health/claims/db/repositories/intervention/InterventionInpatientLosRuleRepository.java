package io.coredge.health.claims.db.repositories.intervention;

import io.coredge.health.claims.db.entity.intervention.InterventionInpatientLosRule;
import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface InterventionInpatientLosRuleRepository extends JpaRepository<InterventionInpatientLosRule, Long> {
    @Query("SELECT r FROM InterventionInpatientLosRule r WHERE r.packageMasterId.id = :id AND r.level = :level AND :losDays >= r.duration AND r.ownership = :ownership")
    Optional<InterventionInpatientLosRule> findRuleByParams(@Param("id") Long id, @Param("level") String level, @Param("losDays") int losDays, @Param("ownership") String ownership);
}
