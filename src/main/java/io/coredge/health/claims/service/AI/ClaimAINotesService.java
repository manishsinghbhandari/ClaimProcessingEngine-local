package io.coredge.health.claims.service.AI;

import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.validation.FhirValidator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import io.camunda.zeebe.client.ZeebeClient;
import io.coredge.health.claims.client.AIClient;
import io.coredge.health.claims.constants.Constants;
import io.coredge.health.claims.db.entity.AI.AIValidation;
import io.coredge.health.claims.db.entity.AI.AIValidationScore;
import io.coredge.health.claims.db.entity.AiRoute;
import io.coredge.health.claims.db.entity.ClaimAINotes;
import io.coredge.health.claims.db.entity.InsuranceClaim;
import io.coredge.health.claims.db.entity.permission.User;
import io.coredge.health.claims.db.repositories.AiNotes.AIValidationRepository;
import io.coredge.health.claims.db.repositories.AiNotes.AiValidationMappingRepository;
import io.coredge.health.claims.db.repositories.AiNotes.ClaimAiNotesRepository;
import io.coredge.health.claims.db.repositories.BenefitsMasterRepository;
import io.coredge.health.claims.db.repositories.InsuranceClaimsRepository;
import io.coredge.health.claims.db.repositories.LineItemRepository;
import io.coredge.health.claims.context.HealthIdContextHolder;
import io.coredge.health.claims.enums.ClaimAIProcessStatusEnum;
import io.coredge.health.claims.enums.Decision;
import io.coredge.health.claims.enums.ExtensionStateCodeEnum;
import io.coredge.health.claims.exception.app.*;
import io.coredge.health.claims.exception.models.ErrorAttribute;
import io.coredge.health.claims.models.AI.DecisionDetail;
import io.coredge.health.claims.models.AI.ScoreDetail;
import io.coredge.health.claims.models.claims.*;
import io.coredge.health.claims.service.ClaimResponseService;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

import static io.coredge.health.claims.constants.Constants.HandlerMethod.AI_NOTES_RESPONSE;
import static io.coredge.health.claims.constants.Constants.WorkflowParams.*;

@Slf4j
@Service
public class ClaimAINotesService {

    @Autowired
    private AIClient aiClient;

    @Autowired
    private InsuranceClaimsRepository claimsRepository;

    @Autowired
    private IParser fhirParser;

    @Autowired
    private ClaimAiNotesRepository claimAINotesRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private IGenericClient fhirClient;

    @Autowired
    private FhirValidator validator;

    @Autowired
    private ZeebeClient zeebe;

    @Autowired
    private ClaimResponseService claimResponseService;

    @Autowired
    private AIValidationRepository validationRepository;

    @Autowired
    private LineItemRepository lineItemRepository;

    @Autowired
    private BenefitsMasterRepository benefitsMasterRepository;

    @Autowired
    private AiValidationMappingRepository validationMappingRepository;

    @Autowired
    private AIUserService aiUserService;

    @Autowired
    private AiValidationLogicService aiValidationLogicService;

    public void sendClaimToAIEngine(Bundle inputBundle, UUID claimUUId) {
        sendClaimToAIEngine(inputBundle, claimUUId, null);
    }

    public void sendClaimToAIEngine(Bundle inputBundle, UUID claimUUId, String routeOverride) {

        InsuranceClaim insuranceClaim = claimsRepository.findByClaimId(claimUUId)
                .flatMap(claims -> claims.stream().findFirst())
                .orElseThrow(() -> new NoSuchElementException("Claim not found for claimId: " + claimUUId));
        String bundleJson = fhirParser.encodeResourceToString(inputBundle);

        String routeEndpoint = (routeOverride != null && !routeOverride.trim().isEmpty())
                ? routeOverride
                : resolveAiRouteForClaim(claimUUId);

        ResponseEntity<String> response = aiClient.postClaimToRoute(bundleJson, claimUUId, routeEndpoint);
        processResponse(response, String.valueOf(claimUUId), insuranceClaim);

    }

