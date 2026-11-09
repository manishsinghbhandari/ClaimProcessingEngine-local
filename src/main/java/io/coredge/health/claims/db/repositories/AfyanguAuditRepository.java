package io.coredge.health.claims.db.repositories;

import io.coredge.health.claims.db.entity.AfyanguClaimsAudit;
import io.coredge.health.claims.db.entity.CivilServantClaimsAudit;
import org.springframework.data.domain.Range;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AfyanguAuditRepository extends JpaRepository<AfyanguClaimsAudit, Long> {

    Optional<AfyanguClaimsAudit> findByClaimId(String claimId);

    Optional<AfyanguClaimsAudit> findByClaimIdAndStatus(String claimId, String identifierType);

    Optional<AfyanguClaimsAudit> findByClaimIdAndClaimStatusAndIdentifierType(String claimId, String claimStatus, String identifierType);

    @Query("SELECT a.claimId FROM AfyanguClaimsAudit a WHERE a.claimId IN :claimIds")
    List<String> findExistingClaimIds(@Param("claimIds") List<String> claimIds);

    Optional<AfyanguClaimsAudit> findByClaimIdAndIdentifierType(String claimId, String identifierType);
}
