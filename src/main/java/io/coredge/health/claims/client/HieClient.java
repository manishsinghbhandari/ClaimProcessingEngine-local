package io.coredge.health.claims.client;

import io.coredge.health.claims.config.properties.ApplicationProperties;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Slf4j
@Component
public class HieClient {

    private final WebClient webClient;
    private final ApplicationProperties.Hie hieProperties;

    public HieClient(WebClient.Builder webClientBuilder, ApplicationProperties.Hie hieProperties) {
        this.webClient = webClientBuilder.build();
        this.hieProperties = hieProperties;
    }

    public String fetchDetails(UriComponentsBuilder uriBuilder) {
        String uri = uriBuilder.toUriString();
        String authToken = generateJwtToken();

        HttpHeaders headers = getHttpHeaders(authToken);

        return webClient.get()
                .uri(uri)
                .headers(httpHeaders -> httpHeaders.addAll(headers))
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    private @NotNull HttpHeaders getHttpHeaders(String authToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", authToken);
        headers.set("Referer", hieProperties.getRefer());
        headers.set("x-source-msisdn", hieProperties.getKey());
        return headers;
    }

    public String generateJwtToken() {
        String auth = hieProperties.getUsername() + ":" + hieProperties.getPassword();
        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
        String authHeader = "Basic " + encodedAuth;

        return webClient.get()
                .uri(hieProperties.getAuthUri() + "?key=" + hieProperties.getKey())
                .headers(headers -> headers.addAll(getHttpHeaders(authHeader)))
                .retrieve()
                .bodyToMono(String.class)
                .doOnSuccess(response -> log.info("JWT token received successfully"))
                .doOnError(error -> log.warn("Error in JWT token retrieval: {}", error.getMessage()))
                .onErrorReturn("")
                .block(); // Blocking call for synchronous execution
    }
}
