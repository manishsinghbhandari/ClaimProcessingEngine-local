package io.coredge.health.claims.db.repositories.benefits;

import io.coredge.health.claims.db.entity.benefits.BenefitsMasterLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BenefitsMasterLogRepository extends JpaRepository<BenefitsMasterLog, Long> {
}
