package io.coredge.health.claims.utils;

import io.coredge.health.claims.db.entity.UserConsumer;
import lombok.experimental.UtilityClass;

import java.time.LocalDateTime;
import java.util.UUID;

@UtilityClass
public class TokenGenerator {
    public String generateToken() {
        return UUID.randomUUID().toString(); // Generate a random UUID as the token
    }
}
