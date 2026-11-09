package io.coredge.health.claims.db.repositories.category;

import io.coredge.health.claims.db.entity.category.ReasonCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReasonCategoryRepository extends JpaRepository<ReasonCategory, Long> {

    boolean existsByCategoryName(String categoryName);
    boolean existsByPrefix(String prefix);
    Page<ReasonCategory> findById(Long id, Pageable pageable);

}