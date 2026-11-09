package io.coredge.health.claims.db.repositories;

import io.coredge.health.claims.db.entity.ClaimBundleLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ClaimBundleLogRepository extends JpaRepository<ClaimBundleLogEntity, Long> {

    @Query(value = "SELECT COUNT(cbl.claim_id) FROM claim_bundle_log cbl where cbl.claim_id = :claimId", nativeQuery = true)
    Long countClaimId(@Param("claimId") UUID claimId);


}
