package io.coredge.health.claims.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.coredge.health.claims.models.NotificationRequest;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.TimeUnit;

import static io.coredge.health.claims.constants.RedisConstants.*;

@Slf4j
@Service
public class NotificationService {

    private final RestTemplate restTemplate;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${notification.service.url}")
    private String notificationUrl;

    @Value("${notification.service.health.check.url}")
    private String notificationHealthCheckUrl;

    private static final int MAX_RETRY_ATTEMPTS = 5;

    public NotificationService(RestTemplate restTemplate,
                               StringRedisTemplate redisTemplate,
                               ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public void sendNotification(String provider, String action, String claimId, String claimResponse) {
        log.info("Send Notification: provider{}, action: {}, claimId: {}", provider, action, claimId);
        pushNotification(provider, action, claimId, claimResponse);
    }

    @Async
    @CircuitBreaker(name = "notificationService", fallbackMethod = "queueNotificationFallback")
    protected void pushNotification(String provider, String action, String claimId, String payload) {

        log.info("Send Push-MAIN Notification: provider: {}, action: {}, claimId: {}", provider, action, claimId);

        String dedupKey = claimId + ":" + provider + ":" + action;

        // Idempotency check
        Boolean alreadySent = redisTemplate.hasKey(NOTIFICATION_DEDUP_SET + ":" + dedupKey);
        if (alreadySent) {
            log.info("Skipping duplicate notification for ClaimId {} (dedupKey={})", claimId, dedupKey);
            return;
        }

        NotificationRequest request = NotificationRequest.builder()
                .claimId(claimId)
                .audience("PROVIDER")
                .provider(provider)
                .action(action)
                .payload(payload)
                .dedupKey(dedupKey)
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<NotificationRequest> entity = new HttpEntity<>(request, headers);

        restTemplate.postForObject(notificationUrl, entity, String.class);

        // Mark as delivered
        redisTemplate.opsForValue().set(NOTIFICATION_DEDUP_SET + ":" + dedupKey, "1", 7, TimeUnit.DAYS);
        log.info("Notification sent successfully for ClaimId: {}", claimId);
    }

    protected void queueNotificationFallback(String provider, String action, String claimId, String payload, Throwable t) {

        log.warn("Circuit breaker triggered for ClaimId {}: {}. Queueing request.", claimId, t.getMessage());

        NotificationRequest request = NotificationRequest.builder()
                .claimId(claimId)
                .audience("PROVIDER")
                .provider(provider)
                .action(action)
                .payload(payload)
                .dedupKey(claimId + ":" + provider + ":" + action)
                .retryCount(1)
                .build();

        try {
            String json = objectMapper.writeValueAsString(request);
            redisTemplate.opsForList().rightPush(NOTIFICATION_QUEUE, json);
            log.info("Queued notification for ClaimId {} in Redis", claimId);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize request for ClaimId {}", claimId, e);
        }
    }

    @Scheduled(fixedDelay = 60000)
    public void checkHealthAndRepublish() {
        log.info("Running notification health check...");
        try {
            String health = restTemplate.getForObject(notificationHealthCheckUrl, String.class);
            if (health != null && health.contains("\"UP\"")) {
                log.info("Notification service healthy. Processing queue...");
                processQueuedNotifications();
            } else {
                log.warn("Notification service unhealthy. Response: {}", health);
            }
        } catch (Exception e) {
            log.error("Health check failed: {}", e.getMessage());
        }
    }

    private void processQueuedNotifications() {
        log.info("Processing notification queue...");
        String json = redisTemplate.opsForList().leftPop(NOTIFICATION_QUEUE);

        while (json != null) {
            try {
                NotificationRequest req = objectMapper.readValue(json, NotificationRequest.class);
                log.info("Retrying queued notification for ClaimId {}", req.getClaimId());

                // Retry count logic
                if (req.getRetryCount() >= MAX_RETRY_ATTEMPTS) {
                    log.error("Max retries exceeded for ClaimId {}. Sending to DLQ", req.getClaimId());
                    redisTemplate.opsForList().rightPush(NOTIFICATION_DLQ, json);
                } else {
                    pushNotification(req.getProvider(), req.getAction(), req.getClaimId(), req.getPayload());
                    req.setRetryCount(req.getRetryCount() + 1);
                }

            } catch (Exception e) {
                log.error("Failed to republish queued notification. Moving to DLQ", e);
                redisTemplate.opsForList().rightPush(NOTIFICATION_DLQ, json);
            }

            json = redisTemplate.opsForList().leftPop(NOTIFICATION_QUEUE);
        }
    }
}
