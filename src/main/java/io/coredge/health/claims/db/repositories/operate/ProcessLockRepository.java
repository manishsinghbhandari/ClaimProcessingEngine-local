package io.coredge.health.claims.db.repositories.operate;

import io.coredge.health.claims.db.entity.operate.ProcessLock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
public interface ProcessLockRepository extends JpaRepository<ProcessLock, String> {

    @Modifying
    @Transactional
    @Query(value = "INSERT INTO process_lock(claim_id, locked_by, locked_at) " +
            "VALUES (:claimId, :lockedBy, CURRENT_TIMESTAMP) " +
            "ON CONFLICT (claim_id) DO NOTHING", nativeQuery = true)
    int acquireLock(@Param("claimId") String claimId, @Param("lockedBy") String lockedBy);

    @Query(value = "SELECT * FROM process_lock WHERE claim_id = :claimId AND locked_at < NOW() - INTERVAL '2 minutes'", nativeQuery = true)
    Optional<ProcessLock> findStaleLock(@Param("claimId") String claimId);
}