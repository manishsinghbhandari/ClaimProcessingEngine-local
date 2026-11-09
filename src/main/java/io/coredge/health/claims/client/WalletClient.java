package io.coredge.health.claims.client;

import io.coredge.health.claims.models.wallet.WalletCreateRequest;
import io.coredge.health.claims.models.wallet.WalletCreateResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class WalletClient {

    private final WebClient webClient;

    @Value("${walletBaseUrl}")
    private String walletBaseUrl;

    @Value("${walletCreateEndpoint}")
    private String walletCreateEndpoint;

    public WalletClient(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    public Mono<WalletCreateResponse> createWallet(String userId) {
        WalletCreateRequest request = new WalletCreateRequest();
        request.setUserId(userId);

        String url = walletBaseUrl + walletCreateEndpoint;
        return webClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .onStatus(
                        status -> !(status == HttpStatus.OK || status == HttpStatus.CREATED),
                        clientResponse -> clientResponse
                                .bodyToMono(String.class)
                                .flatMap(errorBody -> {
                                    log.error("Wallet creation failed for userId: {}, status: {}, errorBody: {}",
                                            userId, clientResponse.statusCode(), errorBody);
                                    return Mono.error(new RuntimeException("Wallet creation failed with status: "
                                            + clientResponse.statusCode()));
                                })
                )
                .bodyToMono(WalletCreateResponse.class)
                .doOnSuccess(response ->
                        log.info("Wallet created successfully for userId: {}", userId))
                .doOnError(error ->
                        log.error("Error creating wallet for userId: {}, error: {}", userId, error.getMessage()));

    }
}