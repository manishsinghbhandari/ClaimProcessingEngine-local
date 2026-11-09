package io.coredge.health.claims.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.coredge.health.claims.config.properties.ApplicationProperties;
import io.coredge.health.claims.livia.LiviaClaimRequest;
import io.coredge.health.claims.livia.LiviaPreAuthCancelRequest;
import io.coredge.health.claims.livia.LiviaPreAuthRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

@Slf4j
@Service
public class LiviaClient {

    private final WebClient webClient;
    private final ApplicationProperties.Livia liviaProperties;

    private static final String ALGORITHM = "AES/CBC/PKCS5Padding";
    private static final String SUBMIT_CLAIM_URL = "/membersearch/providers/claim-submission";
    private static final String VERIFY_CLAIM_URL = "/membersearch/providers/VerifyClaim";
    private static final String DECLINE_PREAUTH_URL = "/membersearch/providers/v1/decline-preauth";

    public LiviaClient(WebClient.Builder webClientBuilder, ApplicationProperties.Livia liviaProperties) {
        this.liviaProperties = liviaProperties;
        this.webClient = webClientBuilder.baseUrl(liviaProperties.getBaseUrl()).build();
    }

    public ResponseEntity<String> submitClaim(LiviaClaimRequest request, String claimId) {
        String url = liviaProperties.getBaseUrl() + SUBMIT_CLAIM_URL;

        return webClient.post()
                .uri(url)
                .headers(headers -> {
                    headers.set("x-forwarded-for", liviaProperties.getForwardedHeader());
                    headers.setContentType(MediaType.APPLICATION_JSON);
                    headers.setBearerAuth(liviaProperties.getAuthToken());
                })
                .bodyValue(request)
                .exchangeToMono(response -> response.bodyToMono(String.class)
                        .flatMap(body -> {
                            if (response.statusCode() == HttpStatus.FORBIDDEN) {
                                log.warn("HTTP 403 error occurred while submitting claim. Duplicate Invoice Found. Processing response: {} claimId: {} ", body, claimId);
                                try {
                                    String decryptedData = decryptSafely(body);
                                    log.info("Decrypted data from 403 response: {}", decryptedData);
                                    return Mono.just(ResponseEntity.ok(decryptedData));
                                } catch (Exception ex) {
                                    log.error("Error while decrypting data from 403 response: {}", ex.getMessage());
                                    throw new RuntimeException("Error occurred while decrypting 403 response: " + ex.getMessage());
                                }
                            } else {
                                String decryptedBody = decryptSafely(body);
                                log.info("Livia response : {} ClaimId: {}", decryptedBody, claimId);

                                if (!response.statusCode().is2xxSuccessful()) {
                                    log.warn("Error while submitting claim: HTTP {} - {}", response.statusCode(), decryptedBody);
                                }

                                return Mono.just(ResponseEntity.status(response.statusCode()).body(decryptedBody));
                            }
                        }))
                .block();
    }

    public ResponseEntity<String> verifyClaim(LiviaPreAuthRequest requestBody, String claimId) {
        String url = liviaProperties.getBaseUrl() + VERIFY_CLAIM_URL;

        return webClient.post()
                .uri(url)
                .headers(headers -> {
                    headers.set("x-forwarded-for", liviaProperties.getForwardedHeader());
                    headers.setContentType(MediaType.APPLICATION_JSON);
                    headers.setBearerAuth(liviaProperties.getAuthToken());
                })
                .bodyValue(requestBody)
                .exchangeToMono(response -> response.bodyToMono(String.class)
                        .doOnNext(body -> {
                            log.info("Livia response : {} Pre Auth: {} ", body, claimId);
                            if (!response.statusCode().is2xxSuccessful()) {
                                log.warn("Error while verifying claim: HTTP {} - {}", response.statusCode(), body);
                            }
                        })
                        .map(body -> ResponseEntity.status(response.statusCode()).body(body)))
                .block();
    }

