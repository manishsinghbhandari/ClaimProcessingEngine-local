package io.coredge.health.claims.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.context.refresh.ContextRefresher;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Redis Pub/Sub based refresh broadcaster.
 * Allows broadcasting config refresh to all application instances.
 * 
 * Usage: Call broadcastRefresh() or POST to /actuator/refresh-all
 * to refresh all instances simultaneously.
 */
@Slf4j
@Component
public class RedisRefreshBroadcaster {

    private static final String REFRESH_CHANNEL = "config-refresh";

    private final StringRedisTemplate redisTemplate;
    private final ContextRefresher contextRefresher;
    private final RedisMessageListenerContainer listenerContainer;

    public RedisRefreshBroadcaster(StringRedisTemplate redisTemplate,
                                   ContextRefresher contextRefresher,
                                   org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory connectionFactory) {
        this.redisTemplate = redisTemplate;
        this.contextRefresher = contextRefresher;
        
        // Create a dedicated listener container for refresh messages
        this.listenerContainer = new RedisMessageListenerContainer();
        this.listenerContainer.setConnectionFactory(connectionFactory);
    }

    @PostConstruct
    public void subscribe() {
        listenerContainer.addMessageListener(
                new MessageListenerAdapter(new RefreshMessageHandler()),
                new ChannelTopic(REFRESH_CHANNEL)
        );
        listenerContainer.afterPropertiesSet();
        listenerContainer.start();
        log.info("Subscribed to Redis channel for config refresh: {}", REFRESH_CHANNEL);
    }

    /**
     * Broadcasts a refresh signal to ALL instances via Redis Pub/Sub.
     * Each instance subscribed to the channel will refresh its context.
     */
    public void broadcastRefresh() {
        log.info("Broadcasting refresh signal to all instances via Redis channel: {}", REFRESH_CHANNEL);
        redisTemplate.convertAndSend(REFRESH_CHANNEL, "REFRESH");
    }

    /**
     * Inner class to handle refresh messages.
     */
    private class RefreshMessageHandler implements MessageListener {
        @Override
        public void onMessage(Message message, byte[] pattern) {
            String payload = new String(message.getBody());
            log.info("Received refresh signal via Redis: {}", payload);
            
            Set<String> refreshedKeys = contextRefresher.refresh();
            
            if (refreshedKeys.isEmpty()) {
                log.info("Context refreshed, no property changes detected");
            } else {
                log.info("Context refreshed, updated properties: {}", refreshedKeys);
            }
        }
    }
}
