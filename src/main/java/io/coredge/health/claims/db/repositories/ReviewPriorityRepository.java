package io.coredge.health.claims.db.repositories;

import io.coredge.health.claims.db.entity.ReviewPriority;
import io.coredge.health.claims.enums.Status;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface ReviewPriorityRepository extends JpaRepository<ReviewPriority, Long> {

    List<ReviewPriority> findByStatusOrderByPriorityAsc(Status status);

    @Transactional
    @Modifying
    @Query("UPDATE ReviewPriority rp SET rp.status = 'DELETED'")
    int markAsDeleted();
}