    /**
     * Cancels a pre-authorization in Livia system.
     */
    public ResponseEntity<String> cancelPreAuth(LiviaPreAuthCancelRequest request, String claimId) {

        String url = liviaProperties.getBaseUrl() + DECLINE_PREAUTH_URL;

        log.info("Sending preauth cancellation to Livia for claimId: {}, preAuthReferenceNo: {}",
                claimId, request.getPreAuthReferenceNo());

        return webClient.post()
                .uri(url)
                .headers(headers -> {
                    headers.set("secret-key", liviaProperties.getIv());
                    headers.set("x-forwarded-for", liviaProperties.getForwardedHeader());
                    headers.setContentType(MediaType.APPLICATION_JSON);
                    headers.setBearerAuth(liviaProperties.getAuthToken());
                })
                .bodyValue(request)
                .exchangeToMono(response ->
                        response.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .flatMap(body -> handleLiviaResponse(response, body, claimId))
                )
                .retryWhen(Retry.backoff(
                                liviaProperties.getMaxRetries(),
                                Duration.ofMillis(liviaProperties.getRetryDelayMs()))
                        .maxBackoff(Duration.ofMillis(liviaProperties.getRetryDelayMs() * 5))
                        .filter(ex -> ex instanceof LiviaRetryableException)
                        .doBeforeRetry(retry ->
                                log.warn("Retrying Livia preauth cancellation for claimId: {}, attempt: {}, error: {}", claimId, retry.totalRetries() + 1, retry.failure().getMessage()
                                )))
                .block();
    }

    private Mono<ResponseEntity<String>> handleLiviaResponse(ClientResponse response, String body, String claimId) {

        log.info("Livia preauth cancellation response for claimId: {}, status: {}, body: {}",
                claimId, response.statusCode(), body);

        // Business-level error encoded in response body (Livia-specific behaviour)
        if (isLiviaErrorResponse(body)) {
            log.warn("Livia returned business error in response body for claimId: {}", claimId);
            return Mono.error(new LiviaRetryableException(body));
        }

        // Transport-level HTTP error
        if (response.statusCode().isError()) {
            log.warn("HTTP error from Livia for claimId: {}, status: {}", claimId, response.statusCode());
            return Mono.error(new LiviaRetryableException("HTTP " + response.statusCode() + " from Livia"));
        }

        return Mono.just(ResponseEntity.status(response.statusCode()).body(body));
    }

    private boolean isLiviaErrorResponse(String body) {
        if (body == null || body.isBlank()) {
            return false;
        }
        try {
            JsonObject jsonObject = JsonParser.parseString(body).getAsJsonObject();
            return jsonObject.has("statusCode") && jsonObject.get("statusCode").getAsInt() >= 400;
        } catch (Exception e) {
            log.warn("Failed to parse Livia response body: {}", body);
            return false;
        }
    }

    private static class LiviaRetryableException extends RuntimeException {
        public LiviaRetryableException(String message) { super(message);
        }
    }


    private String decryptSafely(String encryptedData) {
        try {
            return decrypt(encryptedData);
        } catch (Exception e) {
            log.warn("Error decrypting data: {}", e.getMessage());
            return encryptedData;
        }
    }

    public String decrypt(String responseData) throws Exception {
        try {
            JsonObject responseBody = JsonParser.parseString(responseData).getAsJsonObject();
            String data = responseBody.get("data").getAsString();

            IvParameterSpec ivParameterSpec = new IvParameterSpec(liviaProperties.getIv().getBytes(StandardCharsets.UTF_8));
            SecretKeySpec secretKeySpec = new SecretKeySpec(liviaProperties.getEncryptionKey().getBytes(StandardCharsets.UTF_8), "AES");

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, ivParameterSpec);

            byte[] decodedEncryptedData = Base64.getDecoder().decode(data);
            byte[] original = cipher.doFinal(decodedEncryptedData);

            return new String(original, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("Error decrypting data: {}", e.getMessage());
            throw new RuntimeException("Decryption failed: " + e.getMessage());
        }
    }
}
