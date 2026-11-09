package io.coredge.health.claims.service.operate;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.coredge.health.claims.dto.operate.OperateProcessInstance;
import io.coredge.health.claims.dto.operate.OperateProcessInstanceResponse;
import io.coredge.health.claims.service.keycloak.KeycloakTokenService;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
public class OperateProcess {

    @Value("${camunda.client.operate.base-url}")
    private String operateBaseUrl;

    private final KeycloakTokenService keycloakTokenService;
    private final RestTemplate restTemplate;

    @Autowired
    public OperateProcess(KeycloakTokenService keycloakTokenService, RestTemplate restTemplate) {
        this.keycloakTokenService = keycloakTokenService;
        this.restTemplate = restTemplate;
    }

    public OperateProcessInstanceResponse getProcessInstances(String claimId) {

        String url = operateBaseUrl + "/api/process-instances";

        log.info("Operate URL: {}", url);
        log.info("Searching for claimId: {}", claimId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String token = keycloakTokenService.getToken();
        log.info("Token fetched: {}", token != null ? "YES" : "NO");

        assert token != null;
        headers.setBearerAuth(token);

        // IMPORTANT: Operate UI wraps value in quotes
        String quotedValue = "\"" + claimId + "\"";

        HttpEntity<Map<String, Object>> entity = getMapHttpEntity(quotedValue, headers);

        try {
            ResponseEntity<String> rawResponse = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    String.class
            );

            log.info("Raw JSON Response: {}", rawResponse.getBody());

            // Now deserialize manually to confirm mapping
            ObjectMapper mapper = new ObjectMapper();
            mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

            OperateProcessInstanceResponse parsed =
                    mapper.readValue(rawResponse.getBody(), OperateProcessInstanceResponse.class);

            log.info("Parsed totalCount: {}", parsed.getTotalCount());

            return parsed;

        } catch (Exception e) {
            log.error("Error fetching claimId {} from Operate: {}", claimId, e.getMessage(), e);
            throw new RuntimeException("Exception occurred in operate client", e);
        }
    }

    private static @NotNull HttpEntity<Map<String, Object>> getMapHttpEntity(String claimId, HttpHeaders headers) {
        Map<String, Object> payload = Map.of(
                "query", Map.of(
                        "active", true,
                        "running", true,
                        "incidents", true,
                        "variable", Map.of(
                                "name", "claimId",
                                "values", List.of(claimId)
                        )
                ),
                "sorting", Map.of(
                        "sortBy", "startDate",
                        "sortOrder", "desc"
                ),
                "pageSize", 50
        );


        return new HttpEntity<>(payload, headers);
    }

    /**
     * Check if the claimId has any active process instance
     */
    public OperateProcessInstanceResponse getActiveInstances(String claimId) {
        OperateProcessInstanceResponse operateProcessInstanceResponse = getProcessInstances(claimId);

        List<OperateProcessInstance> filtered = Optional.ofNullable(operateProcessInstanceResponse.getProcessInstances())
                .stream().flatMap(Collection::stream)
                .filter(pi -> {
                    String state = pi.getState();
                    return state != null && (state.equalsIgnoreCase("ACTIVE"));
                }).toList();

        return new OperateProcessInstanceResponse(filtered, filtered.size());
    }

    public boolean hasOnlyActiveInstance(String claimId) {
        OperateProcessInstanceResponse operateResp = getProcessInstances(claimId);

        return operateResp.getProcessInstances().stream()
                .anyMatch(pi -> "ACTIVE".equalsIgnoreCase(pi.getState()));
    }
}
