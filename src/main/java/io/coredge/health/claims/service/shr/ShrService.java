package io.coredge.health.claims.service.shr;

import ca.uhn.fhir.context.FhirContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.coredge.health.claims.exception.app.ResourceNotFoundException;
import io.coredge.health.claims.exception.shr.ShrDataNotFoundException;
import io.coredge.health.claims.utils.FhirHelper;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Slf4j
@Service
public class ShrService {

    @Value("${shr.base-url}")
    private String baseUrl;

     @Value("${shr.auth-endpoint}")
     private String authEndpoint;

    @Value("${shr.summary-endpoint}")
    private String summaryEndpoint;

    @Value("${shr.consumer-key}")
    private String consumerKey;

    @Value("${shr.username}")
    private String username;

    @Value("${shr.password}")
    private String password;

    private final WebClient webClient;

    private final FhirHelper fhirHelper;
    private final FhirContext fhirContext;


    public ShrService(WebClient.Builder webClientBuilder, FhirHelper fhirHelper, FhirContext fhirContext) {
        this.webClient = webClientBuilder.build();
        this.fhirHelper = fhirHelper;
        this.fhirContext = fhirContext;
    }


    public Mono<ResponseEntity<String>> getShrSummary(String crId) {
        String authUrl = baseUrl + authEndpoint + "?key=" + consumerKey;
        log.info("Calling SHR Auth URL: {}", authUrl);

        return webClient.get()
                .uri(authUrl)
                .header(HttpHeaders.AUTHORIZATION, createBasicAuthHeader(username, password))
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(String.class)
                .map(token -> token != null ? token.trim() : "")
                .flatMap(token -> callShrSummaryApi(token, crId));
    }

    private Mono<ResponseEntity<String>> callShrSummaryApi(String token, String crId) {
        String url = summaryEndpoint + "?cr_id=" + crId;
        log.info("Calling SHR Summary API: {}", url);

        return webClient.get()
                .uri(url)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .toEntity(String.class)
                .onErrorResume(WebClientResponseException.NotFound.class, ex -> {
                    log.warn("CR ID not found: {}", crId);
                    return Mono.just(ResponseEntity
                            .status(HttpStatus.NOT_FOUND)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(ex.getResponseBodyAsString()));
                })
                .onErrorResume(WebClientResponseException.class, ex -> {
                    log.error("SHR API error: {}", ex.getResponseBodyAsString(), ex);
                    return Mono.just(ResponseEntity
                            .status(ex.getStatusCode())
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(ex.getResponseBodyAsString()));
                });
    }



    private String createBasicAuthHeader(String username, String password) {
        String credentials = username + ":" + password;
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
      }

    /**
     * Retrieves the IPS (International Patient Summary) Bundle from the FHIR server
     * using the provided bundle ID and returns it as a formatted JSON string.
     *
     * @param bundleId the ID of the FHIR Bundle to retrieve
     * @return the Bundle resource serialized as a pretty-printed JSON string
     * @throws ShrDataNotFoundException if the bundle is not found on the FHIR server
     */
    public String getIpsDetails(String bundleId) {
        try {
            log.info("Fetching FHIR Bundle using fhirClient for bundleId: {}", bundleId);

            Bundle bundle = fhirHelper.getBundleObject(bundleId);
            return fhirContext
                    .newJsonParser()
                    .encodeResourceToString(bundle);

        } catch (ResourceNotFoundException ex) {
            String responseBody = ex.getMessage();
            log.error("FHIR resource not found (ResourceNotFoundException): {}", responseBody);

            String diagnostics = extractDiagnostics(responseBody);
            throw new ShrDataNotFoundException(diagnostics);

        } catch (Exception ex) {
            log.error("Error fetching IPS Bundle: {}", ex.getMessage(), ex);
            throw ex; // or wrap if needed
        }
    }

    public String extractDiagnostics(String json) {
        try {
            JsonNode root = new ObjectMapper().readTree(json);
            return root.path("issue").get(0).path("diagnostics").asText(null);
        } catch (Exception e) {
            return null;
        }
    }
}
