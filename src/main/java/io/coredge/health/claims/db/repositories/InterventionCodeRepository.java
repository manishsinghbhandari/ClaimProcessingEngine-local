package io.coredge.health.claims.db.repositories;

import io.coredge.health.claims.db.entity.InterventionCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.Optional;

@Repository
public interface InterventionCodeRepository extends JpaRepository<InterventionCode, Long> {

    @Query(value = """
    SELECT * 
    FROM intervention_code ic 
    WHERE :inputCode LIKE '%' || ic.code || '%' 
      AND ic.effective_date <= :effectiveDate
    """, nativeQuery = true)
    Optional<InterventionCode> findCodeThatSubStringOf(
            @Param("inputCode") String inputCode,
            @Param("effectiveDate") Date effectiveDate
    );
}
