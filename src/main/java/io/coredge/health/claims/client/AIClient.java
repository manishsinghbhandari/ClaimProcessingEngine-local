package io.coredge.health.claims.client;

import io.coredge.health.claims.config.properties.ApplicationProperties;
import io.coredge.health.claims.exception.app.AIEngineException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import jakarta.annotation.PostConstruct;
import reactor.util.retry.Retry;
import java.time.Duration;
import java.util.UUID;

@Slf4j
@Service
public class AIClient {

    private WebClient webClient;

    private final WebClient.Builder webClientBuilder;
    private final ApplicationProperties.Ai aiProperties;

    public AIClient(WebClient.Builder webClientBuilder, ApplicationProperties.Ai aiProperties) {
        this.webClientBuilder = webClientBuilder;
        this.aiProperties = aiProperties;
    }

    @PostConstruct
    public void init() {
        // Normalize base URL and URI
        String baseUrl = aiProperties.getBaseUrl();
        if (baseUrl == null || baseUrl.isEmpty()) {
            log.warn("AI notes base URL is not configured");
            return;
        }
        baseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
        String claimUri = aiProperties.getClaimBundleUri();
        claimUri = claimUri.startsWith("/") ? claimUri.substring(1) : claimUri;
        String fullUrl = baseUrl + claimUri;

        this.webClient = webClientBuilder
                .baseUrl(fullUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + aiProperties.getAuthToken())
                .defaultHeader("X-Encryption-Key", aiProperties.getEncryptionKey())
                .build();
    }

    public ResponseEntity<String> postClaim(String bundle, UUID claimUUId) {
        return postClaimToRoute(bundle, claimUUId, null);
    }

    /**
     * Post a claim to a specific AI route endpoint.
     * 
     * @param bundle        the claim bundle JSON
     * @param claimUUId     the claim UUID
     * @param routeEndpoint optional custom route endpoint. If null or empty, uses default.
     * @return the response from the AI engine
     */
    public ResponseEntity<String> postClaimToRoute(String bundle, UUID claimUUId, String routeEndpoint) {
        WebClient targetClient = this.webClient;

        // If a custom route endpoint is provided, build a new WebClient with that
        // endpoint
        if (routeEndpoint != null && !routeEndpoint.isEmpty()) {
            String fullUrl;
            if (routeEndpoint.startsWith("http://") || routeEndpoint.startsWith("https://")) {
                fullUrl = routeEndpoint;
            } else {
                String baseUrl = aiProperties.getBaseUrl();
                if (baseUrl == null || baseUrl.isEmpty()) {
                    log.warn("AI base URL is not configured and route endpoint is relative: {}", routeEndpoint);
                    return targetClient.post()
                            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .bodyValue(bundle)
                            .retrieve()
                            .toEntity(String.class)
                            .block();
                }
                baseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
                String routePath = routeEndpoint.startsWith("/") ? routeEndpoint.substring(1) : routeEndpoint;
                fullUrl = baseUrl + routePath;
                log.info("Using custom AI route for claimId {}: {}", claimUUId, fullUrl);
            }

            targetClient = webClientBuilder
                    .baseUrl(fullUrl)
                    .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + aiProperties.getAuthToken())
                    .defaultHeader("X-Encryption-Key", aiProperties.getEncryptionKey())
                    .build();
        }

        return targetClient.post()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .bodyValue(bundle)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, response -> response.bodyToMono(String.class)
                        .doOnNext(body -> log.warn(
                                "4xx Client Error while sending claim with claimId {} to AI, Status: {}, Response: {}",
                                claimUUId, response.statusCode(), body))
                        .then(Mono.error(new AIEngineException("Client error while sending claim with claimId: "
                                + claimUUId + " to AI engine: " + response.statusCode()))))
                .onStatus(HttpStatusCode::is5xxServerError, response -> response.bodyToMono(String.class)
                        .doOnNext(body -> log.error(
                                "5xx Server Error while sending claim with claimId {} to AI, Status: {}, Response: {}",
                                claimUUId, response.statusCode(), body))
                        .then(Mono.error(new RuntimeException("Server error while sending claim with claimId: "
                                + claimUUId + " to AI engine: " + response.statusCode()))))
                .onStatus(status -> !status.is2xxSuccessful(), response -> response.bodyToMono(String.class)
                        .doOnNext(body -> log.error(
                                "Unexpected HTTP status while sending claim with claimId {} to AI, Status: {}, Response: {}",
                                claimUUId, response.statusCode(), body))
                        .then(Mono.error(new RuntimeException("Unexpected HTTP error with claimId: " + claimUUId
                                + " status: " + response.statusCode()))))
                .toEntity(String.class)
                .doOnSuccess(response -> log.info("Claim sent with claimId {} to AI engine, Status: {}", claimUUId,
                        response.getStatusCode()))
                .doOnError(error -> log.error("Error sending claim with claimId {} to AI engine: {}", claimUUId,
                        error.getMessage()))
                .retryWhen(
                        Retry.backoff(aiProperties.getRetryMaxAttempt(),
                                Duration.ofSeconds(aiProperties.getRetryDurationSecond()))
                                .filter(this::isRetryableException))
                .onErrorResume(ex -> {
                    log.info("Error while Processing AI Engine Validation for claimId:{} : with error :{}", claimUUId,
                            ex.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body("[AI Engine] Retries exhausted for claimId: " + claimUUId + ":: with error:"
                                    + ex.getMessage()));

                })
                .block(); // This makes the call synchronous
    }

    private boolean isRetryableException(Throwable throwable) {
        if (throwable instanceof WebClientResponseException ex) {
            return ex.getStatusCode().is5xxServerError(); // retry only on 5xx
        }
        return false;
    }
}