package io.coredge.health.claims.client;

import io.coredge.health.claims.config.properties.ApplicationProperties;
import io.coredge.health.claims.models.AfyanguClaimsDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class AfyanguClient {

    private final WebClient webClient;
    private final ApplicationProperties.ExternalServices externalServicesProperties;

    public AfyanguClient(WebClient.Builder webClientBuilder, ApplicationProperties.ExternalServices externalServicesProperties) {
        this.externalServicesProperties = externalServicesProperties;
        this.webClient = webClientBuilder.baseUrl(externalServicesProperties.getAfyanguBaseUrl()).build();
    }

    public Mono<ResponseEntity<String>> postClaims(AfyanguClaimsDTO requestBody) {
        String url = externalServicesProperties.getAfyanguBaseUrl() + externalServicesProperties.getAfyanguPostClaimUrl();

        return webClient.post()
                .uri(url)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .bodyValue(requestBody)
                .retrieve()
                .toEntity(String.class)
                .doOnSuccess(response -> log.info("Claim successfully sent to Afyangu. Response: {}", response.getBody()))
                .doOnError(error -> log.warn("Error while sending claim to Afyangu: {}", error.getMessage()));
    }
}
