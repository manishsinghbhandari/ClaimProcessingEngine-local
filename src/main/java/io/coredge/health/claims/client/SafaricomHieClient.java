package io.coredge.health.claims.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.coredge.health.claims.config.properties.ApplicationProperties;
import io.coredge.health.claims.exception.app.HIEAuthenticationException;
import io.coredge.health.claims.exception.app.HIEServiceNotAvailableException;
import io.coredge.health.claims.mapper.FhirOrganizationMapper;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static io.coredge.health.claims.constants.Constants.Request.*;

@Slf4j
@Component
public class SafaricomHieClient {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final ApplicationProperties.SafaricomHie safaricomHieProperties;

    public SafaricomHieClient(WebClient.Builder webClientBuilder, ObjectMapper objectMapper,                               ApplicationProperties.SafaricomHie safaricomHieProperties) {
        this.webClient = webClientBuilder.build();
        this.objectMapper = objectMapper;
        this.safaricomHieProperties = safaricomHieProperties;
    }

    public String fetchDetails(UriComponentsBuilder uriBuilder) {
        String uri = uriBuilder.toUriString();
        String authTokenResponse = getBasicAuth();

        if (authTokenResponse.isEmpty()) {
            log.warn("⚠️🚨 SAFARICOM HIE Token API failed. Cannot proceed.");
            throw new HIEAuthenticationException("Token response is empty.");
        }

        String authToken;
        try {
            JsonNode jsonNode = objectMapper.readTree(authTokenResponse);
            authToken = jsonNode.path("access_token").asText(null);
        } catch (Exception e) {
            log.warn("❌ Failed to parse access token response.", e);
            throw new HIEAuthenticationException("Failed to parse access token JSON.");
        }

        if (authToken == null) {
            log.warn("❌ Access token missing in SAFARICOM HIE response.");
            throw new HIEAuthenticationException("Access token is null in response.");
        }

        try {
            HttpHeaders headers = createAuthHeaders(authToken);
            String response = webClient.get()
                    .uri(uri)
                    .headers(httpHeaders -> httpHeaders.addAll(headers))
                    .retrieve()
                    .onStatus(
                            HttpStatusCode::is5xxServerError,
                            clientResponse -> Mono.error(new HIEServiceNotAvailableException(uri, new RuntimeException("Service returned 5xx error")))
                    )
                    .bodyToMono(String.class)
                    .block();

            log.debug("✅ Response from SAFARICOM HIE: {}", response);

            return safaricomHieProperties.isCustomApi()
                    ? FhirOrganizationMapper.getOrganizationBundle(response)
                    : response;

        } catch (Exception e) {
            log.warn("❌ SAFARICOM HIE call failed to URI: {}", uri, e);
            throw new HIEServiceNotAvailableException(uri, e);
        }
    }


    private @NotNull HttpHeaders createAuthHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(AUTHORIZATION, token.startsWith(BASIC_AUTH.trim()) ? token : BEARER + token);
        return headers;
    }

    public String getBasicAuth() {
        String authHeader = BASIC_AUTH_HEADER + Base64.getEncoder()
                .encodeToString((safaricomHieProperties.getUsername() + ":" + safaricomHieProperties.getPassword()).getBytes(StandardCharsets.UTF_8));

        String uri = safaricomHieProperties.getAuthUri() + "?grant_type=client_credentials";

        WebClient.RequestHeadersSpec<?> request = safaricomHieProperties.getTokenApiType().equalsIgnoreCase("GET")
                ? webClient.get().uri(uri)
                : webClient.post().uri(uri);

        return request.headers(headers -> headers.addAll(createAuthHeaders(authHeader)))
                .retrieve()
                .bodyToMono(String.class)
                .doOnSuccess(response -> log.info("✅ Access Token received successfully"))
                .doOnError(error -> log.warn("🚨 SAFARICOM TOKEN API ERROR: Failed to retrieve access token! Cause: {}", error.getMessage(), error))
                .onErrorReturn("")
                .block();
    }
}
