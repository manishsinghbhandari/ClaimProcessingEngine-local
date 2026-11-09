package io.coredge.health.claims.db.repositories.BMS;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import io.coredge.health.claims.db.entity.BMS.ClaimStatus;
import java.util.Optional;
import java.util.List;

@Repository
public interface ClaimStatusRepository extends JpaRepository<ClaimStatus, Long> {
    // Find by autoCode
    Optional<ClaimStatus> findByAutoCode(String autoCode);

    // Find by name
    List<ClaimStatus> findByName(String name);

    // Delete by autoCode
    void deleteByAutoCode(String autoCode);
}