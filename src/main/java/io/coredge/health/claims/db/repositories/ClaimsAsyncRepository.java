package io.coredge.health.claims.db.repositories;

import io.coredge.health.claims.models.redis.Message;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ClaimsAsyncRepository extends MongoRepository<Message, String> {

    // Find all priority messages
    List<Message> findByIsPriorityTrue();

    // Find all non priority messages
    List<Message> findByIsPriorityFalse();

    List<Message> findByIsPriorityTrueAndRetryCountLessThan(int retryCount);
    List<Message> findByIsPriorityFalseAndRetryCountLessThan(int retryCount);

    // Find all messages by channel
    List<Message> findByChannel(String channel);

    // Find up to 10 messages by channel
    @Query(value = "{'channel': ?0}")
    List<Message> findTop10ByChannel(String channel);

    // Custom query to find messages with a specific content
    @Query("{'content': ?0}")
    List<Message> findByContent(String content);

    // Find messages by claimID
    @Query("{'claimId': ?0}")
    Optional<List<Message>> findByClaimId(String claimId);

    // Check if a message exists by claimID
    @Query(value = "{'claimId': ?0}", exists = true)
    boolean existsByClaimId(String claimId);

    // Count messages with isPriority set to true
    long countByIsPriorityTrue();

    // Count messages with isPriority set to false
    long countByIsPriorityFalse();
}
