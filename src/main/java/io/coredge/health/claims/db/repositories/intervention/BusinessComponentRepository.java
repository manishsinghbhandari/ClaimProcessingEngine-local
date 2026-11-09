package io.coredge.health.claims.db.repositories.intervention;

import io.coredge.health.claims.db.entity.intervention.BusinessComponent;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BusinessComponentRepository extends JpaRepository<BusinessComponent, Long> {

    List<BusinessComponent> findByCodeAndIsDeletedIsFalse(String code);

    List<BusinessComponent> findByIsDeletedFalse();

    @Modifying
    @Transactional
    @Query("UPDATE BusinessComponent bc SET bc.isDeleted = true WHERE bc.id = :id AND bc.isDeleted = false")
    void markDelete(@Param("id") Long id);

    Optional<BusinessComponent> findByIdAndIsDeletedFalse(Long id);
}