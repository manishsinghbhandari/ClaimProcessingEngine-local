package io.coredge.health.claims.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClaimAuditRepository extends JpaRepository<ClaimAudit, Long> {
    Optional<ClaimAudit> findByClaimId(UUID claimId);

    @Query("SELECT c FROM ClaimAudit c WHERE c.processed = false AND c.id > :lastId ORDER BY c.id ASC")
    List<ClaimAudit> fetchUnprocessedAfterId(Long lastId, Pageable pageable);

    Optional<ClaimAudit> findByClaimIdAndInterventionCode(UUID claimId, String interventionCode);

    @Query("SELECT COUNT(c) > 0 FROM ClaimAudit c WHERE c.claimId = :claimId AND c.processed = false")
    boolean hasUnprocessedAuditItems(UUID claimId);

    @Query("SELECT COUNT(c) > 0 FROM ClaimAudit c WHERE c.claimId = :claimId AND c.interventionCode = :interventionCode AND c.auditStatus = :status AND c.processRemarks = :processRemarks")
    boolean existsByClaimIdAndInterventionCodeAndStatusAndProcessRemarks(UUID claimId, String interventionCode, String status, String processRemarks);
}
