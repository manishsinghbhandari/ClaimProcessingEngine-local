package io.coredge.health.claims.service;

import io.coredge.health.claims.context.HealthIdContextHolder;
import io.coredge.health.claims.db.entity.permission.User;
import io.coredge.health.claims.enums.UserStatusEnum;
import io.coredge.health.claims.exception.app.BadRequestException;
import io.coredge.health.claims.exception.app.ResourceNotAuthorizedException;
import io.coredge.health.claims.exception.app.UserNotFoundException;
import io.coredge.health.claims.helpers.Helper;
import io.coredge.health.claims.models.UserRequest;
import io.coredge.health.claims.models.users.AuthResponseToken;
import io.coredge.health.claims.models.users.RoleRepresentationResponse;
import io.coredge.health.claims.models.users.UserCreationResponse;
import io.coredge.health.claims.service.blockchain.WalletServices;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.ClientResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.AccessTokenResponse;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
public class KeycloakUserService {

    private final Keycloak keycloak;
    private final PermissionService permissionService;
    private final WalletServices walletServices;

    @Value("${keycloak.realm}")
    private String realmName;

    @Value("${keycloak.eportal.access.role:eclaim_protal_access}")
    private List<String> keycloakUserEPortalRoles;

    @Value("${keycloak.eportal.name}")
    private String clientName;

    @Value("${keycloak.auth-server-url}")
    private String authServerUrl;

    @Value("${keycloak.client-id}")
    private String clientId;

    @Value("${keycloak.client-secret}")
    private String clientSecret;

    public KeycloakUserService(Keycloak keycloak, PermissionService permissionService, WalletServices walletServices) {
        this.keycloak = keycloak;
        this.permissionService = permissionService;
        this.walletServices = walletServices;
    }


    public UserCreationResponse createUser(UserRequest userRequest) {
        try {
            log.info("Creating user for request : {}", userRequest.getUsername());
            if(!isValidRequest(userRequest.getEmail(),userRequest.getMobileNo(),userRequest.getPassword()))
                throw new BadRequestException("Check data validation while create user ") ;
            
            UserRepresentation user = new UserRepresentation();
            BeanUtils.copyProperties(userRequest, user);
            user.setEnabled(true);
            user.setCredentials(Collections.singletonList(createPasswordCredential(userRequest.getPassword())));

            try (Response response = keycloak.realm(realmName).users().create(user)) {
                log.info("Response user creation: {}", response.getStatus());
                if (response.getStatus() == 201) {
                    log.info("User created: {},", userRequest.getUsername());
                    assignRolesToUser(user.getUsername(), keycloakUserEPortalRoles);
                    log.info("keycloak role assigned to the user - {}, Roles - {}", userRequest.getUsername(), keycloakUserEPortalRoles);
                    // Insert To the DB
                    User userSaved = permissionService.saveUser(getUser(userRequest));
                    
                    // Handle wallet creation based on request settings
                    return handleWalletCreation(userRequest, userSaved, true);
                    
                } else if (response.getStatus() == 409) {
                    User userInDb = permissionService.getUserByUserName(userRequest.getUsername());
                    if (userInDb == null) {
                        // if not exist in the DB
                        userInDb = permissionService.saveUser(getUser(userRequest));
                    }

                    log.info("User already exist: {},", userRequest.getUsername());
                    assert userInDb != null;
                    if (userInDb.getStatus().equals(UserStatusEnum.ACTIVE)) {
                        assignRolesToUser(user.getUsername(), keycloakUserEPortalRoles);
                        // Check wallet is assign to user, if not create and assign
                        if (ObjectUtils.isEmpty(userInDb.getMnemonic())) {
                            // Create the wallet address
                            return handleWalletCreation(userRequest, userInDb, false);
                        }
                    }
                    return UserCreationResponse.builder()
                        .message("User already exists")
                        .username(userRequest.getUsername())
                        .userId(userInDb.getId().toString())
                        .status("SUCCESS")
                        .walletCreated(!ObjectUtils.isEmpty(userInDb.getMnemonic()))
                        .build();
                } else if (response.getStatus() == 403 || response.getStatus() == 401){
                    throw new ResourceNotAuthorizedException("User is not authorized to access the resources");
                } else {
                    return UserCreationResponse.builder()
                        .message(response.getStatusInfo().getReasonPhrase())
                        .username(userRequest.getUsername())
                        .status("FAILED")
                        .build();
                }
            }
        } catch (Exception e) {
            log.error("Exception while creating user : {}", e.getMessage(), e);
            return UserCreationResponse.builder()
                .message("Error creating user: " + e.getMessage())
                .username(userRequest.getUsername())
                .status("FAILED")
                .errors(List.of(e.getMessage()))
                .build();
        }
    }
    
