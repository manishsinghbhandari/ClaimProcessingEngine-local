package io.coredge.health.claims.service;

import io.coredge.health.claims.db.repositories.GlobalPropertyRepository;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class GlobalPropertyResolver {

    private final Environment environment;
    private final GlobalPropertyRepository repository;

    @Getter
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public GlobalPropertyResolver(Environment environment,
                                  GlobalPropertyRepository repository) {
        this.environment = environment;
        this.repository = repository;
    }

    /**
     * Load all DB properties at startup
     */
    @PostConstruct
    public void preload() {
        repository.findByDeletedFalseAndStatusTrue()
                .forEach(p -> cache.put(p.getProperty(), p.getValue()));
    }

    /**
     * Resolve property:
     * 1. DB cache
     * 2. application.properties fallback
     */
    public String resolve(String key) {
        return cache.getOrDefault(key, environment.getProperty(key));
    }

    public void updateCache(String key, String value) {

        cache.put(key, value);

        log.info("Cache updated: {}={}", key, value);
    }

    public void removeFromCache(String key) {

        cache.remove(key);

        log.info("Cache removed: {}", key);
    }
}