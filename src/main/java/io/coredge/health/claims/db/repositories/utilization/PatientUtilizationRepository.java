package io.coredge.health.claims.db.repositories.utilization;

import io.coredge.health.claims.db.entity.utilization.PatientUtilization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface PatientUtilizationRepository extends JpaRepository<PatientUtilization, Long> {

    @Query("""
        SELECT p
            FROM PatientUtilization p
            WHERE p.patient.crId = :patientId
                AND (p.benefitsMaster.code = :benefitCode OR 
                p.benefitsMaster.interventionCode = :benefitCode)
""")
    List<PatientUtilization> findByBenefitCodeAndPatient(
            @Param("benefitCode") String benefitCode,
            @Param("patientId") String patientId);

    @Transactional
    @Modifying
    @Query(value = """
    DELETE FROM fund_utilization_limit f
    USING patient_utilization p
    WHERE f.utilization = p.id
      AND p.valid_till < CURRENT_DATE
    """, nativeQuery = true)
    void deleteExpiredFundUtilizationLimits();

    @Transactional
    @Modifying
    @Query(value = """
    DELETE FROM patient_utilization
    WHERE valid_till < CURRENT_DATE
    """, nativeQuery = true)
    void deleteExpiredPatientUtilization();

}