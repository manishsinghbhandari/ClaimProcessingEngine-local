package io.coredge.health.claims.utils.AI;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public class JsonUtil {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private JsonUtil() {
        // prevent instantiation
    }

    /**
     * Converts any Java object to a JSON string.
     *
     * @param object the object to convert
     * @return JSON representation as String
     */
    public static <T> String convertToJson(T object) {
        if (object == null) {
            return null;
        }
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());
            mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS); // ISO-8601
            return mapper.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error converting object to JSON: " + e.getMessage(), e);
        }
    }

}
