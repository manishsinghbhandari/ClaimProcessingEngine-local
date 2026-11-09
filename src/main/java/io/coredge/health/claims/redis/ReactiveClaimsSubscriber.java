package io.coredge.health.claims.redis;

import io.coredge.health.claims.db.repositories.ClaimsAsyncRepository;
import io.coredge.health.claims.models.redis.Message;
import io.coredge.health.claims.service.ClaimBundleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;


/**
 * Author: [Rajesh kumar <rajeshk@coredge.io></>]
 * Description: Facilitates reactive claims subscription for processing
 * business logic related to claim bundles async method.
 */

@Slf4j
@Service
public class ReactiveClaimsSubscriber {

    @Value("${redis.fallback.enable:true}")
    private Boolean enableFallback;

    @Value("${max.retry.async.count:3}")
    private int maxRetryCount;

    private final ClaimsAsyncRepository claimsAsyncRepository;
    private final ClaimBundleService claimBundleService;

    /**
     * Constructs an instance of ReactiveClaimsSubscriber, which subscribes to
     * two reactive streams for processing priority and normal claims.
     *
     * @param priorityClaimsStream a Flux stream containing priority claims to be processed
     * @param normalClaimsStream   a Flux stream containing normal claims to be processed
     * @param claimsAsyncRepository     an instance of MessageRepository for interacting with claims data storage
     * @param claimBundleService    an instance of ClaimBundleService to handle business logic related to claim processing
     */
    public ReactiveClaimsSubscriber(Flux<String> priorityClaimsStream, Flux<String> normalClaimsStream, ClaimsAsyncRepository claimsAsyncRepository, ClaimBundleService claimBundleService) {
        this.claimsAsyncRepository = claimsAsyncRepository;
        this.claimBundleService = claimBundleService;
        priorityClaimsStream.subscribe(this::processEmergencyChannelMessage);    // Subscribe to priority claims
        normalClaimsStream.subscribe(this::processNormalChannelMessage);         // Subscribe to normal claims
    }

    /**
     * Scheduled task to process remaining claims in MongoDB if fallback is enabled.
     */
    @Scheduled(fixedRateString = "${fallback.scheduler.async.delay:60000}") // Reload every 1 minutes (600,000 ms)
    public void processRemainingMessages() {
        if (enableFallback) {
            processRemainingPriorityMessages();
            processRemainingNormalMessages();
        }
    }

    /**
     * Processes a priority claims by invoking business logic and retries if necessary.
     *
     * @param claimID The claim ID of the claims.
     */
    private void processEmergencyChannelMessage(String claimID) {
        log.info("Processing Emergency Request, Claim ID: {}", claimID);
        executeBusinessLogic(claimID);
    }


    /**
     * Processes a normal claims by invoking business logic and retries if necessary.
     *
     * @param claimID The claim ID of the claims.
     */
    private void processNormalChannelMessage(String claimID) {
        log.info("Processing Normal Request, Claim ID: {}", claimID);
        executeBusinessLogic(claimID);
    }

    /**
     * Simulates the execution of business logic for a given claim ID.
     *
     * @param claimID The unique identifier of the claim to process.
     */
    private void executeBusinessLogic(String claimID) {
        try {
            // Add your business logic here
            log.info("Executing business logic for Claim-ID : {}", claimID);
            claimsAsyncRepository.findByClaimId(claimID).ifPresent(mongoMessage -> {
                claimBundleService.processRequestBundleInitially(mongoMessage.getFirst().getContent(), false, false); // Mark isAsync false , process as normal flow.
                // once process successfully , Delete from Mongo-DB
                 deleteMessageByClaimId(claimID);
            });
        } catch (Exception e) {
            log.error("Error processing claim/pre-auth with ID:  {}, error: {}", claimID, e.getMessage());
        }

    }


