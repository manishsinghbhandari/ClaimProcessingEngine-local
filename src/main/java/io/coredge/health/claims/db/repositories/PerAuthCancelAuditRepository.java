package io.coredge.health.claims.db.repositories;

import io.coredge.health.claims.db.entity.PreAuthCancelAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PerAuthCancelAuditRepository extends JpaRepository<PreAuthCancelAudit, Long> {
}
