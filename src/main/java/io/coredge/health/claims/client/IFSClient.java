package io.coredge.health.claims.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.coredge.health.claims.config.properties.ApplicationProperties;
import io.coredge.health.claims.exception.app.DataValidationException;
import io.coredge.health.claims.exception.base.ExternalServiceException;
import io.coredge.health.claims.models.eclaims.ERPResponseModel;
import io.coredge.health.claims.payments.dto.IfsBankPaymentUpdateRequest;
import io.coredge.health.claims.models.eclaims.IfsRequest;
import io.coredge.health.claims.models.eclaims.IfsResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Set;

import static io.coredge.health.claims.constants.Constants.BEARER_PREFIX;
import static io.coredge.health.claims.constants.Constants.HEADER_AUTHORIZATION;
import static io.coredge.health.claims.exception.models.ErrorCode.THIRD_PARTY_API_ERROR;

@Slf4j
@Component
public class IFSClient {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final ApplicationProperties.ExternalServices externalServicesProperties;
    private static final Set<String> IFS_SUCCESS_CODES = Set.of("SUCCESS", "SUCESS");
    @Autowired
    private IFSTokenClient tokenClient;

    public IFSClient(WebClient.Builder webClientBuilder, ObjectMapper objectMapper,
                     ApplicationProperties.ExternalServices externalServicesProperties) {
        this.webClient = webClientBuilder.build();
        this.objectMapper = objectMapper;
        this.externalServicesProperties = externalServicesProperties;
    }

    public String processIFSClaim(IfsRequest ifsRequest) {

        try {
            String requestJson = objectMapper.writeValueAsString(ifsRequest);
            log.info("Request Body IFS : {}", requestJson);

            String ifsRawResponse = callIFSClient(requestJson);
            log.info("IFS API Response: {}", ifsRawResponse);

            // Bind response
            IfsResponse ifsResponse = objectMapper.readValue(ifsRawResponse, IfsResponse.class);
            log.info("Mapped IFS response: {}", objectMapper.writeValueAsString(ifsResponse));

            String ifsStatusCode = ifsResponse.getStatusCode();
            // Allow  only SUCCESS or SUCESS . Note: In Negative case IFS is giving the 200 http code.
            if (isIfsSuccess(ifsResponse.getStatusCode())) {
                // SUCCESS ONLY
                String statusCode = "201";
                ERPResponseModel erpResponse = ERPResponseModel.builder()
                        .statusCode(statusCode)
                        .responseStatus(ifsStatusCode)
                        .responseBody(ERPResponseModel.ResponseBody.builder()
                                .result(ERPResponseModel.ResponseBody.Result.builder()
                                        .statusCode(statusCode).status(ifsStatusCode)
                                        .message(ifsResponse.getStatusMessage())
                                        .claimId(ifsRequest.getInvoices().getClaimId())
                                        .id(ifsRequest.getInvoices().getClaimId())
                                        .build())
                                .build())
                        .build();

                return objectMapper.writeValueAsString(erpResponse);
            } else {
                log.error("IFS returned error: {}", ifsResponse.getStatusMessage());
                log.error("IFS returned response error: {}", ifsResponse);
                throw new ExternalServiceException(THIRD_PARTY_API_ERROR, ifsResponse.getStatusMessage());
            }

        } catch (ExternalServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("IFS processing failed", e);
            throw new ExternalServiceException(THIRD_PARTY_API_ERROR, "IFS processing [THIRD_PARTY_API_ERROR] failed: " + e.getMessage());
        }
    }

    private boolean isIfsSuccess(String status) {
        return status != null && IFS_SUCCESS_CODES.contains(status.toUpperCase());
    }

    /**
     * Updates bank payment information in IFS system.
     * 
     * @param request The bank payment update request containing claim details and payment info
     * @return String response from IFS API
     */
    public String updateBankPaymentInfo(IfsBankPaymentUpdateRequest request) {
        try {
            String requestJson = objectMapper.writeValueAsString(request);
            log.info("IFS Bank Payment Update Request: {}", requestJson);

            String ifsRawResponse = callIFSBankPaymentUpdateClient(requestJson);
            log.info("IFS Bank Payment Update Response: {}", ifsRawResponse);

            // Bind response
            IfsResponse ifsResponse = objectMapper.readValue(ifsRawResponse, IfsResponse.class);

            log.info("Mapped IFS Bank Payment Update response: {}", objectMapper.writeValueAsString(ifsResponse));

            String ifsStatusCode = ifsResponse.getStatusCode();

            // Block everything except SUCCESS
            if (!"SUCESS".equalsIgnoreCase(ifsStatusCode) && !"SUCCESS".equalsIgnoreCase(ifsStatusCode)) {
                log.error("IFS Bank Payment Update returned error: {}", ifsResponse.getStatusMessage());
                throw new ExternalServiceException(THIRD_PARTY_API_ERROR, ifsResponse.getStatusMessage());
            }

            // SUCCESS ONLY
            String statusCode = "201";
            ERPResponseModel erpResponse = ERPResponseModel.builder()
                    .statusCode(statusCode)
                    .responseStatus(ifsStatusCode)
                    .responseBody(ERPResponseModel.ResponseBody.builder()
                            .result(ERPResponseModel.ResponseBody.Result.builder()
                                    .statusCode(statusCode)
                                    .status(ifsStatusCode)
                                    .message(ifsResponse.getStatusMessage())
                                    .claimId(request.getClaimId())
                                    .id(request.getClaimId())
                                    .build())
                            .build())
                    .build();

            return objectMapper.writeValueAsString(erpResponse);

        } catch (ExternalServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("IFS Bank Payment Update failed", e);
            throw new ExternalServiceException(THIRD_PARTY_API_ERROR, "IFS Bank Payment Update failed: " + e.getMessage());
        }
    }

