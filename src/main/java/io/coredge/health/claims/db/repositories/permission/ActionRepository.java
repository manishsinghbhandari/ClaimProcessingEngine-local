package io.coredge.health.claims.db.repositories.permission;

import io.coredge.health.claims.db.entity.permission.Action;
import io.coredge.health.claims.enums.permission.ActionEnum;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ActionRepository extends JpaRepository<Action, Long> {
    Optional<Action> findByName(ActionEnum name);
    Optional<List<Action>> findAllByNameIn(List<ActionEnum> names);

}
