package io.coredge.health.claims.utils.AI;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import io.coredge.health.claims.models.AI.ScoreDetail;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.List;

@Slf4j
public class ScoreDetailDeserializer extends JsonDeserializer<ScoreDetail> {

    @Override
    public ScoreDetail deserialize(JsonParser parser, DeserializationContext context)
            throws IOException {

        JsonNode node = parser.getCodec().readTree(parser);

        // Case 1: "imageQuality": "0%"
        if (node.isTextual()) {
            log.warn("AI ScoreDetail received as STRING value: {}", node.asText());
            return new ScoreDetail(node.asText(), List.of());
        }

        // Case 2: "imageQuality": false
        if (node.isBoolean()) {
            log.warn("AI ScoreDetail received as BOOLEAN value: {}", node.asBoolean());
            return new ScoreDetail(String.valueOf(node.asBoolean()), List.of());
        }

        // Case 3: "imageQuality": 0
        if (node.isNumber()) {
            log.warn("AI ScoreDetail received as NUMBER value: {}", node.asText());
            return new ScoreDetail(node.asText(), List.of());
        }

        // Case 4: Proper object
        if (node.isObject()) {
            String score = null;
            List<String> remarks = List.of();

            if (node.has("score") && !node.get("score").isNull()) {
                score = node.get("score").asText();
            } else {
                log.warn("AI ScoreDetail object missing 'score' field: {}", node);
            }

            if (node.has("remarks") && node.get("remarks").isArray()) {
                remarks = parser.getCodec().treeToValue(node.get("remarks"), List.class);
            } else {
                log.warn("AI ScoreDetail object missing or invalid 'remarks' field: {}", node);
            }

            log.debug("AI ScoreDetail parsed successfully | score={} | remarksCount={}",
                    score, remarks.size());

            return new ScoreDetail(score, remarks);
        }

        // Fallback (unexpected structure)
        log.error("AI ScoreDetail received in UNKNOWN format: {}", node);
        return new ScoreDetail(null, List.of());
    }
}