    private String callIFSClient(String requestJson) {
        WebClient.RequestHeadersSpec<?> request = webClient.post()
                .uri(externalServicesProperties.getIfsBaseUri())
                .bodyValue(requestJson);

        return request
                .headers(httpHeaders -> httpHeaders.addAll(getHttpHeaders()))
                .retrieve()
                .onStatus(
                        HttpStatusCode::is4xxClientError,
                        response -> {
                            if (response.statusCode().value() == 409) {
                                log.warn("IFS responded with 409 Conflict. Proceeding to read the response body.");
                                return Mono.empty(); // Ignore error, read body
                            }
                            return this.handle4xxErrors(response); // Handle other 4xx
                        }) // Handle all 4xx except 409
                .onStatus(HttpStatusCode::is5xxServerError, this::handle5xxErrors)
                .bodyToMono(String.class)
                .doOnSuccess(response -> log.info("IFS response received successfully"))
                .doOnError(error -> log.warn("Error in IFS call: {}", error.getMessage()))
                .block();
    }

    /**
     * Calls the IFS Bank Payment Update endpoint.
     * 
     * @param requestJson The JSON request body
     * @return String response from IFS API
     */
    private String callIFSBankPaymentUpdateClient(String requestJson) {
        WebClient.RequestHeadersSpec<?> request = webClient.post()
                .uri(externalServicesProperties.getIfsBankPaymentUpdateUri())
                .bodyValue(requestJson);

        return request
                .headers(httpHeaders -> httpHeaders.addAll(getHttpHeaders()))
                .retrieve()
                .onStatus(
                        HttpStatusCode::is4xxClientError,
                        response -> {
                            if (response.statusCode().value() == 409) {
                                log.warn("IFS Bank Payment Update responded with 409 Conflict. Proceeding to read the response body.");
                                return Mono.empty(); // Ignore error, read body
                            }
                            return this.handle4xxErrors(response); // Handle other 4xx
                        }
                )
                .onStatus(HttpStatusCode::is5xxServerError, this::handle5xxErrors)
                .bodyToMono(String.class)
                .doOnSuccess(response -> log.info("IFS Bank Payment Update response received successfully"))
                .doOnError(error -> log.warn("Error in IFS Bank Payment Update call: {}", error.getMessage()))
                .block();
    }

    private Mono<? extends Throwable> handle4xxErrors(org.springframework.web.reactive.function.client.ClientResponse response) {
        return response.bodyToMono(String.class)
                .flatMap(errorBody -> {
                    log.warn("Client Error: Status Code {}, Response Body: {}", response.statusCode(), errorBody);
                    if (response.statusCode().isSameCodeAs(org.springframework.http.HttpStatus.FORBIDDEN)) {
                        return Mono.error(new DataValidationException("$$Error: Access is forbidden."));
                    } else if (response.statusCode().isSameCodeAs(org.springframework.http.HttpStatus.UNAUTHORIZED)) {
                        return Mono.error(new DataValidationException("$$Error: Unauthorized access."));
                    }
                    return Mono.error(new RuntimeException(errorBody));
                });
    }

    private Mono<? extends Throwable> handle5xxErrors(ClientResponse response) {
        return response.bodyToMono(String.class)
                .flatMap(errorBody -> {
                    log.warn("Server Error: Status Code {}, Response Body: {}", response.statusCode(), errorBody);
                    return Mono.error(new RuntimeException("Error: Server encountered an issue. Please try again later."));
                });
    }

    private HttpHeaders getHttpHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String accessToken = tokenClient.getAccessToken().block();
        headers.set(HEADER_AUTHORIZATION, BEARER_PREFIX + accessToken);
        return headers;
    }

}
