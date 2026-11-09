package io.coredge.health.claims.db.repositories.AiNotes;

import io.coredge.health.claims.db.entity.ClaimAiReport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClaimAiReportRepository extends JpaRepository<ClaimAiReport, Long> {

    @Query("SELECT r FROM ClaimAiReport r WHERE r.claim.claimId = :claimId")
    Optional<ClaimAiReport> findByClaimId(@Param("claimId") UUID claimId);

    List<ClaimAiReport> findByStatus(String status);

    Page<ClaimAiReport> findByStatus(String status, Pageable pageable);

    @Query("SELECT r FROM ClaimAiReport r WHERE r.claim.claimId IN :claimIds")
    List<ClaimAiReport> findByClaimIdIn(@Param("claimIds") List<UUID> claimIds);

    long countByStatus(String status);

    @Query("SELECT CASE WHEN COUNT(r) > 0 THEN true ELSE false END FROM ClaimAiReport r WHERE r.claim.claimId = :claimId")
    boolean existsByClaimId(@Param("claimId") UUID claimId);

    @Query("SELECT r FROM ClaimAiReport r WHERE r.scores >= :threshold ORDER BY r.scores DESC")
    Page<ClaimAiReport> findByScoresGreaterThanEqual(@Param("threshold") java.math.BigDecimal threshold, Pageable pageable);

    @Query("SELECT r FROM ClaimAiReport r WHERE r.status = :status ORDER BY r.creationDate DESC")
    List<ClaimAiReport> findByStatusOrderByCreationDateDesc(@Param("status") String status);

    @Query("SELECT CASE WHEN COUNT(r) > 0 THEN true ELSE false END FROM ClaimAiReport r WHERE r.claim.claimId = :claimId AND r.trigger.id = :triggerId")
    boolean existsByClaimIdAndTriggerId(@Param("claimId") UUID claimId, @Param("triggerId") Long triggerId);
}
