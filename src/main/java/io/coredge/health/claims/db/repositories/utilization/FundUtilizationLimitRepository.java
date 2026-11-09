package io.coredge.health.claims.db.repositories.utilization;

import io.coredge.health.claims.db.entity.utilization.FundUtilizationLimit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FundUtilizationLimitRepository extends JpaRepository<FundUtilizationLimit, Long> {
}
