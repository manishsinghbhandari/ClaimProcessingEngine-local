package io.coredge.health.claims.service.provider;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import com.google.common.collect.Lists;
import io.coredge.health.claims.db.repositories.InsuranceClaimsRepository;
import io.coredge.health.claims.mapper.ClaimCallbackResponse;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.instance.model.api.IAnyResource;
import org.hl7.fhir.r4.model.*;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;


@Service
@Slf4j
public class ProviderBulkFhirService {

    private final InsuranceClaimsRepository insuranceClaimsRepository;
    private final IGenericClient fhirClient;

    private static final int  BATCH_SIZE = 20;

    public ProviderBulkFhirService(InsuranceClaimsRepository insuranceClaimsRepository, IGenericClient fhirClient) {
        this.insuranceClaimsRepository = insuranceClaimsRepository;
        this.fhirClient = fhirClient;
    }

    public CompletableFuture<List<ClaimCallbackResponse>> sendBulkClaimsToProvider(List<UUID> claimsIds) {
        Instant startTime = Instant.now();

        // Step 1: Fetch valid claim-response mappings from DB
        List<InsuranceClaimProjection> insuranceClaimProjectionList = insuranceClaimsRepository.findResponseIdsByClaimIds(claimsIds);


        // Map<UUID, String> — safely convert claim ID to UUID for comparison
        Map<UUID, String> claimToResponseMap = insuranceClaimProjectionList.stream()
                .filter(p -> p.getClaimId() != null && p.getResponseId() != null)
                .collect(Collectors.toMap(
                        p -> UUID.fromString(p.getClaimId()),
                        InsuranceClaimProjection::getResponseId
                ));
        List<String> responseIds = new ArrayList<>(claimToResponseMap.values());

        // Step 2: Batch response IDs for FHIR calls
        List<List<String>> fhirBatches = Lists.partition(responseIds, BATCH_SIZE);

        List<CompletableFuture<List<ClaimCallbackResponse>>> futures = fhirBatches.stream()
                .map(batch -> CompletableFuture.supplyAsync(() -> getFhirBundleByClaimIds(batch))
                        .thenApply(this::mapFhirToResponse))
                .toList();

        // Step 3: Compose final response
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> futures.stream()
                        .flatMap(f -> f.join().stream())
                        .toList())
                .thenApply(fhirResponses -> {
                    List<ClaimCallbackResponse> finalResponses = new ArrayList<>(fhirResponses);

                    // Step 4: Add message for missing claims
                    Set<UUID> foundClaimIds = claimToResponseMap.keySet();
                    claimsIds.stream()
                            .filter(claimId -> !foundClaimIds.contains(claimId))
                            .map(cr -> ClaimCallbackResponse.builder()
                                    .claimID(cr.toString())
                                    .message("This claim ID does not exist in the system.")
                                    .build())
                            .forEach(finalResponses::add);

                    return finalResponses;
                })
                .whenComplete((response, ex) -> {
                    long duration = java.time.Duration.between(startTime, Instant.now()).toMillis();
                    if (ex != null) {
                        log.error("Error processing claims batch:", ex);
                    } else {
                        log.info("Total time taken for {} claims in {} ms (~{} sec)", claimsIds.size(), duration, duration / 1000.0);
                    }
                });
    }


    private List<ClaimCallbackResponse> mapFhirToResponse(List<ClaimResponse> claimResponses) {
        return claimResponses.stream()
                .map(cr -> ClaimCallbackResponse.builder()
                        .claimID(extractClaimId(cr))
                        .status(extractStatus(cr))
                        .transitionDate(extractTransitionDate(cr))
                        .notes(extractNotes(cr))
                        .build())
                .toList();
    }

    private String extractClaimId(ClaimResponse claimResponse) {
        return Optional.ofNullable(claimResponse.getRequest())
                .map(Reference::getReference)
                .map(ref -> ref.replaceAll(".*/", ""))
                .orElse("");

    }

    private String extractStatus(ClaimResponse claimResponse) {
        return claimResponse.getExtension().stream()
                .filter(ext -> ext.getUrl() != null && ext.getUrl().contains("claim-state-extension"))
                .findFirst()
                .flatMap(ext -> ext.getValue() instanceof CodeableConcept cc
                        ? cc.getCoding().stream().findFirst().map(Coding::getCode)
                        : Optional.empty())
                .orElse("Queued");
    }

    private String extractTransitionDate(ClaimResponse claimResponse) {
        return Optional.ofNullable(claimResponse.getMeta())
                .map(Meta::getLastUpdatedElement)
                .map(InstantType::getValueAsString)
                .orElse("");
    }

    private List<ClaimCallbackResponse.Note> extractNotes(ClaimResponse claimResponse) {
        return claimResponse.getProcessNote().stream()
                .map(ClaimResponse.NoteComponent::getText)
                .filter(Objects::nonNull)
                .map(ClaimCallbackResponse.Note::new)
                .toList();
    }

    public List<ClaimResponse> getFhirBundleByClaimIds(List<String> responseIds) {
        if (responseIds == null || responseIds.isEmpty()) {
            return Collections.emptyList();
        }
        Bundle bundle = fhirClient.search()
                .forResource(ClaimResponse.class)
                .where(IAnyResource.RES_ID.exactly().codes(responseIds)) // Fetch by response ID
                .returnBundle(Bundle.class)
                .execute();

        return bundle.hasEntry()
                ? bundle.getEntry().stream()
                .map(Bundle.BundleEntryComponent::getResource)
                .filter(ClaimResponse.class::isInstance)
                .map(ClaimResponse.class::cast)
                .toList()
                : Collections.emptyList();
    }

}
