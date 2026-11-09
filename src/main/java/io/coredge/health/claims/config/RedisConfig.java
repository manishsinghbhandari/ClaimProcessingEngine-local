package io.coredge.health.claims.config;

import io.coredge.health.claims.service.GlobalPropertyResolver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.connection.ReactiveSubscription;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer;
import org.springframework.data.redis.listener.Topic;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import reactor.core.publisher.Flux;

import static io.coredge.health.claims.constants.RedisConstants.*;

@Slf4j
@Configuration
public class RedisConfig {

    @Value("${redis.host}")
    private String redisHost;

    @Value("${redis.port}")
    private int redisPort;

    @Value("${redis.password}")
    private String redisPassword;

    @Value("${redis.ssl.enabled:false}") // Default to false if not specified
    private boolean redisSslEnabled;

    @Bean(name = "RedisCustomConnect")
    @Primary
    public LettuceConnectionFactory redisConnectionFactory() {
        log.info("Connecting to Redis {}:{} SSL={}",
                redisHost, redisPort, redisSslEnabled);
        RedisStandaloneConfiguration redisConfig = new RedisStandaloneConfiguration(redisHost, redisPort);
        redisConfig.setPassword(redisPassword);
        LettuceClientConfiguration.LettuceClientConfigurationBuilder clientConfigBuilder =
                LettuceClientConfiguration.builder();
        if (redisSslEnabled) {
            clientConfigBuilder.useSsl();
        }
        return new LettuceConnectionFactory(redisConfig, clientConfigBuilder.build());
    }

    @Bean
    public ReactiveRedisMessageListenerContainer redisMessageListenerContainer(LettuceConnectionFactory factory) {
        return new ReactiveRedisMessageListenerContainer(factory);
    }

    @Bean
    @Primary
    public ReactiveRedisTemplate<String, Object> reactiveRedisTemplate(ReactiveRedisConnectionFactory factory) {
        RedisSerializationContext<String, Object> serializationContext = RedisSerializationContext
                .<String, Object>newSerializationContext(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .hashKey(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .hashValue(RedisSerializationContext.SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer()))
                .value(RedisSerializationContext.SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer()))
                .build();
        return new ReactiveRedisTemplate<>(factory, serializationContext);
    }

    @Bean
    public Topic priorityTopic() {
        return new ChannelTopic(EMERGENCY_CHANNEL);
    }

    @Bean
    public Topic normalTopic() {
        return new ChannelTopic(NORMAL_CHANNEL);
    }

    @Bean
    public Flux<String> priorityClaimsStream(ReactiveRedisMessageListenerContainer container, Topic priorityTopic) {
        return container
                .receive((ChannelTopic) priorityTopic)
                .map(ReactiveSubscription.Message::getMessage)
                .doOnNext(msg -> log.info("Received emergency request,  claim ID: {}", msg));
    }

    @Bean
    public Flux<String> normalClaimsStream(ReactiveRedisMessageListenerContainer container, Topic normalTopic) {
        return container
                .receive((ChannelTopic) normalTopic)
                .map(ReactiveSubscription.Message::getMessage)
                .doOnNext(msg -> log.info("Received normal request, claim ID: {}", msg));
    }

    @Bean
    public Topic globalPropertyTopic() {
        return new ChannelTopic(GLOBAL_PROPERTY_CHANNEL);
    }

}
