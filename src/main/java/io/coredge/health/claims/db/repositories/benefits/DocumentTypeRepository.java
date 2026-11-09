package io.coredge.health.claims.db.repositories.benefits;

import io.coredge.health.claims.db.entity.benefits.DocumentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DocumentTypeRepository extends JpaRepository<DocumentType, Long> {

    @Query(value = """
        SELECT dt.* 
        FROM document_types dt
        JOIN benefit_document_type_mapping bdtm 
             ON dt.id = bdtm.document_type_id
        WHERE bdtm.benefit_master_id = :benefitsMasterId
        """,
            nativeQuery = true)
    List<DocumentType> findByBenefitMasterIdAndUse(
            @Param("benefitsMasterId") Long benefitsMasterId);
}
