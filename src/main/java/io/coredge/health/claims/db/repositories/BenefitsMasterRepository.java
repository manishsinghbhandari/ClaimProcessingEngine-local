package io.coredge.health.claims.db.repositories;

import io.coredge.health.claims.db.entity.AiRoute;
import io.coredge.health.claims.db.entity.BenefitsMaster;
import io.coredge.health.claims.models.DropDown;
import io.coredge.health.claims.models.benefits.BenefitsMasterProjection;
import io.coredge.health.claims.db.entity.utilization.BenefitsMasterView;
import io.coredge.health.claims.models.benefits.FundProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BenefitsMasterRepository extends JpaRepository<BenefitsMaster, Long> {

        Optional<List<BenefitsMaster>> findByInterventionCode(String interventionCode);

        Optional<List<BenefitsMaster>> findByInterventionNameContainingIgnoreCase(String interventionName);

        Optional<List<BenefitsMaster>> findByCodeAndInterventionNameContainingIgnoreCase(String code,
                        String interventionName);

        Optional<List<BenefitsMaster>> findByShaCategory(String shaCategory);

        Optional<List<BenefitsMaster>> findBySubCategoryBenefitsPackage(String subCategoryBenefitsPackage);

        Optional<List<BenefitsMaster>> findByShaCategoryContainingIgnoreCase(String shaCategory);

        Optional<List<BenefitsMaster>> findByCode(String code);

        Optional<List<BenefitsMaster>> findByInterventionCodeIn(List<String> interventionCodes);

        @Query("select bm.interventionCode from BenefitsMaster bm where bm.interventionCode in :interventionCodes and bm.needsPreAuth = true ")
        List<String> findByInterventionCodeInAndNeedsPreAuthIsTrue(List<String> interventionCodes);

        Optional<List<BenefitsMaster>> findByInterventionCodeNotIn(List<String> interventionCodes);

        boolean existsByInterventionCode(String interventionCode);

        @Query("SELECT bm FROM BenefitsMaster bm "
                        + "WHERE (:interventionCode IS NULL OR bm.interventionCode = :interventionCode) " +
                        "AND (:shaCategory IS NULL OR bm.shaCategory = :shaCategory) "
                        + "AND (:interventionName IS NULL OR bm.interventionName = :interventionName)")
        Optional<Page<BenefitsMaster>> findBenefitsByOptionalParams(@Param("interventionCode") String interventionCode,
                        @Param("shaCategory") String shaCategory,
                        @Param("interventionName") String interventionName, Pageable pageable);

        boolean existsByInterventionCodeAndProviderPaymentMechanism(String interventionCode,
                        String providerPaymentMechanism);

        boolean existsByInterventionCodeInAndProviderPaymentMechanism(List<String> interventionCodes,
                        String providerPaymentMechanism);

        @Query("SELECT COUNT(b) > 0 FROM BenefitsMaster b WHERE b.interventionCode = :code AND b.appliesToSchemes = :exactValue")
        boolean existsByInterventionCodeAndExactAppliesToSchemes(@Param("code") String interventionCode,
                        @Param("exactValue") String exactValue);

        @Query("SELECT COUNT(b) > 0 FROM BenefitsMaster b WHERE b.interventionCode = :code AND b.appliesToSchemes IN :values")
        boolean existsByInterventionCodeAndAnyAppliesToSchemes(@Param("code") String interventionCode,
                        @Param("values") List<String> values);

        @Query(value = "SELECT intervention_code FROM benefits_master WHERE is_per_diem_club = true", nativeQuery = true)
        List<String> findPerDiemClubInterventionCodes();

        @Query("""
                            SELECT bm FROM BenefitsMaster bm
                            WHERE (:interventionCode IS NULL OR bm.interventionCode = :interventionCode)
                              AND (:shaCategory IS NULL OR bm.shaCategory = :shaCategory)
                              AND (:code IS NULL OR bm.code = :code)
                              AND (:interventionName IS NULL OR bm.interventionName = :interventionName)
                              AND NOT (bm.code LIKE 'SHA-02%' OR bm.code LIKE 'SHA-04%' OR bm.code LIKE 'SHA-11%' OR bm.code LIKE 'SHA-15%')
                        """)
        Optional<Page<BenefitsMaster>> findBenefitsByAllOptionalParams(
                        @Param("interventionCode") String interventionCode,
                        @Param("shaCategory") String shaCategory,
                        @Param("code") String code,
                        @Param("interventionName") String interventionName,
                        Pageable pageable);

        @Query("""
                            SELECT DISTINCT
                                b.providerPaymentMechanism AS providerPaymentMechanism,
                                b.limitsIndividualHousehold AS limitsIndividualHousehold,
                                b.interventionQuantityYear AS interventionQuantityYear,
                                b.appliesToSchemes AS appliesToSchemes,
                                b.bedType AS bedType,
                                b.accessCode AS accessCode,
                                b.gender AS gender,
                                b.needsPreAuth AS needsPreAuth
                            FROM BenefitsMaster b
                        """)
        List<BenefitsMasterProjection> findAllDropdownValues();

        Optional<BenefitsMaster> findByInterventionCodeAndProviderPaymentMechanism(String interventionCode,
                        String providerPaymentMechanism);

        @Query("""
                        SELECT bm.interventionCode AS interventionCode,
                               bm.code AS code,
                               bm.dateFrom AS dateFrom,
                               bm.dateTill AS dateTill
                        FROM BenefitsMaster bm
                        """)
        List<BenefitsMasterView> findBenefitMasterRecords();

        List<BenefitsMaster> findByCodeOrInterventionCode(String code, String interventionCode);

        boolean existsByInterventionCodeAndEfwaCheckEnabledIsTrue(String interventionCode);

        boolean existsByInterventionCodeAndPreauthEfwaEnabledIsTrue(String interventionCode);

        @Query("""
                            SELECT b.phc AS phc,
                                   b.shif AS shif,
                                   b.eccif AS eccif,
                                   b.pmsf AS pmsf,
                                   b.interventionCode as interventionCode
                            FROM BenefitsMaster b
                            WHERE b.interventionCode IN :interventionCode
                        """)
        List<FundProjection> findFundByInterventionCodes(@Param("interventionCode") List<String> interventionCode);

        Page<BenefitsMaster> findByCodeIgnoreCaseStartingWith(String code, Pageable pageable);

        Page<BenefitsMaster> findByInterventionCodeIgnoreCaseStartingWith(String interventionCode, Pageable pageable);

        @Query("""
                            SELECT bm.id
                            FROM BenefitsMaster bm
                            WHERE LOWER(bm.code) IN :codes
                               OR LOWER(bm.interventionCode) IN :codes
                        """)
        List<Long> findAllIdsByCodeOrInterventionCodeIn(@Param("codes") List<String> codes);

        @Query("SELECT bm.id FROM BenefitsMaster bm")
        List<Long> findAllIds();

        @Query("""
                            SELECT DISTINCT new io.coredge.health.claims.models.DropDown(
                                cm.code,
                                CONCAT(cm.categoryName, ' ( ', cm.code, ' )')
                            )
                            FROM CategoryMaster cm
                            WHERE (:code IS NULL OR :code = '' OR UPPER(cm.code) LIKE CONCAT(UPPER(:code),'%'))
                        """)
        List<DropDown> findBenefitMasterPackageRecords(@Param("code") String code);

        @Query("""
                            SELECT DISTINCT new io.coredge.health.claims.models.DropDown(
                                bm.interventionCode,
                                CONCAT(bm.interventionName, ' ( ', bm.interventionCode, ' )')
                            )
                            FROM BenefitsMaster bm
                            WHERE (:code IS NULL OR :code = '' OR UPPER(bm.interventionCode) LIKE CONCAT(UPPER(:code), '%'))
                        """)
        List<DropDown> findBenefitMasterSubPackageRecords(@Param("code") String code);

        /**
         * Finds all intervention codes for a given package that are classified as
         * "Minor" surgery.
         *
         * @param packageCode The package code to search for (e.g., "SHA-19").
         * @return A list of intervention codes for minor surgeries within that package.
         */
        @Query("SELECT bm.interventionCode FROM BenefitsMaster bm WHERE bm.code LIKE :packageCode% AND bm.complexity = :complexity")
        List<String> findInterventionCodesByPackageCodeAndComplexity(@Param("packageCode") String packageCode,
                        @Param("complexity") String complexity);

        @Query("SELECT b.interventionCode FROM BenefitsMaster b WHERE b.subPackage = :subPackage")
        Optional<List<String>> findInterventionCodesBySubPackage(String subPackage);

        @Query("SELECT b.subPackage FROM BenefitsMaster b WHERE b.interventionCode = :interventionCode")
        Optional<String> findSubPackageByInterventionCode(String interventionCode);

        /**
         * Find the AI route configured for a specific intervention code.
         * 
         * @return the AI route if configured, empty otherwise
         */
        @Query("SELECT b.aiRoute FROM BenefitsMaster b WHERE b.interventionCode = :code AND b.aiRoute IS NOT NULL")
        Optional<AiRoute> findAiRouteByInterventionCode(@Param("code") String code);

}
