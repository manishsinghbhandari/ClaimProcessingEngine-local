package io.coredge.health.claims.db.repositories.category;

import io.coredge.health.claims.db.entity.category.ReasonCode;
import io.coredge.health.claims.enums.category.ReasonCodeStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;


public interface ReasonCodeRepository extends JpaRepository<ReasonCode, Long> {

    boolean existsByCode(String code);

    Page<ReasonCode> findByCategory_Id(Long categoryId, Pageable pageable);

    boolean existsByDescriptionAndIdNot(String description, Long id);

    boolean existsByDescription(String description);

    List<ReasonCode> findByStatusAndIsActiveTrue(ReasonCodeStatus status);

}
