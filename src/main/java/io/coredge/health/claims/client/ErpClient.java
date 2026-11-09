package io.coredge.health.claims.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.Http;
import io.coredge.health.claims.exception.app.DataValidationException;
import io.coredge.health.claims.config.properties.ApplicationProperties;
import io.coredge.health.claims.models.eclaims.CreateClaimFundScheme;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class ErpClient {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final ApplicationProperties.Erp erpProperties;

    public ErpClient(WebClient.Builder webClientBuilder, ObjectMapper objectMapper, ApplicationProperties.Erp erpProperties) {
        this.webClient = webClientBuilder.build();
        this.objectMapper = objectMapper;
        this.erpProperties = erpProperties;
    }


    // Single ERP call
    public String callOdooErp(String requestJson) {
        return sendRequest(erpProperties.getBaseUri(), requestJson);
    }


    // Update the Payment status,  ERP call
    public String updateOdooErp(String requestJson) {
        return sendRequest(erpProperties.getBaseUpdateStatusUri(), requestJson, getHttpHeaders(erpProperties.getApiUpdateKey()));
    }


    // Multi ERP call
    public String callErpMulti(List<CreateClaimFundScheme> claimRequestList) {
        try {
            Map<String, Object> payload = Map.of("claims", claimRequestList);
            String requestJson = objectMapper.writeValueAsString(payload);
            log.debug("Request Body Multi ERP: {}", requestJson);
            return sendRequest(erpProperties.getBaseMultiUri(), requestJson);
        } catch (Exception e) {
            log.warn("ERP Multi call error: {}", e.getMessage(), e);
            return "Error:";
        }
    }


    private String sendRequest(String url, String requestJson) {
        return sendRequest(url, requestJson, getHttpHeaders(erpProperties.getApiKey()));
    }

    // Generic WebClient call
    private String sendRequest(String url, String requestJson, HttpHeaders header) {
        return webClient.post()
                .uri(url)
                .bodyValue(requestJson)
                .headers(headers -> headers.addAll(header))
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, this::handle4xxErrors)
                .onStatus(HttpStatusCode::is5xxServerError, this::handle5xxErrors)
                .bodyToMono(String.class)
                .doOnSuccess(resp -> log.info("ERP response received"))
                .doOnError(err -> log.warn("Error in ERP call: {}", err.getMessage()))
                .block();
    }

    private Mono<? extends Throwable> handle4xxErrors(org.springframework.web.reactive.function.client.ClientResponse response) {
        return response.bodyToMono(String.class)
                .flatMap(errorBody -> {
                    log.warn("Client Error: Status Code {}, Response Body: {}", response.statusCode(), errorBody);
                    return Mono.error(new RuntimeException(errorBody));
                });
    }

    private Mono<? extends Throwable> handle5xxErrors(org.springframework.web.reactive.function.client.ClientResponse response) {
        return response.bodyToMono(String.class)
                .flatMap(errorBody -> {
                    log.warn("Server Error: Status Code {}, Response Body: {}", response.statusCode(), errorBody);
                    return Mono.error(new RuntimeException("Server error occurred"));
                });
    }

    private HttpHeaders getHttpHeaders(String apiKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("api-key", apiKey);
        return headers;
    }
}
