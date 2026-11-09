package io.coredge.health.claims.db.repositories;

import io.coredge.health.claims.db.entity.SubCategoryMaster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SubCategoryRepository extends JpaRepository<SubCategoryMaster, Long> {}
