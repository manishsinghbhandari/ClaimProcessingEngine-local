package io.coredge.health.claims.utils;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Slf4j
@Service
public class RedisIdempotencyService {

    @Value("${idempotency.ttl.seconds:30}")
    private int ttlTimeInSeconds;

    private final ReactiveRedisTemplate<String, Object> reactiveRedisTemplate;

    private static final String IDEMPOTENCY_PREFIX = "idempotency:";
    private static final String PROCESSED_VALUE = "processed";

    // ❗Cannot use final — must be assigned after @Value injection
    private Duration DEFAULT_TTL;

    public RedisIdempotencyService(
            @Qualifier("reactiveRedisTemplate")
            ReactiveRedisTemplate<String, Object> reactiveRedisTemplate
    ) {
        this.reactiveRedisTemplate = reactiveRedisTemplate;
    }

    // ✅ Initialize DEFAULT_TTL after Spring loads @Value
    @PostConstruct
    public void init() {
        this.DEFAULT_TTL = Duration.ofSeconds(ttlTimeInSeconds);
        log.info("🔧 Idempotency TTL configured as: {} seconds", ttlTimeInSeconds);
    }

    /**
     * SIMPLE SET NX version - Use this for synchronous code
     * Returns true if first request, false if duplicate or error
     */
    public boolean shouldProcessRequest(String key) {
        return shouldProcessRequest(key, DEFAULT_TTL);
    }

    public boolean shouldProcessRequest(String key, Duration ttl) {
        String redisKey = IDEMPOTENCY_PREFIX + key;
        try {
            Boolean result = reactiveRedisTemplate.opsForValue()
                    .setIfAbsent(redisKey, PROCESSED_VALUE, ttl)
                    .timeout(Duration.ofSeconds(2))
                    .onErrorReturn(false)  // fail-safe: treat as duplicate
                    .block(Duration.ofSeconds(3));
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            log.error("Redis idempotency check failed for key {} - {}", redisKey, e.getMessage());
            return false; // fail-safe reject
        }
    }

    /**
     * Reactive version - Use this if caller can handle Mono
     */
    public Mono<Boolean> shouldProcessRequestReactive(String key) {
        return shouldProcessRequestReactive(key, DEFAULT_TTL);
    }

    public Mono<Boolean> shouldProcessRequestReactive(String key, Duration ttl) {
        String redisKey = IDEMPOTENCY_PREFIX + key;
        return reactiveRedisTemplate.opsForValue()
                .setIfAbsent(redisKey, PROCESSED_VALUE, ttl)
                .doOnSuccess(isFirst -> {
                    if (Boolean.TRUE.equals(isFirst)) {
                        log.info("✅ FIRST request (reactive) - {}", redisKey);
                    } else {
                        log.info("🚫 DUPLICATE request (reactive) - {}", redisKey);
                    }
                })
                .doOnError(err ->
                        log.warn("⚠️ Redis error (reactive) {} - {}", redisKey, err.getMessage())
                )
                .onErrorReturn(false);
    }

    /** Manually clear an idempotency key */
    public Mono<Boolean> clearKey(String key) {
        String redisKey = IDEMPOTENCY_PREFIX + key;
        return reactiveRedisTemplate.delete(redisKey)
                .map(count -> count > 0)
                .doOnSuccess(deleted -> {
                    if (deleted) {
                        log.warn("⚠️ Manually cleared idempotency key: {}", redisKey);
                    }
                });
    }

    /** Check if a key exists */
    public Mono<Boolean> keyExists(String key) {
        String redisKey = IDEMPOTENCY_PREFIX + key;
        return reactiveRedisTemplate.hasKey(redisKey)
                .doOnSuccess(exists -> {
                    if (exists) {
                        log.debug("🔎 Key exists: {}", redisKey);
                    }
                });
    }

    /** Get count of active idempotency keys */
    public Mono<Long> getKeyCount() {
        return reactiveRedisTemplate.keys(IDEMPOTENCY_PREFIX + "*")
                .doOnNext(key -> log.debug("📝 Found key: {}", key))
                .count()
                .doOnSuccess(count ->
                        log.info("📊 Total active idempotency keys: {}", count)
                );
    }

    /** Get TTL of key */
    public Mono<Duration> getKeyTtl(String key) {
        String redisKey = IDEMPOTENCY_PREFIX + key;
        return reactiveRedisTemplate.getExpire(redisKey)
                .doOnSuccess(ttl -> {
                    if (ttl != null) {
                        log.debug("⏰ TTL for {}: {} seconds", redisKey, ttl.getSeconds());
                    }
                });
    }
}
