package io.coredge.health.claims.service.AI;

import ca.uhn.fhir.parser.IParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import io.camunda.zeebe.client.ZeebeClient;
import io.coredge.health.claims.client.PreAuthAIClient;
import io.coredge.health.claims.constants.Constants;
import io.coredge.health.claims.db.entity.AI.AIValidation;
import io.coredge.health.claims.db.entity.ClaimAINotes;
import io.coredge.health.claims.db.entity.InsuranceClaim;
import io.coredge.health.claims.db.entity.permission.User;
import io.coredge.health.claims.db.repositories.AiNotes.ClaimAiNotesRepository;
import io.coredge.health.claims.db.repositories.InsuranceClaimsRepository;
import io.coredge.health.claims.db.repositories.LineItemRepository;
import io.coredge.health.claims.enums.ClaimAIProcessStatusEnum;
import io.coredge.health.claims.enums.Decision;
import io.coredge.health.claims.exception.app.*;
import io.coredge.health.claims.models.AI.ScoreDetail;
import io.coredge.health.claims.models.claims.ClaimAINotesCallBackResponse;
import io.coredge.health.claims.models.claims.ClaimAINotesResponsePayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.hl7.fhir.r4.model.Bundle;
import io.coredge.health.claims.db.entity.InsuranceClaim;
import io.coredge.health.claims.db.repositories.BenefitsMasterRepository;
import io.coredge.health.claims.db.repositories.InsuranceClaimsRepository;
import io.coredge.health.claims.db.repositories.LineItemRepository;
import io.coredge.health.claims.enums.Decision;
import io.coredge.health.claims.exception.app.AIEngineException;
import io.coredge.health.claims.exception.app.WorkFlowException;
import io.coredge.health.claims.models.claims.ClaimAINotesResponsePayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;

import static io.coredge.health.claims.constants.Constants.HandlerMethod.AI_NOTES_RESPONSE;

@Slf4j
@Service
@RequiredArgsConstructor
public class PreAuthAINotesService {

    private final PreAuthAIClient preAuthAIClient;
    private final ZeebeClient zeebe;
    private final ObjectMapper objectMapper;
    private final LineItemRepository lineItemRepository;
    private final AiValidationLogicService aiValidationLogicService;
    private final IParser fhirParser;
    private final AIUserService aiUserService;
    private final ClaimAiNotesRepository claimAINotesRepository;
    private final InsuranceClaimsRepository insuranceClaimsRepository;

    public void sendPreAuthToAIEngine(Bundle inputBundle, UUID claimUUId) {
        log.info("Preparing to send PreAuth bundle for claimId: {}", claimUUId);

        String bundleJson = fhirParser.encodeResourceToString(inputBundle);

        try {
            ResponseEntity<String> response = preAuthAIClient.postPreAuthBundle(bundleJson, claimUUId);
            log.info("----Raw response from preauth AI Engine: {}", response);
            processResponse(response, claimUUId.toString());
        } catch (Exception e) {
            log.error("Failed to process PreAuth AI request for claimId: {}", claimUUId, e);
            // In case of failure, we might want to let the workflow continue or retry
            // For now, we'll log and throw or handle as per requirement.
            // If synchronous, the caller catches this.
            throw new AIEngineException("Failed to process PreAuth AI request", e);
        }
    }