    /**
     * Deletes a claims from MongoDB by claim ID, logging success, errors, or absence of the claims.
     *
     * @param claimId the unique identifier of the claim to delete
     */
    public void deleteMessageByClaimId(String claimId) {
        // Fetch the claims synchronously
        claimsAsyncRepository.findByClaimId(claimId).ifPresentOrElse(existingMessage -> {
            try {
                // Delete the claims from MongoDB
                claimsAsyncRepository.deleteAll(existingMessage);
                log.info("Successfully Processed claim ID: {}, Deleted claimID from MongoDB.", claimId);
            } catch (Exception e) {
                log.error("Error deleting claim ID:  {}, error: {}", claimId, e.getMessage());
            }
        }, () -> log.info("Claims not found in MongoDB,Claim ID: {}", claimId));
    }

    /**
     * Processes unhandled priority claims in MongoDB by fetching and reprocessing them.
     * Ensures integrity by invoking business logic for each claim and logging its claim ID.
     */
    private void processRemainingPriorityMessages() {
        log.info("Checking for pending emergency claims in MongoDB...");
        List<Message> pendingPriorityClaims = claimsAsyncRepository.findByIsPriorityTrueAndRetryCountLessThan(maxRetryCount);
        if (pendingPriorityClaims.isEmpty()) {
            log.info("No pending emergency claims in MongoDB.");
            return;
        }
        pendingPriorityClaims.parallelStream().forEach(this::retryRemainingMessage); // Updated to use retry logic
    }

    private void processRemainingNormalMessages() {
        log.info("Checking for pending normal claims in MongoDB...");
        List<Message> pendingNormalClaims = claimsAsyncRepository.findByIsPriorityFalseAndRetryCountLessThan(maxRetryCount);
        if (pendingNormalClaims.isEmpty()) {
            log.info("No pending normal claims in MongoDB.");
            return;
        }
        pendingNormalClaims.parallelStream().forEach(this::retryRemainingMessage); // Updated to use retry logic
    }

    /**
     * Retries the processing of a remaining claims using the retry logic.
     *
     * @param claims The claims to retry.
     */
    private void retryRemainingMessage(Message claims) {
        log.info("Reprocessing Claim-ID: {}", claims.getClaimId());
        retryBusinessLogic(claims.getClaimId());
    }

    /**
     * Executes business logic for a claims and handles retries in case of failure.
     *
     * @param claimID    The claim ID of the claims.
     */
    private void retryBusinessLogic(String claimID) {
        try {
            claimsAsyncRepository.findByClaimId(claimID).ifPresent(claims -> {
                if (claims.getFirst().getRetryCount() < maxRetryCount) { // Check if retry count is within limit
                    try {
                        log.info("Executing business logic for Claim-ID: {}", claimID);
                        claimBundleService.processRequestBundleInitially(claims.getFirst().getContent(), false, false);
                        // Delete from MongoDB on success
                         deleteMessageByClaimId(claimID);
                    } catch (Exception e) {
                        log.error("Error processing claims ID: {}, error: {}", claimID, e.getMessage());
                        updateRetryDetails(claims.getFirst(), e.getMessage()); // Update retry count and exception
                    }
                } else {
                    log.warn("Max retry count reached for Claim-ID: {}", claimID);
                }
            });
        } catch (Exception e) {
            log.error("Unhandled error for Claim-ID: {}, error: {}", claimID, e.getMessage());
        }
    }

    /**
     * Updates the retry count and exceptions list in a claims object.
     *
     * @param claims          The claims object to update.
     * @param exceptionMessage The exception claims to add.
     */
    private void updateRetryDetails(Message claims, String exceptionMessage) {
        try {
            // Increment retry count
            claims.setRetryCount(claims.getRetryCount() + 1);

            // Add exception to exceptionsList
            if (claims.getExceptionsList() == null) {
                claims.setExceptionsList(new ArrayList<>());
            }
            claims.getExceptionsList().add(exceptionMessage);

            // Save updated claims
            claimsAsyncRepository.save(claims);
            log.info("Updated retry details for Claim-ID: {}", claims.getClaimId());
        } catch (Exception e) {
            log.error("Error updating retry details for Claim-ID: {}, error: {}", claims.getClaimId(), e.getMessage());
        }
    }
}
