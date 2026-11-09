package io.coredge.health.claims.client;

import io.coredge.health.claims.models.ClaimResponseWebhooks;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

import static io.coredge.health.claims.constants.Constants.Request.AUTHORIZATION;
import static io.coredge.health.claims.constants.Constants.Request.BASIC_AUTH;

@Slf4j
@Component
public class WebHookClient {

    public static final String SUCCESSFULLY_SENT_WEBHOOK = "Successfully sent webhook to url : {} , claimId : {} and claimResponseId : {} , response: {}";
    private static final String COLON = ":";

    @Value("${webhook.callback.timeout.seconds:30}")
    private int callbackTimeoutSeconds;

    private final WebClient webClient;

    public WebHookClient(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build(); // No base URL since webhook URLs vary
    }

    private static @NotNull HttpHeaders getHeadersWithBasicAuth(ClaimResponseWebhooks.Webhook webhook) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String auth = webhook.getApiKey() + COLON + webhook.getSecret();
        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
        headers.set(AUTHORIZATION, BASIC_AUTH + encodedAuth);
        return headers;
    }

    public Mono<Void> sendCallBack(ClaimResponseWebhooks.Webhook webhook, String claimResponseJson, String claimResponseId, String claimId) {
        log.info("Sending callback to [{}] SOURCE, ClaimID [{}], claimResponseJson [{}]", webhook.getConsumer().toUpperCase(), claimId, claimResponseJson);
        HttpHeaders headers = getHeadersWithBasicAuth(webhook);

        return webClient.post()
                .uri(webhook.getUrl())
                .headers(httpHeaders -> httpHeaders.addAll(headers))
                .bodyValue(claimResponseJson)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(callbackTimeoutSeconds))
                .doOnSuccess(response -> log.info(SUCCESSFULLY_SENT_WEBHOOK, webhook.getUrl(), claimId, claimResponseId, response))
                .doOnError(error -> log.warn("Failed to send to webhook for claimResponseId: {} and claimId: {} with url: {} - Error: {}", claimResponseId, claimId, webhook.getUrl(), error.getMessage()))
                .onErrorResume(error -> {
                    // Swallow the error or return a fallback
                    log.warn("Callback error handled in onErrorResume for claimId: {} - {}", claimId, error.getMessage());
                    return Mono.empty();
                })
                .then(); // returns Mono<Void> Convert Mono<String> to Mono<Void>
    }

}
