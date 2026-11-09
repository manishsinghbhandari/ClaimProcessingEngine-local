package io.coredge.health.claims.service;

import io.coredge.health.claims.context.HealthIdContextHolder;
import io.coredge.health.claims.db.entity.permission.*;
import io.coredge.health.claims.db.repositories.permission.*;
import io.coredge.health.claims.enums.UserStatusEnum;
import io.coredge.health.claims.enums.permission.ActionEnum;
import io.coredge.health.claims.enums.permission.ResourceEnum;
import io.coredge.health.claims.exception.app.*;
import io.coredge.health.claims.models.users.PermissionRequest;
import io.coredge.health.claims.models.users.PermissionResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.PagedModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;


@Slf4j
@Service
public class PermissionService {

    @Autowired
    private PagedResourcesAssembler<PermissionResponse> pagedResourcesAssembler;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ResourceRepository resourceRepository;

    @Autowired
    private ActionRepository actionRepository;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private UserAuditRepository userAuditRepository;

    private static PermissionResponse processUserPermission(User user, List<Permission> permissions) {
        if(!permissions.isEmpty()) {
            // Group permissions by resource and map to action lists
            Map<ResourceEnum, List<ActionEnum>> resourceActions = permissions.stream()
                    .collect(Collectors
                            .groupingBy(p ->
                                    p.getResource().getName(), Collectors
                                    .mapping(p -> p.getAction().getName(), Collectors.toList())));

            // Map grouped data to PermissionDetail list
            List<PermissionResponse.PermissionDetail> permissionDetails =
                    resourceActions.entrySet().stream()
                            .map(entry -> new PermissionResponse
                                    .PermissionDetail(entry.getKey(), entry.getValue())).collect(Collectors.toList());
            // Construct PermissionResponse for the user
            return new PermissionResponse(user.getUsername(), user.getStatus(), permissionDetails);
        }else
        {
            // Construct PermissionResponse for the user
            return new PermissionResponse(user.getUsername(), user.getStatus(), Collections.emptyList());
        }

    }

    @Transactional
//    @CacheEvict(value = "permissionsCache", key = "#request.username")
    public void assignPermissions(PermissionRequest request) {
        // Restrict Operation on its own accounts.
        validateRequestForOwnAccounts(request.getUsername());

        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(UserNotFoundException::new);
        // Check if user status is ACTIVE
        if (!user.getStatus().equals(UserStatusEnum.ACTIVE)) {
            throw new BadRequestException(String.format("$$User is currently %s, Please Enable and Retry", user.getStatus()));
        }

        for (PermissionRequest.PermissionDetail detail : request.getPermissions()) {
            Resource resource = resourceRepository.findByName(detail.getResource())
                    .orElseGet(() -> resourceRepository.save(Resource.builder().name(detail.getResource()).build()));

            for (ActionEnum actionName : detail.getAction()) {
                Action action = actionRepository.findByName(actionName)
                        .orElseGet(() -> actionRepository.save(Action.builder().name(actionName).build()));

                if (!permissionRepository.existsByUserAndResourceAndAction(user, resource, action)) {
                    // Evict the cache before deletion
                    evictPermissionCache(user, resource, action);
                    permissionRepository.save(Permission.builder().user(user).resource(resource).action(action).build());
                }
            }
        }
    }

