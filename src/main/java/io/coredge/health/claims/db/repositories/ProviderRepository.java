package io.coredge.health.claims.db.repositories;

import io.coredge.health.claims.db.entity.Provider;

import java.util.List;
import java.util.Optional;

import io.coredge.health.claims.models.DropDown;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ProviderRepository extends JpaRepository<Provider, Long> , JpaSpecificationExecutor<Provider> {

    Optional<List<Provider>> findByMflCode(String mflCode);

    Optional<List<Provider>> findByFidCode(String fidCode);

    Optional<Provider> findOneByFidCode(String fidCode);

    Optional<List<Provider>> findByRegNo(String regNo);

    Optional<Page<Provider>> findByLevelIgnoreCase(String level, Pageable pageable);

    Optional<Boolean> existsByIdentifier(String identifier);

    Optional<Boolean> existsByMflCode(String mflCode);

    Optional<List<Provider>> findByIdentifier(String identifier);

    @Query("SELECT p FROM Provider p WHERE LOWER(p.name) LIKE LOWER(CONCAT('%', :searchKey, '%')) " +
            "AND p.id IN (SELECT MIN(p2.id) FROM Provider p2 GROUP BY p2.name)")
    Optional<Page<Provider>> findDistinctByNameContainingIgnoreCase(String searchKey, Pageable page);

    @Query("""
        SELECT DISTINCT new io.coredge.health.claims.models.DropDown(
            TRIM(LOWER(p.level))
        )
        FROM Provider p
        WHERE LOWER(TRIM(p.level)) LIKE LOWER(CONCAT('%', :searchKey, '%'))
        """)
    List<DropDown> findDistinctByLevelDropdownIgnoreCase(String searchKey);

    @Query("""
        SELECT DISTINCT new io.coredge.health.claims.models.DropDown(
            TRIM(LOWER(p.facilityAgent))
        )
        FROM Provider p
        WHERE LOWER(TRIM(p.facilityAgent)) LIKE LOWER(CONCAT('%', :searchKey, '%'))
        """)
    List<DropDown> findDistinctByFacilityAgentDropdownIgnoreCase(String searchKey);

    @Query("""
        SELECT DISTINCT new io.coredge.health.claims.models.DropDown(
            TRIM(LOWER(p.fidCode)),
            CONCAT(TRIM(p.name), ' ( ', p.fidCode, ' )')
        )
        FROM Provider p
        WHERE LOWER(TRIM(p.name)) LIKE LOWER(CONCAT('%', :searchKey, '%'))
        """)
    List<DropDown> findDistinctByNameDropdownIgnoreCase(String searchKey);

    @Query("SELECT DISTINCT p.fidCode FROM Provider p WHERE LOWER(p.fidCode) LIKE LOWER(CONCAT('%', :searchValue, '%'))")
    Page<String> findDistinctByFidCodeContaining(@Param("searchValue") String searchKey, Pageable pageable);

//    List<String> findDistinctByLevels(@Param("level") String level);

    @Query("SELECT DISTINCT LOWER(p.level) FROM Provider p")
    List<String> findAllDistinctLevels();

    @Query("SELECT DISTINCT p.subCounty FROM Provider p")
    List<String> findAllDistinctSubcounty();

    @Query("SELECT DISTINCT p.county FROM Provider p")
    List<String> findAllByCounty();

    Optional<Boolean> existsByfidCode(String fidCode);


}
