package io.coredge.health.claims.db.repositories.AiNotes;

import io.coredge.health.claims.db.entity.ClaimAiTrigger;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface ClaimAiTriggerRepository extends JpaRepository<ClaimAiTrigger, Long> {

    List<ClaimAiTrigger> findByStatus(String status);

    @Query("SELECT t FROM ClaimAiTrigger t WHERE t.status = 'PENDING' ORDER BY t.creationDate ASC")
    List<ClaimAiTrigger> findPendingTriggers();

    @Query("SELECT t FROM ClaimAiTrigger t WHERE t.status = 'IN_PROGRESS' ORDER BY t.lastModifiedDate ASC")
    List<ClaimAiTrigger> findInProgressTriggers();

    List<ClaimAiTrigger> findByPackageCodeAndStatus(String packageCode, String status);

    @Query("""
        SELECT t FROM ClaimAiTrigger t 
        WHERE (:interventionCode IS NULL OR t.interventionCode = :interventionCode)
        AND t.startDate = :startDate
        AND t.endDate = :endDate
        AND (:status IS NULL OR t.status = :status)
        """)
    List<ClaimAiTrigger> findByInterventionCodeAndDateRangeAndStatus(
            @Param("interventionCode") String interventionCode,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("status") String status);

    @Query("""
        SELECT t FROM ClaimAiTrigger t 
        WHERE t.startDate <= :endDate AND t.endDate >= :startDate
        ORDER BY t.creationDate DESC
        """)
    List<ClaimAiTrigger> findByDateRange(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);
}
