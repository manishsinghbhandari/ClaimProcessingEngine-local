package io.coredge.health.claims.service.blockchain;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.coredge.health.claims.client.WalletClient;
import io.coredge.health.claims.db.entity.permission.User;
import io.coredge.health.claims.db.repositories.permission.UserRepository;
import io.coredge.health.claims.enums.UserStatusEnum;
import io.coredge.health.claims.exception.app.WalletCreationException;
import io.coredge.health.claims.models.users.WalletUpdateRequest;
import io.coredge.health.claims.models.users.WalletUpdateResponse;
import io.coredge.health.claims.models.wallet.WalletCreateResponse;
import io.coredge.health.claims.service.PermissionService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
public class WalletServices {

    private final ObjectMapper objectMapper;
    private final WalletClient walletClient;
    private final PermissionService permissionService;
    private final UserRepository userRepository;


    public WalletServices(ObjectMapper objectMapper, WalletClient walletClient, PermissionService permissionService, UserRepository userRepository) {
        this.objectMapper = objectMapper;
        this.walletClient = walletClient;
        this.permissionService = permissionService;
        this.userRepository = userRepository;
    }

    // Create wallet and Save to user
    public void addWalletDetailsToUser(User userToSave) {
        try {
            WalletCreateResponse response = walletClient.createWallet(userToSave.getUsername()).block();
            if (response != null && response.getMessage().contains("wallet created successfully")) {
                userToSave.setMnemonic(response.getMnemonic());
                userToSave.setAddresses(objectMapper.writeValueAsString(response.getAddresses()));
                permissionService.saveUser(userToSave);
                log.info("Wallet created and saved for user: {}", userToSave.getUsername());
            } else {
                String errorMsg = String.format("Wallet creation failed for user: %s, response: %s",
                        userToSave.getUsername(), response);
                log.error(errorMsg);
                throw new WalletCreationException(errorMsg);
            }
        } catch (WalletCreationException e) {
            // Re-throw WalletCreationException as-is
            throw e;
        } catch (Exception e) {
            String errorMsg = String.format("Error creating wallet for user: %s", userToSave.getUsername());
            log.error(errorMsg, e);
            throw new WalletCreationException(errorMsg, e);
        }
    }

    // Async version of wallet creation for better performance
    @Async
    public CompletableFuture<Void> addWalletDetailsToUserAsync(User userToSave) {
        return CompletableFuture.runAsync(() -> {
            try {
                WalletCreateResponse response = walletClient.createWallet(userToSave.getUsername()).block();
                if (response != null && response.getMessage().contains("wallet created successfully")) {
                    userToSave.setMnemonic(response.getMnemonic());
                    userToSave.setAddresses(objectMapper.writeValueAsString(response.getAddresses()));
                    permissionService.saveUser(userToSave);
                    log.info("Async Wallet created and saved for user: {}", userToSave.getUsername());
                } else {
                    String errorMsg = String.format("Async Wallet creation failed for user: %s, response: %s",
                            userToSave.getUsername(), response);
                    log.error(errorMsg);
                }
            } catch (Exception e) {
                String errorMsg = String.format("Async Error creating wallet for user: %s", userToSave.getUsername());
                log.error(errorMsg, e);
            }
        });
    }

    public void updateAllUsersWithoutWallet() {
        userRepository.findByMnemonicIsNull().ifPresent(users -> {
            for (User user : users) {
                if (user != null && user.getStatus().equals(UserStatusEnum.ACTIVE) && ObjectUtils.isEmpty(user.getMnemonic())) {
                    addWalletDetailsToUserAsync(user);
                }
            }
        });
    }

    // Method to check if wallet creation is needed
    public boolean isWalletCreationNeeded(User user) {
        return ObjectUtils.isEmpty(user.getMnemonic()) &&
                user.getStatus().equals(UserStatusEnum.ACTIVE);
    }

