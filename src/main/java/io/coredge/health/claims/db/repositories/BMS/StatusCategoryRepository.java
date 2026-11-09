package io.coredge.health.claims.db.repositories.BMS;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import io.coredge.health.claims.db.entity.BMS.StatusCategory;

@Repository
public interface StatusCategoryRepository extends JpaRepository<StatusCategory, Long> {
}