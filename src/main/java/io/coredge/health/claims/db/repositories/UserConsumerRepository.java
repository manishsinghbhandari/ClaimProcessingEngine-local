package io.coredge.health.claims.db.repositories;

import io.coredge.health.claims.db.entity.UserConsumer;
import io.coredge.health.claims.db.entity.permission.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserConsumerRepository extends JpaRepository<UserConsumer, Long> {
    Optional<UserConsumer> findByUsernameAndIsActive(String username , boolean isActive);
    Optional<UserConsumer> findByUsernameAndIsActiveAndIsCallBack(String username, Boolean isActive, Boolean isCallBack);
    Optional<List<UserConsumer>> findAllByIsCallBack(Boolean isCallBack);

    Optional<UserConsumer> findByUsernameIgnoreCaseAndIsActiveAndIsCallBack(String username, Boolean isActive, Boolean isCallBack);
    Optional<UserConsumer> findByTokenIgnoreCaseAndIsActiveAndIsCallBack(String token, Boolean isActive, Boolean isCallBack);
}
