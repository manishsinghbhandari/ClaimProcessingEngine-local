package io.coredge.health.claims.utils.benefits;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class BenefitsMasterToJson {


    private BenefitsMasterToJson() {
    }


    public static String toJson(ObjectMapper objectMapper, Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize audit JSON in BenefitsMasterAuditUtils :{}", e.getMessage());
            throw new RuntimeException("Failed to serialize audit JSON", e);
        }
    }
}

