package io.coredge.health.claims.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.coredge.health.claims.models.GlobalPropertyEvent;
import io.coredge.health.claims.service.GlobalPropertyResolver;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.ReactiveSubscription;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;

import static io.coredge.health.claims.constants.RedisConstants.GLOBAL_PROPERTY_CHANNEL;

@Component
@Slf4j
public class GlobalPropertyRedisSubscriber {

    private final ReactiveRedisMessageListenerContainer container;
    private final GlobalPropertyResolver resolver;
    private final ObjectMapper mapper;

    public GlobalPropertyRedisSubscriber(
            ReactiveRedisMessageListenerContainer container,
            GlobalPropertyResolver resolver, ObjectMapper mapper) {

        this.container = container;
        this.resolver = resolver;
        this.mapper = mapper;
    }

    @PostConstruct
    public void subscribe() {

        container.receive(ChannelTopic.of(GLOBAL_PROPERTY_CHANNEL))
                .map(ReactiveSubscription.Message::getMessage)
                .map(String::new)
                .doOnNext(key -> {
                    log.info("Received global property update for key: {}", key);
                    try {
                        log.info("Instance {} received property update: {}",
                                InetAddress.getLocalHost().getHostName(),
                                key);
                    } catch (UnknownHostException e) {
                        log.warn("Instance {} received unknown host exception for key{}", e.getMessage() ,key);
                    }
                    handleMessage(key);
                })
                .doOnError(e -> log.error("Redis subscription error", e))
                .subscribe(); // IMPORTANT
    }

    private void handleMessage(String message) {

        try {
            GlobalPropertyEvent event =
                    mapper.readValue(message, GlobalPropertyEvent.class);

            log.info("Instance {} received property update: {} action={}",
                    System.getenv("HOSTNAME"),
                    event.getKey(),
                    event.getAction());

            if ("DELETE".equals(event.getAction())) {

                resolver.removeFromCache(event.getKey());

            } else {

                resolver.updateCache(event.getKey(), event.getValue());
            }

        } catch (Exception e) {
            log.error("Error processing redis message {}", message, e);
        }
    }
}