    private void processResponse(ResponseEntity<String> response, String claimId) {
        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            log.info("Received successful response from AI Engine for PreAuth claimId: {}", claimId);
            try {
                ClaimAINotesResponsePayload payload = objectMapper.readValue(response.getBody(),
                        ClaimAINotesResponsePayload.class);

                processAiDecision(payload, claimId);
                handlePreAuthAICallBack(payload, claimId);
            } catch (JsonProcessingException e) {
                log.error("Error parsing AI response for PreAuth claimId: {}", claimId, e);
                throw new AIEngineException("Error parsing AI response", e);
            }
        } else {
            log.warn("Received non-success response from AI Engine for PreAuth claimId: {}. Status: {}", claimId,
                    response.getStatusCode());

            throw new AIEngineException("AI Engine returned error: " + response.getStatusCode());
        }
    }

    private void processAiDecision(ClaimAINotesResponsePayload payload, String claimId) {
        List<String> interventionCodes = lineItemRepository.findDistinctCodesByClaimId(claimId)
                .orElse(Collections.emptyList());
        // For date, assuming current date or we fetch claim date. Using current date
        // for validation mapping lookup
        LocalDate validationDate = LocalDate.now();

        List<AIValidation> validations = aiValidationLogicService.getAiValidationMapping(interventionCodes,
                validationDate, Constants.AI_USE.PRE_AUTH_USE);

        log.info("Validations for PreAuth AI claimId: {}", validations);

        List<String> remarks = new ArrayList<>();
        Decision decision = aiValidationLogicService.calculateAiValidationScore(payload, validations,
                Constants.AI_USE.PRE_AUTH_USE,
                remarks);

        log.info("AI Decision for PreAuth claimId: {} is {}", claimId, decision);

        // For preauthorizations translate manual review to clinical review
        if (decision == Decision.MANUAL_REVIEW) {
            log.info("------Manual Review for PreAuth claimId: {}", claimId);
            decision = Decision.CLINICAL_REVIEW;
        }

        log.info("-----Final PreAuth AI Decision for PreAuth: {}", decision);

        // Update process variables
        Map<String, Object> variables = new HashMap<>();
        variables.put(Constants.WorkflowParams.VAR_IS_EFWA_APPROVED, decision == Decision.APPROVE);
        variables.put(Constants.WorkflowParams.VAR_IS_EFWA_REQUIRE_DOCTOR_REVIEW,
                decision == Decision.CLINICAL_REVIEW || decision == Decision.SENT_BACK);
        // Set isAiRejected - only true for REJECT decision, false for APPROVE and CLINICAL_REVIEW
        // CLINICAL_REVIEW should proceed through normal auto-adjudication flow
        variables.put("isAiRejected", decision == Decision.SENT_BACK);
        // Track if AI approved this preauth - used for auto-approval from clinical review
        // Logic: Approved and has validations
        boolean isAiApproved = (decision == Decision.APPROVE) && !CollectionUtils.isEmpty(validations);
        variables.put("isAiApprovedPreauth", isAiApproved);
        // Set aiDecision as a string for 3-way BPMN gateway routing
        variables.put(Constants.WorkflowParams.VAR_AI_DECISION, decision.name());

        // Add scores and payload to variables if needed for history or frontend
        try {
            variables.put("aiScores", objectMapper.writeValueAsString(payload.getScores().get("preauthDecision").getScore()));
            variables.put("aiRemarks", objectMapper.writeValueAsString(payload.getScores().get("preauthDecision").getRemarks()));
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize AI scores/remarks for PreAuth claimId: {}", claimId);
        }

        // Correlate message back to Camunda
        log.info("Correlating PreAuth AI response to Camunda for claimId: {}", claimId);

        zeebe.newPublishMessageCommand()
                .messageName(Constants.HandlerMethod.AI_NOTES_RESPONSE)
                .correlationKey(claimId)
                .variables(variables)
                .send().join();
    }

    /**
     * Handles PreAuth AI callback with the new payload structure.
     */
    public void handlePreAuthAICallBack(ClaimAINotesResponsePayload payload, String claimId) {
        // Validate payload
        if (payload == null) {
            log.warn("Invalid payload in AI callback for claimId: {}", claimId);
            return;
        }

        String decision = payload.getDecision();
        log.info("Processing AI callback for claimId: {} with decision: {}", claimId, decision);

        // Fetch InsuranceClaim using the original claimId (not from payload)
        InsuranceClaim claim = insuranceClaimsRepository.findByClaimId(UUID.fromString(claimId))
                .flatMap(claims -> claims.stream().findFirst())
                .orElseThrow(() -> {
                    log.error("Claim not found for claimId: {}", claimId);
                    return new DataNotFoundException();
                });

        // Get AI user for audit purposes
        User aiUser = aiUserService.getAiUser();
        log.info("AI Username found {}", aiUser.getUsername());

        // Fetch existing ClaimAINotes by claimId
        ClaimAINotes claimAINotes = claimAINotesRepository.findByClaimId(UUID.fromString(claimId));
        if (claimAINotes == null) {
            // If no existing record, create a new one
            claimAINotes = new ClaimAINotes();
            claimAINotes.setClaim(claim);
            claimAINotes.setCreatedBy(aiUser.getUsername());
            claimAINotes.setLastModifiedBy(aiUser.getUsername());
        }

        try {
            // Get existing notes (JSON string) or initialize as empty array
            String existingNotes = claimAINotes.getNotes();
            ArrayNode notesArray;

            if (existingNotes != null && !existingNotes.isEmpty()) {
                JsonNode existingNode = objectMapper.readTree(existingNotes);
                if (existingNode.isArray()) {
                    notesArray = (ArrayNode) existingNode;
                } else {
                    throw new InvalidJsonFormatException(
                            "Invalid notes JSON format in database for claimId: " + claimId);
                }
            } else {
                notesArray = objectMapper.createArrayNode();
            }

            // Extract remarks from scores.preauthDecision if available
            List<String> remarks = new ArrayList<>();
            if (payload.getScores() != null && payload.getScores().containsKey("preauthDecision")) {
                ScoreDetail preauthDecision = payload.getScores().get("preauthDecision");
                if (preauthDecision != null && preauthDecision.getRemarks() != null) {
                    remarks = preauthDecision.getRemarks();
                }
            }

            // Add remarks to notes array
            for (String remark : remarks) {
                notesArray.add(remark);
            }

            // Convert updated notes array back to JSON string
            claimAINotes.setNotes(objectMapper.writeValueAsString(notesArray));
            claimAINotes.setStatus(ClaimAIProcessStatusEnum.PROCESSED);
            claimAINotes.setScores(payload.getScores());
            claimAINotes.setCreatedBy(aiUser.getUsername());
            claimAINotes.setLastModifiedBy(aiUser.getUsername());

            // Save updated entity
            claimAINotesRepository.save(claimAINotes);

            ClaimAINotesCallBackResponse.builder()
                    .status("success")
                    .message("AI notes processed and workflow updated successfully")
                    .build();
        } catch (JsonProcessingException e) {
            log.error("Failed to process JSON for claim notes for claimId: {}", claimId, e);
            throw new JsonProcessingFailureException(
                    "Failed to process claim notes JSON for claimId: " + claimId, e);
        }
    }

    private void preauthAICallBackToCamunda(String claimId, Map<String, Object> variables) {
        try {
            zeebe.newPublishMessageCommand().messageName(AI_NOTES_RESPONSE)
                    .correlationKey(claimId)
                    .variables(variables)
                    .send().join();

            log.info("Workflow message for preauth AI published successfully for claimId: {}", claimId);
        } catch (Exception e) {
            log.warn("Failed to publish workflow message of preauth AI for claimId: {}", claimId, e);
            throw new WorkFlowException("Failed to publish message for preauth AI to workflow engine", e);
        }
    }
}
