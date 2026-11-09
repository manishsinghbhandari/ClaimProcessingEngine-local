package io.coredge.health.claims.db.repositories.permission;

import io.coredge.health.claims.db.entity.UserConsumer;
import io.coredge.health.claims.db.entity.permission.User;
import io.coredge.health.claims.enums.UserStatusEnum;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);

    Optional<Page<User>> findByStatus(UserStatusEnum status, Pageable page);

    Optional<Page<User>> findByUsernameContainingIgnoreCase(String username, Pageable page);
    
    Optional<Boolean> existsByUsername(String username);

    Optional<List<User>> findByStatus(UserStatusEnum status);

    Optional<Boolean> existsByUsernameAndStatus(String username, UserStatusEnum status);

    // Add method to find by username and status
    Optional<User> findByUsernameAndStatus(String username, UserStatusEnum status);

    Optional<List<User>> findByIdInAndStatus(List<Long> userIds, UserStatusEnum userStatusEnum);

    Optional<List<User>> findByMnemonicIsNull();
}
