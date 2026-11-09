package io.coredge.health.claims.utils;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class ServiceCodeDeserializer extends JsonDeserializer<List<String>> {

    @Override
    public List<String> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        // Read the environment variable
        String format = System.getProperty("SERVICE_CODE_FORMAT", "string");
        if ("string".equalsIgnoreCase(format)) {
            // Parse the input string (e.g., "['a','b','c']")
            String text = p.getText();

            // Remove the surrounding brackets and split by commas
            return Arrays.stream(text.substring(1, text.length() - 1) // Remove []
                    .split(","))
                    .map(s -> s.trim().replaceAll("^'(.*)'$", "$1")) // Remove single quotes from elements
                    .collect(Collectors.toList());
        } else {
            // Default: Deserialize as a JSON array
            return ctxt.readValue(p, ctxt.getTypeFactory().constructCollectionType(List.class, String.class));
        }
    }
}
