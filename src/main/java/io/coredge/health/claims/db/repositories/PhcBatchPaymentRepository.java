package io.coredge.health.claims.db.repositories;

import io.coredge.health.claims.db.entity.PhcBatchPayment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface PhcBatchPaymentRepository extends JpaRepository<PhcBatchPayment, Long>, JpaSpecificationExecutor<PhcBatchPayment> {

    boolean existsByDate(LocalDate date);

    List<PhcBatchPayment> findByStatus(PhcBatchPayment.Status status);

    long countByStatus(PhcBatchPayment.Status status);

    List<PhcBatchPayment> findByDateBetween(LocalDate startDate, LocalDate endDate);

    List<PhcBatchPayment> findByStatusAndDateBetween(PhcBatchPayment.Status status, LocalDate startDate, LocalDate endDate);

    // Report query methods
    List<PhcBatchPayment> findByDate(LocalDate date);

    // Check if batch payment exists for specific admin level and month
    boolean existsByAdminLevelPopulationIdAndDate(Long adminLevelPopulationId, LocalDate date);

    // Find batch payment by admin level and date
    Optional<PhcBatchPayment> findByAdminLevelPopulationIdAndDate(Long adminLevelPopulationId, LocalDate date);

    @Query(value = """
                SELECT pbp
                FROM PhcBatchPayment pbp
                JOIN pbp.adminLevelPopulation alp
                JOIN alp.adminLevelEntity ale
                WHERE (:status IS NULL OR pbp.status = :status)
                AND pbp.status <> io.coredge.health.claims.db.entity.PhcBatchPayment.Status.REJECTED
                AND (CAST(:startDate AS date) IS NULL OR pbp.date >= :startDate)
                AND (CAST(:endDate AS date) IS NULL OR pbp.date <= :endDate)
                AND (:countyName IS NULL OR ale.name = :countyName)
            """)
    Optional<Page<PhcBatchPayment>> findPaymentsOptionalParams(@Param("status") PhcBatchPayment.Status status,
                                                     @Param("startDate") LocalDate startDate,
                                                     @Param("endDate") LocalDate endDate,
                                                     @Param("countyName") String countyName,
                                                     Pageable pageable);
}