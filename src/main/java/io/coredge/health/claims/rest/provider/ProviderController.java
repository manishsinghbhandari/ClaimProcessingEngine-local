package io.coredge.health.claims.rest.provider;

import io.coredge.health.claims.constants.Constants;
import io.coredge.health.claims.exception.callback.MaxLimitExceededException;
import io.coredge.health.claims.mapper.ClaimCallbackResponse;
import io.coredge.health.claims.service.provider.ProviderBulkFhirService;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping(Constants.EndPoints.FHIR_PATH)
@Slf4j
public class ProviderController {

    private final ProviderBulkFhirService providerBulkFhirService;
    private static final int MAX_ALLOWED = 10000;

    public ProviderController(ProviderBulkFhirService providerBulkFhirService) {
        this.providerBulkFhirService = providerBulkFhirService;
    }

    @PostMapping(Constants.EndPoints.CLAIM_STATUS_BULK_FHIR_API)
    public CompletableFuture<ResponseEntity<List<ClaimCallbackResponse>>> bulkClaimsStatus(
            @NotNull @RequestBody List<@NotNull UUID> claimsIds) {

        if (claimsIds.size() > MAX_ALLOWED) {
            throw new MaxLimitExceededException("Request exceeds maximum allowed limit of " + MAX_ALLOWED);
        }

        return providerBulkFhirService.sendBulkClaimsToProvider(claimsIds)
                .thenApply(ResponseEntity::ok)
                .exceptionally(ex -> {
                    log.error("Error in method sendBulkClaimsToProvider: {}", ex.getMessage(), ex);
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(Collections.emptyList());
                });
    }
}
