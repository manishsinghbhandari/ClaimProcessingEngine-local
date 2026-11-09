package io.coredge.health.claims.db.repositories;

import io.coredge.health.claims.db.entity.Diagnosis;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DiagnosisRepository extends JpaRepository<Diagnosis, Long> {
  Optional<List<Diagnosis>> findByCode(String code);

  Optional<List<Diagnosis>> findByClaimId(String claimId);
}
