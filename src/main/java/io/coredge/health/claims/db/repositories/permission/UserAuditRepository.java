package io.coredge.health.claims.db.repositories.permission;

import io.coredge.health.claims.db.entity.permission.AuditUserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserAuditRepository extends JpaRepository<AuditUserStatus,Long> {
}
