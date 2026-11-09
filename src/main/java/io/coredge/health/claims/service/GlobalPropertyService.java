package io.coredge.health.claims.service;

import io.coredge.health.claims.db.entity.GlobalProperty;
import io.coredge.health.claims.db.repositories.GlobalPropertyRepository;
import io.coredge.health.claims.exception.app.DataNotFoundException;
import io.coredge.health.claims.exception.app.DatabaseException;
import io.coredge.health.claims.models.GlobalPropertyEvent;
import io.coredge.health.claims.service.properties.GlobalPropertyRequest;
import io.coredge.health.claims.service.properties.GlobalPropertyResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.listener.Topic;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.PagedModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
public class GlobalPropertyService {

    private final GlobalPropertyRepository globalPropertyRepository;
    private final PagedResourcesAssembler<GlobalPropertyResponse> pagedResourcesAssembler;
    private final GlobalPropertyResolver globalPropertyResolver;
    private final ReactiveRedisTemplate<String, Object> redisTemplate;
    private final Topic globalPropertyTopic;


    @Autowired
    public GlobalPropertyService(GlobalPropertyRepository globalPropertyRepository, PagedResourcesAssembler<GlobalPropertyResponse> pagedResourcesAssembler, GlobalPropertyResolver globalPropertyResolver, ReactiveRedisTemplate<String, Object> redisTemplate, Topic globalPropertyTopic) {
        this.globalPropertyRepository = globalPropertyRepository;
        this.pagedResourcesAssembler = pagedResourcesAssembler;
        this.globalPropertyResolver = globalPropertyResolver;
        this.redisTemplate = redisTemplate;
        this.globalPropertyTopic = globalPropertyTopic;
    }

    @Transactional
    public GlobalPropertyResponse createGlobalProperty(GlobalPropertyRequest request) {
        boolean exist = globalPropertyRepository.existsByPropertyAndDeleted(request.getKey(), false);
        if (exist) {
            throw new DatabaseException("Duplicate Entry not allowed for Given Key:" + request.getKey());
        }
        GlobalProperty property = GlobalProperty.builder()
                .property(request.getKey())
                .value(request.getValue())
                .status(true)
                .deleted(false)
                .build();

        GlobalProperty storedValue = globalPropertyRepository.save(property);
        // Publish event reactively
        publishGloPropertyTopic(storedValue);
        return convertToGlobalPropertyResponse(storedValue);

    }

    public PagedModel<EntityModel<GlobalPropertyResponse>> fetchGlobalProperties(String search, Pageable pageable) {
        Optional<Page<GlobalProperty>> properties = globalPropertyRepository.filterByKey(search, false, pageable);

        Page<GlobalPropertyResponse> responses = properties.filter(Page::hasContent)
                .map(page -> page.map(this::convertToGlobalPropertyResponse))
                .orElseThrow(DataNotFoundException::new);

        return pagedResourcesAssembler.toModel(responses);
    }

    public GlobalPropertyResponse fetchGlobalProperty(Long id) {
        Optional<GlobalProperty> property = globalPropertyRepository.findByIdAndDeleted(id, false);
        if (property.isEmpty()) {
            throw new DataNotFoundException();
        }
        return convertToGlobalPropertyResponse(property.get());
    }

    @Transactional
    public GlobalPropertyResponse updateGlobalProperty(long id, GlobalPropertyRequest request) {

        Optional<GlobalProperty> property = globalPropertyRepository.findByIdAndDeleted(id, false);
        if (property.isEmpty()) {
            throw new DataNotFoundException();
        }

        boolean exist = globalPropertyRepository.existsByPropertyAndDeleted(request.getKey(), false);
        if (exist) {
            throw new DatabaseException("Duplicate Entry not allowed for Given Key:" + request.getKey());
        }
        GlobalProperty value = property.get();
        value.setDeleted(false);
        value.setValue(request.getValue());
        value.setProperty(request.getKey());
        GlobalProperty storedValue = globalPropertyRepository.save(value);
        // Publish event reactively
        publishGloPropertyTopic(storedValue);
        return convertToGlobalPropertyResponse(storedValue);
    }

    @Transactional
    public void updateGlobalPropertyStatus(long id, boolean status) {
        Optional<GlobalProperty> property = globalPropertyRepository.findByIdAndDeleted(id, false);
        if (property.isEmpty()) {
            throw new DataNotFoundException();
        }
        GlobalProperty globalProperty = property.get();
        globalProperty.setStatus(status);
        GlobalProperty storedValue = globalPropertyRepository.save(globalProperty);
        // Publish event reactively
        publishGloPropertyTopic(storedValue);
    }

    @Transactional
    public void deleteGlobalProperty(long id) {
        Optional<GlobalProperty> property = globalPropertyRepository.findByIdAndDeleted(id, false);
        if (property.isEmpty()) {
            throw new DataNotFoundException();
        }

        GlobalProperty storedValue = property.get();
        storedValue.setDeleted(true);
        globalPropertyRepository.save(storedValue);
        // Publish event reactively
        publishGloPropertyTopic(storedValue);
    }


    public Map<String, String> getProperties() {
        return globalPropertyResolver.getCache();
    }

    public String getPropertyValue(String key) {

        return globalPropertyResolver.resolve(key);
    }

    private GlobalPropertyResponse convertToGlobalPropertyResponse(GlobalProperty property) {
        return GlobalPropertyResponse.builder()
                .id(property.getId())
                .key(property.getProperty())
                .value(property.getValue())
                .status(property.getStatus())
                .build();
    }

    public void publishGloPropertyTopic(GlobalProperty property) {
        GlobalPropertyEvent event = new GlobalPropertyEvent(
                property.getProperty(),
                property.getValue(),
                property.getStatus() && !property.getDeleted() ? "UPSERT"  : "DELETE"
        );

        redisTemplate.convertAndSend(globalPropertyTopic.getTopic(), event)
                .doOnSuccess(count ->
                        log.info("Published property update to {} subscribers", count))
                .doOnError(err ->
                        log.error("Failed to publish redis event", err))
                .subscribe();
    }
}
