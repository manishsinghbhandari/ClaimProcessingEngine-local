package io.coredge.health.claims.db.repositories;

import io.coredge.health.claims.db.entity.CategoryMaster;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CategoryRepository extends JpaRepository<CategoryMaster, Long> {
  Optional<List<CategoryMaster>> findByCode(String code);
}
