package io.coredge.health.claims.payments.utils;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

@Slf4j
@Converter
public class JpaCryptoConverter implements AttributeConverter<String, String> {

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (!StringUtils.hasText(attribute)) {
            return attribute;
        }
        try {
            return CryptoUtils.encrypt(attribute);
        } catch (Exception e) {
            log.error("Encryption failed for attribute: {}", attribute, e);
            throw new RuntimeException("Encryption failed", e);
        }
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (!StringUtils.hasText(dbData)) {
            return dbData;
        }
        try {
            return CryptoUtils.decrypt(dbData);
        } catch (Exception e) {
            log.warn("Decryption failed for data. Returning raw data. Error: {}", e.getMessage());
            return dbData;
        }
    }
}
