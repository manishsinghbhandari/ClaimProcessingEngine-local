package io.coredge.health.claims.db.repositories.AiNotes;

import io.coredge.health.claims.db.entity.AI.AIValidation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AIValidationRepository extends JpaRepository<AIValidation, Long> {


    Optional<AIValidation> findByIdAndIsDeletedIsFalse(Long id);

    List<AIValidation> findByIsDeletedIsFalse();

    Page<AIValidation> findByIsDeletedIsFalse(Pageable pageable);


    List<AIValidation> findAllByValidationKey(String validationKey);

}
