package io.coredge.health.claims.db.repositories;

import io.coredge.health.claims.db.entity.AdminLevelOne;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AdminLevelOneRepository extends JpaRepository<AdminLevelOne, Long> {
    Optional<AdminLevelOne> findByCode(String code);
    Optional<AdminLevelOne> findByName(String name);
}
