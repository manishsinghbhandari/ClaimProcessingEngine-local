package io.coredge.health.claims.db.repositories.permission;

import io.coredge.health.claims.db.entity.permission.Action;
import io.coredge.health.claims.db.entity.permission.Permission;
import io.coredge.health.claims.db.entity.permission.Resource;
import io.coredge.health.claims.db.entity.permission.User;
import io.coredge.health.claims.enums.UserStatusEnum;
import io.coredge.health.claims.enums.permission.ResourceEnum;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

@Repository
public interface PermissionRepository extends JpaRepository<Permission, Long> {

    @EntityGraph(attributePaths = {"resource", "action"})
    List<Permission> findAllByUser(User user);

    boolean existsByUserAndResourceAndAction(User user, Resource resource, Action action);

    Optional<Permission> findByUserAndResourceAndAction(User user, Resource resource, Action action);

    @Query("SELECT p FROM Permission p JOIN FETCH p.user u WHERE u.status = :status")
    List<Permission> findAllPermissionsByUserStatus(@Param("status") UserStatusEnum status);

    @EntityGraph(attributePaths = {"resource", "action", "user"})
    @Query("SELECT p FROM Permission p JOIN FETCH p.user u WHERE p.resource.name IN :resourceNames AND u.status = :status AND p.action.id = 2")
    Page<Permission> findByResourceNameInAndStatusAndActionEdit(@Param("resourceNames") List<ResourceEnum> resourceNames, @Param("status") UserStatusEnum status, Pageable pageable);

    @Query("SELECT p.user FROM Permission p WHERE p.resource.name = :resourceName AND p.user.status = :status AND p.action.id = 2 AND p.user.id IN :userIds")
    Optional<List<User>> findUsersByPermission(@Param("resourceName") ResourceEnum resourceName, @Param("status") UserStatusEnum status, @Param("userIds") List<Long> userIds);

    @Query("""
            SELECT DISTINCT p.resource
            FROM Permission p
            WHERE p.user.username = :username
              AND p.user.status = 'ACTIVE'
            """)
    List<Resource> findResourcesByUsername(@Param("username") String username);

}
