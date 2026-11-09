package io.coredge.health.claims.db.repositories;

import io.coredge.health.claims.db.entity.ClaimPractitionerMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ClaimPractitionerMappingRepository extends JpaRepository<ClaimPractitionerMapping , Long> {
}
