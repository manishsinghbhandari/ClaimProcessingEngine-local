package io.coredge.health.claims.db.repositories;

import io.coredge.health.claims.db.entity.PredeterminationLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PredeterminationLogRepository extends JpaRepository<PredeterminationLog, Long> {

    @Query(
            value = """
                    SELECT *
                    FROM (
                        SELECT *, ROW_NUMBER() OVER (PARTITION BY claim_id ORDER BY created_date DESC) AS rn
                        FROM predetermination_log
                        WHERE claim_id IS NOT NULL AND TRIM(claim_id) <> ''
                    ) sub
                    WHERE rn = 1
                      AND (:claimId IS NULL OR claim_id = :claimId)
                      AND (:status IS NULL OR status = :status)
                    ORDER BY created_date DESC
                    """,
            countQuery = """
                    SELECT COUNT(*) FROM (
                        SELECT 1
                        FROM predetermination_log
                        WHERE claim_id IS NOT NULL AND TRIM(claim_id) <> ''
                          AND (:claimId IS NULL OR claim_id = :claimId)
                          AND (:status IS NULL OR status = :status)
                        GROUP BY claim_id
                    ) sub
                    """,
            nativeQuery = true)
    Page<PredeterminationLog> fetchPredeterminationDetails(
            @Param("claimId") String claimId,
            @Param("status") Boolean status,
            Pageable pageable
    );

    @Query(value = "select * from predetermination_log where claim_id = :claimId" , nativeQuery = true)
    Optional<Page<PredeterminationLog>> findByClaimId(@Param("claimId") String claimId , Pageable pageable );
}
