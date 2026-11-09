package io.coredge.health.claims.db.repositories;

import io.coredge.health.claims.db.entity.BatchApprovalRequest;
import io.coredge.health.claims.enums.BatchApprovalStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BatchApprovalRequestRepository extends JpaRepository<BatchApprovalRequest, Long> {

    List<BatchApprovalRequest> findByBatchStatus(BatchApprovalStatus status);

    Page<BatchApprovalRequest> findByInitiatedByOrderByCreatedAtDesc(String initiatedBy, Pageable pageable);

    Page<BatchApprovalRequest> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
