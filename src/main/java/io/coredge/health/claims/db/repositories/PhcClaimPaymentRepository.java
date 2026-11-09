package io.coredge.health.claims.db.repositories;

import io.coredge.health.claims.db.entity.PhcBatchPayment;
import io.coredge.health.claims.db.entity.PhcClaimPayment;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PhcClaimPaymentRepository
    extends JpaRepository<PhcClaimPayment, Long>, JpaSpecificationExecutor<PhcClaimPayment> {

  List<PhcClaimPayment> findByPhcBatchPayment(PhcBatchPayment batchPayment);

  List<PhcClaimPayment> findByStatus(String status);

  long countByStatus(String status);

    List<PhcClaimPayment> findByProviderId(Long providerId);
    
    List<PhcClaimPayment> findByStatusAndPhcBatchPayment(String status, PhcBatchPayment batchPayment);
    
    // Report query methods
    List<PhcClaimPayment> findByBatchPreparationDate(LocalDate date);
    
    @Query("SELECT pcp FROM PhcClaimPayment pcp WHERE pcp.batchPreparationDate = :date")
    List<PhcClaimPayment> findAllByBatchPreparationDate(@Param("date") LocalDate date);

    // Find facility payment by provider and batch
    Optional<PhcClaimPayment> findByProviderIdAndPhcBatchPayment(Long providerId, PhcBatchPayment batchPayment);
    
    // Healer query - find facility payments by date and status
    List<PhcClaimPayment> findByBatchPreparationDateAndStatus(LocalDate batchPreparationDate, String status);
}

