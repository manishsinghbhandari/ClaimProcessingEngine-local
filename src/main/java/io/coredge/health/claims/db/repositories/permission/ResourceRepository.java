package io.coredge.health.claims.db.repositories.permission;

import io.coredge.health.claims.db.entity.permission.Resource;
import io.coredge.health.claims.enums.permission.ResourceEnum;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ResourceRepository extends JpaRepository<Resource, Long> {
    Optional<Resource> findByName(ResourceEnum name);
}
