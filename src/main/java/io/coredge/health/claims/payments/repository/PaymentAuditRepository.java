package io.coredge.health.claims.payments.repository;

import io.coredge.health.claims.payments.entity.PaymentAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentAuditRepository extends JpaRepository<PaymentAudit, Long> {
}
