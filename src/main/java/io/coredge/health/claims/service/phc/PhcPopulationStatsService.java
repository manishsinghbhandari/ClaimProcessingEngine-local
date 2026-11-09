package io.coredge.health.claims.service.phc;


import io.coredge.health.claims.config.OAuth2ClientProperties;
import io.coredge.health.claims.config.SafaricomApiProperties;
import io.coredge.health.claims.models.phccalculation.CountyStatDto;
import io.coredge.health.claims.models.phccalculation.CountyStatsResponse;
import io.coredge.health.claims.service.oauth.OAuth2TokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PhcPopulationStatsService {

    private final OAuth2TokenService tokenService;
    private final WebClient.Builder webClientBuilder;
    private final SafaricomApiProperties safaricomApiProperties;

    private static final String EFFECTIVE_DATE = "date";
    private static final String BEARER = "Bearer ";



    public Mono<List<CountyStatDto>> getStats(String date) {
        OAuth2ClientProperties clientProps = new OAuth2ClientProperties();
        clientProps.setBaseUrl(safaricomApiProperties.getBaseUrl());
        clientProps.setAuthEndpoint(safaricomApiProperties.getAuthEndpoint());
        clientProps.setClientId(safaricomApiProperties.getClientId());
        clientProps.setClientSecret(safaricomApiProperties.getClientSecret());

        return tokenService.fetchAccessToken(clientProps)
                .flatMap(token -> callStatsApi(token, date))
                .map(CountyStatsResponse::data);
    }

    private Mono<CountyStatsResponse> callStatsApi(String accessToken, String date) {
        return webClientBuilder.baseUrl(safaricomApiProperties.getBaseUrl())
                .build()
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path(safaricomApiProperties.getStatsEndpoint())
                        .queryParam(EFFECTIVE_DATE, date)
                        .build())
                .header(HttpHeaders.AUTHORIZATION, BEARER + accessToken)
                .retrieve()
                .bodyToMono(CountyStatsResponse.class)
                .doOnSuccess(body -> log.info("PHC Stats API success"))
                .doOnError(error -> log.error("PHC Stats API error: {}", error.getMessage()));
    }
}