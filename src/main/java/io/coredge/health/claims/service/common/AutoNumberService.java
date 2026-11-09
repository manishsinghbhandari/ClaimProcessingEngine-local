package io.coredge.health.claims.service.common;

import java.lang.reflect.InvocationTargetException;

import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class AutoNumberService {

    // This method should fetch the last auto number from the repository and increment it.
    // For demonstration, it just increments the passed value.
    public Integer getNextAutoNumber(Integer lastAutoNumber) {
        return (lastAutoNumber == null) ? 1 : lastAutoNumber + 1;
    }

    // Generate a new UUID
    public String generateUuid() {
        return UUID.randomUUID().toString();
    }

    // Generate auto code (implement your logic here)
    public String generateAutoCode(Integer autoNumber) {
        return "C-" + autoNumber;
    }

    // Set all fields in the entity
    public <T> void setAutoFields(T entity, Integer lastAutoNumber) {
        try {
            entity.getClass().getMethod("setAutoNumber", Integer.class).invoke(entity, getNextAutoNumber(lastAutoNumber));
            entity.getClass().getMethod("setAutoCode", String.class).invoke(entity, generateAutoCode(getNextAutoNumber(lastAutoNumber)));
            entity.getClass().getMethod("setUuid", String.class).invoke(entity, generateUuid());
        } catch (IllegalAccessException | NoSuchMethodException | SecurityException | InvocationTargetException e) {
            throw new RuntimeException("Failed to set auto fields", e);
        }
    }
}