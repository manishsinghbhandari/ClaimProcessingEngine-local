package io.coredge.health.claims.redis;

import io.coredge.health.claims.db.repositories.ClaimsAsyncRepository;
import io.coredge.health.claims.models.redis.Message;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import static io.coredge.health.claims.constants.RedisConstants.*;


/**
 * Author: [Rajesh kumar <rajeshk@coredge.io></>]
 * Description: Facilitates reactive message publisher for accepting
 * claim bundles.
 */
@Slf4j
@Service
public class RedisMessagePublisher {

    private final StringRedisTemplate redisTemplate;
    private final ClaimsAsyncRepository claimsAsyncRepository;

    public RedisMessagePublisher(StringRedisTemplate redisTemplate, ClaimsAsyncRepository claimsAsyncRepository) {
        this.redisTemplate = redisTemplate;
        this.claimsAsyncRepository = claimsAsyncRepository;
    }


    /**
     * Publishes a message to the appropriate Redis channel and optionally saves it to MongoDB
     * based on its priority.
     *
     * @param claimId The unique identifier for the claim message to be published.
     * @param content The content of the message to be published.
     * @param isEmergency A boolean flag indicating whether the message is a priority (true for
     *                    priority messages, false for normal messages).
     */
    // Publish a priority message to the priority channel and save to MongoDB
    public void publishMessage(String claimId, String content, Boolean isEmergency) {
        if (isEmergency) {
            publishPriorityMessage(claimId, content);
        } else {
            publishNormalMessage(claimId,content);
        }
    }


    /**
     * Publishes a priority message to a Redis priority channel and stores it in a priority queue.
     * <p>
     * This method performs the following actions:
     * 1. Adds the provided claim ID with a priority score to a Redis sorted set (priority queue).
     * 2. Checks if the claim ID already exists in MongoDB.
     * 3. Publishes the message to the Redis priority channel and, if necessary, saves it to MongoDB.
     *
     * @param claimId The unique identifier for the claim message to be published.
     * @param content The content of the message to be published.
     */
    private void publishPriorityMessage(String claimId, String content) {
        // Add the message to the priority queue (sorted set) with the given priority score
        redisTemplate.opsForZSet().add(PRIORITY_QUEUE, claimId, 1.0);
        // Check if the claimId already exists in MongoDB
        publishToRedis(claimId, content, EMERGENCY_CHANNEL);
    }


    /**
     * Publishes a normal-priority message to a predefined Redis channel.
     * <p>
     * This method checks if the claim ID already exists in the MongoDB repository.
     * If the claim ID exists, the message is not saved to the repository,
     * but it is published to the normal Redis channel instead.
     * If the claim ID does not exist, the message is persisted in the repository
     * and subsequently published to the normal Redis channel.
     *
     * @param claimId The unique identifier for the claim message to be published.
     * @param content The content of the message to be published.
     */
    private void publishNormalMessage(String claimId, String content) {
        // Check if the claimId already exists in MongoDB
        publishToRedis(claimId, content, NORMAL_CHANNEL);
    }

    /**
     * Publishes a message to a specified Redis channel and optionally saves it to MongoDB
     * if the claim ID does not already exist in the repository.
     *
     * @param claimId The unique identifier for the claim message to be published.
     * @param content The content of the message to be published.
     * @param priorityChannel The Redis channel to which the message should be published,
     *                        indicating its priority level.
     */
    private void publishToRedis(String claimId, String content, String priorityChannel) {
        if (claimsAsyncRepository.existsByClaimId(claimId)) {
            // Log a warning message, Publish to the priority channel (fall back processing from queue)
            log.warn("ClaimId {} already exists MongoDB. Skipping save. Publish to {} channel", claimId, priorityChannel);
        } else {
            // Create Message object and save it to MongoDB
            Message mongoMessage = Message.builder().content(content)
                    .isPriority(EMERGENCY_CHANNEL.equals(priorityChannel)).claimId(claimId).channel(priorityChannel).build();
            claimsAsyncRepository.save(mongoMessage);
        }
        // Publish to the priority channel (after processing from queue)
        redisTemplate.convertAndSend(priorityChannel, claimId);
    }


}
