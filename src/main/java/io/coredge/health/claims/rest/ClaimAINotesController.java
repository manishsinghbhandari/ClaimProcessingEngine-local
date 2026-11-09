package io.coredge.health.claims.rest;

import io.coredge.health.claims.constants.Constants;
import io.coredge.health.claims.models.claims.*;
import io.coredge.health.claims.service.AI.ClaimAINotesService;
import io.coredge.health.claims.service.AI.AutoClaimAiAnalysisService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

import static io.coredge.health.claims.constants.Constants.EndPoints.CLAIM_AI_NOTES;

@RestController
@RequestMapping(CLAIM_AI_NOTES)
@Slf4j
public class ClaimAINotesController {

    private static final String CLAIM_SUBMITTED_SUCCESS_MESSAGE = "Claim submitted to AI engine successfully";

    @Autowired
    private ClaimAINotesService claimAINotesService;

    @Autowired
    private AutoClaimAiAnalysisService autoClaimAiAnalysisService;

    @PostMapping(Constants.EndPoints.CLAIM_AI_NOTES_CALL_BACK)
    public ResponseEntity<ClaimAINotesCallBackResponse> handleClaimAINotesCallBack(@RequestBody ClaimAINotesResponsePayload payload) {
        ClaimAINotesCallBackResponse aiNotesCallBackResponse = claimAINotesService.handleClaimAINotesCallBack(payload);
        return ResponseEntity.ok(aiNotesCallBackResponse);
    }

    @GetMapping("/{claimId}")
    public ResponseEntity<ClaimAINotesResponse> fetchClaimAiNotesByClaimId(@PathVariable String claimId) {
        ClaimAINotesResponse claimAINotes = claimAINotesService.findClaimAINotes(UUID.fromString(claimId));

        return claimAINotes != null
                ? ResponseEntity.ok(claimAINotes)
                : ResponseEntity.notFound().build();
    }

    @PostMapping(Constants.EndPoints.CLAIM_AI_NOTE_USER_REVIEW)
    public ResponseEntity<ClaimAINotesResponse> userReviewClaimAiNotes(
            @RequestBody ClaimAINotesUserReviewRequest request) {
        ClaimAINotesResponse response = claimAINotesService.saveClaimNotesUserReview(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping(Constants.EndPoints.CLAIM_AI_NOTES_MANUAL_TRIGGER)
    public ResponseEntity<String> triggerClaimAiNotesForClaim(
            @PathVariable("claimId") String claimId) {

        claimAINotesService.submitClaimToAIEngine(UUID.fromString(claimId));
        log.info("Successfully triggered AI notes processing for claimId: {}", claimId);
        return ResponseEntity.ok(CLAIM_SUBMITTED_SUCCESS_MESSAGE);
    }

    // ==================== Claim AI Trigger Management Endpoints ====================

    @PostMapping(Constants.EndPoints.CLAIM_AI_TRIGGER_CREATE)
    public ResponseEntity<String> createAiTrigger(
            @RequestBody ClaimAiTriggerRequest request) {
        log.info("Creating AI trigger for package: {}, intervention: {}",
                request.getPackageCode(), request.getInterventionCode());
        String response = autoClaimAiAnalysisService.createTrigger(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping(Constants.EndPoints.CLAIM_AI_TRIGGER_GET_BY_ID)
    public ResponseEntity<ClaimAiTriggerResponse> getAiTriggerById(
            @PathVariable("id") Long id) {
        log.info("Fetching AI trigger with ID: {}", id);
        ClaimAiTriggerResponse response = autoClaimAiAnalysisService.getTriggerById(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping(Constants.EndPoints.CLAIM_AI_TRIGGER_GET_ALL)
    public ResponseEntity<List<ClaimAiTriggerResponse>> getAllAiTriggers() {
        log.info("Fetching all AI triggers");
        List<ClaimAiTriggerResponse> responses = autoClaimAiAnalysisService.getAllTriggers();
        return ResponseEntity.ok(responses);
    }

    @PutMapping(Constants.EndPoints.CLAIM_AI_TRIGGER_UPDATE)
    public ResponseEntity<ClaimAiTriggerResponse> updateAiTrigger(
            @PathVariable("id") Long id,
            @RequestBody ClaimAiTriggerRequest request) {
        log.info("Updating AI trigger with ID: {}", id);
        ClaimAiTriggerResponse response = autoClaimAiAnalysisService.updateTrigger(id, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping(Constants.EndPoints.CLAIM_AI_TRIGGER_PROCESS)
    public ResponseEntity<String> processAiTriggers() {
        autoClaimAiAnalysisService.processAiTriggers();
        return ResponseEntity.ok("AI analysis processing initiated successfully");
    }
}
