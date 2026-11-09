package io.coredge.health.claims.service;

import io.coredge.health.claims.dto.eligibility.*;
import io.coredge.health.claims.exception.app.BadRequestException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Service for integrating with Finsprint Eligibility API
 * Handles authentication and coverage validation
 */
@Slf4j
@Service
public class FinsprintEligibilityService {

    private final RestTemplate restTemplate;

    @Value("${finsprint.auth.url}")
    private String authUrl;

    @Value("${finsprint.eligibility.url}")
    private String eligibilityUrl;

    @Value("${finsprint.auth.email}")
    private String authEmail;

    @Value("${finsprint.auth.password}")
    private String authPassword;

    @Value("${finsprint.token.cache.minutes}")
    private int tokenCacheMinutes;

    // Token cache variables
    private String cachedAccessToken;
    private LocalDateTime tokenExpiryTime;

    public FinsprintEligibilityService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Get bearer token with caching to avoid frequent authentication calls
     */
    public String getBearerToken() {
        // Check if token is cached and still valid
        if (cachedAccessToken != null && tokenExpiryTime != null && LocalDateTime.now().isBefore(tokenExpiryTime)) {
            log.info("Using cached Finsprint bearer token. Expires at: {}", tokenExpiryTime);
            return cachedAccessToken;
        }

        // Authenticate and cache new token
        log.info("Fetching new bearer token from Finsprint authentication API");
        try {
            FinsprintAuthRequest authRequest = new FinsprintAuthRequest(authEmail, authPassword);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<FinsprintAuthRequest> entity = new HttpEntity<>(authRequest, headers);

            ResponseEntity<FinsprintAuthResponse> response = restTemplate.exchange(
                    authUrl,
                    HttpMethod.POST,
                    entity,
                    FinsprintAuthResponse.class
            );

            if (response.getBody() != null && response.getBody().getAccessToken() != null) {
                cachedAccessToken = response.getBody().getAccessToken();
                tokenExpiryTime = LocalDateTime.now().plusMinutes(tokenCacheMinutes);
                log.info("Successfully authenticated with Finsprint. Token cached until: {}", tokenExpiryTime);
                return cachedAccessToken;
            } else {
                throw new BadRequestException("BMS authentication response is empty or missing access token");
            }

        } catch (BadRequestException | HttpClientErrorException ex) {
            throw ex;
        } catch (Exception e) {
            log.error("Unexpected error during Finsprint authentication", e);
            throw new BadRequestException("Unexpected error authenticating with Finsprint: " + e.getMessage());
        }
    }

    /**
     * Validate member eligibility using Finsprint v5 API
     * 
     * @param idType Coverage ID type (1-11, see FinsprintEligibilityRequest for mapping)
     * @param idNumber The identification number to validate
     * @return FinsprintEligibilityResponse containing coverage details for multiple schemes
     * @throws BadRequestException if member is not found or has no schemes
     */

    public FinsprintEligibilityResponse checkEligibility(String idType, String idNumber) {

        String bearerToken = getBearerToken();

        FinsprintEligibilityRequest request = FinsprintEligibilityRequest.builder()
                .requestIdType(idType)
                .requestIdNumber(idNumber)
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(bearerToken);

        HttpEntity<FinsprintEligibilityRequest> entity = new HttpEntity<>(request, headers);

        ResponseEntity<FinsprintEligibilityResponse> response = restTemplate.exchange(
                    eligibilityUrl,
                    HttpMethod.POST,
                    entity,
                    FinsprintEligibilityResponse.class
        );

        if (response.getBody() != null) {
            FinsprintEligibilityResponse eligibilityResponse = response.getBody();

            // Validate that member was found and has schemes
            if (eligibilityResponse.getSchemes() == null || eligibilityResponse.getSchemes().isEmpty()) {
                String errorMessage = String.format(
                        "Member with ID %s not found or has no active schemes. Status: %s - %s",
                        idNumber,
                        eligibilityResponse.getStatusCode() != null ? eligibilityResponse.getStatusCode() : "Unknown",
                        eligibilityResponse.getStatusDesc() != null ? eligibilityResponse.getStatusDesc() : "N/A"
                );
                log.warn(errorMessage);
                throw new IllegalArgumentException(errorMessage);
            }

            log.info("Finsprint eligibility check successful for ID: {}. Found {} schemes.",
                    idNumber, eligibilityResponse.getSchemes().size());
            return eligibilityResponse;
        } else {
            throw new IllegalArgumentException("BMS eligibility response is empty for "+idType+" : "+idNumber);
        }
    }
}
