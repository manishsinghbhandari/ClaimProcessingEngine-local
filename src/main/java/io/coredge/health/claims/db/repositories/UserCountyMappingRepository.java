package io.coredge.health.claims.db.repositories;

import io.coredge.health.claims.db.entity.UserCountyMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for UserCountyMapping entity.
 * Provides methods to manage user-county assignments for surveillance case follow-up.
 */
@Repository
public interface UserCountyMappingRepository extends JpaRepository<UserCountyMapping, Long> {
    List<UserCountyMapping> findByUsernameAndIsActive(String username, Boolean isActive);

    List<UserCountyMapping> findByAdminLevelOneIdAndIsActive(Long adminLevelOneId, Boolean isActive);

    Optional<UserCountyMapping> findByUsernameAndAdminLevelOneIdAndIsActive(String username, Long adminLevelOneId, Boolean isActive);

    List<UserCountyMapping> findByUsername(String username);

    @Query("SELECT ucm.adminLevelOneId FROM UserCountyMapping ucm WHERE ucm.username = :username AND ucm.isActive = true")
    List<Long> findCountyIdsByUsername(@Param("username") String username);

    @Query("SELECT alo.name FROM UserCountyMapping ucm " +
           "JOIN AdminLevelOne alo ON ucm.adminLevelOneId = alo.id " +
           "WHERE ucm.username = :username AND ucm.isActive = true")
    List<String> findCountyNamesByUsername(@Param("username") String username);

    boolean existsByUsernameAndIsActive(String username, Boolean isActive);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE UserCountyMapping ucm SET ucm.isActive = false WHERE ucm.username = :username AND ucm.isActive = true")
    void deactivateAllByUsername(@Param("username") String username);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE UserCountyMapping ucm SET ucm.isActive = false WHERE ucm.username = :username AND ucm.adminLevelOneId IN :countyIds AND ucm.isActive = true")
    void deactivateByUsernameAndCountyIds(@Param("username") String username, @Param("countyIds") List<Long> countyIds);

    @Query("SELECT DISTINCT ucm.username FROM UserCountyMapping ucm WHERE ucm.isActive = true ORDER BY ucm.username ASC")
    List<String> findDistinctUsernamesWithActiveAssignments();
}
