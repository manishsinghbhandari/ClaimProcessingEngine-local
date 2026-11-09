package io.coredge.health.claims.db.repositories;

import io.coredge.health.claims.db.entity.PackageMaster;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PackageMasterRepository extends JpaRepository<PackageMaster, Long> {
   Optional<PackageMaster> findByCode(String code);
}
