package io.coredge.health.claims.rest.admin;

import io.coredge.health.claims.helpers.Helper;
import io.coredge.health.claims.models.UserRequest;
import io.coredge.health.claims.models.users.*;
import io.coredge.health.claims.service.blockchain.WalletServices;
import io.coredge.health.claims.service.KeycloakUserService;
import io.coredge.health.claims.service.PermissionService;
import io.coredge.health.claims.service.UserConsumerService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static io.coredge.health.claims.constants.Constants.AdminEndPoints.ADMIN_ENDPOINT_PREFIX;

@RestController
@RequestMapping(ADMIN_ENDPOINT_PREFIX)
@Slf4j
public class UserControllerImpl implements UserController {

    private final KeycloakUserService keycloakUserService;

    private final PermissionService permissionService;

    private final UserConsumerService userConsumerService;
    
    private final WalletServices walletServices;

    public UserControllerImpl(KeycloakUserService keycloakUserService, PermissionService permissionService,
            UserConsumerService userConsumerService, WalletServices walletServices) {
        this.keycloakUserService = keycloakUserService;
        this.permissionService = permissionService;
        this.userConsumerService = userConsumerService;
        this.walletServices = walletServices;
    }

    @Override
    public ResponseEntity<UserCreationResponse> createUser(@RequestBody UserRequest userRequest) {
        try {
            UserCreationResponse response = keycloakUserService.createUser(userRequest);
            HttpStatus status = "FAILED".equals(response.getStatus()) ? 
                HttpStatus.BAD_REQUEST : HttpStatus.CREATED;
            return ResponseEntity.status(status).body(response);
        } catch (Exception e) {
            UserCreationResponse errorResponse = UserCreationResponse.builder()
                .message("Error creating user: " + e.getMessage())
                .username(userRequest.getUsername())
                .status("FAILED")
                .errors(List.of(e.getMessage()))
                .build();
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
        }
    }

    @Override
    public ResponseEntity<String> assignKeycloakRole(@RequestParam String userId,
            @RequestParam List<String> rolesName) {
        try {
            return ResponseEntity.ok(keycloakUserService.assignRolesToUser(userId, rolesName));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Error assigning role: " + e.getMessage());
        }
    }

    @Override
    public ResponseEntity<List<PermissionService.ResourceWithActions>> listPermissionWithActions() {
        try {
            List<PermissionService.ResourceWithActions> roles = permissionService.getResourcesWithActions();
            return ResponseEntity.ok(roles);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Collections.emptyList());
        }
    }

    @Override
    public ResponseEntity<List<RoleRepresentationResponse>> listAction() {
        try {
            List<RoleRepresentationResponse> roles = keycloakUserService.listRoles();
            return ResponseEntity.ok(roles);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Collections.emptyList());
        }
    }

    @Override
    public ResponseEntity<CollectionModel<EntityModel<PermissionResponse>>> getAllUsersWithPermissions(String search,
            int page, int size, String sortBy, String direction) {
        return ResponseEntity.ok(permissionService.getAllUsersWithPermissions(search,
                PageRequest.of(page, size, Sort.by(Helper.getSortDirection(direction), sortBy))));
    }

    @Override
    public ResponseEntity<CollectionModel<EntityModel<PermissionResponse>>> getAllDisabledDeletedUser(int page,
            int size, String sortBy, String direction) {
        return ResponseEntity.ok(permissionService.getAllDisabledDeletedUser(
                PageRequest.of(page, size, Sort.by(Helper.getSortDirection(direction), sortBy))));
    }

    @Override
    public ResponseEntity<String> assignPermissions(@RequestBody PermissionRequest request) {
        permissionService.assignPermissions(request);
        return ResponseEntity.ok("Permissions assigned successfully");
    }

    @Override
    public ResponseEntity<String> removePermissions(@PathVariable String username,
            @RequestBody PermissionRequest request) {
        permissionService.removePermissions(username, request);
        return ResponseEntity.ok("Permissions removed successfully");
    }

    /**
     * @param username Username of the user
     * @return OK status if user status changed
     */
    @Override
    public ResponseEntity<String> changeUserStatus(String username, boolean isEnabled) {
        if (permissionService.checkIfUserDeleted(username)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Cannot change status of a user in DELETED state.");
        }
        // call the KeycloakUserService
        return ResponseEntity.ok(keycloakUserService.updateUserStatus(username, isEnabled));
    }

    /**
     * @param username
     * @return OK status if user deleted
     */
    @Override
    public ResponseEntity<String> deleteUser(String username) {
        if (permissionService.checkIfUserDeleted(username)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Cannot change status of a user in DELETED state.");
        }
        if (!permissionService.isUserActive(username)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Cannot change status of a user in DISABLE state.");
        }
        keycloakUserService.deleteUser(username);
        return ResponseEntity.ok("User deletion requested successfully.");
    }

    /**
     * ENDPOINT For the Creating the External Consumer or CallBack API Entry. Accessible to admin internal only
     */
    @Override
    public ResponseEntity<UserConsumerResponse> createUserConsumer(UserConsumerRequestDTO request) {
        return ResponseEntity.ok(userConsumerService.createUser(request));
    }

    /**
     * Update wallet for a specific user
     */
    @Override
    public ResponseEntity<WalletUpdateResponse> updateUserWallet(@RequestBody WalletUpdateRequest request) {
        try {
            WalletUpdateResponse response = walletServices.updateUserWallet(request);
            HttpStatus status = "FAILED".equals(response.getStatus()) ? 
                HttpStatus.BAD_REQUEST : HttpStatus.OK;
            return ResponseEntity.status(status).body(response);
        } catch (Exception e) {
            WalletUpdateResponse errorResponse = WalletUpdateResponse.builder()
                .message("Error updating wallet: " + e.getMessage())
                .username(request.getUsername())
                .status("FAILED")
                .errors(List.of(e.getMessage()))
                .build();
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
        }
    }
    
    /**
     * Batch update wallets for all users without wallets (async)
     */
    @Override
    public ResponseEntity<WalletUpdateResponse> batchUpdateWallets() {
        try {
            log.info("Starting batch wallet update request");
            CompletableFuture<WalletUpdateResponse> future = walletServices.batchUpdateWalletsAsync();
            
            // Return immediately with a processing response
            WalletUpdateResponse processingResponse = WalletUpdateResponse.builder()
                .message("Batch wallet update started successfully")
                .status("SUCCESS")
                .walletUpdated(true)
                .processingTimeMs(0)
                .build();
                
            return ResponseEntity.ok(processingResponse);
        } catch (Exception e) {
            log.error("Error starting batch wallet update", e);
            WalletUpdateResponse errorResponse = WalletUpdateResponse.builder()
                .message("Error starting batch wallet update: " + e.getMessage())
                .status("FAILED")
                .errors(List.of(e.getMessage()))
                .build();
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
        }
    }
    
    /**
     * Get count of users without wallets
     */
    @Override
    public ResponseEntity<Long> getUsersWithoutWalletCount() {
        try {
            long count = walletServices.getUsersWithoutWalletCount();
            return ResponseEntity.ok(count);
        } catch (Exception e) {
            log.error("Error getting users without wallet count", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(0L);
        }
    }

    /**
     * Endpoint to authenticate user and generate an access token.
     */
    @Override
    public ResponseEntity<AuthResponseToken> generateToken(@RequestBody GenerateTokenDTO generateTokenDTO) {
        return ResponseEntity.ok(keycloakUserService.authenticateAndGetToken(generateTokenDTO.getUsername(), generateTokenDTO.getPassword()));
    }
}
