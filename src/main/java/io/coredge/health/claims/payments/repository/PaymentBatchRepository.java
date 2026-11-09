package io.coredge.health.claims.payments.repository;

import io.coredge.health.claims.payments.entity.PaymentBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentBatchRepository extends JpaRepository<PaymentBatch, String> {
}
