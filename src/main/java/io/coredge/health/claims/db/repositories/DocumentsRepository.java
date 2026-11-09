package io.coredge.health.claims.db.repositories;

import io.coredge.health.claims.db.entity.Documents;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DocumentsRepository extends JpaRepository<Documents, Long> {
  Optional<List<Documents>> findByClaimId(String claimId);
}