    private String resolveAiRouteForClaim(UUID claimUUId) {
        try {
            Optional<List<String>> interventionCodes = lineItemRepository
                    .findDistinctCodesByClaimId(claimUUId.toString());

            if (interventionCodes.isPresent() && !interventionCodes.get().isEmpty()) {
                for (String code : interventionCodes.get()) {
                    Optional<AiRoute> route = benefitsMasterRepository.findAiRouteByInterventionCode(code);
                    if (route.isPresent() && route.get().getApiEndpoint() != null
                            && !route.get().getApiEndpoint().isEmpty()) {
                        log.info("Resolved AI route '{}' with endpoint '{}' for intervention code '{}' in claim {}",
                                route.get().getName(), route.get().getApiEndpoint(), code, claimUUId);
                        return route.get().getApiEndpoint();
                    }
                }
            }
            log.info("No specific AI route configured for claim {}, using default route", claimUUId);
        } catch (Exception e) {
            log.warn("Error resolving AI route for claim {}, using default route: {}", claimUUId, e.getMessage());
        }
        return null; // Use default route
    }

    public ClaimAINotesResponse findClaimAINotes(UUID claimId) {
        if (claimId == null) {
            throw new BadRequestException(
                    ErrorAttribute.builder().key(VAR_CLAIM_ID).value(String.valueOf(claimId)).build());
        }
        ClaimAINotes claimAINotes = claimAINotesRepository.findByClaimId(claimId);
        if (claimAINotes == null) {
            throw new DataNotFoundException("No ClaimAINotes found for claimId: " + claimId);
        }
        try {
            return ClaimAINotesResponse.builder()
                    .claimId(String.valueOf(claimAINotes.getClaim().getClaimId()))
                    .notes(claimAINotes.getNotes() != null ? objectMapper.readTree(claimAINotes.getNotes()) : null)
                    .userReview(
                            claimAINotes.getUser_review() != null ? objectMapper.readTree(claimAINotes.getUser_review())
                                    : null)
                    .overallNotes(claimAINotes.getOverallNotes())
                    .status(claimAINotes.getStatus())
                    .createdBy(aiUserService.getAiUser().getCreatedBy())
                    .createdDate(claimAINotes.getCreationDate())
                    .lastModifiedBy(aiUserService.getAiUser().getLastModifiedBy())
                    .lastModifiedDate(claimAINotes.getLastModifiedDate())
                    .build();
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse notes JSON", e);
        }
    }

    public ClaimAINotesCallBackResponse handleClaimAINotesCallBack(ClaimAINotesResponsePayload payload) {

        // Fetch InsuranceClaim
        InsuranceClaim claim = claimsRepository.findByClaimId(UUID.fromString(payload.getClaimId()))
                .flatMap(claims -> claims.stream().findFirst())
                .orElseThrow(DataNotFoundException::new);
        return handleClaimAINotesCallBack(payload, claim);
    }