    private UserCreationResponse handleWalletCreation(UserRequest userRequest, User user, boolean isNewUser) {
        List<String> errors = new ArrayList<>();
        boolean walletCreated = false;
        String walletId = null;
        String mnemonic = null;
        
        try {
            if (userRequest.isCreateWallet()) {
                if (walletServices.isWalletCreationNeeded(user)) {
                    if (isNewUser) {
                        // For new users, try synchronous wallet creation for immediate feedback
                        walletServices.addWalletDetailsToUser(user);
                    } else {
                        // For existing users, use async to avoid blocking
                        walletServices.addWalletDetailsToUserAsync(user);
                    }
                    walletCreated = true;
                    walletId = user.getUsername(); // Assuming wallet ID is based on username
                    mnemonic = user.getMnemonic();
                }
            }
            
            String baseMessage = isNewUser ? "User created successfully" : "User updated successfully";
            String walletMessage = walletCreated ? " with wallet" : " without wallet";
            
            return UserCreationResponse.builder()
                .message(baseMessage + walletMessage)
                .username(userRequest.getUsername())
                .userId(user.getId().toString())
                .walletCreated(walletCreated)
                .walletId(walletId)
                .mnemonic(mnemonic)
                .status(walletCreated ? "SUCCESS" : "PARTIAL_SUCCESS")
                .errors(errors.isEmpty() ? null : errors)
                .build();
                
        } catch (Exception e) {
            log.error("Wallet creation failed for user: {}", userRequest.getUsername(), e);
            errors.add("Wallet creation failed: " + e.getMessage());
            
            return UserCreationResponse.builder()
                .message(isNewUser ? "User created successfully but wallet creation failed" : "User updated but wallet creation failed")
                .username(userRequest.getUsername())
                .userId(user.getId().toString())
                .walletCreated(false)
                .status("PARTIAL_SUCCESS")
                .errors(errors)
                .build();
        }
    }

    private CredentialRepresentation createPasswordCredential(String password) {
        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(password);
        return credential;
    }


    public String assignRolesToUser(String userName, List<String> roles) {
        // Restrict Operation on its own accounts.
        if(HealthIdContextHolder.userId().equalsIgnoreCase(userName)){
            throw new BadRequestException("$$Invalid request: Operation is not allowed on owned accounts.");
        }
        UsersResource usersResource = keycloak.realm(realmName).users();
        UserResource userResource = usersResource.get(fetchUserUuid(userName));

        // Get the client
        ClientRepresentation clientRepresentation = keycloak.realm(realmName).clients().get(getClientUuidDetails()).toRepresentation();
        ClientResource clientResource = keycloak.realm(realmName).clients().get(clientRepresentation.getId());

        // Fetch all roles for the client
        List<RoleRepresentation> availableRoles = clientResource.roles().list();

        // Filter roles to assign based on input
        List<RoleRepresentation> rolesToAssign = availableRoles.stream().filter(role -> roles.contains(role.getName())).toList();

        if (rolesToAssign.isEmpty()) {
            throw new RuntimeException("No matching roles found to assign.");
        }

        try {
            // Assign roles to the user
            userResource.roles().clientLevel(clientRepresentation.getId()).add(rolesToAssign);
            log.info("Role assigned, User - {}, Roles - {}", userName, keycloakUserEPortalRoles);
            return "Role assigned successfully";
        } catch (NotFoundException e) {
            log.error("Error in role assignment: {}", e.toString());
            throw new RuntimeException("Error: Resource not found - " + e.getMessage());
        } catch (Exception e) {
            throw new RuntimeException("Error assigning roles: " + e.getMessage());
        }
    }

