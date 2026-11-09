package io.coredge.health.claims.db.repositories;

import io.coredge.health.claims.db.entity.AiRoute;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for AI route configurations.
 */
@Repository
public interface AiRouteRepository extends JpaRepository<AiRoute, Long> {

    /**
     * Find the default AI route.
     * 
     * @return the default route if exists
     */
    Optional<AiRoute> findByIsDefaultTrue();

    /**
     * Find AI route by name.
     * 
     * @param name the route name
     * @return the route if exists
     */
    Optional<AiRoute> findByName(String name);
}
