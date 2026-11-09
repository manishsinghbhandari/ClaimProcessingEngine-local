package io.coredge.health.claims.service;

import io.coredge.health.claims.db.entity.UserConsumer;
import io.coredge.health.claims.db.repositories.UserConsumerRepository;
import io.coredge.health.claims.exception.app.UserNotAuthorizedException;
import io.coredge.health.claims.models.ClaimResponseWebhooks;
import io.coredge.health.claims.models.users.UserConsumerRequestDTO;

import io.coredge.health.claims.models.users.UserConsumerResponse;
import io.coredge.health.claims.utils.TokenGenerator;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import static io.coredge.health.claims.constants.Constants.Scheduler.RELOAD_WEB_HOOK_FIX_RATE;

/**
 * Service class to handle user consumer-related operations such as
 * creating users, reloading webhook configurations, and validating users.
 *
 * <p>This service interacts with the {@link UserConsumerRepository} for database
 * operations and {@link ClaimResponseWebhooks} for managing webhook configurations.</p>
 *
 * Author: [Rajesh kr]
 * Email: rajeshk@coredge.io
 * Date: January 5, 2025
 */

@Slf4j
@Service
public class UserConsumerService {

    private final UserConsumerRepository userConsumerRepository;
    private final ClaimResponseWebhooks claimResponseWebhooks;

    public UserConsumerService(UserConsumerRepository userConsumerRepository, ClaimResponseWebhooks claimResponseWebhooks) {
        this.userConsumerRepository = userConsumerRepository;
        this.claimResponseWebhooks = claimResponseWebhooks;
    }

    /**
     * Initializes the service by loading webhooks from the database after construction.
     */
    @PostConstruct
    public void init() {
        loadWebhooksFromDatabase();
    }

    /**
     * Scheduled task to reload the webhook configurations from the database at a fixed interval.
     */
    @Scheduled(fixedRate = RELOAD_WEB_HOOK_FIX_RATE) // Reload every 1 minutes (600,000 ms)
    public void loadWebhooksFromDatabase() {
        userConsumerRepository.findAllByIsCallBack(Boolean.TRUE)
                .ifPresentOrElse(userConsumers -> {
                    claimResponseWebhooks.setWebhook(userConsumers.stream()
                            .map(u -> ClaimResponseWebhooks.Webhook.builder()
                                    .consumer(u.getConsumer())
                                    .url(u.getUrl())
                                    .apiKey(u.getUsername())
                                    .secret(u.getPassword())
                                    .authType(u.getAuthType())
                                    .callbackType(u.getCallbackType())
                                    .build())
                            .collect(Collectors.toList()));
                    log.info("Reloaded webhooks list with {} entries.", userConsumers.size());
                }, () -> log.warn("No user consumers found."));
    }


    /**
     * Creates a new user and assigns a token with 30 years of validity if not a callback user.
     *
     * @param request the DTO containing the user details
     * @return a response object containing details of the created user
     */
    public UserConsumerResponse createUser(UserConsumerRequestDTO request) {
        String token = null;
        UserConsumer userConsumer = new UserConsumer();
        userConsumer.setConsumer(request.getConsumer());
        userConsumer.setUsername(request.getUsername());
        userConsumer.setPassword(request.getPassword());
        userConsumer.setAuthType(request.getAuthType());
        userConsumer.setUrl(request.getUrl());
        userConsumer.setIsActive(true);
        userConsumer.setIsCallBack(true);
        if (Boolean.FALSE.equals(request.isCallback())) {
            token = TokenGenerator.generateToken();
            userConsumer.setToken(token);
            userConsumer.setIsCallBack(false);
        }
        userConsumer.setValidUntil(LocalDateTime.now().plusYears(30)); // default 30 years validity
        // Save and return the new user
        try {
            userConsumerRepository.save(userConsumer);
        } catch (DataIntegrityViolationException e) {
            if (e.getCause() != null && e.getCause().getMessage().contains("user_consumer_username_unique")) {
                throw new DuplicateKeyException("Username already exists: " + userConsumer.getUsername(), e);
            }
            throw new RuntimeException("Data integrity violation: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new RuntimeException("An error occurred: " + e.getMessage(), e);
        }
        return UserConsumerResponse.builder()
                .consumer(request.getConsumer())
                .username(request.getUsername())
                .message("Created callBack/consumer successfully")
                .token(token).build();
    }


    /**
     * Validates a user by checking the provided username and token, ensuring the token is valid and not expired.
     *
     * @param username the username to validate
     * @param token    the token to validate
     * @return true if validation succeeds
     * @throws UserNotAuthorizedException if validation fails
     */
    public boolean validateUser(String username, String token) {
        UserConsumer userConsumer = userConsumerRepository
                .findByUsernameIgnoreCaseAndIsActiveAndIsCallBack(username, Boolean.TRUE, Boolean.FALSE)
                .orElseThrow(() -> new UserNotAuthorizedException("User not authorized"));
        // Validate the token and expiration time
        if (!token.equals(userConsumer.getToken()) ||
                (userConsumer.getValidUntil() != null && userConsumer.getValidUntil().isBefore(LocalDateTime.now()))) {
            throw new UserNotAuthorizedException("User not authorized");
        }
        return Boolean.TRUE;
    }

    public UserConsumer getUserData(String username) {
        return userConsumerRepository
                .findByUsernameIgnoreCaseAndIsActiveAndIsCallBack(username, Boolean.TRUE, Boolean.FALSE)
                .orElseThrow(() -> new UserNotAuthorizedException("User not authorized"));
    }

    public UserConsumer fetchUserViaToken(String token) {
        return userConsumerRepository
                .findByTokenIgnoreCaseAndIsActiveAndIsCallBack(token, Boolean.TRUE, Boolean.FALSE)
                .orElseThrow(() -> new UserNotAuthorizedException("User not authorized"));
    }

}
