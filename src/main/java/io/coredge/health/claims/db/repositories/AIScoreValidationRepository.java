package io.coredge.health.claims.db.repositories;

import io.coredge.health.claims.db.entity.AIValidationScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AIScoreValidationRepository extends JpaRepository<AIValidationScore, Long> {

}
