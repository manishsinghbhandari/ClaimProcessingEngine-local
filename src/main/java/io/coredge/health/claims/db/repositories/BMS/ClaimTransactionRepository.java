package io.coredge.health.claims.db.repositories.BMS;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import io.coredge.health.claims.db.entity.BMS.ClaimTransaction;

@Repository
public interface ClaimTransactionRepository extends JpaRepository<ClaimTransaction, Long> {
    Optional<ClaimTransaction> findTopByClaim_ClaimIdOrderByCreatedAtDesc(UUID claimId);
}