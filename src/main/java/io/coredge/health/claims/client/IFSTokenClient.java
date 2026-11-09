package io.coredge.health.claims.client;

import io.coredge.health.claims.config.properties.ApplicationProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import static io.coredge.health.claims.constants.Constants.*;

@Component
@Slf4j
public class IFSTokenClient {

    private final WebClient webClient;
    private final ApplicationProperties.ExternalServices externalServicesProperties;

    public IFSTokenClient(WebClient webClient, ApplicationProperties.ExternalServices externalServicesProperties) {
        this.webClient = webClient;
        this.externalServicesProperties = externalServicesProperties;
    }


    public Mono<String> getAccessToken() {
        return webClient.post()
                .uri(externalServicesProperties.getIfsTokenUrl())
                .header(HEADER_CONTENT_TYPE, CONTENT_TYPE_FORM)
                .body(BodyInserters
                        .fromFormData(PARAM_GRANT_TYPE, externalServicesProperties.getIfsGrantType())
                        .with(PARAM_SCOPE, externalServicesProperties.getIfsScope())
                        .with(PARAM_CLIENT_ID, externalServicesProperties.getIfsClientId())
                        .with(PARAM_CLIENT_SECRET, externalServicesProperties.getIfsClientSecret()))
                .retrieve()
                // Throw WebClientResponseException automatically for non-2xx responses
                .onStatus(status -> !status.is2xxSuccessful(),
                        clientResponse -> clientResponse.createException())
                .bodyToMono(TokenResponse.class)
                .doOnSubscribe(sub -> log.info("Requesting IFS token from URL: {}", externalServicesProperties.getIfsTokenUrl()))
                .doOnNext(tokenResponse -> log.info("IFS token received successfully"))
                .doOnError(ex -> log.error("Error while fetching IFS token", ex))
                .map(TokenResponse::getAccessToken);
    }

        public record TokenResponse(String access_token, String token_type, long expires_in, String scope) {
        public String getAccessToken() {
            return access_token;
        }
    }
}
