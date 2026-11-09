package io.coredge.health.claims.service.oauth;

import io.coredge.health.claims.config.OAuth2ClientProperties;
import io.coredge.health.claims.models.phccalculation.OAuth2TokenResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Base64;

@Service
@RequiredArgsConstructor
@Slf4j
public class OAuth2TokenService {

    private final WebClient.Builder webClientBuilder;

    private static final String GRANT_TYPE = "grant_type";
    private static final String CLIENT_CREDENTIALS = "client_credentials";
    private static final String BASIC = "Basic ";


    public Mono<String> fetchAccessToken(OAuth2ClientProperties properties) {
        String basicAuth = Base64.getEncoder()
                .encodeToString((properties.getClientId() + ":" + properties.getClientSecret()).getBytes());

        WebClient client = webClientBuilder.baseUrl(properties.getBaseUrl()).build();

        return client.get()
                .uri(uriBuilder -> uriBuilder
                        .path(properties.getAuthEndpoint())
                        .queryParam(GRANT_TYPE, CLIENT_CREDENTIALS)
                        .build())
                .header(HttpHeaders.AUTHORIZATION, BASIC + basicAuth)
                .retrieve()
                .bodyToMono(OAuth2TokenResponse.class)
                .map(OAuth2TokenResponse::accessToken)
                .doOnNext(token -> log.info("Access token fetched successfully"))
                .doOnError(error -> log.error("Failed to fetch access token: {}", error.getMessage()));
    }
}