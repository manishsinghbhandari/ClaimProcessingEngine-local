package io.coredge.health.claims.db.repositories.converter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.coredge.health.claims.models.Predetermination;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class JsonConverter implements AttributeConverter<Predetermination, String> {

    private final ObjectMapper mapper;

    public JsonConverter() {
        this.mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Override
    public String convertToDatabaseColumn(Predetermination object) {
        try {
            return mapper.writeValueAsString(object);
        } catch (Exception e) {
            throw new IllegalArgumentException("Error converting to JSON", e);
        }
    }

    @Override
    public Predetermination convertToEntityAttribute(String json) {
        try {
            return mapper.readValue(json, Predetermination.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Error converting from JSON", e);
        }

    }
}
