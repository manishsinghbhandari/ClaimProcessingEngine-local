package io.coredge.health.claims.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.Base64;
import java.util.Map;
import java.util.Collections;
import java.util.List;
@Slf4j
@UtilityClass
public class JwtDecoderUtils {

    private Map<String, Object> decodeJWT(String jwtToken) {
        try {
            String payload = new String(Base64.getUrlDecoder().decode(jwtToken.split("\\.")[1]));
            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.readValue(payload, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.error("Error decoding JWT: {}", e.getMessage());
            throw new RuntimeException("Failed to decode JWT", e);
        }
    }

    public String getUserIdByToken(String jwtToken) {
        return (String) decodeJWT(jwtToken).get("preferred_username");
    }

    public boolean hasAdminRole(String jwtToken) {
        List<String> roles = getRolesFromToken(jwtToken);
        return roles.contains("admin");
    }

    public List<String> getRolesFromToken(String jwtToken) {
        Map<String, Object> tokenPayload = decodeJWT(jwtToken);
        Map<String, Object> resourceAccess = (Map<String, Object>) tokenPayload.get("resource_access");
        if (resourceAccess != null) {
            Map<String, Object> eclaimportal = (Map<String, Object>) resourceAccess.get("eclaimportal");
            if (eclaimportal != null) {
                return (List<String>) eclaimportal.get("roles");
            }
        }
        return Collections.emptyList();
    }
}
