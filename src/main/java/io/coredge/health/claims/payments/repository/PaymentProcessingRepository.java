package io.coredge.health.claims.payments.repository;

import io.coredge.health.claims.payments.entity.PaymentProcessing;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

@Repository
public interface PaymentProcessingRepository extends JpaRepository<PaymentProcessing, Long> {

        List<PaymentProcessing> findByBatchId(String batchId);

        List<PaymentProcessing> findByBatchIdAndPaymentStatus(String batchId, String paymentStatus);

        Page<PaymentProcessing> findByBatchIdAndPaymentStatus(String batchId, String paymentStatus, Pageable pageable);

        @Query("SELECT p FROM PaymentProcessing p WHERE p.batchId = :batchId AND p.paymentStatus = :paymentStatus")
        Stream<PaymentProcessing> streamByBatchIdAndPaymentStatus(String batchId, String paymentStatus);

        List<PaymentProcessing> findByProviderIdAndPaymentStatus(String providerId, String status);

        @Query("SELECT DISTINCT p.providerId FROM PaymentProcessing p WHERE p.paymentStatus = :status")
        List<String> findDistinctProviderIdsByStatus(String status);

        Optional<PaymentProcessing> findByClaimId(String claimId);

        @Modifying
        @Query("UPDATE PaymentProcessing p SET p.paymentStatus = :status, " +
                        "p.bankReferenceNumber = :bankRef, p.paymentCompletionTime = :completionTime, " +
                        "p.remarks = :remarks, " +
                        "p.lastModifiedDate = :now, p.lastModifiedBy = :modifiedBy " +
                        "WHERE p.batchId = :batchId")
        void updateBatchClaimsStatus(String batchId, String status, String bankRef, LocalDateTime completionTime,
                        String remarks, LocalDateTime now, String modifiedBy);

        @Modifying
        @Query("UPDATE PaymentProcessing p SET p.batchId = :batchId, " +
                        "p.lastModifiedDate = :now, p.lastModifiedBy = :modifiedBy " +
                        "WHERE p.claimId IN :claimIds")
        void updateBatchIdByClaimIdIn(String batchId, List<String> claimIds, LocalDateTime now, String modifiedBy);

        @Modifying
        @Query("UPDATE PaymentProcessing p SET p.paymentStatus = :status, p.remarks = :remarks, p.batchId = :batchId, "
                        +
                        "p.lastModifiedDate = :now, p.lastModifiedBy = :modifiedBy " +
                        "WHERE p.claimId IN :claimIds")
        void updateStatusRemarksAndBatchIdByClaimIdIn(String status, String remarks, String batchId,
                        List<String> claimIds, LocalDateTime now, String modifiedBy);

        @Modifying
        @Query("UPDATE PaymentProcessing p SET p.remarks = :remarks, p.batchId = :batchId, " +
                        "p.lastModifiedDate = :now, p.lastModifiedBy = :modifiedBy " +
                        "WHERE p.claimId IN :claimIds")
        void updateRemarksAndBatchIdByClaimIdIn(String remarks, String batchId, List<String> claimIds,
                        LocalDateTime now, String modifiedBy);

        @Modifying
        @Query("UPDATE PaymentProcessing p SET p.remarks = :remarks, " +
                        "p.lastModifiedDate = :now, p.lastModifiedBy = :modifiedBy " +
                        "WHERE p.claimId IN :claimIds AND p.batchId = :batchId")
        void updateRemarksByClaimIdsAndBatchId(String remarks, List<String> claimIds, String batchId,
                        LocalDateTime now, String modifiedBy);
}
