package io.coredge.health.claims.payments.clients;

import io.coredge.health.claims.payments.dto.DisbursementRequest;
import io.coredge.health.claims.payments.exception.PaymentGateway400Exception;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;

@Slf4j
@Component
public class PaymentGatewayClient {

    private final WebClient webClient;

    @Value("${payment.gateway.base-url}")
    private String baseUrl;

    @Value("${payment.gateway.create-endpoint}")
    private String createEndpoint;

    @Value("${payment.gateway.auth-endpoint}")
    private String authEndpoint;

    @Value("${payment.gateway.username}")
    private String username;

    @Value("${payment.gateway.password}")
    private String password;

    public PaymentGatewayClient(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    /**
     * Retrieves a new authentication token from the gateway.
     */
    private String getToken() {
        log.info("Fetching authentication token from payment gateway: {}", baseUrl + authEndpoint);
        Map<String, String> authRequest = Map.of("username", username, "password", password);

        try {
            Map<String, Object> response = webClient.post()
                    .uri(baseUrl + authEndpoint)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .bodyValue(authRequest)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .onErrorResume(e -> {
                        log.error("Authentication failed: {}", e.getMessage());
                        return Mono.error(new RuntimeException("Failed to authenticate with Payment Gateway", e));
                    })
                    .block();

            log.info("Authentication response: {}", response);

            if (response != null) {
                String token = findToken(response);
                if (token != null) {
                    return token;
                }

                if (response.get("data") instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> data = (Map<String, Object>) response.get("data");
                    String nestedToken = findToken(data);
                    if (nestedToken != null) {
                        return nestedToken;
                    }
                }
            }
            throw new RuntimeException(
                    "Empty or invalid response from authentication payment gateway. Response: " + response);
        } catch (Exception e) {
            log.error("Critical error during authentication: {}", e.getMessage());
            throw e;
        }
    }

    private String findToken(Map<String, Object> map) {
        return java.util.stream.Stream.of("bearerToken", "token", "accessToken", "access_token")
                .filter(map::containsKey)
                .map(key -> String.valueOf(map.get(key)))
                .findFirst()
                .orElse(null);
    }

    /**
     * Sends a batch of claims to the Payment Gateway for processing.
     *
     * @param request The disbursement request payload.
     * @return An acknowledgement/reference string if successful.
     */
    public String sendBatchPayment(DisbursementRequest request) {
        log.info("Sending batch payment request to {} for batch ID: {}", baseUrl + createEndpoint,
                request.getInvoice().getBatchId());

        String token = getToken();

        return webClient.post()
                .uri(baseUrl + createEndpoint)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .bodyValue(request)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError,
                        clientResponse -> clientResponse.bodyToMono(String.class).flatMap(errorBody -> {
                            log.error("Gateway 400 error: {}", errorBody);
                            return Mono.error(new PaymentGateway400Exception(errorBody));
                        }))
                .bodyToMono(String.class)
                .onErrorResume(e -> {
                    if (e instanceof PaymentGateway400Exception) {
                        return Mono.error(e);
                    }
                    log.error("Failed to call payment gateway: {}", e.getMessage());
                    return Mono.error(new RuntimeException("Payment Gateway call failed", e));
                })
                .block();
    }
}
