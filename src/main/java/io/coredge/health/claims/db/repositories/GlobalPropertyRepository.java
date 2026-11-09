package io.coredge.health.claims.db.repositories;

import io.coredge.health.claims.db.entity.GlobalProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GlobalPropertyRepository extends JpaRepository<GlobalProperty, Long> {

    Optional<GlobalProperty> findByIdAndDeleted(Long id , boolean deleted);

    Boolean existsByPropertyAndDeleted(String property, boolean deleted);

    boolean existsByPropertyAndDeletedFalseAndIdNot(String property, Long id);

    Optional<GlobalProperty> findByPropertyAndDeletedFalse(String property);

    List<GlobalProperty> findByDeletedFalseAndStatusTrue();

    Optional<GlobalProperty> findByPropertyAndDeletedFalseAndStatusTrue(String property);

    @Query("""
        SELECT gp FROM GlobalProperty gp WHERE
        gp.deleted = :deleted AND
        (:key IS NULL OR gp.property LIKE CONCAT('%', CAST(:key AS string), '%'))
    """)
    Optional<Page<GlobalProperty>> filterByKey(
            @Param("key") String key,
            @Param("deleted")  boolean deleted,
            Pageable pageable);
}
