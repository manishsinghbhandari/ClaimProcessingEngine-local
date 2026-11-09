package io.coredge.health.claims.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import io.coredge.health.claims.config.properties.ApplicationProperties;
import io.coredge.health.claims.constants.Constants;
import io.coredge.health.claims.models.cpp.RequestCppProviders;
import io.coredge.health.claims.service.cpp.CppService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import io.coredge.health.claims.exception.base.ExternalServiceException;

import io.coredge.health.claims.models.eclaims.CppPaymentRequest;
import io.coredge.health.claims.models.eclaims.CppResponse;

import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Component
public class CppClient {

    private final WebClient webClient;
    private final ApplicationProperties.ExternalServices externalServicesProperties;
    private final ObjectMapper objectMapper;

    protected final String preAuthCreateUrl = "/api/v1/claims/handler/pre-auth";
    protected final String claimCreateUrl = "/api/v1/claims/handler/submission";

    protected final String preAuthUpdateUrl = "/api/v1/claims/handler/pre-auth-status-update";
    protected final String claimUpdateUrl = "/api/v1/claims/handler/submission-status-update";

    protected final String providersUrl = "/api/v1/providers/pushProviderData";
    private RequestCppProviders requestBody;

    protected final String  phcUrl= "/api/v1/claims/handler/push-phc";

    public CppClient(WebClient.Builder webClientBuilder, ApplicationProperties.ExternalServices externalServicesProperties, ObjectMapper objectMapper) {
        this.externalServicesProperties = externalServicesProperties;
        this.webClient = webClientBuilder.baseUrl(externalServicesProperties.getCppBaseUrl()).build();
        this.objectMapper = objectMapper;
    }

    public Mono<ResponseEntity<String>> postClaims(CppService.RequestCppObject requestBody) {
        log.info("Push claims[{}] to cpp, claimId:{}",
                !requestBody.isUpdate() ? "create" : "update", requestBody.claimId());

        String url = getUrl(requestBody.claimType(), requestBody.isUpdate());
        log.info("URL for submission: {}, Claim ID: {}", url, requestBody.claimId());
        log.info("Push to cpp for submission, RequestBody :{}", new Gson().toJson(requestBody));
        return webClient.post()
                .uri(url)
                .headers(httpHeaders -> httpHeaders.addAll(getHttpHeaders(externalServicesProperties.getCppApiKey())))
                .bodyValue(requestBody.request())
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, response -> response.bodyToMono(String.class)
                        .doOnNext(body -> log.warn("4xx Error while sending claim to CPP. ClaimId: {}, Status: {}, Body: {}",
                                requestBody.claimId(), response.statusCode(), body))
                        .then(Mono.error(new RuntimeException("Client error while sending claim to CPP"))))
                .toEntity(String.class)
                .doOnSuccess(response -> log.info("Claim successfully sent to CPP. ClaimID: {}, {}, Response:{}",
                        requestBody.claimId(), requestBody.isUpdate() ? "update" : "create", response.getBody()))
                .doOnError(error -> log.warn("Error while sending claim to CPP, ClaimId:{}, Error:{}",
                        requestBody.claimId(), error.getMessage()));
    }


    private String getUrl(String claimType, Boolean isUpdate) {
        String path;
        if (claimType.equalsIgnoreCase(Constants.CppClaimType.PRE_AUTH)) {
            path = isUpdate ? preAuthUpdateUrl : preAuthCreateUrl;
        } else {
            path = isUpdate ? claimUpdateUrl : claimCreateUrl;
        }
        return externalServicesProperties.getCppBaseUrl() + path;
    }


    public Mono<ResponseEntity<String>> postProvides(RequestCppProviders requestBody) {
        log.info("Push providers to cpp, providerId :{}", requestBody.getFrCode());
        log.debug("Push providers to cpp, RequestBody :{}", new Gson().toJson(requestBody));
        String url = externalServicesProperties.getCppBaseProviderUrl() + providersUrl;
        return webClient.post()
                .uri(url)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .bodyValue(requestBody)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, response -> response.bodyToMono(String.class)
                        .doOnNext(body -> log.warn("4xx Error while sending claim to CPP. provider FidCode: {}, Status: {}, Body: {}",
                                requestBody.getFrCode(), response.statusCode(), body))
                        .then(Mono.error(new RuntimeException("Client error while sending claim to CPP"))))
                .toEntity(String.class)
                .doOnSuccess(response -> log.info("Provides successfully sent to CPP. provider FidCode: {}, Response:{}",
                        requestBody.getFrCode(), response.getBody()))
                .doOnError(error -> log.warn("Error while sending claim to CPP, provider FidCode:{}, Error:{}",
                        requestBody.getFrCode(), error.getMessage()));
    }

    public Mono<CppResponse> paymentProcessByCpp(CppPaymentRequest claimRequest) {
        String url = externalServicesProperties.getCppBaseUrl() + claimUpdateUrl;
        return sendToCpp(url, claimRequest, "NORMAL");
    }

    public Mono<CppResponse> processPhcClaim(CppPaymentRequest phcRequest) {
        String url = externalServicesProperties.getCppBaseUrl() + phcUrl;
        return sendToCpp(url, phcRequest, "PHC");
    }


    private Mono<CppResponse> sendToCpp(String url, CppPaymentRequest request, String label) {

        log.info("Push {} claim [{}] to CPP", label, request.getClaimId());
        log.info("Final request to CPP service for claim [{}], Request: {}", request.getClaimId(), new Gson().toJson(request));
        return webClient.post()
                .uri(url)
                .headers(headers -> headers.addAll(getHttpHeaders(externalServicesProperties.getCppApiKey())))
                .bodyValue(request)
                .retrieve()
                .bodyToMono(CppResponse.class)
                .doOnSuccess(resp ->
                        log.info("CPP Response for {} ClaimId {}: {}", label, request.getClaimId(), resp)
                )
                .onErrorMap(WebClientResponseException.class, ex -> {
                    String responseBody = ex.getResponseBodyAsString();
                    log.error("CPP {} returned error: Status={}, Body={}", label, ex.getRawStatusCode(), responseBody);
                    String cppErrorMessage = ex.getMessage();

                    try {
                        CppResponse cppResponse =
                                objectMapper.readValue(responseBody, CppResponse.class);

                        if (cppResponse.getErrors() != null && !cppResponse.getErrors().isEmpty()) {
                            cppErrorMessage = String.join(" | ", cppResponse.getErrors());
                        }
                    } catch (Exception parseEx) {
                        log.warn("Unable to parse CPP error response", parseEx);
                    }
                    return new ExternalServiceException(
                            "CPP " + label + " error: " + cppErrorMessage,
                            ex
                    );
                })
                .onErrorMap(Exception.class, ex -> {
                    log.error("Unexpected error calling CPP {}: {}", label, ex.getMessage());
                    return new ExternalServiceException(
                            "Unexpected CPP " + label + " error: " + ex.getMessage(),
                            ex
                    );
                });
    }

    private HttpHeaders getHttpHeaders(String apiKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-API-Key", apiKey);
        return headers;
    }
}
