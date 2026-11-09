package io.coredge.health.claims.service;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import io.coredge.health.claims.context.HealthIdContextHolder;
import io.coredge.health.claims.db.entity.InsuranceClaim;
import io.coredge.health.claims.db.entity.ProcessNotes;
import io.coredge.health.claims.db.repositories.AiNotes.ClaimAiNotesRepository;
import io.coredge.health.claims.db.repositories.InsuranceClaimsRepository;
import io.coredge.health.claims.db.repositories.LineItemRepository;
import io.coredge.health.claims.db.repositories.ProcessNotesRepository;
import io.coredge.health.claims.enums.ExtensionStateCodeEnum;
import io.coredge.health.claims.exception.app.DataNotFoundException;
import io.coredge.health.claims.helpers.Helper;
import io.coredge.health.claims.models.ProcessNotesDTO;
import io.coredge.health.claims.service.AI.AIUserService;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.ClaimResponse;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ProcessNotesService {

    @Autowired
    private ProcessNotesRepository repository;

    @Autowired
    private LineItemRepository lineItemRepository;

    @Autowired
    private InsuranceClaimsRepository insuranceClaimsRepository;

    @Autowired
    private PostOfficeService postOfficeService;

    @Autowired
    private IGenericClient fhirClient;

    @Autowired
    private ClaimAiNotesRepository claimAiNotesRepository;

    private static ProcessNotesDTO getResponse(ProcessNotes processNotes) {
        ProcessNotesDTO response = ProcessNotesDTO.builder().build();
        BeanUtils.copyProperties(processNotes, response); // Copy properties
        return response;
    }

    public void saveProcessNotes(String claimId, ExtensionStateCodeEnum status, String notes) {
        ProcessNotes processNotes = ProcessNotes.builder().build();
        processNotes.setClaimId(claimId);
        processNotes.setNotes(notes);
        processNotes.setStatus(status);
        processNotes.setCreatedBy(HealthIdContextHolder.userId());
        Integer maxSequenceNo = repository.findMaxSequenceNoByClaimId(processNotes.getClaimId());
        processNotes.setSequenceNo((maxSequenceNo != null ? maxSequenceNo : 0) + 1);
        repository.save(processNotes);
    }

    //EPORTAL-1421
    public String saveProcessNotesAsCodeAndDesc(String claimId, ExtensionStateCodeEnum status, String notes) {
        ProcessNotes processNotes = ProcessNotes.builder().build();
        processNotes.setClaimId(claimId);
        processNotes.setNotes(notes);
        processNotes.setStatus(status);
        processNotes.setCreatedBy(HealthIdContextHolder.userId());
        Integer maxSequenceNo = repository.findMaxSequenceNoByClaimId(processNotes.getClaimId());
        processNotes.setSequenceNo((maxSequenceNo != null ? maxSequenceNo : 0) + 1);
        repository.save(processNotes);
        return notes;
    }

    public List<ProcessNotesDTO> getProcessNotesByClaimId(String claimId) {
        Optional<List<ProcessNotes>> processNotes = repository.findByClaimId(claimId);
        return processNotes.filter(list -> !list.isEmpty())
                .map(list -> list.stream().map(ProcessNotesService::getResponse)
                        .collect(Collectors.toList()))
                .orElseThrow(DataNotFoundException::new);
    }


    public String addHistoryData(ExtensionStateCodeEnum status, String use, int count) {
        syncProcessNotes(status, use, count);
        return "Stared Syncing of ProcessNotes";
    }

    @Async("virtualThreadExecutor")
    private void syncProcessNotes(ExtensionStateCodeEnum status, String use, int count) {
        Optional<Page<InsuranceClaim>> response = insuranceClaimsRepository.findByUseAndStatusAndClaimResponseIdIsNull(use, status, PageRequest.of(0, count, Sort.by(Helper.getSortDirection("DESC"), "creationDate")));
        List<String> claimIds = response.filter(Page::hasContent).map(page -> page.getContent().stream().map(InsuranceClaim::getExternalId).toList()).orElse(Collections.emptyList());
        if(!claimIds.isEmpty()){
            fetchClaimResponsesWithBatchRequest(claimIds);
            log.info("Started ProcessNotes Syncing for found elements : {}", response.get().getTotalElements());
            response.filter(Page::hasContent).ifPresent(page -> page.getContent().stream().filter(claims -> claims.getClaimResponseId() == null)
                    .forEach(claims -> {
                        ClaimResponse claimResponse = postOfficeService.getClaimResponseByClaimId(claims.getExternalId(), claims.getClaimResponseId());
                        claims.setClaimResponseId(claimResponse.getIdPart());
                        insuranceClaimsRepository.save(claims);
                        List<ClaimResponse.NoteComponent> processNotes = claimResponse.getProcessNote();
                        processNotes.forEach(note -> saveProcessNotes(claims.getExternalId(), status, note.getText()));
                    }));
            log.info("Completed ProcessNotes Syncing Successfully");
        }
    }

    /**
     * Fetch ClaimResponse resources for multiple claim IDs using a batch request.
     *
     * @param claimIds List of claim IDs to fetch ClaimResponse resources for.
     * @return A Bundle containing the results of all ClaimResponse resources.
     */
    public Bundle fetchClaimResponsesWithBatchRequest(List<String> claimIds) {
        try {
            // Create a Batch Bundle
            Bundle batchBundle = new Bundle();
            batchBundle.setType(Bundle.BundleType.BATCH);

            // Add GET requests for each claim reference
            claimIds.forEach(claimId -> {
                Bundle.BundleEntryComponent entry = new Bundle.BundleEntryComponent();
                entry.setRequest(new Bundle.BundleEntryRequestComponent()
                        .setMethod(Bundle.HTTPVerb.GET)
                        .setUrl("ClaimResponse?request=" + claimId)); // Define the query for each claim
                batchBundle.addEntry(entry);
            });

            // Execute the batch transaction
            Bundle responseBundle = fhirClient.transaction()
                    .withBundle(batchBundle)
                    .execute();

            return responseBundle;

        } catch (Exception e) {
            log.warn("Error fetching ClaimResponses using batch request for claim IDs : {}", claimIds, e.getMessage());
            throw new RuntimeException("Failed to execute batch request for ClaimResponses : "+ e.getMessage());
        }

    }

    public String formatNotes(String code, String description, String notes) {
        String base = "%s:%s".formatted(code, description);
        return (notes == null || notes.isBlank()) ? base : "%s - %s".formatted(base, notes);
    }

}
