package io.coredge.health.claims.db.repositories.AiNotes;


import io.coredge.health.claims.db.entity.AI.AIValidationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AIValidationLogRepository extends JpaRepository<AIValidationLog, Long> {

}
