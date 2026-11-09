package io.coredge.health.claims.rest.admin;

import io.coredge.health.claims.annotations.RequiresPermission;
import io.coredge.health.claims.enums.permission.ActionEnum;
import io.coredge.health.claims.enums.permission.ResourceEnum;
import io.coredge.health.claims.models.UserRequest;
import io.coredge.health.claims.models.users.*;
import io.coredge.health.claims.service.PermissionService;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static io.coredge.health.claims.constants.Constants.AdminEndPoints.*;
import static io.coredge.health.claims.constants.Constants.EndPoints.AUTH_GENERATE_TOKEN;

public interface UserController {


    @PreAuthorize("hasRole('admin')")
    @RequiresPermission(resource = ResourceEnum.USER_MANAGE, action = {ActionEnum.VIEW, ActionEnum.EDIT})
    @PostMapping(USER_API_CREATE_USER)
    ResponseEntity<UserCreationResponse> createUser(@RequestBody UserRequest userRequest);

    @PreAuthorize("hasRole('admin')")
    @RequiresPermission(resource = ResourceEnum.USER_MANAGE, action = {ActionEnum.VIEW, ActionEnum.EDIT})
    @PostMapping(USER_API_ASSIGN_KEYCLOAK_ROLES)
    ResponseEntity<String> assignKeycloakRole(@RequestParam String userId, @RequestParam List<String> rolesName);

    @PreAuthorize("hasRole('admin')")
    @RequiresPermission(resource = ResourceEnum.USER_MANAGE, action = {ActionEnum.VIEW, ActionEnum.EDIT})
    @GetMapping(USER_API_LIST_ACTIONS)
    ResponseEntity<List<PermissionService.ResourceWithActions>> listPermissionWithActions();

    @PreAuthorize("hasRole('admin')")
    @RequiresPermission(resource = ResourceEnum.USER_MANAGE, action = {ActionEnum.VIEW, ActionEnum.EDIT})
    @GetMapping(USER_API_LIST_KEYCLOAK_ROLES)
    ResponseEntity<List<RoleRepresentationResponse>> listAction();

    @PreAuthorize("hasRole('admin')")
    @RequiresPermission(resource = ResourceEnum.USER_MANAGE, action = {ActionEnum.VIEW, ActionEnum.EDIT})
    @GetMapping(USER_API_BY_USERNAME_ALL)
    ResponseEntity<CollectionModel<EntityModel<PermissionResponse>>> getAllUsersWithPermissions(@RequestParam(required = false) String search, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size, @RequestParam(defaultValue = "username") String sortBy, @RequestParam(defaultValue = "ASC") String direction);

    @PreAuthorize("hasRole('admin')")
    @RequiresPermission(resource = ResourceEnum.USER_MANAGE, action = {ActionEnum.VIEW, ActionEnum.EDIT})
    @GetMapping(USER_API_BY_USERNAME_D_ALL)
    ResponseEntity<CollectionModel<EntityModel<PermissionResponse>>> getAllDisabledDeletedUser(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size, @RequestParam(defaultValue = "username") String sortBy, @RequestParam(defaultValue = "ASC") String direction);

    @PreAuthorize("hasRole('admin')")
    @RequiresPermission(resource = ResourceEnum.USER_MANAGE, action = {ActionEnum.VIEW, ActionEnum.EDIT})
    @PostMapping(USER_API_BY_ASSIGN_PERMISSION)
    ResponseEntity<String> assignPermissions(@RequestBody PermissionRequest request);

    @PreAuthorize("hasRole('admin')")
    @RequiresPermission(resource = ResourceEnum.USER_MANAGE, action = {ActionEnum.VIEW, ActionEnum.EDIT})
    @DeleteMapping(USER_API_REMOVE_PERMISSIONS)
    ResponseEntity<String> removePermissions(@PathVariable String username, @RequestBody PermissionRequest request);

    @PreAuthorize("hasRole('admin')")
    @RequiresPermission(resource = ResourceEnum.USER_MANAGE, action = {ActionEnum.VIEW, ActionEnum.EDIT})
    @PutMapping(USER_API_CHANGE_STATUS)
    ResponseEntity<String> changeUserStatus(@PathVariable String username, @RequestParam boolean isEnabled);

    @PreAuthorize("hasRole('admin')")
    @RequiresPermission(resource = ResourceEnum.USER_MANAGE, action = {ActionEnum.VIEW, ActionEnum.EDIT})
    @DeleteMapping(USER_API_DELETE_USER)
    ResponseEntity<String> deleteUser(@PathVariable String username);

    @PreAuthorize("hasRole('admin')")
    @RequiresPermission(resource = ResourceEnum.USER_MANAGE, action = {ActionEnum.VIEW, ActionEnum.EDIT})
    @PostMapping(USER_CONSUMER_INTERNAL_API)
    ResponseEntity<UserConsumerResponse> createUserConsumer(@RequestBody UserConsumerRequestDTO request);

    /**
     * Update wallet for a specific user
     */
    @PreAuthorize("hasRole('admin')")
    @RequiresPermission(resource = ResourceEnum.USER_MANAGE, action = {ActionEnum.VIEW, ActionEnum.EDIT})
    @PostMapping(USER_API_UPDATE_WALLET)
    ResponseEntity<WalletUpdateResponse> updateUserWallet(@RequestBody WalletUpdateRequest request);
    
    /**
     * Batch update wallets for all users without wallets (async)
     */
    @PreAuthorize("hasRole('admin')")
    @RequiresPermission(resource = ResourceEnum.USER_MANAGE, action = {ActionEnum.VIEW, ActionEnum.EDIT})
    @PostMapping(USER_API_BATCH_UPDATE_WALLETS)
    ResponseEntity<WalletUpdateResponse> batchUpdateWallets();
    
    /**
     * Get count of users without wallets
     */
    @PreAuthorize("hasRole('admin')")
    @RequiresPermission(resource = ResourceEnum.USER_MANAGE, action = {ActionEnum.VIEW, ActionEnum.EDIT})
    @GetMapping(USER_API_GET_USERS_WITHOUT_WALLETS_COUNT)
    ResponseEntity<Long> getUsersWithoutWalletCount();

    /**
     * Authenticate user using username and password, then generate an access token.
     */
    @PostMapping(AUTH_GENERATE_TOKEN)
    ResponseEntity<AuthResponseToken> generateToken(@RequestBody GenerateTokenDTO generateTokenDTO);
}
