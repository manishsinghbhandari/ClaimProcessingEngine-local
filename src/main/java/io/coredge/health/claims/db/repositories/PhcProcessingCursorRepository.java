package io.coredge.health.claims.db.repositories;

import io.coredge.health.claims.db.entity.PhcProcessingCursor;
import io.coredge.health.claims.db.entity.PhcProcessingCursor.ProcessingAction;
import io.coredge.health.claims.db.entity.PhcProcessingCursor.ProcessingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for PHC Processing Cursor
 */
@Repository
public interface PhcProcessingCursorRepository extends JpaRepository<PhcProcessingCursor, Long> {
    // Find cursor by year-month and action type (may return multiple if FAILED cursors exist)
    Optional<PhcProcessingCursor> findByYearMonthAndAction(String yearMonth, ProcessingAction action);

    // Find latest non-FAILED cursor by year-month and action type
    @Query("SELECT c FROM PhcProcessingCursor c WHERE c.yearMonth = :yearMonth " +
           "AND c.action = :action AND c.status != :failedStatus " +
           "ORDER BY c.creationDate DESC")
    Optional<PhcProcessingCursor> findLatestNonFailedByYearMonthAndAction(
            @Param("yearMonth") String yearMonth,
            @Param("action") ProcessingAction action,
            @Param("failedStatus") ProcessingStatus failedStatus);

    // Check if a cursor exists for year-month, action, and status
    boolean existsByYearMonthAndActionAndStatus(String yearMonth, ProcessingAction action, ProcessingStatus status);

    // Find cursors by multiple statuses, ordered by creation date
    List<PhcProcessingCursor> findByStatusInOrderByCreationDateAsc(List<ProcessingStatus> statuses);
    
    // Healer query - find all cursors by action and status
    List<PhcProcessingCursor> findByActionAndStatus(ProcessingAction action, ProcessingStatus status);
}