    public List<UserRepresentation> searchUser(String username) {
        // Search for users by username
        return keycloak.realm(realmName).users().search(username, 0, 1); // Limit to one result
    }

    public String fetchUserUuid(String username) {
        List<UserRepresentation> users = searchUser(username);
        if (users.isEmpty()) {
            log.info("No user found with username: {}}", username);
            throw new UserNotFoundException(String.format("$$No user found with username: %s", username));
        }
        return users.getFirst().getId();
    }

    public List<RoleRepresentationResponse> listRoles() {
        String clientUuidDetails = getClientUuidDetails();
        // Fetch roles of the client
        List<RoleRepresentation> roles = keycloak.realm(realmName).clients().get(clientUuidDetails).roles().list();
        // Map to custom RoleDTO objects
        return roles.stream().map(role -> new RoleRepresentationResponse(role.getId(), role.getName(), role.getDescription())).toList();
    }

    private String getClientUuidDetails() {
        // Fetch the client UUID by client ID
        return keycloak.realm(realmName).clients().findByClientId(clientName).stream().findFirst().orElseThrow(() -> new UserNotFoundException("$$Client not found :" + clientName)).getId();
    }

    public String updateUserStatus(String username, boolean isEnabled) {
        log.info("Updating status for user: {} to {}", username, isEnabled);
        List<UserRepresentation> users = searchUser(username);
        if (users.isEmpty()) {
            throw new UserNotFoundException("User not found in Keycloak.");
        }
        UserRepresentation user = users.getFirst();
        user.setEnabled(isEnabled);
        try {
            keycloak.realm(realmName).users().get(user.getId()).update(user);
            log.info("User status updated in Keycloak: {}", username);
            // Update status in the database
            permissionService.updateUserStatus(username, isEnabled);
            return "User status updated successfully";
        } catch (Exception e) {
            throw new RuntimeException("Error updating user status: " + e.getMessage(), e);
        }
    }

    public void deleteUser(String username) {
        // Fetch the user's UUID from Keycloak
        String userId = fetchUserUuid(username);
        if (userId == null) {
            throw new UserNotFoundException("User not found in Keycloak: " + username);
        }

        // Disable the user in Keycloak
        UserRepresentation userRepresentation = keycloak.realm(realmName).users().get(userId).toRepresentation();
        if (userRepresentation.isEnabled()) {
            userRepresentation.setEnabled(false); // Set to disabled
            keycloak.realm(realmName).users().get(userId).update(userRepresentation);
            log.info("User successfully disabled in Keycloak: {}", username);
        } else {
            log.info("User is already disabled in Keycloak: {}", username);
        }
        permissionService.markUserAsDeleted(username);
    }


    private User getUser(UserRequest userRequest){
        return User.builder().username(userRequest.getUsername()).email(userRequest.getEmail()).mobileNo(userRequest.getMobileNo()).build();
    }

    public boolean isValidRequest(String email, String mobileNo, String password){
        return Helper.isValidMobileNo(mobileNo) && Helper.isValidEmail(email) && Helper.isValidPassword(password);
    }


  /**
   * Authenticate user using username and password, then generate an access token.
   */
  public AuthResponseToken authenticateAndGetToken(String username, String password) {
      // Create a new Keycloak instance for authentication
      try (Keycloak keycloakClient = KeycloakBuilder.builder().serverUrl(authServerUrl).realm(realmName)
              .clientId(clientId).clientSecret(clientSecret).username(username).password(password)
              .grantType(OAuth2Constants.PASSWORD).build()) {

          // Retrieve access token from Keycloak
          AccessTokenResponse token = keycloakClient.tokenManager().getAccessToken();

          // Return the JWT token
          return AuthResponseToken.builder().token(token.getToken()).expiresIn(token.getExpiresIn())
                  .refreshExpiresIn(token.getRefreshExpiresIn())
                  .tokenType(token.getTokenType())
                  .build();
      }
  }

}