    @Transactional
    public void removePermissions(String username, PermissionRequest request) {
        // Restrict Operation on its own accounts.
        validateRequestForOwnAccounts(username);
        // Fetch the user once, ensuring it's active
        User user = userRepository.findByUsername(username).orElseThrow(() -> new UserNotFoundException("User not found"));
        // Check if user status is ACTIVE
        if (!user.getStatus().equals(UserStatusEnum.ACTIVE)) {
            throw new BadRequestException(String.format("$$User is currently %s, Please Enable and Retry", user.getStatus()));
        }
        // Process each permission detail in the request
        for (PermissionRequest.PermissionDetail detail : request.getPermissions()) {
            Resource resource = resourceRepository.findByName(detail.getResource())
                    .orElseThrow(() -> new ResourceNotFoundException("Resource not found"));

            // Fetch all actions for the current resource detail at once
            List<Action> actions = actionRepository.findAllByNameIn(detail.getAction())
                    .orElseThrow(() -> new ResourceNotFoundException("Actions not found"));
            if (actions.size() != detail.getAction().size()) {
                throw new ResourceNotFoundException("Some actions were not found");
            }

            for (Action action : actions) {
                permissionRepository.findByUserAndResourceAndAction(user, resource, action)
                        .ifPresent(permission -> {
                            // Evict the cache before deletion
                            evictPermissionCache(user, resource, action);
                            permissionRepository.delete(permission);
                        });
            }
        }
    }

    //    @CacheEvict(value = "permissionsCache", key = "#user.username + '_' + #resource.name + '_' + #action.name")
    public void evictPermissionCache(User user, Resource resource, Action action) {
        // Cache eviction handled automatically
    }

    //    @Cacheable(value = "permissionsCache", key = "#username")
    @Transactional(readOnly = true)
    public PermissionResponse getPermissionsByUsername(String username) {
        if (HealthIdContextHolder.userId().equalsIgnoreCase(username)) {
            User user = userRepository.findByUsernameAndStatus(username, UserStatusEnum.ACTIVE)
                    .orElseThrow(() -> new UserNotFoundException("User is either not-found/disabled/deleted."));

            List<Permission> permissions = permissionRepository.findAllByUser(user);

            Map<ResourceEnum, List<ActionEnum>> resourceActions = permissions.stream()
                    .collect(Collectors.groupingBy(p -> p.getResource().getName(),
                            Collectors.mapping(p -> p.getAction().getName(), Collectors.toList())));

            List<PermissionResponse.PermissionDetail> permissionDetails = resourceActions.entrySet().stream()
                    .map(entry -> new PermissionResponse.PermissionDetail(entry.getKey(), entry.getValue()))
                    .collect(Collectors.toList());

            return new PermissionResponse(user.getUsername(), user.getStatus(), permissionDetails);
        }
        throw new UserNotAuthorizedException();
    }

    // Check if the user has permission for a specific resource and action
    public boolean hasPermission(String username, ResourceEnum resourceName, ActionEnum actionName) {
        log.info("Username: {}, ResourceName : {}, ActionName: {}", username, resourceName, actionName);
        User user = userRepository.findByUsernameAndStatus(username, UserStatusEnum.ACTIVE).orElse(null);
        if (user == null) {
            return false;  // User not found
        }
        Resource resource = resourceRepository.findByName(resourceName).orElse(null);
        Action action = actionRepository.findByName(actionName).orElse(null);
        if (resource == null || action == null) {
            log.error("resource == {} || action == {}", resource, action);
            return false;  // Invalid resource or action
        }
        return permissionRepository.existsByUserAndResourceAndAction(user, resource, action);
    }

    //    @Cacheable(value = "users", key = "'active_users'")
    public Optional<Page<User>> getAllActiveUsers(Pageable pageable) {
        return userRepository.findByStatus(UserStatusEnum.ACTIVE, pageable);
        //.orElseThrow(() -> new UserNotFoundException("User Not Found"));
    }

    //    @Cacheable(value = "users", key = "'active_users'")
    public Optional<Page<User>> getAllUsers(Pageable pageable) {
        return Optional.of(userRepository.findAll(pageable));
        //.orElseThrow(() -> new UserNotFoundException("User Not Found"));
    }

    public Optional<Page<User>> getUsersByName(String searchUser, Pageable pageable) {
        return userRepository.findByUsernameContainingIgnoreCase(searchUser, pageable);
        //.orElseThrow(() -> new UserNotFoundException("User Not Found"));
    }

    //    @Cacheable(value = "permissions", key = "#user.id")
    public List<Permission> getPermissionsForUser(User user) {
        return permissionRepository.findAllByUser(user);
    }

