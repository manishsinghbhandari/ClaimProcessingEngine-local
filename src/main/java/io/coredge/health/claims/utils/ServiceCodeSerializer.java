package io.coredge.health.claims.utils;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public class ServiceCodeSerializer extends JsonSerializer<List<String>> {

    @Override
    public void serialize(List<String> value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        // Read the environment variable
        String format = System.getProperty("SERVICE_CODE_FORMAT", "string");
        if ("string".equalsIgnoreCase(format)) {
            // Convert the list into a single string with single quotes around the elements
            String formattedList = value.stream()
                    .map(s -> "'" + s + "'")  // Add single quotes around each element
                    .collect(Collectors.joining(",", "[", "]"));  // Join the elements with commas, with [] at the start and end

            // Write the formatted list as a string
            gen.writeString(formattedList);
        } else {
            // Default: Serialize as a JSON array
            gen.writeObject(value);
        }
    }
}