    /**
     * Update wallet for a specific user
     */
    public WalletUpdateResponse updateUserWallet(WalletUpdateRequest request) {
        long startTime = System.currentTimeMillis();
        List<String> errors = new ArrayList<>();

        try {
            // Get user from database
            User user = permissionService.getUserByUserName(request.getUsername());
            if (user == null) {
                return WalletUpdateResponse.builder()
                        .message("User not found")
                        .username(request.getUsername())
                        .status("FAILED")
                        .errors(List.of("User not found in database"))
                        .processingTimeMs(System.currentTimeMillis() - startTime)
                        .build();
            }

            // Check if user is active
            if (!user.getStatus().equals(UserStatusEnum.ACTIVE)) {
                return WalletUpdateResponse.builder()
                        .message("Cannot update wallet for inactive user")
                        .username(request.getUsername())
                        .userId(user.getId().toString())
                        .status("FAILED")
                        .errors(List.of("User is not active"))
                        .processingTimeMs(System.currentTimeMillis() - startTime)
                        .build();
            }

            // Check if wallet already exists and forceUpdate is false
            if (!ObjectUtils.isEmpty(user.getMnemonic())) {
                return WalletUpdateResponse.builder()
                        .message("Wallet already exists for user")
                        .username(request.getUsername())
                        .userId(user.getId().toString())
                        .walletId(request.getUsername())
                        .walletUpdated(false)
                        .status("ALREADY_EXISTS")
                        .processingTimeMs(System.currentTimeMillis() - startTime)
                        .build();
            }

            // Create new wallet
            addWalletDetailsToUser(user);

            return WalletUpdateResponse.builder()
                    .message("Wallet updated successfully")
                    .username(request.getUsername())
                    .userId(user.getId().toString())
                    .walletId(user.getUsername())
                    .mnemonic(user.getMnemonic())
                    .addresses(getUserAddresses(user))
                    .walletUpdated(true)
                    .status("SUCCESS")
                    .processingTimeMs(System.currentTimeMillis() - startTime)
                    .build();

        } catch (Exception e) {
            log.error("Error updating wallet for user: {}", request.getUsername(), e);
            errors.add(e.getMessage());

            return WalletUpdateResponse.builder()
                    .message("Error updating wallet")
                    .username(request.getUsername())
                    .status("FAILED")
                    .errors(errors)
                    .processingTimeMs(System.currentTimeMillis() - startTime)
                    .build();
        }
    }

    /**
     * Batch update wallets for all users without wallets (async)
     */
    @Async
    public CompletableFuture<WalletUpdateResponse> batchUpdateWalletsAsync() {
        long startTime = System.currentTimeMillis();
        List<String> processedUsers = new ArrayList<>();
        List<String> failedUsers = new ArrayList<>();
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        return CompletableFuture.runAsync(() -> {
            log.info("Starting batch wallet update for users without wallets");
            try {
                userRepository.findByMnemonicIsNull().ifPresent(users -> {
                    for (User user : users) {
                        try {
                            if (user != null && user.getStatus().equals(UserStatusEnum.ACTIVE)) {
                                addWalletDetailsToUserAsync(user);
                                processedUsers.add(user.getUsername());
                                successCount.incrementAndGet();
                                log.debug("Scheduled wallet creation for user: {}", user.getUsername());
                            }
                        } catch (Exception e) {
                            failedUsers.add(user.getUsername());
                            failureCount.incrementAndGet();
                            log.error("Failed to schedule wallet creation for user: {}", user.getUsername(), e);
                        }

                    }
                });
            } catch (Exception e) {
                log.error("Failed to wallet creation for user: {}", null, e);
            }
            log.info("Batch wallet update completed. Success: {}, Failures: {}", successCount.get(), failureCount.get());
        }).thenApply(voidResult -> WalletUpdateResponse.builder()
                .message("Batch wallet update completed")
                .status("SUCCESS")
                .walletUpdated(true)
                .processingTimeMs(System.currentTimeMillis() - startTime)
                .errors(failureCount.get() > 0 ? List.of("Some users failed: " + String.join(", ", failedUsers)) : null)
                .build());
    }

    /**
     * Get user addresses as list from JSON
     */
    private List<String> getUserAddresses(User user) {
        try {
            if (ObjectUtils.isEmpty(user.getAddresses())) {
                return List.of();
            }
            return objectMapper.readValue(user.getAddresses(), List.class);
        } catch (Exception e) {
            log.warn("Failed to parse addresses JSON for user: {}", user.getUsername(), e);
            return List.of();
        }
    }

    /**
     * Get count of users without wallets
     */
    public long getUsersWithoutWalletCount() {
        return userRepository.findByMnemonicIsNull()
                .map(users -> users.stream()
                        .filter(user -> user != null && user.getStatus().equals(UserStatusEnum.ACTIVE))
                        .count())
                .orElse(0L);
    }
}
