package io.coredge.health.claims.db.repositories;

import io.coredge.health.claims.db.entity.SurveillanceLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository interface for SurveillanceLog entity operations.
 * Provides methods to manage surveillance log entries for claims.
 */
@Repository
public interface SurveillanceLogRepository extends JpaRepository<SurveillanceLog, Long> {
    List<SurveillanceLog> findByClaimIdAndStatusOrderByCreationDateDesc(UUID claimId, String status);

    List<SurveillanceLog> findByClaimIdOrderByCreationDateDesc(UUID claimId);
}
