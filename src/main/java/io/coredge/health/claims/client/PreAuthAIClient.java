package io.coredge.health.claims.client;

import io.coredge.health.claims.config.properties.ApplicationProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PreAuthAIClient {

    private final ApplicationProperties.Ai aiProperties;
    private final RestTemplate restTemplate = new RestTemplate();

    public ResponseEntity<String> postPreAuthBundle(String bundleJson, UUID claimId) {
        String url = resolveUrl();

        log.info("Sending PreAuth Bundle for ClaimId: {} to AI URL: {}", claimId, url);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + aiProperties.getAuthToken());
        headers.set("X-Encryption-Key", aiProperties.getEncryptionKey());

        HttpEntity<String> request = new HttpEntity<>(bundleJson, headers);

        try {
            return restTemplate.postForEntity(url, request, String.class);
        } catch (Exception e) {
            log.error("Error sending PreAuth to AI for ClaimId: {}: {}", claimId, e.getMessage());
            throw e;
        }
    }

    private String resolveUrl() {
        String baseUrl = aiProperties.getPreAuthValidateUri();

        baseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
        String preAuthBundleUri = aiProperties.getPreAuthBundleUri();
        preAuthBundleUri = preAuthBundleUri.startsWith("/") ? preAuthBundleUri.substring(1) : preAuthBundleUri;

        return baseUrl + preAuthBundleUri;
    }
}
