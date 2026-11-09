package io.coredge.health.claims.db.repositories;

import io.coredge.health.claims.db.entity.IcdCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * PostgreSQL repository for ICD codes - replaces MongoDB ICDCodeRepository
 * Used for PHC payment calculations
 */
@Repository
public interface IcdCodesRepository extends JpaRepository<IcdCode, Long> {

    /**
     * Find ICD Code by ICD 10 Disease Code
     */
    Optional<IcdCode> findByIcd10DiseaseCode(String icd10DiseaseCode);

    /**
     * Find ICD Code by ICD 11 CODES
     */
    Optional<IcdCode> findByIcd11Codes(String icd11Codes);

    /**
     * Find ICD Codes by list of ICD 11 codes
     */
    @Query("SELECT i FROM IcdCode i WHERE i.icd11Codes IN :icdCodes")
    List<IcdCode> findByIcd11CodesIn(@Param("icdCodes") List<String> icdCodes);

    /**
     * Find ICD Codes by list of ICD 10 codes
     */
    @Query("SELECT i FROM IcdCode i WHERE i.icd10DiseaseCode IN :icdCodes")
    List<IcdCode> findByIcd10DiseaseCodeIn(@Param("icdCodes") List<String> icdCodes);

    /**
     * Find all ICD codes with their weights for PHC calculations
     */
    @Query("SELECT i FROM IcdCode i ORDER BY i.icd10DiseaseCode")
    List<IcdCode> findAllOrderByIcd10DiseaseCode();

    /**
     * Check if ICD code exists by ICD 10 or ICD 11 code
     */
    @Query("SELECT CASE WHEN COUNT(i) > 0 THEN true ELSE false END FROM IcdCode i WHERE i.icd10DiseaseCode = :code OR i.icd11Codes = :code")
    boolean existsByIcd10DiseaseCodeOrIcd11Codes(@Param("code") String code);
}
