package io.coredge.health.claims.db.repositories.AiNotes;

import io.coredge.health.claims.db.entity.ClaimAINotes;
import io.coredge.health.claims.db.entity.InsuranceClaim;
import io.coredge.health.claims.enums.ExtensionStateCodeEnum;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClaimAiNotesRepository extends JpaRepository<ClaimAINotes, Long> {

    /**
     * Find the AI notes for a specific claim ID.
     *
     * @param claimId the ID of the claim
     * @return the AI notes associated with the claim, or null if not found
     */
    @Query("""
            SELECT ain FROM ClaimAINotes ain WHERE ain.claim.claimId = :claimId
            """)
    ClaimAINotes findByClaimId(UUID claimId);


    @Query("""
            SELECT ain
            FROM ClaimAINotes ain
            JOIN ain.claim ic
            JOIN ic.provider p
            JOIN ic.patient pt
            LEFT JOIN ClaimLineItem item ON ic.claimId = item.claim.claimId
              WHERE (:shaCode IS NULL OR item.code IN :shaCode)
               AND (:claimNumber IS NULL OR ic.claimNumber = :claimNumber)
               AND (:claimId IS NULL OR ic.claimId = :claimId)
               AND (:status IS NULL OR ic.status = :status)
               AND (:accessCode IS NULL OR ic.accessCode = :accessCode)
               AND (:auditFlag IS NULL OR ic.auditFlag = :auditFlag)
               AND (:providerName IS NULL OR p.name ILIKE %:providerName%)
               AND (:patientName IS NULL OR pt.name ILIKE %:patientName%)
               AND (:crId IS NULL OR pt.crId = :crId)
               AND (:shaNumber IS NULL OR pt.shaNumber = :shaNumber)
               AND (:nationalId IS NULL OR pt.nationalId = :nationalId)
               AND (CAST(:startDate AS timestamp) IS NULL OR ic.creationDate >= :startDate)
               AND (CAST(:endDate AS timestamp) IS NULL OR ic.creationDate <= :endDate)
               ORDER BY ain.creationDate DESC
        """)
    Page<ClaimAINotes> findAllClaimsNotesWithAllOptionalParams(
            @Param("shaCode") List<String> shaCode,
            @Param("claimId") UUID claimId,
            @Param("providerName") String providerName,
            @Param("accessCode") String accessCode,
            @Param("patientName") String patientName,
            @Param("crId") String crId,
            @Param("nationalId") String nationalId,
            @Param("shaNumber") String shaNumber,
            @Param("claimNumber") Long claimNumber,
            @Param("status") ExtensionStateCodeEnum status,
            @Param("auditFlag") Boolean auditFlag,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable);

    @Query("""
            SELECT ain.claim.claimId FROM ClaimAINotes ain WHERE ain.claim.claimId = :claimId""")
    Optional<UUID>  findClaimUuidByClaimId(UUID claimId);

}
