package io.coredge.health.claims.db.repositories;

import io.coredge.health.claims.db.entity.UserBucket;
import io.coredge.health.claims.models.assignments.UserReporting;
import io.coredge.health.claims.models.assignments.UserReportingDTO;
import io.coredge.health.claims.models.assignments.UserTaskSummary;
import io.coredge.health.claims.enums.ClaimType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserBucketRepository extends JpaRepository<UserBucket, Long> {

    @Query("SELECT c FROM UserBucket c WHERE c.claim.claimId = :claimId AND c.status = :status")
    List<UserBucket> findByClaimId(@Param("claimId") UUID claimId ,@Param("status") ClaimType.BucketStatus status);

    Optional<List<UserBucket>> findAllByClaimTypeAndStatusAndAssignedDateBetween(ClaimType claimType, ClaimType.BucketStatus status, LocalDateTime startDate, LocalDateTime endDate);

    @Query("SELECT new io.coredge.health.claims.models.assignments.UserTaskSummary(u.id, u.username, " +
            "COUNT(CASE WHEN b.status = 'COMPLETED' THEN 1 END), " +
            "COUNT(CASE WHEN b.status = 'PENDING' THEN 1 END), " +
            "COUNT(b)) " +
            "FROM UserBucket b " +
            "JOIN b.user u " +
            "GROUP BY u.id, u.username")
    Optional<List<UserTaskSummary>> findUserTaskSummary();


    @Query("SELECT new io.coredge.health.claims.models.assignments.UserReportingDTO(u.id, u.username, " +
            "COUNT(CASE WHEN b.status = 'COMPLETED' THEN 1 END), " +
            "COUNT(CASE WHEN b.status = 'PENDING' THEN 1 END), " +
            "COUNT(b), " +
            "MAX(b.assignedDate), " +
            "MAX(b.actionDate)) " +
            "FROM UserBucket b " +
            "JOIN b.user u " +
            "WHERE b.assignedDate BETWEEN :startDate AND :endDate " +
            "AND b.claimType = :claimType " +
            "GROUP BY u.id, u.username")
    Optional<List<UserReportingDTO>> findUserReportingWithLatestDates(@Param("claimType") ClaimType claimType, @Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    Optional<List<UserBucket>> findAllByClaimTypeAndAssignedDateBetween(ClaimType claimType, LocalDateTime startDate, LocalDateTime endDate);

    long countAllByClaimTypeAndAssignedDateBetween(ClaimType claimType, LocalDateTime startDate, LocalDateTime endDate);

    long countByUserIdAndStatus(Long userId, ClaimType.BucketStatus status);

        @Modifying
        @Query("UPDATE UserBucket ub SET ub.status = 'RELEASED', " +
                "ub.lastModifiedDate = :now, " +
                "ub.lastModifiedBy = :user " +
                "WHERE ub.user.id = :userId AND ub.status = 'PENDING'")
        int updatePendingToReleased(@Param("userId") Long userId,
                                    @Param("now") LocalDateTime now,
                                    @Param("user") String user);


    @Query("""
    SELECT new io.coredge.health.claims.models.assignments.UserReporting(
        ub.user.id,
        ub.user.username,
        COUNT(ub),
        SUM(CASE WHEN ub.status = 'PENDING' THEN 1 ELSE 0 END),
        SUM(CASE WHEN ub.status = 'RELEASED' THEN 1 ELSE 0 END),
        SUM(CASE WHEN ub.status = 'COMPLETED' THEN 1 ELSE 0 END),
        MAX(CASE WHEN ub.status = 'COMPLETED' THEN ub.actionDate ELSE NULL END)
    )
    FROM UserBucket ub
    WHERE (:userName IS NULL OR :userName = ''
           OR LOWER(ub.user.username) LIKE LOWER(CONCAT('%', :userName, '%')))
    GROUP BY ub.user.id, ub.user.username
""")
    Page<UserReporting> getUserReporting(
            @Param("userName") String userName,
            Pageable pageable
    );


    @Query("""
    SELECT new io.coredge.health.claims.models.assignments.UserReporting(
        ub.user.id,
        ub.user.username,
        COUNT(ub),
        SUM(CASE WHEN ub.status = 'PENDING' THEN 1 ELSE 0 END),
        SUM(CASE WHEN ub.status = 'RELEASED' THEN 1 ELSE 0 END),
        SUM(CASE WHEN ub.status = 'COMPLETED' THEN 1 ELSE 0 END),
        MAX(CASE WHEN ub.status = 'COMPLETED' THEN ub.actionDate ELSE NULL END)
    )
    FROM UserBucket ub
    WHERE (:userName IS NULL OR :userName = '' 
           OR LOWER(ub.user.username) LIKE LOWER(CONCAT('%', :userName, '%')))
      AND (ub.creationDate BETWEEN :startDate AND :endDate)
    GROUP BY ub.user.id, ub.user.username
""")
    Page<UserReporting> getUserReporting(
            @Param("userName") String userName,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable
    );

    Page<UserBucket> findByClaim_ClaimIdOrderByCreationDateDesc(UUID claimId, Pageable pageable);

}