    //    @Cacheable(value = "resources", key = "#resourceId")
    public Resource getResourceById(Long resourceId) {
        return resourceRepository.findById(resourceId).orElseThrow(() -> new ResourceNotFoundException("Resource not found"));
    }

    //    @Cacheable(value = "actions", key = "#actionId")
    public Action getActionById(Long actionId) {
        return actionRepository.findById(actionId).orElseThrow(() -> new ActionNotFoundException("Action not found"));
    }

    public PagedModel<EntityModel<PermissionResponse>> getAllUsersWithPermissions(String searchUser, Pageable pageable) {
        //Optional<Page<User>> users = getAllActiveUsers(pageable);
        Optional<Page<User>> users;
        if (StringUtils.isNotBlank(searchUser)) {
            users = getUsersByName(searchUser, pageable);
        } else {
            users = getAllUsers(pageable);
        }
        Page<PermissionResponse> getAllUserPermissionResponse = users
                .filter(Page::hasContent)
                .map(page -> page.map(user -> {
                    // Fetch permissions for each user
                    List<Permission> permissions = getPermissionsForUser(user); // Uses cached value
                    return processUserPermission(user, permissions);
                }))
                .orElseThrow(DataNotFoundException::new);
        return pagedResourcesAssembler.toModel(getAllUserPermissionResponse);
    }

    public List<PermissionResponse> getAllUsersWithPermissions() {
        Optional<List<User>> users = userRepository.findByStatus(UserStatusEnum.ACTIVE); // Uses cached value
        return users
                .map(userList -> userList.stream()
                        .map(user -> {
                            // Fetch permissions for each user
                            List<Permission> permissions = getPermissionsForUser(user); // Uses cached value
                            return processUserPermission(user, permissions);
                        })
                        .toList()) // Collect the stream into a list
                .orElseThrow(DataNotFoundException::new);
    }


    public PagedModel<EntityModel<PermissionResponse>> getAllDisabledDeletedUser(Pageable pageable) {
        Optional<Page<User>> users = getAllUsers(pageable); // Uses cached value

        Page<PermissionResponse> getAllUserPermissionResponse = users
                .filter(Page::hasContent)
                .map(page -> {
                    // Filter users and map them to PermissionResponse
                    List<PermissionResponse> filteredAndProcessedUsers = page.stream()
                            .filter(user -> user.getStatus().equals(UserStatusEnum.DISABLED) ||
                                    user.getStatus().equals(UserStatusEnum.DELETED)) // Filter users
                            .map(user -> processUserPermission(user, Collections.emptyList()))
                            .toList(); // Collect to a list
                    // Convert the list back to a Page
                    return new PageImpl<>(filteredAndProcessedUsers, pageable, filteredAndProcessedUsers.size());
                })
                .orElseThrow(DataNotFoundException::new);
        return pagedResourcesAssembler.toModel(getAllUserPermissionResponse);
    }

    /**
     * Get all resources.
     */
    @Transactional(readOnly = true)
    public List<ResourceEnum> getAllResources() {
        return resourceRepository.findAll().stream()
                .map(Resource::getName)
                .collect(Collectors.toList());
    }

    /**
     * Get all actions.
     */
    @Transactional(readOnly = true)
    public List<ActionEnum> getAllActions() {
        return actionRepository.findAll().stream()
                .map(Action::getName)
                .collect(Collectors.toList());
    }

    /**
     * Get all resources with their associated actions.
     */
    @Transactional(readOnly = true)
    public List<ResourceWithActions> getResourcesWithActions() {
        List<ActionEnum> actions = actionRepository.findAll().stream()
                .map(Action::getName)
                .collect(Collectors.toList());

        return resourceRepository.findAll().stream()
                .map(resource -> new ResourceWithActions(resource.getName(), actions))
                .collect(Collectors.toList());
    }

    public Boolean isExistByUserNameAndStatus(String userName) {
        return userRepository.existsByUsernameAndStatus(userName, UserStatusEnum.ACTIVE).orElse(false);
    }

