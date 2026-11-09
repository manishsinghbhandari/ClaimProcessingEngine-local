package io.coredge.health.claims.db.repositories;

import io.coredge.health.claims.db.entity.ClaimStatusTransitionDefinition;
import io.coredge.health.claims.enums.ExtensionStateCodeEnum;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ClaimStatusTransitionDefinitionRepository extends JpaRepository<ClaimStatusTransitionDefinition, Long>{


    /**
     * Checks whether an active status transition rule exists for the given
     * current status, allowed status, and use context.
     *
     * <p>
     * Used to validate if a claim is permitted to transition from {@code status}
     * to {@code allowedStatus} under the specified {@code use} and {@code active} flag.
     * </p>
     */
    boolean existsByStatusAndAllowedStatusAndUseAndActive(ExtensionStateCodeEnum status , ExtensionStateCodeEnum allowedStatus , String use, Boolean active);
}