    public ClaimAINotesCallBackResponse handleClaimAINotesCallBack(ClaimAINotesResponsePayload payload,
            InsuranceClaim claim) {
        // Validate payload

        // Validate payload
        User aiUser = aiUserService.getAiUser();
        log.info("AI Username found {}", aiUser.getUsername());

        if (payload == null || payload.getClaimId() == null) {
            return ClaimAINotesCallBackResponse.builder()
                    .status("error")
                    .message("Invalid payload or missing claim_id")
                    .build();
        }

        // Fetch existing ClaimAINotes by claimId
        ClaimAINotes claimAINotes = claimAINotesRepository.findByClaimId(UUID.fromString(payload.getClaimId()));
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
                            "Invalid notes JSON format in database for claimId: " + payload.getClaimId());
                }
            } else {
                notesArray = objectMapper.createArrayNode();
            }

            // Parse new document_notes from payload
            JsonNode newNotesNode = objectMapper.readTree(objectMapper.writeValueAsString(payload.getDocumentNotes()));
            if (!newNotesNode.isArray()) {
                throw new InvalidJsonFormatException(
                        "Invalid document_notes JSON format in request for claimId: " + payload.getClaimId());
            }
            ArrayNode newNotes = (ArrayNode) newNotesNode;
            // Append new notes to existing array
            notesArray.addAll(newNotes);

            // Convert updated notes array back to JSON string
            claimAINotes.setNotes(objectMapper.writeValueAsString(notesArray));
            claimAINotes.setOverallNotes(payload.getOverallNotes());
            claimAINotes.setStatus(ClaimAIProcessStatusEnum.PROCESSED);
            claimAINotes.setScores(payload.getScores());
            claimAINotes.setCreatedBy(aiUser.getUsername());
            claimAINotes.setLastModifiedBy(aiUser.getUsername());

            // Save updated entity
            claimAINotesRepository.save(claimAINotes);

            return ClaimAINotesCallBackResponse.builder()
                    .status("success")
                    .message("AI notes appended and saved successfully")
                    .build();
        } catch (JsonProcessingException e) {
            log.error("Failed to process JSON for claim notes for claimId: {}", payload.getClaimId(), e);
            throw new JsonProcessingFailureException(
                    "Failed to process claim notes JSON for claimId: " + payload.getClaimId(), e);
        }
    }

    public ClaimAINotesResponse saveClaimNotesUserReview(ClaimAINotesUserReviewRequest request) {
        if (request.getClaimId() == null || request.getUserReview() == null || request.getUserReview().isEmpty()) {
            throw new BadRequestException(
                    ErrorAttribute.builder()
                            .key("claimId or userReview")
                            .value("Claim ID or user review cannot be null or empty")
                            .build());
        }

        ClaimAINotes claimAINotes = claimAINotesRepository.findByClaimId(UUID.fromString(request.getClaimId()));
        if (claimAINotes == null) {
            throw new BadRequestException(
                    ErrorAttribute.builder()
                            .key("claimId")
                            .value("No AI notes found for claim ID: " + request.getClaimId())
                            .build());
        }

        try {
            // Get existing user_review (JSON string) or initialize as empty array
            String existingUserReview = claimAINotes.getUser_review();
            ArrayNode reviewsArray;

            if (existingUserReview != null && !existingUserReview.isEmpty()) {
                JsonNode existingNode = objectMapper.readTree(existingUserReview);
                if (existingNode.isArray()) {
                    reviewsArray = (ArrayNode) existingNode;
                } else if (existingNode.isObject()) {
                    reviewsArray = objectMapper.createArrayNode();
                    reviewsArray.add(existingNode);
                } else {
                    throw new InvalidJsonFormatException(
                            "Invalid user_review JSON format in database for claimId: " + request.getClaimId());
                }
            } else {
                reviewsArray = objectMapper.createArrayNode();
            }

            // Parse new user review from request
            JsonNode newReviewNode = objectMapper.readTree(objectMapper.writeValueAsString(request.getUserReview()));
            if (newReviewNode.isObject()) {
                reviewsArray.add(newReviewNode);
            } else if (newReviewNode.isArray()) {
                reviewsArray.addAll((ArrayNode) newReviewNode);
            } else {
                throw new InvalidJsonFormatException(
                        "Invalid user review JSON format in request for claimId: " + request.getClaimId());
            }

            // Convert updated reviews array back to JSON string
            claimAINotes.setUser_review(objectMapper.writeValueAsString(reviewsArray));

            // Save updated entity
            ClaimAINotes savedClaimAINotes = claimAINotesRepository.save(claimAINotes);

            // Build response
            return ClaimAINotesResponse.builder()
                    .id(savedClaimAINotes.getId())
                    .claimId(savedClaimAINotes.getClaim().getClaimId().toString()) // Fixed to use claimId directly
                    .notes(savedClaimAINotes.getNotes() != null ? objectMapper.readTree(savedClaimAINotes.getNotes())
                            : null)
                    .userReview(savedClaimAINotes.getUser_review() != null
                            ? objectMapper.readTree(savedClaimAINotes.getUser_review())
                            : null)
                    .overallNotes(savedClaimAINotes.getOverallNotes())
                    .status(savedClaimAINotes.getStatus())
                    .createdBy(savedClaimAINotes.getCreatedBy())
                    .createdDate(savedClaimAINotes.getCreationDate())
                    .lastModifiedBy(savedClaimAINotes.getLastModifiedBy())
                    .lastModifiedDate(savedClaimAINotes.getLastModifiedDate())
                    .build();
        } catch (JsonProcessingException e) {
            log.error("Failed to process user review JSON for claimId: {}", request.getClaimId(), e);
            throw new JsonProcessingFailureException(
                    "Failed to process user review JSON for claimId: " + request.getClaimId(), e);
        }
    }

    /**
     * Manual Submit claim to AI engine
     * @param claimId
     * @return
     */
    @Async
    public void submitClaimToAIEngine(UUID claimId) {
        submitClaimToAIEngine(claimId, null);
    }
    public void submitClaimToAIEngine(UUID claimId, String routeOverride) {
        log.info("Triggering claim to AI engine for claimId: {}, route url: {}", claimId, routeOverride);
        Bundle bundle = fetchClaimFromFhir(claimId);
        sendClaimToAIEngine(bundle, claimId, routeOverride);
        log.info("Claim {} submitted to AI engine.", claimId);
    }

    private Bundle fetchClaimFromFhir(UUID claimId) {
        log.info("Fetching claim from FHIR server for claimId: {}", claimId);

        Optional<String> bundleId = claimsRepository.findBundleIdByClaimId(claimId);
        if (bundleId.isEmpty()) {
            throw new DataValidationException(
                    "FHIR bundle validation failed. ClaimId " + claimId + " does not contain bundle record");
        }
        return fhirClient.read().resource(Bundle.class).withId(bundleId.get()).execute();
    }

    private void processResponse(ResponseEntity<String> response, String claimId, InsuranceClaim insuranceClaim) {
        log.info("Received AI claim response for claim id {} with status {}", claimId, response.getStatusCode());
        try {
            Map<String, Object> variables = new HashMap<>();
            if (response.getStatusCode().is2xxSuccessful()) {
                // Deserialize the response body into ClaimAINotesCallBackPayload
                ClaimAINotesResponsePayload payload = objectMapper.readValue(response.getBody(),
                        ClaimAINotesResponsePayload.class);
                handleClaimAINotesCallBack(payload, insuranceClaim);
                variables = evaluateDecision(payload, claimId);
            } else {
                // AI Engine is not reachable - let claim continue through normal processing
                log.warn("AI Engine is not available for claimId: {}, status: {}. Claim will proceed to manual review.", claimId, response.getStatusCode());
                variables.put(VAR_CLAIM_ID, claimId);
                variables.put(VAR_IS_EFWA_APPROVED, true);
                variables.put(VAR_IS_EFWA_REQUIRE_DOCTOR_REVIEW, true);
                variables.put("aiNotesError", response.getBody());
            }
            aiNotesCallBackToCamunda(claimId, variables);

            // If claim is currently in MANUAL_REVIEW or CLINICAL_REVIEW,
            // trigger workflow action from current state based on AI decision.
            String aiDecision = (String) variables.get(VAR_AI_DECISION);
            if (aiDecision != null) {
                triggerWorkflowFromCurrentState(claimId, insuranceClaim, aiDecision);
            }
        } catch (DataAccessException daex) {
            log.error("Failed to process due to DataAccessException for claimId: {}", claimId, daex);
            throw daex;
        } catch (Exception e) {
            log.error("Unexpected error processing AI Notes response: {} : with error : {}", claimId, e.getMessage(),
                    e);
            throw new BadRequestException(
                    "Unexpected error processing response for: " + claimId + "with error :" + e.getMessage());
        }
    }

    /**
     * Trigger workflow action from current state based on AI decision.    */
    private void triggerWorkflowFromCurrentState(String claimId, InsuranceClaim insuranceClaim, String aiDecision) {
        ExtensionStateCodeEnum currentStatus = insuranceClaim.getStatus();

        if (currentStatus != ExtensionStateCodeEnum.MANUAL_REVIEW && currentStatus != ExtensionStateCodeEnum.CLINICAL_REVIEW) {
            return;
        }

        User aiUser = aiUserService.getAiUser();
        HealthIdContextHolder.userId(aiUser.getUsername());

        Decision decision = Decision.fromDisplayName(aiDecision);

        if (decision == Decision.APPROVE) {
            triggerApproval(claimId, currentStatus);
        } else if (decision == Decision.SENT_BACK) {
            triggerSentBack(claimId, currentStatus);
        } else {
            log.info("AI decision {} for claim {} in {}. FHIR/DB updated; workflow stays for human review.",
                    decision, claimId, currentStatus);
        }
    }

    private void triggerApproval(String claimId, ExtensionStateCodeEnum currentStatus) {
        String notes = "AI Approved. Transitioning from " + currentStatus + " to Approved.";
        AdjudicateRequestBody body = AdjudicateRequestBody.builder()
                .action(ExtensionStateCodeEnum.APPROVED)
                .notes(notes)
                .build();

        if (currentStatus == ExtensionStateCodeEnum.MANUAL_REVIEW) {
            claimResponseService.processClaimNewStages(claimId, body, true);
        } else {
            claimResponseService.processPreAuth(claimId, body, true, true);
        }
        log.info("AI APPROVE for claim {} in {}. Workflow advanced to Approved.", claimId, currentStatus);
    }

    private void triggerSentBack(String claimId, ExtensionStateCodeEnum currentStatus) {
        Map<String, Object> vars = new HashMap<>();
        vars.put(VAR_CLAIM_ID, claimId);
        vars.put(VAR_IS_RETURN_TO_SENDER, true);

        if (currentStatus == ExtensionStateCodeEnum.MANUAL_REVIEW) {
            log.info("AI SENT_BACK for claim {} in MANUAL_REVIEW. Publishing ManualReviewAction.", claimId);
            publishWorkflowMessage(Constants.BPMN_USER_TASK_MESSAGE.MANUAL_REVIEW, claimId, vars);
        } else {
            vars.put(VAR_IS_APPROVED_BY_DOC, false);
            log.info("AI SENT_BACK for claim {} in CLINICAL_REVIEW. Publishing DoctorReviewAction + DeclineReviewAction.", claimId);
            publishWorkflowMessage(Constants.BPMN_USER_TASK_MESSAGE.DOCTOR_REVIEW, claimId, vars);
            publishWorkflowMessage(Constants.BPMN_USER_TASK_MESSAGE.DECLINE_REVIEW, claimId, Map.of(VAR_CLAIM_ID, claimId));
        }
    }

    private void aiNotesCallBackToCamunda(String claimId, Map<String, Object> variables) {
        try {
            publishWorkflowMessage(AI_NOTES_RESPONSE, claimId, variables);
            log.info("Workflow message for AI Notes published successfully for claimId: {}", claimId);
        } catch (Exception e) {
            log.warn("Failed to publish workflow message of AI Notes for claimId: {}", claimId, e);
            throw new WorkFlowException("Failed to publish message for AI Notes to workflow engine", e);
        }
    }

    private void publishWorkflowMessage(String messageName, String correlationKey, Map<String, Object> variables) {
        zeebe.newPublishMessageCommand()
                .messageName(messageName)
                .correlationKey(correlationKey)
                .variables(variables)
                .send().join();
    }

    private Map<String, Object> evaluateDecision(ClaimAINotesResponsePayload payload, String claimId) {

        Optional<List<String>> listOfCodes = lineItemRepository.findDistinctCodesByClaimId(claimId);

        if (listOfCodes.isEmpty()) {
            throw new DataValidationException("No Intervention Codes found for claimId: " + claimId);
        }

        List<AIValidation> validations = aiValidationLogicService.getAiValidationMapping(listOfCodes.get(),
                LocalDate.now(), Constants.AI_USE.CLAIM_USE);

        List<String> remarks = new ArrayList<>();
        Decision decision = aiValidationLogicService.calculateAiValidationScore(payload, validations, Constants.AI_USE.CLAIM_USE, remarks);

        log.info("Decision generated for Claim {} is : {} ", claimId, decision);

        return createVariablesOfAICallBackPayload(claimId, decision, String.join(", ", remarks));
    }

    private Map<String, Object> createVariablesOfAICallBackPayload(String claimId, Decision decision, String reason) {

        Map<String, Object> variables = new HashMap<>();

        variables.put(VAR_CLAIM_ID, claimId);
        variables.put(VAR_IS_EFWA_APPROVED, decision.isApproved());
        variables.put(VAR_IS_EFWA_REQUIRE_DOCTOR_REVIEW, decision.isRequiresDoctorReview());
        variables.put(VAR_AI_DECISION, decision.name());

        boolean isAiApprovedClaim = (decision == Decision.APPROVE);
        variables.put("isAiApprovedClaim", isAiApprovedClaim);

        if (decision == Decision.SENT_BACK || decision == Decision.MANUAL_REVIEW) {
            ExtensionStateCodeEnum action = (decision == Decision.SENT_BACK)
                    ? ExtensionStateCodeEnum.SENT_BACK
                    : ExtensionStateCodeEnum.CLINICAL_REVIEW;

            AdjudicateRequestBody body = AdjudicateRequestBody.builder()
                    .notes(reason)
                    .action(action)
                    .build();

            claimResponseService.processClaim(claimId, body, false, false);
        }
        return variables;

    }
}