    public Boolean isExistByUserName(String userName) {
        return userRepository.existsByUsername(userName).orElse(false);
    }

    public User getUserByUserName(String userName) {
        return userRepository.findByUsername(userName).orElse(null);
    }

    // Save to the User DB
    public User saveUser(User user) {
        return userRepository.save(user);
    }

    @Transactional
    public void updateUserStatus(String username, boolean isEnabled) {
        // Restrict Operation on its own accounts.
        validateRequestForOwnAccounts(username);
        log.info("Updating user status in DB: {} to {}", username, isEnabled ? "ACTIVE" : "DISABLED");
        try {
            User user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new UserNotFoundException("User not found in DB."));
            UserStatusEnum previousStatus = user.getStatus();
            user.setStatus(isEnabled ? UserStatusEnum.ACTIVE : UserStatusEnum.DISABLED);
            UserStatusEnum newStatus = user.getStatus();
            userRepository.save(user);
            logStatusChange(user, previousStatus, newStatus, "Edit");
            log.info("User status updated in DB: {}", username);
        } catch (Exception e) {
            throw new RuntimeException("Error updating user status in Database: " + e.getMessage());
        }
    }

    @Transactional
    public void markUserAsDeleted(String username) {
        // Restrict Operation on its own accounts.
        validateRequestForOwnAccounts(username);
        User user = userRepository.findByUsernameAndStatus(username, UserStatusEnum.ACTIVE)
                .orElseThrow(() -> new UserNotFoundException("User not found in the database or it is not ACTIVE : " + username));
        UserStatusEnum previousStatus = user.getStatus();
        user.setStatus(UserStatusEnum.DELETED);
        UserStatusEnum newStatus = user.getStatus();
        userRepository.save(user);
        logStatusChange(user, previousStatus, newStatus, "Edit");
        log.info("User status set to DELETED for username: {}", username);
    }

    /**
     * @param username
     * @return true or false based on whether a user is DELETED
     */
    public boolean checkIfUserDeleted(String username) {
        return userRepository.findByUsername(username)
                .map(user -> user.getStatus() == UserStatusEnum.DELETED)
                .orElseThrow(() -> new UserNotFoundException("User not found."));
    }

    /**
     * @param username
     * @return true/false if user ACTIVE or not
     */
    public boolean isUserActive(String username) {
        return userRepository.findByUsername(username)
                .map(user -> user.getStatus() == UserStatusEnum.ACTIVE)
                .orElseThrow(() -> new UserNotFoundException("User not found."));
    }

    /**
     * @param user
     * @param previousStatus
     * @param newStatus
     * @param action         - hardcoded for now to "Edit"
     */
    private void logStatusChange(User user, UserStatusEnum previousStatus, UserStatusEnum newStatus, String action) {
        AuditUserStatus audit = AuditUserStatus.builder()
                .user(user)
                .previousStatus(previousStatus)
                .newStatus(newStatus)
                .action(action)
                .build();
        userAuditRepository.save(audit);
    }

    public record ResourceWithActions(ResourceEnum resourceName, List<ActionEnum> actions) {
    }

    public PagedModel<EntityModel<PermissionResponse>> getAllActiveUsersWithPermissions(Pageable pageable) {
        Optional<Page<User>> users = getAllActiveUsers(pageable); // Uses cached value
        Page<PermissionResponse> getAllUserPermissionResponse = users
                .filter(Page::hasContent)
                .map(page -> page.map(user -> {
                    // Fetch permissions for each user
                    List<Permission> permissions = getPermissionsForUser(user); // Uses cached value
                    return processUserPermission(user, permissions);
                }))
                .orElseThrow(DataNotFoundException::new);
        return pagedResourcesAssembler.toModel(getAllUserPermissionResponse);
    }


    private static void validateRequestForOwnAccounts(String username){
        // Restrict Operation on its own accounts.
        if(HealthIdContextHolder.userId().equalsIgnoreCase(username)){
            throw new BadRequestException("$$Invalid request: Operation is not allowed on owned accounts.");
        }
    }

}
