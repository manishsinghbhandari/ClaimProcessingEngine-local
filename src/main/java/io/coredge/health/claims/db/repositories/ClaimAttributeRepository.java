package io.coredge.health.claims.db.repositories;

import io.coredge.health.claims.db.entity.ClaimAttribute;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClaimAttributeRepository extends JpaRepository<ClaimAttribute, Long> {

    List<ClaimAttribute> findByClaimId(UUID claimId);

    @Query("SELECT ca FROM ClaimAttribute ca WHERE ca.claimId = ?1 AND ca.status = 'ACTIVE'")
    Optional<ClaimAttribute> findActiveAttributeByClaimId(UUID claimId);
}
