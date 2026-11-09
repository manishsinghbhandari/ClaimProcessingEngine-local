package io.coredge.health.claims.db.repositories;

import io.coredge.health.claims.db.entity.CivilServantClaimsAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CivilServantAuditRepository extends JpaRepository<CivilServantClaimsAudit, Long> {
    Optional<CivilServantClaimsAudit> findByClaimId(String claimId);
    List<CivilServantClaimsAudit> findAllByClaimId(String claimId);
}
