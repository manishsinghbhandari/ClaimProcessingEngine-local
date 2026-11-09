package io.coredge.health.claims.db.repositories;

import io.coredge.health.claims.db.entity.projection.ClaimStatusProjection;
import io.coredge.health.claims.db.repositories.utilization.ClaimUtilizationProjection;
import io.coredge.health.claims.db.repositories.utilization.PreAuthUtilizationProjection;
import io.coredge.health.claims.enums.ClaimType;
import io.coredge.health.claims.models.eclaims.ClaimPreAuthMappingProjection;
import io.coredge.health.claims.models.eclaims.ClaimProjection;
import io.coredge.health.claims.models.eclaims.PreAuthProjection;
import io.coredge.health.claims.service.provider.InsuranceClaimProjection;
import io.coredge.health.claims.db.entity.InsuranceClaim;
import io.coredge.health.claims.db.entity.Provider;
import io.coredge.health.claims.enums.ExtensionStateCodeEnum;
import io.coredge.health.claims.models.phccalculation.ClaimsAfyanguProjection;
import io.coredge.health.claims.models.phccalculation.PHCClaimsDbDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InsuranceClaimsRepository extends JpaRepository<InsuranceClaim, Long> {

    Optional<List<InsuranceClaim>> findByClaimId(UUID claimId);

    Optional<List<InsuranceClaim>> findByClaimIdAndUse(UUID claimId, String use);

    Optional<List<InsuranceClaim>> findByExternalId(String externalClaimId);

    Optional<Boolean> existsByExternalId(String externalClaimId);

    Optional<Boolean> existsByClaimId(UUID claimId);

    Optional<List<InsuranceClaim>> findByPreAuthId(UUID claimId);

    Optional<InsuranceClaim> findByPreAuthIdAndUse(UUID claimId, String use);

    List<InsuranceClaim> findAllByExternalIdIn(List<String> externalClaimId);

    Optional<List<InsuranceClaim>> findByClaimIdIn(List<UUID> claimIds);

    @Query("SELECT ic FROM InsuranceClaim ic " +
            "WHERE ic.claimId = :claimId " +
            "AND ic.use = :use")
    Optional<InsuranceClaim> getClaimIdAndUse(@Param("claimId") UUID claimId, @Param("use") String use);

    @Query("SELECT DISTINCT ic FROM ClaimLineItem ci "
            + "JOIN ci.claim ic "
            + "JOIN ic.provider p "
            + "JOIN ic.patient pt "
            + "WHERE (:claimNumber IS NULL OR ic.claimNumber = :claimNumber)"
            + "AND (:shaPackageCode IS NULL OR ci.code ILIKE %:shaPackageCode%)"
            + "AND (:claimId IS NULL OR ic.claimId = :claimId)"
            + "AND (:status IS NULL OR ic.status = :status)"
            + "AND (:accessCode IS NULL OR ic.accessCode = :accessCode)"
            + "AND (ic.use = :use)"
            + "AND (ic.isPHCClaim = :isPHCClaim)"
            + "AND (:auditFlag IS NULL OR ic.auditFlag = :auditFlag)"
            + "AND (:providerName IS NULL OR p.name ILIKE %:providerName%)"
            + "AND (:patientName IS NULL OR pt.name ILIKE %:patientName%)"
            + "AND (:crId IS NULL OR pt.crId = :crId)"
            + "AND (:shaNumber IS NULL OR pt.shaNumber = :shaNumber)"
            + "AND (:nationalId IS NULL OR pt.nationalId = :nationalId)"
            + "AND (:county IS NULL OR p.county = :county)"
            + "AND (CAST(:startDate AS timestamp) IS NULL OR ic.creationDate >= :startDate)"
            + "AND (CAST(:endDate AS timestamp) IS NULL OR ic.creationDate <= :endDate)"
            + "AND (NOT (:status IS NULL AND :auditFlag = TRUE AND ic.status = 'MISSING_DOCUMENTS'))"
            + "AND (:hasCounties = FALSE OR p.county IN :counties)"
    )
    Optional<Page<InsuranceClaim>> findByOptionalParams(
            @Param("claimId") UUID claimId,
            @Param("isPHCClaim") Boolean isPHCClaim,
            @Param("providerName") String providerName,
            @Param("accessCode") String accessCode,
            @Param("patientName") String patientName,
            @Param("crId") String crId,
            @Param("nationalId") String nationalId,
            @Param("shaNumber") String shaNumber,
            @Param("claimNumber") Long claimNumber,
            @Param("status") ExtensionStateCodeEnum status,
            @Param("use") String use,
            @Param("auditFlag") Boolean auditFlag,
            @Param("counties") List<String> counties,
            @Param("hasCounties") boolean hasCounties,
            @Param("shaPackageCode") String shaPackageCode,
            @Param("county") String county,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable);

    @Query("SELECT ic FROM InsuranceClaim ic "
            + "JOIN ic.provider p "
            + "JOIN ic.patient pt "
            + "WHERE (:claimNumber IS NULL OR ic.claimNumber = :claimNumber)"
            + "AND (:claimId IS NULL OR ic.claimId = :claimId)"
            + "AND (:status IS NULL OR ic.status = :status)"
            + "AND (:accessCode IS NULL OR ic.accessCode = :accessCode)"
            + "AND (ic.use = :use)"
            + "AND (ic.isPHCClaim = :isPHCClaim)"
            + "AND (:auditFlag IS NULL OR ic.auditFlag = :auditFlag)"
            + "AND (:providerName IS NULL OR p.name ILIKE %:providerName%)"
            + "AND (:patientName IS NULL OR pt.name ILIKE %:patientName%)"
            + "AND (:crId IS NULL OR pt.crId = :crId)"
            + "AND (:shaNumber IS NULL OR pt.shaNumber = :shaNumber)"
            + "AND (:nationalId IS NULL OR pt.nationalId = :nationalId)"
            + "AND (CAST(:startDate AS timestamp) IS NULL OR ic.creationDate >= :startDate)"
            + "AND (CAST(:endDate AS timestamp) IS NULL OR ic.creationDate <= :endDate)"
            + "AND (:status IS NOT NULL OR :auditFlag IS NULL OR :auditFlag = FALSE OR ic.status != 'MISSING_DOCUMENTS')"
            + "AND (:hasCounties = FALSE OR p.county IN :counties)"
    )
    Optional<Page<InsuranceClaim>> findPreAuthByOptionalParams(@Param("claimId") UUID claimId, @Param("isPHCClaim") Boolean isPHCClaim,
                                                               @Param("providerName") String providerName, @Param("accessCode") String accessCode,
                                                               @Param("patientName") String patientName, @Param("crId") String crId,
                                                               @Param("nationalId") String nationalId, @Param("shaNumber") String shaNumber,
                                                               @Param("claimNumber") Long claimNumber,
                                                               @Param("status") ExtensionStateCodeEnum status, @Param("use") String use,
                                                               @Param("auditFlag") Boolean auditFlag,
                                                               @Param("counties") List<String> counties,
                                                               @Param("hasCounties") boolean hasCounties,
                                                               @Param("startDate") LocalDateTime startDate,
                                                               @Param("endDate") LocalDateTime endDate, Pageable pageable);

    /**
     * Fetches insurance claims based on status, use type, and whether it's a PHC claim,
     * with an optional date range filter on the creation date.
     *
     * @param status     The claim status (e.g., PENDING, APPROVED, REJECTED).
     * @param use        The type of claim usage.
     * @param isPHCClaim Flag indicating whether the claim is a PHC (Primary Health Care) claim.
     * @param startDate  Optional start date for filtering claims created on or after this date.
     * @param endDate    Optional end date for filtering claims created on or before this date.
     * @param pageable   Pagination parameters.
     * @return A paginated list of insurance claims that match the filters.
     */
    @Query("""
                SELECT ic FROM InsuranceClaim ic WHERE\s
                            (:status IS NULL OR ic.status = :status)\s
                            AND ic.use = :use AND ic.isPHCClaim = :isPHCClaim\s
                            AND (:auditFlag IS NULL OR ic.auditFlag = :auditFlag)\s
                            AND (CAST(:startDate AS timestamp) IS NULL OR ic.creationDate >= :startDate)\s
                            AND (CAST(:endDate AS timestamp) IS NULL OR ic.creationDate <= :endDate)\s
                            AND (NOT (:status IS NULL AND :auditFlag = TRUE AND ic.status = 'MISSING_DOCUMENTS'))\s
                            AND (:hasCounties = FALSE OR ic.provider.county IN :counties)
            """)
    Optional<Page<InsuranceClaim>> getClaimsByStatusAndUseAndIsPHCClaimAndCreationDateBetween(
            @Param("status") ExtensionStateCodeEnum status,
            @Param("use") String use,
            @Param("isPHCClaim") boolean isPHCClaim,
            @Param("auditFlag") Boolean auditFlag,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("counties") List<String> counties,
            @Param("hasCounties") boolean hasCounties,
            Pageable pageable);

    Optional<List<InsuranceClaim>> findByExternalIdAndStatus(String claimId, @Param("status") ExtensionStateCodeEnum status);

    Optional<Page<InsuranceClaim>> findByClaimId(UUID claimId, Pageable pageable);


    @Query("SELECT count(DISTINCT ic) FROM InsuranceClaim ic "
            + "JOIN ic.patient pt "
            + "LEFT JOIN ClaimLineItem item ON ic.claimId = item.claim.claimId "
            + "WHERE (pt.householdNumber = :householdNumber) "
            + "AND (ic.status in :status) "
            + "AND (item.code in :shaCode) "
            + "AND (ic.use = :use) "
            + "AND (:excludeClaimId IS NULL OR ic.claimId != :excludeClaimId) "
            + "AND (CAST(:policyStartDate AS timestamp) IS NULL OR item.servicedDate >= :policyStartDate) "
            + "AND (CAST(:policyEndDate AS timestamp) IS NULL OR item.servicedDate <= :policyEndDate)"
    )
    int countActiveClaimsByHouseholdAndCode(String householdNumber, List<ExtensionStateCodeEnum> status, List<String> shaCode, String use,
                                            @Param("excludeClaimId") UUID excludeClaimId,
                                            @Param("policyStartDate") LocalDate policyStartDate,
                                            @Param("policyEndDate") LocalDate policyEndDate);


    @Query("SELECT count(DISTINCT ic) FROM InsuranceClaim ic "
            + "JOIN ic.patient pt "
            + "LEFT JOIN ClaimLineItem item ON ic.claimId = item.claim.claimId "
            + "WHERE (pt.crId = :crId) "
            + "AND (ic.status in :status) "
            + "AND (item.code in :shaCode) "
            + "AND (ic.use = :use) "
            + "AND (:excludeClaimId IS NULL OR ic.claimId != :excludeClaimId) "
            + "AND (CAST(:policyStartDate AS timestamp) IS NULL OR item.servicedDate >= :policyStartDate) "
            + "AND (CAST(:policyEndDate AS timestamp) IS NULL OR item.servicedDate <= :policyEndDate)"
    )
    int countActiveClaimsByMemberCrIdAndCode(String crId, List<ExtensionStateCodeEnum> status, List<String> shaCode, String use,
                                             @Param("excludeClaimId") UUID excludeClaimId,
                                             @Param("policyStartDate") LocalDate policyStartDate,
                                             @Param("policyEndDate") LocalDate policyEndDate);


    @Query("""
                SELECT COALESCE(SUM(COALESCE(item.approvedPrice, 0) - COALESCE(item.pmfAmount, 0)), 0)
                FROM ClaimLineItem item
                JOIN item.claim ic
                JOIN ic.patient pt
                WHERE pt.crId = :crId
                AND ic.status IN :status
                AND item.code LIKE CONCAT(:shaCodePrefix, '%')
                AND ic.use = :use
                AND (CAST(:policyStartDate AS timestamp) IS NULL OR item.servicedDate >= :policyStartDate)
                AND (CAST(:policyEndDate AS timestamp) IS NULL OR item.servicedDate <= :policyEndDate)
            """)
    BigDecimal getTotalAmtOfClaimsByMemberCrIdAndCode(
            @Param("crId") String crId,
            @Param("status") List<ExtensionStateCodeEnum> status,
            @Param("shaCodePrefix") String shaCodePrefix,
            @Param("use") String use,
            @Param("policyStartDate") LocalDate policyStartDate,
            @Param("policyEndDate") LocalDate policyEndDate
    );

    @Query("""
                SELECT COALESCE(SUM(COALESCE(item.eccifAmount, 0)), 0)
                FROM ClaimLineItem item
                JOIN item.claim ic
                JOIN ic.patient pt
                WHERE pt.crId = :crId
                AND ic.status IN :status
                AND item.code LIKE CONCAT(:shaCodePrefix, '%')
                AND ic.use = :use
                AND (CAST(:policyStartDate AS timestamp) IS NULL OR item.servicedDate >= :policyStartDate)
                AND (CAST(:policyEndDate AS timestamp) IS NULL OR item.servicedDate <= :policyEndDate)
            """)
    BigDecimal getTotalEccifAmtOfClaimsByMemberCrIdAndCode(
            @Param("crId") String crId,
            @Param("status") List<ExtensionStateCodeEnum> status,
            @Param("shaCodePrefix") String shaCodePrefix, // pass 'SHA-06'
            @Param("use") String use,
            @Param("policyStartDate") LocalDate policyStartDate,
            @Param("policyEndDate") LocalDate policyEndDate
    );

    Optional<Page<InsuranceClaim>> findByUseAndStatusAndClaimResponseIdIsNull(String use, ExtensionStateCodeEnum status, Pageable pageable);

    @Query("SELECT ic FROM InsuranceClaim ic " +
            "WHERE ic.use = :use " +
            "AND ic.status in :status " +
            "AND ic.isAssigned = :isAssigned " +
            "ORDER BY ic.creationDate ASC Limit :limit")
    Optional<List<InsuranceClaim>> findByUseAndStatusAndIsAssignedSortByCreationDate(@Param("use") String use, @Param("status") List<ExtensionStateCodeEnum> status,@Param("isAssigned") Boolean isAssigned , @Param("limit") Long limit );

    @Query("SELECT ic FROM InsuranceClaim ic WHERE ic.use = :use AND ic.externalId NOT IN :externalIds")
    Optional<Page<InsuranceClaim>> findAllByUseAndExcludeClaimIds(@Param("use") String use, @Param("externalIds") List<String> externalIds, Pageable pageable);

    long countAllByIsAssignedFalseAndStatusAndUseInIgnoreCase(ExtensionStateCodeEnum status, List<String> use);

    @Query(
            "SELECT DISTINCT ic FROM InsuranceClaim ic "
                    + "JOIN ic.provider p "
                    + "JOIN ic.patient pt "
                    + "LEFT JOIN ClaimLineItem item ON ic.claimId = item.claim.claimId "
                    + "LEFT JOIN UserBucket ub ON ub.claim.claimId = ic.claimId "
                    + "LEFT JOIN User usr ON ub.user.id = usr.id "
                    + "WHERE (:claimNumber IS NULL OR ic.claimNumber = :claimNumber)"
                    + "AND (:claimId IS NULL OR ic.claimId = :claimId)"
                    + "AND (:accessCode IS NULL OR ic.accessCode = :accessCode)"
                    + "AND (:status IS NULL OR ic.status = :status)"
                    + "AND (ic.use = :use)"
                    + "AND (:isPHCClaim IS NULL OR ic.isPHCClaim = :isPHCClaim)"
                    + "AND (:providerName IS NULL OR p.name = :providerName)"
                    + "AND (:mflCode IS NULL OR p.mflCode = :mflCode)"
                    + "AND (:level IS NULL OR p.level = :level)"
                    + "AND (:patientName IS NULL OR pt.name = :patientName)"
                    + "AND (:crId IS NULL OR pt.crId = :crId)"
                    + "AND (:shaNumber IS NULL OR pt.shaNumber = :shaNumber)"
                    + "AND (:birthCertificate IS NULL OR pt.birthCertificate = :birthCertificate)"
                    + "AND (:userId IS NULL OR usr.username = :userId)"
                    + "AND (ub.status = :bucketStatus)"
                    + "AND (:nationalId IS NULL OR pt.nationalId = :nationalId)"
                    + "AND (CAST(:startDate AS timestamp) IS NULL OR ic.creationDate >= :startDate) "
                    + "AND (CAST(:endDate AS timestamp) IS NULL OR ic.creationDate <= :endDate)")
    Optional<Page<InsuranceClaim>> findPreAuthByRolesOptionalParams(
            @Param("claimId") UUID claimId,
            @Param("isPHCClaim") Boolean isPHCClaim,
            @Param("providerName") String providerName,
            @Param("mflCode") String mflCode,
            @Param("patientName") String patientName,
            @Param("crId") String crId,
            @Param("nationalId") String nationalId,
            @Param("shaNumber") String shaNumber,
            @Param("birthCertificate") String birthCertificate,
            @Param("claimNumber") Long claimNumber,
            @Param("accessCode") String accessCode,
            @Param("level") String level,
            @Param("status") ExtensionStateCodeEnum status,
            @Param("use") String use,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("userId") String userId,
            @Param("bucketStatus") ClaimType.BucketStatus bucketStatus,
             Pageable pageable);

    @Query(
            "SELECT DISTINCT ic FROM InsuranceClaim ic "
                    + "JOIN ic.provider p "
                    + "JOIN ic.patient pt "
                    + "LEFT JOIN ClaimLineItem item ON ic.claimId = item.claim.claimId "
                    + "LEFT JOIN UserBucket ub ON ub.claim.claimId = ic.claimId "
                    + "LEFT JOIN User usr ON ub.user.id = usr.id "
                    + "WHERE (:claimNumber IS NULL OR ic.claimNumber = :claimNumber)"
                    + "AND (:claimId IS NULL OR ic.claimId = :claimId)"
                    + "AND (:accessCode IS NULL OR ic.accessCode = :accessCode)"
                    + "AND (:status IS NULL OR ic.status = :status)"
                    + "AND (ic.use = :use)"
                    + "AND (:isPHCClaim IS NULL OR ic.isPHCClaim = :isPHCClaim)"
                    + "AND (:providerName IS NULL OR p.name = :providerName)"
                    + "AND (:mflCode IS NULL OR p.mflCode = :mflCode)"
                    + "AND (:level IS NULL OR p.level = :level)"
                    + "AND (:patientName IS NULL OR pt.name = :patientName)"
                    + "AND (:crId IS NULL OR pt.crId = :crId)"
                    + "AND (:shaNumber IS NULL OR pt.shaNumber = :shaNumber)"
                    + "AND (:birthCertificate IS NULL OR pt.birthCertificate = :birthCertificate)"
                    + "AND (:userId IS NULL OR usr.username = :userId)"
                    + "AND (ub.status = :bucketStatus)"
                    + "AND (:nationalId IS NULL OR pt.nationalId = :nationalId)"
                    + "AND (CAST(:startDate AS timestamp) IS NULL OR ic.creationDate >= :startDate) "
                    + "AND (CAST(:endDate AS timestamp) IS NULL OR ic.creationDate <= :endDate) "
                    + "AND (item.code IS NOT NULL AND item.code LIKE CONCAT(:shaPackageCode, '%'))")
    Optional<Page<InsuranceClaim>> findPreAuthByRolesOptionalParams(
            @Param("claimId") UUID claimId,
            @Param("isPHCClaim") Boolean isPHCClaim,
            @Param("providerName") String providerName,
            @Param("mflCode") String mflCode,
            @Param("patientName") String patientName,
            @Param("crId") String crId,
            @Param("nationalId") String nationalId,
            @Param("shaNumber") String shaNumber,
            @Param("birthCertificate") String birthCertificate,
            @Param("claimNumber") Long claimNumber,
            @Param("accessCode") String accessCode,
            @Param("level") String level,
            @Param("status") ExtensionStateCodeEnum status,
            @Param("use") String use,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("userId") String userId,
            @Param("bucketStatus") ClaimType.BucketStatus bucketStatus,
            @Param("shaPackageCode") String shaPackageCode,
            Pageable pageable);

    @Query("SELECT DISTINCT ic.provider FROM InsuranceClaim ic " +
            "WHERE ic.isPHCClaim = true AND ic.dateFrom BETWEEN :startDate AND :endDate " +
            "AND ic.status = :status " +
            "ORDER BY ic.provider.county")
    List<Provider> findDistinctProvidersByMonth(@Param("startDate") LocalDateTime startDate,
                                                @Param("endDate") LocalDateTime endDate,
                                                @Param("status") ExtensionStateCodeEnum status);


    @Query("SELECT DISTINCT ic.provider FROM InsuranceClaim ic " +
            "WHERE ic.isPHCClaim = true " +
            "AND ic.dateFrom BETWEEN :startDate AND :endDate " +
            "AND ic.status = :status " +
            "AND ic.provider.county = :county " +
            "ORDER BY ic.provider.county")
    List<Provider> findDistinctProvidersByMonthAndCounty(@Param("startDate") LocalDateTime startDate,
                                                         @Param("endDate") LocalDateTime endDate,
                                                         @Param("status") ExtensionStateCodeEnum status,
                                                         @Param("county") String county);


    @Query("SELECT new io.coredge.health.claims.models.phccalculation.PHCClaimsDbDto(" +
            "ic.claimId, " +
            "ic.claimNumber, " +
            "ic.dateFrom, " +
            "p.id, " +
            "p.name, " +
            "p.mflCode, " +
            "p.fidCode, " +
            "p.county, " +
            "p.entityType, " +
            "pa.shaNumber, " +
            "pa.name, " +
            "pa.gender, " +
            "pa.birthDate, " +
            "pa.nationalId, " +
            "ic.icdCodes, " +
            "CAST(0.00 as Double)) " +
            "FROM InsuranceClaim ic " +
            "JOIN ic.provider p " +
            "JOIN ic.patient pa " +
            "WHERE p.county = :county " +
            "AND ic.isPHCClaim = true " +
            "AND ic.status = :status ")
    Optional<List<PHCClaimsDbDto>> findCountyWiseProviderClaimsDtos(@Param("county") String county,@Param("status") ExtensionStateCodeEnum status);


    @Query("SELECT new io.coredge.health.claims.models.phccalculation.PHCClaimsDbDto(" +
            "ic.claimId, " +
            "ic.claimNumber, " +
            "ic.dateFrom, " +
            "p.id, " +
            "p.name, " +
            "p.mflCode, " +
            "p.fidCode, " +
            "p.county, " +
            "p.entityType, " +
            "pa.shaNumber, " +
            "pa.name, " +
            "pa.gender, " +
            "pa.birthDate, " +
            "pa.nationalId, " +
            "ic.icdCodes, " +
            "CAST(0.00 as Double)) " +
            "FROM InsuranceClaim ic " +
            "JOIN ic.provider p " +
            "JOIN ic.patient pa " +
            "WHERE ic.isPHCClaim = true " +
            "AND ic.status = :status " +
            "AND ic.dateFrom BETWEEN :startDate AND :endDate")
    Optional<List<PHCClaimsDbDto>> findPhcClaimsDtosDatesFilter(
            @Param("status") ExtensionStateCodeEnum status,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /**
     * Find PHC claims by date range and county for memory-optimized processing.
     * Fetches claims for a specific county to reduce memory footprint.
     */
    @Query("SELECT new io.coredge.health.claims.models.phccalculation.PHCClaimsDbDto(" +
            "ic.claimId, " +
            "ic.claimNumber, " +
            "ic.dateFrom, " +
            "p.id, " +
            "p.name, " +
            "p.mflCode, " +
            "p.fidCode, " +
            "p.county, " +
            "p.entityType, " +
            "pa.shaNumber, " +
            "pa.name, " +
            "pa.gender, " +
            "pa.birthDate, " +
            "pa.nationalId, " +
            "ic.icdCodes, " +
            "CAST(0.00 as Double)) " +
            "FROM InsuranceClaim ic " +
            "JOIN ic.provider p " +
            "JOIN ic.patient pa " +
            "WHERE ic.isPHCClaim = true " +
            "AND ic.status = :status " +
            "AND ic.dateFrom BETWEEN :startDate AND :endDate " +
            "AND p.county = :county")
    Optional<List<PHCClaimsDbDto>> findPhcClaimsDtosDatesFilterByCounty(
            @Param("status") ExtensionStateCodeEnum status,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("county") String county);

    @Transactional
    @Modifying
    @Query("update InsuranceClaim i set i.preAuthId = :preAuthId where i.claimId = :claimId")
    void updatePreAuthIdByClaimId(UUID preAuthId, UUID claimId);

    @Transactional
    @Query("SELECT ic FROM InsuranceClaim ic WHERE ic.use = :use AND ic.status = :status AND ic.claimId IN :claimId")
    Optional<List<InsuranceClaim>> findAllByClaimId(@Param("claimId") List<UUID> claimId, @Param("status") ExtensionStateCodeEnum extensionStateCodeEnum, @Param("use") String use);


    @Query("SELECT DISTINCT p.county FROM Provider p WHERE p.county IS NOT NULL ORDER BY p.county")
    List<String> findDistinctCountyNames();

    @Query("SELECT ic.provider.county, COUNT(DISTINCT ic.provider) " +
            "FROM InsuranceClaim ic " +
            "WHERE ic.isPHCClaim = true " +
            "AND ic.dateFrom BETWEEN :startDate AND :endDate " +
            "AND ic.status = :status " +
            "AND ic.provider.county IS NOT NULL " +  // Excluding NULL county names
            "GROUP BY ic.provider.county " +
            "ORDER BY ic.provider.county")
    List<Object[]> countDistinctProvidersByCounty(@Param("startDate") LocalDateTime startDate,
                                                  @Param("endDate") LocalDateTime endDate,
                                                  @Param("status") ExtensionStateCodeEnum status);

    @Query("SELECT new io.coredge.health.claims.models.phccalculation.ClaimsAfyanguProjection(" +
            "ic.claimId, " +
            "ic.claimNumber, " +
            "ic.creationDate, " +
            "ic.dateFrom, " +
            "ic.dateTo, " +
            "p.mflCode, " +
            "p.fidCode, " +
            "pa.crId, " +
            "pa.nationalId, " +
            "ic.icdCodes, " +
            "ic.claimedTotal, " +
            "ic.use, " +
            "ic.claimType, " +
            "ic.status, " +
            "ic.accessCode) " +
            "FROM InsuranceClaim ic " +
            "JOIN ic.provider p " +
            "JOIN ic.patient pa " +
            "WHERE ic.isPHCClaim = false " +
            "AND ic.status IN (:statuses) ")
    Page<ClaimsAfyanguProjection> findAllNonPHCClaimsByStatusOptimized(
            @Param("statuses") List<ExtensionStateCodeEnum> statuses,
            Pageable pageable);

    @Query("SELECT DISTINCT ic FROM InsuranceClaim ic "
            + "JOIN ClaimBundleLogEntity cbl ON ic.claimId = cbl.claimId "
            + "WHERE (:shaCode IS NULL OR EXISTS ("
            + "     SELECT 1 FROM ClaimLineItem cli "
            + "     WHERE cli.claim.claimId = ic.claimId "
            + "     AND (:shaCode IS NULL OR cli.code = :shaCode)"
            + ")) "
            + "AND (:shaPackageCode IS NULL OR EXISTS("
            + "     SELECT 1 FROM ClaimLineItem cli "
            + "     WHERE cli.claim.claimId = ic.claimId "
            + "     AND cli.code LIKE CONCAT(CAST(:shaPackageCode AS string), '%')"
            + ")) "
            + "AND (:county IS NULL OR EXISTS("
            + "    SELECT 1 FROM Provider p "
            + "    WHERE p = ic.provider "
            + "    AND p.county LIKE CONCAT(CAST(:county AS string), '%')"
            + ")) "
            + "AND (:claimNumber IS NULL OR ic.claimNumber = :claimNumber) "
            + "AND (:claimId IS NULL OR ic.claimId = :claimId) "
            + "AND (:accessCode IS NULL OR ic.accessCode = :accessCode) "
            + "AND (ic.use = :use) "
            + "AND (:status IS NULL OR ic.status = :status) "
            + "AND (:isPHCClaim IS NULL OR ic.isPHCClaim = :isPHCClaim) "
            + "AND (:providerName IS NULL OR ic.provider.name = :providerName) "
            + "AND (:fidCode IS NULL OR ic.provider.fidCode = :fidCode) "
            + "AND (:level IS NULL OR ic.provider.level = :level) "
            + "AND (:patientName IS NULL OR ic.patient.name = :patientName) "
            + "AND (:crId IS NULL OR ic.patient.crId = :crId) "
            + "AND (:shaNumber IS NULL OR ic.patient.shaNumber = :shaNumber) "
            + "AND (:nationalId IS NULL OR ic.patient.nationalId = :nationalId) "
            + "AND (CAST(:startDate AS timestamp) IS NULL OR ic.creationDate >= :startDate) "
            + "AND (CAST(:endDate AS timestamp) IS NULL OR ic.creationDate <= :endDate) "
            + "AND ic.status != :ignoreStatus")
    Optional<Page<InsuranceClaim>> getUpdatedClaims(
            @Param("claimId") UUID claimId,
            @Param("isPHCClaim") Boolean isPHCClaim,
            @Param("providerName") String providerName,
            @Param("accessCode") String accessCode,
            @Param("patientName") String patientName,
            @Param("crId") String crId,
            @Param("nationalId") String nationalId,
            @Param("shaNumber") String shaNumber,
            @Param("fidCode") String fidCode,
            @Param("level") String level,
            @Param("shaCode") String shaCode,
            @Param("shaPackageCode") String shaPackageCode,
            @Param("county") String county,
            @Param("status") ExtensionStateCodeEnum status,
            @Param("claimNumber") Long claimNumber,
            @Param("use") String use,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("ignoreStatus") ExtensionStateCodeEnum ignoreStatus,
            Pageable pageable);

    @Query("""
    SELECT DISTINCT c.claim FROM ClaimLineItem c
    WHERE c.code IN :codes
    AND c.claim.status = :status
    AND c.claim.use = :use
    AND c.claim.isPHCClaim = :isPhcClaim
    """)
    Optional<List<InsuranceClaim>> findFilteredClaimNumbersByCodes(
            List<String> codes, ExtensionStateCodeEnum status, String use, Boolean isPhcClaim);


    @Query(value = "SELECT c.claim_id AS claimId, c.claim_response_id AS responseId FROM claims c WHERE c.claim_id IN (:claimIds)", nativeQuery = true)
    List<InsuranceClaimProjection> findResponseIdsByClaimIds(@Param("claimIds") List<UUID> claimIds);


    @Query("""
                SELECT COUNT(ci)
                FROM ClaimLineItem ci
                JOIN ci.claim c
                JOIN c.patient p
                WHERE p.householdNumber = :householdNumber
                  AND ci.code = :interventionCode
                  AND ci.servicedDate BETWEEN :startDate AND :endDate
                  AND c.use = :use
                  AND c.status IN :approvedStatuses
            """)
    int countUsedInterventionsInRollingYear(
            @Param("householdNumber") String householdNumber,
            @Param("interventionCode") String interventionCode,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("use") String use,
            @Param("approvedStatuses") List<String> approvedStatuses
    );

    @Query("""
            SELECT COUNT(DISTINCT c)
            FROM InsuranceClaim c
            JOIN c.patient p
            JOIN ClaimLineItem cli ON cli.claim.claimId = c.claimId
            WHERE p.crId = :crId
              AND cli.code = :interventionCode
              AND cli.servicedDate >= :startDate
              AND cli.servicedDate < :endDate
              AND c.status IN :approvedStatuses
              AND c.use = :use
            """)
    int countUsedInterventionsPerCustomYear(
            @Param("crId") String crId,
            @Param("interventionCode") String interventionCode,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("approvedStatuses") List<String> approvedStatuses,
            @Param("use") String use
    );


    @Query("""
                SELECT COUNT(DISTINCT c)
                FROM InsuranceClaim c
                JOIN c.patient p
                JOIN ClaimLineItem cli ON cli.claim.claimId = c.claimId
                WHERE p.crId = :crId
                  AND cli.code = :interventionCode
                  AND cli.servicedDate BETWEEN :startDate AND :endDate
                  AND c.status IN :status
                  AND c.use = :use
            """)
    int countUsedInRollingWeeklyWindow(
            @Param("crId") String crId,
            @Param("interventionCode") String interventionCode,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("status") List<String> status,
            @Param("use") String use
    );


    @Query(value = """
    SELECT COALESCE(SUM(
        CASE
            WHEN ci.serviced_period_start IS NOT NULL AND ci.serviced_period_end IS NOT NULL
                THEN (ci.serviced_period_end - ci.serviced_period_start)
            ELSE (c.date_to::date - c.date_from::date)
        END
    ), 0)::int
    FROM claim_items ci
    JOIN claims c ON ci.claim_id = c.claim_id
    JOIN patient p ON c.patient = p.id
    WHERE p.household_number = :householdNumber
      AND ci.code IN (:interventionCodes)
      AND ci.serviced_date BETWEEN :policyStartDate AND :policyEndDate
      AND c.status IN (:status)
      AND c."use" = :type
    """, nativeQuery = true)
    int sumPerDiemDaysUsedInYear(
            @Param("householdNumber") String householdNumber,
            @Param("interventionCodes") List<String> interventionCodes,
            @Param("policyStartDate") LocalDate policyStartDate,
            @Param("policyEndDate") LocalDate policyEndDate,
            @Param("type") String use,
            @Param("status") List<String> status
    );



    @Query("""
            SELECT COUNT(ci)
            FROM ClaimLineItem ci
            JOIN ci.claim c
            JOIN c.patient p
            WHERE p.householdNumber = :householdNumber
              AND (CAST(:policyStartDate AS timestamp) IS NULL OR CAST(:policyEndDate AS timestamp) IS NULL OR ci.servicedDate BETWEEN :policyStartDate AND :policyEndDate)
              AND c.status IN :approvedStatuses
              AND c.use = :use
            """)
    int countPeritonealClaim(
            @Param("householdNumber") String householdNumber,
            @Param("policyStartDate") LocalDate policyStartDate ,
            @Param("policyEndDate")  LocalDate policyEndDate,
            @Param("approvedStatuses") List<String> approvedStatuses,
            @Param("use") String use
    );

    @Query("""
    SELECT MAX(ci.servicedDate)
    FROM ClaimLineItem ci
    JOIN ci.claim ic
    JOIN ic.patient p
    WHERE p.householdNumber = :householdNumber
      AND ci.code = :interventionCode
      AND (CAST(:admissionDate AS timestamp) IS NULL OR CAST(:dischargeDate AS timestamp) IS NULL OR  ci.servicedDate BETWEEN :admissionDate AND :dischargeDate)
      AND ci.servicedDate < :currentDate
      AND ic.status IN :approvedStatuses
      AND ic.use = :use
    """)
    LocalDate findLastUsedDateForIntervention(
            @Param("interventionCode") String interventionCode,
            @Param("householdNumber") String householdNumber,
            @Param("admissionDate") LocalDate admissionDate,
            @Param("dischargeDate") LocalDate dischargeDate,
            @Param("currentDate") LocalDate currentDate,
            @Param("approvedStatuses") List<String> approvedStatuses,
            @Param("use") String use
    );

    @Query("""
    SELECT COUNT(ci)
    FROM ClaimLineItem ci
    JOIN ci.claim c
    JOIN c.patient p
    WHERE p.householdNumber = :householdNumber
      AND ci.code = :interventionCode
      AND ci.servicedDate BETWEEN :admissionDate AND :dischargeDate
      AND c.status IN :approvedStatuses
      AND c.use = :use
    """)
    int countAcuteDialysisUsageDuringAdmission(
            @Param("householdNumber") String householdNumber,
            @Param("interventionCode") String interventionCode,
            @Param("admissionDate") LocalDate admissionDate,
            @Param("dischargeDate") LocalDate dischargeDate,
            @Param("approvedStatuses") List<String> approvedStatuses,
            @Param("use") String use
    );

    @Query("""
    SELECT c
    FROM ClaimLineItem cli
    JOIN cli.claim c
    JOIN c.patient p
    WHERE p.crId = :crId
      AND cli.code = :interventionCode
      AND cli.servicedDate = :serviceDate
      AND c.use = :claimType
    ORDER BY c.creationDate DESC
""")
    List<InsuranceClaim> findLatestClaimForServiceDate(
            @Param("crId") String crId,
            @Param("interventionCode") String interventionCode,
            @Param("serviceDate") LocalDate serviceDate,
            @Param("claimType") String claimType
    );

    @Query("""
    SELECT c
    FROM ClaimLineItem cli
    JOIN cli.claim c
    JOIN c.patient p
    WHERE p.crId = :crId
      AND cli.code = :interventionCode
      AND cli.servicedDate = :serviceDate
      AND c.use = :claimType
      AND c.claimId <> :excludeClaimId
    ORDER BY c.creationDate DESC
""")
    List<InsuranceClaim> findLatestClaimForServiceDateExcluding(
            @Param("crId") String crId,
            @Param("interventionCode") String interventionCode,
            @Param("serviceDate") LocalDate serviceDate,
            @Param("claimType") String claimType,
            @Param("excludeClaimId") UUID excludeClaimId
    );

    Optional<InsuranceClaim> findByClaimIdAndUseAndStatus(UUID claimId, String use, ExtensionStateCodeEnum status);

    @Query("SELECT DISTINCT ic.source FROM InsuranceClaim ic WHERE ic.source IS NOT NULL")
    List<String> findDistinctSources();

    @Query("""
                SELECT ic.claimId AS claimId,
                       ic.expiryDate AS expiryDate
                FROM InsuranceClaim ic
                JOIN ic.patient pt
                LEFT JOIN ClaimLineItem item ON ic.claimId = item.claim.claimId
                WHERE pt.crId = :crId
                  AND ic.status IN :status
                  AND item.code IN :shaCode
                  AND ic.use = :use
                  AND (:excludeClaimId IS NULL OR ic.claimId != :excludeClaimId)
                  AND (CAST(:policyStartDate AS timestamp) IS NULL OR item.servicedDate >= :policyStartDate)
                  AND (CAST(:policyEndDate AS timestamp) IS NULL OR item.servicedDate <= :policyEndDate)
            """)
    List<PreAuthProjection> findAllPreAuthProjectedOnCrId(
            @Param("crId") String crId,
            @Param("status") List<ExtensionStateCodeEnum> status,
            @Param("shaCode") List<String> shaCode,
            @Param("use") String use,
            @Param("excludeClaimId") UUID excludeClaimId,
            @Param("policyStartDate") LocalDate policyStartDate,
            @Param("policyEndDate") LocalDate policyEndDate
    );

    @Query("""
                SELECT DISTINCT
                       ic.claimId AS claimId,
                       cli.preAuthId AS preAuthId
                FROM InsuranceClaim ic
                JOIN ic.patient pt
                JOIN ClaimLineItem cli ON ic.claimId = cli.claim.claimId
                WHERE pt.crId = :crId
                  AND ic.status IN :status
                  AND cli.code IN :shaCode
                  AND ic.use = :use
                  AND (CAST(:policyStartDate AS timestamp) IS NULL OR cli.servicedDate >= :policyStartDate)
                  AND (CAST(:policyEndDate AS timestamp) IS NULL OR cli.servicedDate <= :policyEndDate)
            """)
    List<ClaimPreAuthMappingProjection> findAllClaimProjectedOnCrId(
            @Param("crId") String crId,
            @Param("shaCode") List<String> shaCode,
            @Param("use") String use,
            @Param("status") List<ExtensionStateCodeEnum> status,
            @Param("policyStartDate") LocalDate policyStartDate,
            @Param("policyEndDate") LocalDate policyEndDate
    );

    @Query("""
                SELECT DISTINCT
                       ic.claimId AS claimId,
                       cli.preAuthId AS preAuthId
                FROM InsuranceClaim ic
                JOIN ic.patient pt
                JOIN ClaimLineItem cli ON ic.claimId = cli.claim.claimId
                WHERE pt.householdNumber = :householdNumber
                  AND ic.status IN :status
                  AND cli.code IN :shaCode
                  AND ic.use = :use
                  AND (CAST(:policyStartDate AS timestamp) IS NULL OR cli.servicedDate >= :policyStartDate)
                  AND (CAST(:policyEndDate AS timestamp) IS NULL OR cli.servicedDate <= :policyEndDate)
            """)
    List<ClaimPreAuthMappingProjection> findAllClaimProjectedOnHouseHoldNo(
            @Param("householdNumber") String householdNumber,
            @Param("shaCode") List<String> shaCode,
            @Param("use") String use,
            @Param("status") List<ExtensionStateCodeEnum> status,
            @Param("policyStartDate") LocalDate policyStartDate,
            @Param("policyEndDate") LocalDate policyEndDate
    );

    @Query("""
                SELECT ic.claimId AS claimId,
                       ic.expiryDate AS expiryDate
                FROM InsuranceClaim ic
                JOIN ic.patient pt
                LEFT JOIN ClaimLineItem item ON ic.claimId = item.claim.claimId
                WHERE pt.householdNumber = :householdNumber
                  AND ic.status IN :status
                  AND item.code IN :shaCode
                  AND ic.use = :use
                  AND (:excludeClaimId IS NULL OR ic.claimId != :excludeClaimId)
                  AND (CAST(:policyStartDate AS timestamp) IS NULL OR item.servicedDate >= :policyStartDate)
                  AND (CAST(:policyEndDate AS timestamp) IS NULL OR item.servicedDate <= :policyEndDate)
            """)
    List<PreAuthProjection> findAllPreAuthProjectedOnHouseHold(
            @Param("householdNumber") String householdNumber,
            @Param("status") List<ExtensionStateCodeEnum> status,
            @Param("shaCode") List<String> shaCode,
            @Param("use") String use,
            @Param("excludeClaimId") UUID excludeClaimId,
            @Param("policyStartDate") LocalDate policyStartDate,
            @Param("policyEndDate") LocalDate policyEndDate
    );

    @Query("""
                SELECT ic.claimId AS claimId,
                       ic.expiryDate AS expiryDate,
                       COALESCE(item.approvedPrice, 0) AS approvedPrice
                FROM InsuranceClaim ic
                JOIN ic.patient pt
                LEFT JOIN ClaimLineItem item ON ic.claimId = item.claim.claimId
                WHERE pt.crId = :crId
                  AND ic.status IN :status
                  AND item.code LIKE CONCAT(:shaCode, '%')
                  AND ic.use = :use
                  AND (:excludeClaimId IS NULL OR ic.claimId != :excludeClaimId)
                  AND (CAST(:policyStartDate AS timestamp) IS NULL OR item.servicedDate >= :policyStartDate)
                  AND (CAST(:policyEndDate AS timestamp) IS NULL OR item.servicedDate <= :policyEndDate)
            """)
    List<PreAuthProjection> findAllPreAuthProjectedByPackage(
            @Param("crId") String crId,
            @Param("status") List<ExtensionStateCodeEnum> status,
            @Param("shaCode") String shaCode,
            @Param("use") String use,
            @Param("excludeClaimId") UUID excludeClaimId,
            @Param("policyStartDate") LocalDate policyStartDate,
            @Param("policyEndDate") LocalDate policyEndDate
    );

    @Query("""
                SELECT ic.claimId AS claimId,
                       ic.expiryDate AS expiryDate,
                       COALESCE(item.eccifAmount, 0) AS approvedPrice
                FROM InsuranceClaim ic
                JOIN ic.patient pt
                LEFT JOIN ClaimLineItem item ON ic.claimId = item.claim.claimId
                WHERE pt.crId = :crId
                  AND ic.status IN :status
                  AND item.code LIKE CONCAT(:shaCode, '%')
                  AND ic.use = :use
                  AND (:excludeClaimId IS NULL OR ic.claimId != :excludeClaimId)
                  AND (CAST(:policyStartDate AS timestamp) IS NULL OR item.servicedDate >= :policyStartDate)
                  AND (CAST(:policyEndDate AS timestamp) IS NULL OR item.servicedDate <= :policyEndDate)
            """)
    List<PreAuthProjection> findAllPreAuthEccifAmountByPackage(
            @Param("crId") String crId,
            @Param("status") List<ExtensionStateCodeEnum> status,
            @Param("shaCode") String shaCode,
            @Param("use") String use,
            @Param("excludeClaimId") UUID excludeClaimId,
            @Param("policyStartDate") LocalDate policyStartDate,
            @Param("policyEndDate") LocalDate policyEndDate
    );

    @Query("""
                SELECT DISTINCT
                       ic.claimId AS claimId,
                       COALESCE(item.eccifAmount, 0) AS approvedPrice,
                       item.preAuthId AS preAuthId
                FROM InsuranceClaim ic
                JOIN ic.patient pt
                JOIN ClaimLineItem item ON ic.claimId = item.claim.claimId
                WHERE pt.crId = :crId
                  AND ic.status IN :status
                  AND item.code LIKE CONCAT(:shaCode, '%')
                  AND ic.use = :use
                  AND (CAST(:policyStartDate AS timestamp) IS NULL OR item.servicedDate >= :policyStartDate)
                  AND (CAST(:policyEndDate AS timestamp) IS NULL OR item.servicedDate <= :policyEndDate)
            """)
    List<ClaimProjection> findAllClaimEccifAmountByPackage(
            @Param("crId") String crId,
            @Param("status") List<ExtensionStateCodeEnum> status,
            @Param("shaCode") String shaCode,
            @Param("use") String use,
            @Param("policyStartDate") LocalDate policyStartDate,
            @Param("policyEndDate") LocalDate policyEndDate
    );

    @Query("""
                SELECT DISTINCT
                       ic.claimId AS claimId,
                       COALESCE(item.approvedPrice, 0) AS approvedPrice,
                       item.preAuthId AS preAuthId
                FROM InsuranceClaim ic
                JOIN ic.patient pt
                JOIN ClaimLineItem item ON ic.claimId = item.claim.claimId
                WHERE pt.crId = :crId
                  AND ic.status IN :status
                  AND item.code LIKE CONCAT(:shaCode, '%')
                  AND ic.use = :use
                  AND (CAST(:policyStartDate AS timestamp) IS NULL OR item.servicedDate >= :policyStartDate)
                  AND (CAST(:policyEndDate AS timestamp) IS NULL OR item.servicedDate <= :policyEndDate)
            """)
    List<ClaimProjection> findAllClaimProjectedByPackage(
            @Param("crId") String crId,
            @Param("status") List<ExtensionStateCodeEnum> status,
            @Param("shaCode") String shaCode,
            @Param("use") String use,
            @Param("policyStartDate") LocalDate policyStartDate,
            @Param("policyEndDate") LocalDate policyEndDate
    );

    @Query("""
                SELECT MAX(cli.servicedDate)
                FROM ClaimLineItem cli
                JOIN cli.claim c
                JOIN c.patient p
                WHERE cli.code = :interventionCode
                  AND p.crId = :crId
                  AND c.status IN (:approvedStatuses)
                  AND c.use = :use
            """)
    LocalDate findLatestServicedDate(
            @Param("interventionCode") String interventionCode,
            @Param("crId") String crId,
            @Param("approvedStatuses") List<String> approvedStatuses,
            @Param("use") String use
    );

    @Query("""
                SELECT MAX(cli.servicedDate)
                FROM ClaimLineItem cli
                JOIN cli.claim c
                JOIN c.patient p
                WHERE cli.code IN (:interventionCodes)
                  AND p.crId = :crId
                  AND c.status IN (:approvedStatuses)
                  AND c.use IN (:use)
            """)
    LocalDate findLatestServicedDate(
            @Param("interventionCodes") List<String> interventionCodes,
            @Param("crId") String crId,
            @Param("approvedStatuses") List<String> approvedStatuses,
            @Param("use") List<String> use
    );

    @Query("SELECT DISTINCT ic.use FROM InsuranceClaim ic WHERE ic.use IS NOT NULL")
    List<String> findDistinctClaimTypes();

    @Query("""
        SELECT DISTINCT ic
        FROM InsuranceClaim ic
        LEFT JOIN ic.provider p
        LEFT JOIN ic.patient pt
        Left JOIN UserBucket ub ON ub.claim.claimId = ic.claimId
        Left JOIN User usr ON ub.user.id = usr.id
        LEFT JOIN ClaimLineItem item ON ic.claimId = item.claim.claimId
          WHERE (:shaCode IS NULL OR item.code IN :shaCode)
           AND (:claimNumber IS NULL OR ic.claimNumber = :claimNumber)
           AND (:claimId IS NULL OR ic.claimId = :claimId)
           AND (:status IS NULL OR ic.status = :status)
           AND (:accessCode IS NULL OR ic.accessCode = :accessCode)
           AND (ic.use = :use)
           AND (ic.isPHCClaim = :isPHCClaim)
           AND (:auditFlag IS NULL OR ic.auditFlag = :auditFlag)
           AND (:providerName IS NULL OR p.name ILIKE %:providerName%)
           AND (:patientName IS NULL OR pt.name ILIKE %:patientName%)
           AND (:crId IS NULL OR pt.crId = :crId)
           AND (:shaNumber IS NULL OR pt.shaNumber = :shaNumber)
           AND (:nationalId IS NULL OR pt.nationalId = :nationalId)
           AND (CAST(:startDate AS timestamp) IS NULL OR ic.creationDate >= :startDate)
           AND (CAST(:endDate AS timestamp) IS NULL OR ic.creationDate <= :endDate)
           AND (NOT (:status IS NULL AND :auditFlag = TRUE AND ic.status = 'MISSING_DOCUMENTS'))
           AND (:userId IS NULL OR usr.username = :userId)
           AND (ub.status = :bucketStatus)
           AND (item.code IS NOT NULL AND item.code LIKE CONCAT(:shaPackageCode, '%'))
  
    """)
    Optional<Page<InsuranceClaim>> findAllClaimsWithAllOptionalParamsByRole(
            @Param("shaCode") List<String> shaCode,
            @Param("claimId") UUID claimId,
            @Param("isPHCClaim") Boolean isPHCClaim,
            @Param("providerName") String providerName,
            @Param("accessCode") String accessCode,
            @Param("patientName") String patientName,
            @Param("crId") String crId,
            @Param("nationalId") String nationalId,
            @Param("shaNumber") String shaNumber,
            @Param("claimNumber") Long claimNumber,
            @Param("status") ExtensionStateCodeEnum status,
            @Param("use") String use,
            @Param("auditFlag") Boolean auditFlag,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("userId") String userId,
            @Param("bucketStatus") ClaimType.BucketStatus bucketStatus,
            @Param("shaPackageCode") String shaPackageCode,
            Pageable pageable
    );

    @Query("""
        SELECT DISTINCT ic
        FROM InsuranceClaim ic
        LEFT JOIN ic.provider p
        LEFT JOIN ic.patient pt
        Left JOIN UserBucket ub ON ub.claim.claimId = ic.claimId
        Left JOIN User usr ON ub.user.id = usr.id
        LEFT JOIN ClaimLineItem item ON ic.claimId = item.claim.claimId
          WHERE (:shaCode IS NULL OR item.code IN :shaCode)
           AND (:claimNumber IS NULL OR ic.claimNumber = :claimNumber)
           AND (:claimId IS NULL OR ic.claimId = :claimId)
           AND (:status IS NULL OR ic.status = :status)
           AND (:accessCode IS NULL OR ic.accessCode = :accessCode)
           AND (ic.use = :use)
           AND (ic.isPHCClaim = :isPHCClaim)
           AND (:auditFlag IS NULL OR ic.auditFlag = :auditFlag)
           AND (:providerName IS NULL OR p.name ILIKE %:providerName%)
           AND (:patientName IS NULL OR pt.name ILIKE %:patientName%)
           AND (:crId IS NULL OR pt.crId = :crId)
           AND (:shaNumber IS NULL OR pt.shaNumber = :shaNumber)
           AND (:nationalId IS NULL OR pt.nationalId = :nationalId)
           AND (CAST(:startDate AS timestamp) IS NULL OR ic.creationDate >= :startDate)
           AND (CAST(:endDate AS timestamp) IS NULL OR ic.creationDate <= :endDate)
           AND (NOT (:status IS NULL AND :auditFlag = TRUE AND ic.status = 'MISSING_DOCUMENTS'))
           AND (:userId IS NULL OR usr.username = :userId)
           AND (ub.status = :bucketStatus)
 
    """)
    Optional<Page<InsuranceClaim>> findAllClaimsWithAllOptionalParamsByRole(
            @Param("shaCode") List<String> shaCode,
            @Param("claimId") UUID claimId,
            @Param("isPHCClaim") Boolean isPHCClaim,
            @Param("providerName") String providerName,
            @Param("accessCode") String accessCode,
            @Param("patientName") String patientName,
            @Param("crId") String crId,
            @Param("nationalId") String nationalId,
            @Param("shaNumber") String shaNumber,
            @Param("claimNumber") Long claimNumber,
            @Param("status") ExtensionStateCodeEnum status,
            @Param("use") String use,
            @Param("auditFlag") Boolean auditFlag,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("userId") String userId,
            @Param("bucketStatus") ClaimType.BucketStatus bucketStatus,
            Pageable pageable
    );

    @Query("""
            SELECT DISTINCT ic
            FROM InsuranceClaim ic
            JOIN ic.provider p
            JOIN ic.patient pt
            LEFT JOIN ClaimLineItem item ON ic.claimId = item.claim.claimId
              WHERE (:shaCode IS NULL OR item.code IN :shaCode)
               AND (:claimNumber IS NULL OR ic.claimNumber = :claimNumber)
               AND (:claimId IS NULL OR ic.claimId = :claimId)
               AND (:status IS NULL OR ic.status = :status)
               AND (:accessCode IS NULL OR ic.accessCode = :accessCode)
               AND (ic.use = :use)
               AND (ic.isPHCClaim = :isPHCClaim)
               AND (:auditFlag IS NULL OR ic.auditFlag = :auditFlag)
               AND (:providerName IS NULL OR p.name ILIKE %:providerName%)
               AND (:patientName IS NULL OR pt.name ILIKE %:patientName%)
               AND (:crId IS NULL OR pt.crId = :crId)
               AND (:shaNumber IS NULL OR pt.shaNumber = :shaNumber)
               AND (:nationalId IS NULL OR pt.nationalId = :nationalId)
               AND (CAST(:startDate AS timestamp) IS NULL OR ic.creationDate >= :startDate)
               AND (CAST(:endDate AS timestamp) IS NULL OR ic.creationDate <= :endDate)
               AND (NOT (:status IS NULL AND :auditFlag = TRUE AND ic.status = 'MISSING_DOCUMENTS'))
        """)
    Optional<Page<InsuranceClaim>> findAllClaimsWithAllOptionalParams(
            @Param("shaCode") List<String> shaCode,
            @Param("claimId") UUID claimId,
            @Param("isPHCClaim") Boolean isPHCClaim,
            @Param("providerName") String providerName,
            @Param("accessCode") String accessCode,
            @Param("patientName") String patientName,
            @Param("crId") String crId,
            @Param("nationalId") String nationalId,
            @Param("shaNumber") String shaNumber,
            @Param("claimNumber") Long claimNumber,
            @Param("status") ExtensionStateCodeEnum status,
            @Param("use") String use,
            @Param("auditFlag") Boolean auditFlag,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable);

    @Query("""
                SELECT c.claimId AS claimId,
                       c.expiryDate AS expiryDate
                FROM InsuranceClaim c
                JOIN c.patient p
                JOIN ClaimLineItem cli ON cli.claim.claimId = c.claimId
                WHERE p.crId = :crId
                  AND cli.code = :interventionCode
                  AND cli.servicedDate BETWEEN :startDate AND :endDate
                  AND c.status IN :status
                  AND c.use = :use
                  AND (CAST(:policyStartDate AS timestamp) IS NULL OR cli.servicedDate >= :policyStartDate)
                  AND (CAST(:policyEndDate AS timestamp) IS NULL OR cli.servicedDate <= :policyEndDate)
            """)
    List<PreAuthProjection> countUsedInRollingWeeklyWindowProjection(
            @Param("crId") String crId,
            @Param("interventionCode") String interventionCode,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("status") List<ExtensionStateCodeEnum> status,
            @Param("use") String use,
            @Param("policyStartDate") LocalDate policyStartDate,
            @Param("policyEndDate") LocalDate policyEndDate
    );

    Optional<List<InsuranceClaim>> findByPhcClaimPaymentsId(Long phcClaimPaymentsId);

    @Query("""
            Select ic.bundleId from InsuranceClaim ic
            where ic.claimId = :claimId
            """)
    Optional<String> findBundleIdByClaimId(UUID claimId);


    Optional<List<InsuranceClaim>> findByClaimIdInAndStatus(List<UUID> claimIds, ExtensionStateCodeEnum status);


    @Query("""
            SELECT COUNT(ci)
            FROM ClaimLineItem ci
            JOIN ci.claim c
            JOIN c.patient p
            JOIN c.provider pr
            WHERE p.householdNumber = :householdNumber
              AND ci.code IN :interventionCodes
              AND c.use = :use
              AND c.status IN :approvedStatuses
              AND LOWER(pr.level) LIKE '%3%'
              AND (CAST(:policyStartDate AS timestamp) IS NULL OR ci.servicedDate >= :policyStartDate)
              AND (CAST(:policyEndDate AS timestamp) IS NULL OR ci.servicedDate <= :policyEndDate)
        """)
    int countMinorSurgeryUsedInterventionsAtLevel3(
            @Param("householdNumber") String householdNumber,
            @Param("interventionCodes") List<String> interventionCodes,
            @Param("use") String use,
            @Param("approvedStatuses") List<String> approvedStatuses,
            @Param("policyStartDate") LocalDate policyStartDate,
            @Param("policyEndDate") LocalDate policyEndDate
    );

    @Query("""
            SELECT DISTINCT c.claimId AS claimId,
                            c.expiryDate AS expiryDate
            FROM ClaimLineItem ci
            JOIN ci.claim c
            JOIN c.patient p
            JOIN c.provider pr
            WHERE p.householdNumber = :householdNumber
              AND ci.code IN :interventionCodes
              AND c.use = :use
              AND c.status IN :approvedStatuses
              AND LOWER(pr.level) LIKE '%3%'
              AND (:excludeClaimId IS NULL OR c.claimId != :excludeClaimId)
              AND (CAST(:policyStartDate AS timestamp) IS NULL OR ci.servicedDate >= :policyStartDate)
              AND (CAST(:policyEndDate AS timestamp) IS NULL OR ci.servicedDate <= :policyEndDate)
        """)
    List<PreAuthProjection> findUsedMinorSurgeryClaimProjectionsAtLevel3(
            @Param("householdNumber") String householdNumber,
            @Param("interventionCodes") List<String> interventionCodes,
            @Param("use") String use,
            @Param("approvedStatuses") List<String> approvedStatuses,
            @Param("excludeClaimId") UUID excludeClaimId,
            @Param("policyStartDate") LocalDate policyStartDate,
            @Param("policyEndDate") LocalDate policyEndDate
    );

    @Query("""
            SELECT DISTINCT c.claimId AS claimId,
                             c.preAuthId AS preAuthId
            FROM ClaimLineItem ci
            JOIN ci.claim c
            JOIN c.patient p
            JOIN c.provider pr
            WHERE p.householdNumber = :householdNumber
              AND ci.code IN :interventionCodes
              AND c.use = :use
              AND c.status IN :approvedStatuses
              AND LOWER(pr.level) LIKE '%3%'
              AND (CAST(:policyStartDate AS timestamp) IS NULL OR ci.servicedDate >= :policyStartDate)
              AND (CAST(:policyEndDate AS timestamp) IS NULL OR ci.servicedDate <= :policyEndDate)
        """)
    List<ClaimPreAuthMappingProjection> findAllUsedMinorSurgeryClaimAtLevel3(
            @Param("householdNumber") String householdNumber,
            @Param("interventionCodes") List<String> interventionCodes,
            @Param("use") String use,
            @Param("approvedStatuses") List<String> approvedStatuses,
            @Param("policyStartDate") LocalDate policyStartDate,
            @Param("policyEndDate") LocalDate policyEndDate
    );



    @Modifying
    @Query("UPDATE InsuranceClaim ic SET ic.isAssigned = false, " +
            "ic.lastModifiedDate = :now " +
            "WHERE EXISTS (" +
            "  SELECT 1 FROM UserBucket ub " +
            "  WHERE ub.claim.claimId = ic.claimId " +
            "    AND ub.user.id = :userId " +
            "    AND ub.status = 'PENDING'" +
            ") AND ic.isAssigned = true")
    int unassignClaimsFromReleasedBuckets(@Param("userId") Long userId,
                                          @Param("now") LocalDateTime now);

    @Query("""
                SELECT ic.claimId AS claimId,
                       ic.expiryDate AS expiryDate,
                       COALESCE(item.shifAmount, 0) AS approvedPrice
                FROM InsuranceClaim ic
                JOIN ic.patient pt
                LEFT JOIN ClaimLineItem item ON ic.claimId = item.claim.claimId
                WHERE pt.crId = :crId
                  AND ic.status IN :status
                  AND item.code LIKE CONCAT(:shaCode, '%')
                  AND ic.use = :use
                  AND (:excludeClaimId IS NULL OR ic.claimId != :excludeClaimId)
                  AND (CAST(:policyStartDate AS timestamp) IS NULL OR item.servicedDate >= :policyStartDate)
                  AND (CAST(:policyEndDate AS timestamp) IS NULL OR item.servicedDate <= :policyEndDate)
            """)
    List<PreAuthProjection> findAllShifPreAuthProjectedByPackage(
            @Param("crId") String crId,
            @Param("status") List<ExtensionStateCodeEnum> status,
            @Param("shaCode") String shaCode,
            @Param("use") String use,
            @Param("excludeClaimId") UUID excludeClaimId,
            @Param("policyStartDate") LocalDate policyStartDate,
            @Param("policyEndDate") LocalDate policyEndDate
    );

    @Query("""
                SELECT DISTINCT
                       ic.claimId AS claimId,
                       COALESCE(item.shifAmount, 0) AS approvedPrice,
                       item.preAuthId AS preAuthId
                FROM InsuranceClaim ic
                JOIN ic.patient pt
                JOIN ClaimLineItem item ON ic.claimId = item.claim.claimId
                WHERE pt.crId = :crId
                  AND ic.status IN :status
                  AND item.code LIKE CONCAT(:shaCode, '%')
                  AND ic.use = :use
                  AND (CAST(:policyStartDate AS timestamp) IS NULL OR item.servicedDate >= :policyStartDate)
                  AND (CAST(:policyEndDate AS timestamp) IS NULL OR item.servicedDate <= :policyEndDate)
            """)
    List<ClaimProjection> findAllShifClaimProjectedByPackage(
            @Param("crId") String crId,
            @Param("status") List<ExtensionStateCodeEnum> status,
            @Param("shaCode") String shaCode,
            @Param("use") String use,
            @Param("policyStartDate") LocalDate policyStartDate,
            @Param("policyEndDate") LocalDate policyEndDate
    );

    @Query("""
                SELECT COALESCE(SUM(COALESCE(item.shifAmount, 0)), 0)
                FROM ClaimLineItem item
                JOIN item.claim ic
                JOIN ic.patient pt
                WHERE pt.crId = :crId
                AND ic.status IN :status
                AND item.code LIKE CONCAT(:shaCodePrefix, '%')
                AND ic.use = :use
                AND (CAST(:policyStartDate AS timestamp) IS NULL OR item.servicedDate >= :policyStartDate)
                AND (CAST(:policyEndDate AS timestamp) IS NULL OR item.servicedDate <= :policyEndDate)
            """)
    BigDecimal getTotalShifAmtOfClaimsByMemberCrIdAndCode(
            @Param("crId") String crId,
            @Param("status") List<ExtensionStateCodeEnum> status,
            @Param("shaCodePrefix") String shaCodePrefix, // pass 'SHA-06'
            @Param("use") String use,
            @Param("policyStartDate") LocalDate policyStartDate,
            @Param("policyEndDate") LocalDate policyEndDate
    );

    // ============ BULK APPROVAL QUERIES ============

    /**
     * Finds eligible claims for bulk approval with row-level locking.
     * Uses FOR UPDATE SKIP LOCKED to prevent race conditions when multiple servers process claims.
     * Only returns claims with exactly one claim item.
     * @param status Optional status filter - if null, uses default statuses (MEDICAL_REVIEW, MANUAL_REVIEW, APPROVED)
     */
    @Query(value = """
        SELECT c.claim_id
        FROM claims c
        JOIN claim_items ci ON c.claim_id = ci.claim_id
        LEFT JOIN claim_reprocess_audit cra ON c.claim_id = cra.claim_id
        WHERE ci.code LIKE :code || '%'
          AND c.status IN ('MEDICAL_REVIEW', 'MANUAL_REVIEW', 'APPROVED')
          AND (CAST(:status AS text) IS NULL OR c.status = CAST(:status AS text))
          AND c."use" = 'Claim'
          AND c.is_phc_claim = false
          AND ci.serviced_date >= :startDate
          AND ci.serviced_date <= :endDate
          AND (SELECT COUNT(*) FROM claim_items ci2 WHERE ci2.claim_id = c.claim_id) = 1
          AND cra.bulk_processing_id IS NULL
        ORDER BY c.claim_number
        LIMIT :batchSize
        FOR UPDATE OF c SKIP LOCKED
        """, nativeQuery = true)
    List<UUID> findEligibleClaimIdsForBulkApproval(
            @Param("code") String code,
            @Param("status") String status,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("batchSize") int batchSize);

    /**
     * Counts eligible claims for bulk approval (for preview/dry-run mode).
     * Returns count of claims with exactly one claim item matching the criteria.
     * @param status Optional status filter - if null, uses default statuses (MEDICAL_REVIEW, MANUAL_REVIEW, APPROVED)
     */
    @Query(value = """
        SELECT COUNT(*) FROM claims c
        JOIN claim_items ci ON c.claim_id = ci.claim_id
        LEFT JOIN claim_reprocess_audit cra ON c.claim_id = cra.claim_id
        WHERE ci.code LIKE :code || '%'
          AND c.status IN ('MEDICAL_REVIEW', 'MANUAL_REVIEW', 'APPROVED')
          AND (CAST(:status AS text) IS NULL OR c.status = CAST(:status AS text))
          AND c."use" = 'Claim'
          AND c.is_phc_claim = false
          AND ci.serviced_date >= :startDate
          AND ci.serviced_date <= :endDate
          AND (SELECT COUNT(*) FROM claim_items ci2 WHERE ci2.claim_id = c.claim_id) = 1
          AND cra.bulk_processing_id IS NULL
        """, nativeQuery = true)
    int countEligibleClaimsForBulkApproval(
            @Param("code") String code,
            @Param("status") String status,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    /**
     * Lists eligible claims for bulk approval with pagination (for UI preview).
     * Uses the same criteria as countEligibleClaimsForBulkApproval to ensure consistency.
     * Filters by serviced_date (not creation_date) to match bulk approval processing.
     */
    @Query(value = """
        SELECT c.claim_id FROM claims c
        JOIN claim_items ci ON c.claim_id = ci.claim_id
        LEFT JOIN claim_reprocess_audit cra ON c.claim_id = cra.claim_id
        WHERE ci.code LIKE :code || '%'
          AND c.status IN ('MEDICAL_REVIEW', 'MANUAL_REVIEW', 'APPROVED')
          AND (CAST(:status AS text) IS NULL OR c.status = CAST(:status AS text))
          AND c."use" = 'Claim'
          AND c.is_phc_claim = false
          AND ci.serviced_date >= :startDate
          AND ci.serviced_date <= :endDate
          AND (SELECT COUNT(*) FROM claim_items ci2 WHERE ci2.claim_id = c.claim_id) = 1
          AND cra.bulk_processing_id IS NULL
        ORDER BY c.claim_number DESC
        """, nativeQuery = true)
    Page<UUID> listEligibleClaimIdsForBulkApproval(
            @Param("code") String code,
            @Param("status") String status,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            Pageable pageable);

    /**
     * Bulk updates claim status and approver for multiple claims.
     * Used for batch approval operations.
     */
    @Modifying
    @Transactional
    @Query("UPDATE InsuranceClaim c SET c.approver1 = :approver WHERE c.claimId IN :claimIds")
    int bulkUpdateStatusAndApprover(
            @Param("claimIds") List<UUID> claimIds,
            @Param("approver") String approver);

    /**
     * Finds claims by their IDs that are in the allowed statuses.
     * Used for Excel upload bulk approval to validate claims before processing.
     */
    @Query("SELECT c FROM InsuranceClaim c WHERE c.claimId IN :claimIds AND c.status IN :statuses")
    List<InsuranceClaim> findByClaimIdInAndStatusIn(
            @Param("claimIds") List<UUID> claimIds,
            @Param("statuses") List<ExtensionStateCodeEnum> statuses);
    // ============ BULK APPROVAL QUERIES ============

    @Query("""
                SELECT COALESCE(SUM(COALESCE(item.approvedPrice, 0) - COALESCE(item.pmfAmount, 0)), 0)
                FROM ClaimLineItem item
                JOIN item.claim ic
                JOIN ic.patient pt
                WHERE pt.crId = :crId
                AND ic.status IN :status
                AND item.code IN :shaCodes
                AND ic.use = :use
                AND (CAST(:policyStartDate AS timestamp) IS NULL OR item.servicedDate >= :policyStartDate)
                AND (CAST(:policyEndDate AS timestamp) IS NULL OR item.servicedDate <= :policyEndDate)
            """)
    BigDecimal getTotalAmtOfClaimsByMemberCrIdAndCodes(
            @Param("crId") String crId,
            @Param("status") List<ExtensionStateCodeEnum> status,
            @Param("shaCodes") List<String> shaCodes,
            @Param("use") String use,
            @Param("policyStartDate") LocalDate policyStartDate,
            @Param("policyEndDate") LocalDate policyEndDate
    );

    @Query("""
                SELECT ic.claimId AS claimId,
                       ic.expiryDate AS expiryDate,
                       COALESCE(item.approvedPrice, 0) AS approvedPrice
                FROM InsuranceClaim ic
                JOIN ic.patient pt
                LEFT JOIN ClaimLineItem item ON ic.claimId = item.claim.claimId
                WHERE pt.crId = :crId
                  AND ic.status IN :status
                  AND item.code IN :shaCode
                  AND ic.use = :use
                  AND (:excludeClaimId IS NULL OR ic.claimId != :excludeClaimId)
                  AND (CAST(:policyStartDate AS timestamp) IS NULL OR item.servicedDate >= :policyStartDate)
                  AND (CAST(:policyEndDate AS timestamp) IS NULL OR item.servicedDate <= :policyEndDate)
            """)
    List<PreAuthProjection> findAllPreAuthProjectedByCodes(
            @Param("crId") String crId,
            @Param("status") List<ExtensionStateCodeEnum> status,
            @Param("shaCode") List<String> shaCode,
            @Param("use") String use,
            @Param("excludeClaimId") UUID excludeClaimId,
            @Param("policyStartDate") LocalDate policyStartDate,
            @Param("policyEndDate") LocalDate policyEndDate
    );

    @Query("""
                SELECT DISTINCT
                       ic.claimId AS claimId,
                       COALESCE(item.approvedPrice, 0) AS approvedPrice,
                       item.preAuthId AS preAuthId
                FROM InsuranceClaim ic
                JOIN ic.patient pt
                JOIN ClaimLineItem item ON ic.claimId = item.claim.claimId
                WHERE pt.crId = :crId
                  AND ic.status IN :status
                  AND item.code IN :shaCode
                  AND ic.use = :use
                  AND (CAST(:policyStartDate AS timestamp) IS NULL OR item.servicedDate >= :policyStartDate)
                  AND (CAST(:policyEndDate AS timestamp) IS NULL OR item.servicedDate <= :policyEndDate)
            """)
    List<ClaimProjection> findAllClaimProjectedByCodes(
            @Param("crId") String crId,
            @Param("status") List<ExtensionStateCodeEnum> status,
            @Param("shaCode") List<String> shaCode,
            @Param("use") String use,
            @Param("policyStartDate") LocalDate policyStartDate,
            @Param("policyEndDate") LocalDate policyEndDate

            );

    @Query(value = """
        SELECT
            -- PreAuth details (main record)
            p.claim_id             AS preAuthId,
            pi.serviced_date       AS preAuthServiceDate,
            pi.approved_price      AS preAuthAmount,
            p.status               AS preAuthStatus,
            p.created_date         AS preauthCreatedDate,
            
            -- Linked Claim details (if exists)
            c.claim_id             AS claimId,
            ci.serviced_date       AS claimServiceDate,
            ci.approved_price      AS claimAmount,
            c.status               AS claimStatus,
            c.created_date         AS claimCreatedDate,
            
            -- Amounts (from pre-auth item)
            pi.shif_amount         AS shifAmount,
            pi.eccif_amount        AS eccifAmount,
            pi.pmf_amount          AS pmfAmount
            
        FROM claims p
        JOIN claim_items pi ON pi.claim_id = p.claim_id
        LEFT JOIN claim_items ci ON ci.pre_auth_id = p.claim_id
        LEFT JOIN claims c ON c.claim_id = ci.claim_id AND c."use" = 'Claim'
        
        WHERE p.patient = :patientId
          AND pi.code = :shaCode
          AND p."use" = 'Preauthorization'
          AND p.created_date <= :anchorCreatedAt
          AND c.policy_start_date >= :policyStartDate
          AND c.policy_end_date <= :policyEndDate
        ORDER BY p.created_date DESC
        """, nativeQuery = true)
    List<PreAuthUtilizationProjection> findPreAuthUtilizationHistory(
            @Param("patientId") UUID patientId,
            @Param("shaCode") String shaCode,
            @Param("anchorCreatedAt") LocalDateTime anchorCreatedAt,
            @Param("policyStartDate") LocalDateTime policyStartDate,
            @Param("policyEndDate") LocalDateTime policyEndDate
    );

    @Query(value = """
        SELECT
            -- Claim details
            c.claim_id             AS claimId,
            ci.serviced_date       AS claimServiceDate,
            ci.approved_price      AS claimAmount,
            c.status               AS claimStatus,
            c.created_date         AS claimCreatedDate,
            
            -- Pre-auth link
            ci.pre_auth_id         AS preAuthId,
            
            -- Amounts (from claim item)
            ci.shif_amount         AS shifAmount,
            ci.eccif_amount        AS eccifAmount,
            ci.pmf_amount          AS pmfAmount
            
        FROM claims c
        JOIN claim_items ci ON ci.claim_id = c.claim_id
        
        WHERE c.patient = :patientId
          AND ci.code = :shaCode
          AND c."use" = 'Claim'
          AND c.created_date <= :anchorCreatedAt
          AND c.policy_start_date >= :policyStartDate
          AND c.policy_end_date <= :policyEndDate
        ORDER BY c.created_date DESC
        """, nativeQuery = true)
    List<ClaimUtilizationProjection> findClaimUtilizationHistory(
            @Param("patientId") UUID patientId,
            @Param("shaCode") String shaCode,
            @Param("anchorCreatedAt") LocalDateTime anchorCreatedAt,
            @Param("policyStartDate") LocalDateTime policyStartDate,
            @Param("policyEndDate") LocalDateTime policyEndDate
    );

    @Query("Select ic.status from InsuranceClaim ic where ic.claimId = :claimId")
    Optional<ExtensionStateCodeEnum> getClaimStatus(@Param("claimId") UUID ClaimId);


    @Query(value = """
    SELECT c.claim_id
    FROM claims c
    WHERE c.claim_id = :claimId
    FOR UPDATE SKIP LOCKED
    """, nativeQuery = true)
    Optional<UUID> findByClaimIdForUpdateSkipLocked(UUID claimId);

    @Query(value = """
       SELECT ic.claim_id AS claimId,
              ic.status AS status
       FROM insurance_claim ic
       WHERE ic.claim_id::text IN (:claimIds)
       """,
            nativeQuery = true)
    List<ClaimStatusProjection> getClaimStatuses(
            @Param("claimIds") List<String> claimIds);


    @Query(
            value = """
            SELECT EXISTS (
                SELECT 1
                FROM user_bucket ub
                JOIN users u ON ub.user_id = u.id
                WHERE ub.claim_id = :claimId
                AND u.username = :userId
                AND ub.status = :bucketStatus
            )
            """,
            nativeQuery = true
    )
    boolean existsAssignedClaimForUser(
            @Param("claimId") UUID claimId,
            @Param("userId") String userId,
            @Param("bucketStatus") String bucketStatus);

}