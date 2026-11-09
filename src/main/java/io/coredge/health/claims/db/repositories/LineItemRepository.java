package io.coredge.health.claims.db.repositories;

import io.coredge.health.claims.db.entity.ClaimLineItem;
import io.coredge.health.claims.db.entity.InsuranceClaim;
import io.coredge.health.claims.enums.ExtensionStateCodeEnum;
import io.coredge.health.claims.models.eclaims.ClaimPreAuthMappingProjection;
import io.coredge.health.claims.models.eclaims.ClaimProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public interface LineItemRepository extends JpaRepository<ClaimLineItem, Long> {

    Optional<List<ClaimLineItem>> findByClaimAndSequence(InsuranceClaim claim, int sequence);

    @Query("SELECT c FROM ClaimLineItem c WHERE c.claim.claimId IN :claimIds")
    Optional<List<ClaimLineItem>> findByClaimIds(@Param("claimIds") List<UUID> claimIds);

    @Query(value = "SELECT NEW io.coredge.health.claims.db.repositories.ClaimPreAuthProjection( cl.code as code, " +
            "cl.netValue as netValue, " +
            "cl.claim.claimId as ClaimId, " +
            "cl.servicedDate as serviceDate, " +
            "cl.servicedPeriodStart as servicedPeriodStart, " +
            "cl.servicedPeriodEnd as servicedPeriodEnd )" +
            "FROM ClaimLineItem cl WHERE " +
            "cl.claim.claimId IN :claimIds AND " +
            "cl.claim.patient.crId = :patientId AND " +
            "cl.claim.provider.fidCode = :providerId AND " +
            "cl.claim.status = :status " +
            "AND cl.claim.use = :use")
    List<ClaimPreAuthProjection> findByClaimIds(@Param("claimIds") List<UUID> claimIds,
                                                @Param("patientId") String patientId,
                                                @Param("providerId") String providerId,
                                                @Param("status") ExtensionStateCodeEnum status,
                                                @Param("use") String use);

    @Query("Select cli FROM ClaimLineItem cli where cli.claim.patient.crId = :crId AND (:shaCode IS NULL OR cli.code = :shaCode)")
    Optional<Page<ClaimLineItem>> findByClaim_Patient_CrIdAndShaCode(@Param("crId") String crId ,@Param("shaCode") String shaCode, Pageable pageable );


    Optional<List<ClaimLineItem>> findByClaim_UseAndClaim_Patient_CrIdAndClaim_StatusIn(
            String use,
            String claimPatientCrId,
            List<ExtensionStateCodeEnum> statuses
    );


    Optional<List<ClaimLineItem>> findByClaim_Patient_HouseholdNumber(String id);

    @Query("SELECT c FROM ClaimLineItem c WHERE c.claim.patient.householdNumber = :householdNumber AND (:claimNumber IS NULL OR c.claim.claimNumber != :claimNumber) AND (:shaCode IS NULL OR c.code = :shaCode )")
    Optional<Page<ClaimLineItem>>  findFilteredClaimLineItemsByHouseHold(@Param("householdNumber") String householdNumber,
                                                                         @Param("claimNumber") Long claimNumber,
                                                                         @Param("shaCode") String shaCode,
                                                                         Pageable pageable );

    @Query("SELECT c FROM ClaimLineItem c WHERE c.claim.claimId = :claimId")
    Optional<List<ClaimLineItem>> findByClaimId(@Param("claimId") UUID claimId);

    @Query("SELECT DISTINCT cli.claim.externalId FROM ClaimLineItem cli")
    List<String> findDistinctClaimIds();

    @Query("Select cli from ClaimLineItem cli where cli.claim.status = :status  and cli.claim.use = :type and cli.claim.isPHCClaim = :isPhc and cli.claim.lastModifiedDate between :startDate and :endDate")
    List<ClaimLineItem> findClaimsByStatus(@Param("status") ExtensionStateCodeEnum status ,
                                           @Param("type") String type,
                                           @Param("isPhc") boolean isPhc,
                                           @Param("startDate") LocalDateTime startDate ,
                                           @Param("endDate") LocalDateTime endDate);

    @Query("SELECT DISTINCT cli.code FROM ClaimLineItem cli WHERE cli.claim.externalId = :claimId")
    Optional<List<String>> findDistinctCodesByClaimId(@Param("claimId") String claimId);

    @Query("SELECT cli.claim.externalId, cli.code FROM ClaimLineItem cli WHERE cli.claim.externalId IN :claimIds")
    List<Object[]> findDistinctCodesByClaimIds(@Param("claimIds") List<String> claimIds);

    @Query("SELECT DISTINCT cli.code FROM ClaimLineItem cli WHERE cli.claim.externalId IN :claimIds")
    List<String> findOnlyDistinctCodesByClaimIds(@Param("claimIds") List<String> claimIds);

    @Query("SELECT DISTINCT cli.code, cli.approvedPrice FROM ClaimLineItem cli WHERE cli.claim.externalId IN :claimIds")
    List<Object[]> findDistinctCodesWithAmountByClaimIds(@Param("claimIds") List<String> claimIds);

    @Query("SELECT c FROM ClaimLineItem c WHERE c.preAuthId = :preAuthId AND c.claim.use = :use")
    List<ClaimLineItem> findByPreAuthIdAndUse(@Param("preAuthId") UUID preAuthId, @Param("use") String use);

    @Query("""
                SELECT DISTINCT cli.preAuthId
                FROM ClaimLineItem cli
                WHERE cli.preAuthId IN :preAuthIds
                  AND cli.claim.use = :use
                  AND cli.claim.status IN :statuses
            """)
    List<UUID> findConsumedPreAuthIds(
            @Param("preAuthIds") List<UUID> preAuthIds,
            @Param("use") String use,
            @Param("statuses") List<ExtensionStateCodeEnum> statuses
    );

    @Query("""
                SELECT DISTINCT cli.preAuthId
                FROM ClaimLineItem cli
                WHERE cli.claim.claimId = :claimId
                  AND cli.preAuthId IS NOT NULL
            """)
    List<UUID> findPreAuthIdsByClaimId(@Param("claimId") UUID claimId);

    @Query("""
                SELECT COALESCE(SUM(COALESCE(item.pmfAmount, 0)), 0)
                FROM ClaimLineItem item
                JOIN item.claim ic
                WHERE ic.claimId IN :claimIds
                  AND ic.use = :use
                  AND item.code LIKE :shaCodeLike
            """)
    BigDecimal sumPmfPriceForPreAuthIds(
            @Param("claimIds") Set<UUID> claimIds,
            @Param("use") String use,
            @Param("shaCodeLike") String shaCodeLike
    );

    /**
     * Find claim IDs by intervention code and date range for automated AI analysis.
     * Queries claims table with join to line items for better performance.
     * Filters by claims.date_from (primary date field) and uses IN operator for intervention codes.
     */
    @Query("""
        SELECT DISTINCT c.claimId, c.claimNumber
        FROM ClaimLineItem cli
        JOIN cli.claim c
        WHERE cli.code = :interventionCode
        AND c.dateFrom >= :startDate
        AND c.dateFrom <= :endDate
        AND c.claimNumber > :lastProcessedClaimId
        ORDER BY c.claimNumber ASC
        """)
    List<Object[]> findClaimIdsByCodeAndDateRange(
            @Param("interventionCode") String interventionCode,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("lastProcessedClaimId") Long lastProcessedClaimId,
            Pageable pageable
    );

    /**
     * Find claim IDs by intervention code and date range for automated AI analysis.
     * Queries claims table with join to line items for better performance.
     * Filters by claims.date_from (primary date field) and uses IN operator for intervention codes.
     */
    @Query("""
        SELECT Count(DISTINCT (c.claimId))
        FROM ClaimLineItem cli
        JOIN cli.claim c
        WHERE cli.code = :interventionCode
        AND c.dateFrom >= :startDate
        AND c.dateFrom <= :endDate
        AND c.claimNumber > :lastProcessedClaimId
        """)
    Page<Long> findClaimIdsCountByCodeAndDateRange(
            @Param("interventionCode") String interventionCode,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("lastProcessedClaimId") Long lastProcessedClaimId,
            Pageable pageable
    );

    /**
     * Find claim IDs by multiple intervention codes and date range for automated AI analysis.
     * Uses IN operator for intervention codes belonging to a package.
     */
    @Query("""
        SELECT DISTINCT c.claimId, c.claimNumber
        FROM ClaimLineItem cli
        JOIN cli.claim c
        WHERE cli.code IN :interventionCodes
        AND c.dateFrom >= :startDate
        AND c.dateFrom <= :endDate
        AND c.claimNumber > :lastProcessedClaimId
        ORDER BY c.claimNumber ASC
        """)
    List<Object[]> findClaimIdsByCodesAndDateRange(
            @Param("interventionCodes") List<String> interventionCodes,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("lastProcessedClaimId") Long lastProcessedClaimId,
            Pageable pageable
    );

    /**
     * Find claim IDs by intervention code, date range, and claim statuses for workflow mode.
     */
    @Query(value = """
        SELECT DISTINCT c.claim_id, c.claim_number
        FROM claim_items cli
        JOIN claims c ON cli.claim_id = c.claim_id
        WHERE cli.code = :interventionCode
        AND c.date_from >= :startDate
        AND c.date_from <= :endDate
        AND c.claim_number > :lastProcessedClaimId
        AND TRIM(c.status) IN :statuses
        ORDER BY c.claim_number ASC
        """, nativeQuery = true)
    List<Object[]> findClaimIdsByCodeAndDateRangeAndStatuses(
            @Param("interventionCode") String interventionCode,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("lastProcessedClaimId") Long lastProcessedClaimId,
            @Param("statuses") List<String> statuses,
            Pageable pageable
    );

    /**
     * Find claim IDs by multiple intervention codes, date range, and claim statuses for workflow mode.
     */
    @Query(value = """
        SELECT DISTINCT c.claim_id, c.claim_number
        FROM claim_items cli
        JOIN claims c ON cli.claim_id = c.claim_id
        WHERE cli.code IN :interventionCodes
        AND c.date_from >= :startDate
        AND c.date_from <= :endDate
        AND c.claim_number > :lastProcessedClaimId
        AND TRIM(c.status) IN :statuses
        ORDER BY c.claim_number ASC
        """, nativeQuery = true)
    List<Object[]> findClaimIdsByCodesAndDateRangeAndStatuses(
            @Param("interventionCodes") List<String> interventionCodes,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("lastProcessedClaimId") Long lastProcessedClaimId,
            @Param("statuses") List<String> statuses,
            Pageable pageable
    );

    @Query("""
        SELECT COUNT(DISTINCT (c.claimId))
        FROM ClaimLineItem cli
        JOIN cli.claim c
        WHERE cli.code IN :interventionCodes
        AND c.dateFrom >= :startDate
        AND c.dateFrom <= :endDate
        AND c.claimNumber > :lastProcessedClaimId
        """)
    Page<Long> findClaimIdsCountByCodesAndDateRange(
            @Param("interventionCodes") List<String> interventionCodes,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("lastProcessedClaimId") Long lastProcessedClaimId,
            Pageable pageable
    );

    @Query("SELECT COUNT(cli) FROM ClaimLineItem cli WHERE cli.claim.patient.crId = :crId AND cli.code = :code AND cli.servicedPeriodStart = :servicedPeriodStart AND cli.servicedPeriodEnd = :servicedPeriodEnd AND cli.claim.use='Claim' AND cli.claim.status != 'REJECTED' AND cli.sequence = :sequence")
    int countByMemberAndInterventionCodeAndServicedPeriodStartAndServicedPeriodEndAndSequence(String crId, String code, LocalDate servicedPeriodStart, LocalDate servicedPeriodEnd, Integer sequence);

    /**
     * Count same-day sessions for a patient with specified intervention codes.
     * Used for surveillance duplicate detection.
     */
    @Query("""
        SELECT COUNT(DISTINCT cli.id)
        FROM ClaimLineItem cli
        WHERE cli.claim.patient.crId = :crId
          AND cli.code IN :codes
          AND cli.servicedDate = :servicedDate
          AND cli.claim.status != io.coredge.health.claims.enums.ExtensionStateCodeEnum.REJECTED
          AND cli.claim.use = 'Claim'
        """)
    int countSameDaySessions(
            @Param("crId") String crId,
            @Param("codes") List<String> codes,
            @Param("servicedDate") LocalDate servicedDate);

    /**
     * Count weekly sessions for a patient with specified intervention codes.
     * Used for surveillance weekly session limit enforcement.
     */
    @Query("""
        SELECT COUNT(DISTINCT cli.id)
        FROM ClaimLineItem cli
        WHERE cli.claim.patient.crId = :crId
          AND cli.code IN :codes
          AND cli.servicedDate BETWEEN :weekStart AND :weekEnd
          AND cli.claim.status != io.coredge.health.claims.enums.ExtensionStateCodeEnum.REJECTED
          AND cli.claim.use = 'Claim'
        """)
    int countWeeklySessions(
            @Param("crId") String crId,
            @Param("codes") List<String> codes,
            @Param("weekStart") LocalDate weekStart,
            @Param("weekEnd") LocalDate weekEnd);
}
