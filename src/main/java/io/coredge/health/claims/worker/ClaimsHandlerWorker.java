package io.coredge.health.claims.worker;

import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.server.exceptions.BaseServerResponseException;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.spring.client.annotation.JobWorker;
import io.coredge.health.claims.context.HealthIdContextHolder;
import io.coredge.health.claims.db.entity.*;
import io.coredge.health.claims.db.entity.permission.User;
import io.coredge.health.claims.db.repositories.*;
import io.coredge.health.claims.db.repositories.BenefitsMasterRepository;
import io.coredge.health.claims.db.repositories.LineItemRepository;
import io.coredge.health.claims.db.repositories.ProviderRepository;
import io.coredge.health.claims.enums.Decision;
import io.coredge.health.claims.enums.ExtensionStateCodeEnum;
import io.coredge.health.claims.exception.app.BadRequestException;
import io.coredge.health.claims.helpers.ClaimValidationUtility;
import io.coredge.health.claims.models.ClaimHistoryDTO;
import io.coredge.health.claims.models.RuleInputDTO;
import io.coredge.health.claims.models.claims.AdjudicateRequestBody;
import io.coredge.health.claims.service.*;
import io.coredge.health.claims.service.AI.AIUserService;
import io.coredge.health.claims.service.AI.ClaimAINotesService;
import io.coredge.health.claims.service.AI.PreAuthAINotesService;
import io.coredge.health.claims.utils.*;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;

import static io.coredge.health.claims.constants.Constants.*;
import static io.coredge.health.claims.constants.Constants.HandlerMethod.*;
import static io.coredge.health.claims.constants.Constants.SYSTEM;
import static io.coredge.health.claims.constants.Constants.WorkflowParams.*;

@ConditionalOnExpression("${camunda.jobworker.enable:true}")
@Component
@Slf4j
public class ClaimsHandlerWorker {

    @Autowired
    private WorkflowService wfService;

    @Autowired
    private IGenericClient fhirClient;

    @Autowired
    private BenefitsMasterRepository benefitsRepository;

    @Autowired
    private ClaimsHandlerUtility claimsHandlerUtility;

    @Autowired
    private ClaimExtensionService extentionService;

    @Autowired
    private InsuranceClaimsService insuranceClaimsService;

    @Autowired
    private TaskListService taskListService;

    @Autowired
    ClaimResponseService claimResponseService;

    @Autowired
    private LineItemRepository lineItemRepository;

    @Autowired
    private StatusUpdateService statusUpdateService;

    @Autowired
    private MemberService memberService;

    @Autowired
    private ClaimsHandlerService handlerService;

    @Autowired
    private ProviderRepository providerRepository;

    @Autowired
    private ClaimAINotesService aiNotesService;

    @Autowired
    private AIUserService aiUserService;

    @Autowired
    private PreAuthAINotesService preAuthAINotesService;

    @Autowired
    private LiviaClaimService liviaClaimService;

    @Autowired
    private ZeebeClient zeebe;

    public ClaimsHandlerWorker() {
        super();
    }

    @JobWorker(type = PROCESS_INITIALIZER)
    public Map<String, Object> processInitializer(ActivatedJob job) {
        return AsyncUtils.withCamundaContext(job, () -> {
            log.info("Started new process with process instance {}", job.getProcessInstanceKey());
            String claimId = (String) job.getVariable(VAR_CLAIM_ID);
            handlerService.processInitializer(claimId, job.getVariablesAsMap());
            return job.getVariablesAsMap();
        });
    }

    @JobWorker(type = CLAIM_HANDLER)
    public Map<String, Object> handleClaim(ActivatedJob job) {
        return AsyncUtils.withCamundaContext(job, () -> {
            log.info("Started new claim process with process instance {}", job.getProcessInstanceKey());
            String claimId = (String) job.getVariable(VAR_CLAIM_ID);
            String claimResponseId = (String) job.getVariable(VAR_CLAIM_RESPONSE_ID);
            wfService.saveWorkFlowData(job.getProcessInstanceKey(), claimId, claimResponseId);
            handlerService.populateAdjudicationData(claimId, job.getVariablesAsMap());
            return job.getVariablesAsMap();
        });
    }

    @JobWorker(type = PRE_AUTH_HANDLER)
    public Map<String, Object> handlePreAuth(ActivatedJob job) {
        return AsyncUtils.withCamundaContext(job, () -> {
            log.info("Started new PreAuth process with process instance {}", job.getProcessInstanceKey());
            String claimId = (String) job.getVariable(VAR_CLAIM_ID);
            // handlerService.processInitializer(claimId, job.getVariablesAsMap());
            String claimResponseId = (String) job.getVariable(VAR_CLAIM_RESPONSE_ID);
            wfService.saveWorkFlowData(job.getProcessInstanceKey(), claimId, claimResponseId);
            handlerService.populateAdjudicationData(claimId, job.getVariablesAsMap());
            return job.getVariablesAsMap();
        });
    }

    /**
     * PreAuth Auto Adjudication failed and assigned to SHA for review.
     * @param job
     * @return
     */
    @JobWorker(type = HANDLE_CLAIM_AUTO_ADJ_FAIL)
    public Map<String, Object> preAuthAutoAdjFailStartEvent(ActivatedJob job) {
        return AsyncUtils.withCamundaContext(job, () -> {
            log.info("AutoAdjudication failed assigning ClaimId: {} to SHA for review.", job.getVariable(VAR_CLAIM_ID));
            // Mark as in-review so that SHA can take action.
            String claimId = (String) job.getVariable(VAR_CLAIM_ID);
            String claimResponseId = (String) job.getVariable(VAR_CLAIM_RESPONSE_ID);
            Claim claim = fhirClient.read().resource(Claim.class).withId(claimId).execute();
            if (ClaimValidationUtility.isPreAuth(claim)) {
                Map<String, Object> variableMap = job.getVariablesAsMap();
                boolean rejectedByRule = isRejectedByRuleEngine(variableMap);
                if (rejectedByRule) {
                    processRejectedByRuleEngine(claim, claimId);
                } else {
                    updateClaimAndResponseStatus(claimId, claim, claimResponseId, ExtensionStateCodeEnum.SENT_BACK, false, false);
                }
            } else {
                updateClaimAndResponseStatus(claimId, claim, claimResponseId, ExtensionStateCodeEnum.DECLINE, false, false);
            }
            return job.getVariablesAsMap();
        });
    }

    private void processRejectedByRuleEngine(Claim claim, String claimId) {
        log.info("Adding notes after RuleEngine rejection preAuth claimId : {}, processInstanceKey : {}", claimId);
        claimResponseService.processPreAuth(claimId, AdjudicateRequestBody.builder()
                .action(ExtensionStateCodeEnum.SENT_BACK)
                .notes(findReason(claim))
                .build(), false);
    }

    /**
     * Check if interventions in the preauth requires doctor approval.
     *
     * @return
     * @Depricated This is now handled at initial stage in populate adjudication data.s
     */
    @JobWorker(type = DOCTOR_APPROVAL_HANDLER, autoComplete = true)
    public Map<String, Object> isDocApprovalRequired(ActivatedJob job) {
        return job.getVariablesAsMap();
    }

    /**
     * Assigning the claim/preAuth to appropriate status after decline by SHA.
     * @return
     */
    @JobWorker(type = PRE_AUTH_SHA_REVIEW_START)
    public Map<String, Object> preAuthShaReviewStart(ActivatedJob job) {
        return AsyncUtils.withCamundaContext(job, () -> {
            log.info("Claim ID {} assigned for SHA review with taskId", job.getVariable(VAR_CLAIM_ID));
            // Mark as in-review so that Sha can take action.
            String claimId = (String) job.getVariable(VAR_CLAIM_ID);
            String claimResponseId = (String) job.getVariable(VAR_CLAIM_RESPONSE_ID);
            Claim claim = fhirClient.read().resource(Claim.class).withId(claimId).execute();
            log.info("Marking ClaimID {} and ClaimResponseId {} as Sha State", claimId, claimResponseId);
            if (ClaimValidationUtility.isPreAuth(claim))
                updateClaimAndResponseStatus(claimId, claim, claimResponseId, ExtensionStateCodeEnum.IN_REVIEW, false, false);
            else
                updateClaimAndResponseStatus(claimId, claim, claimResponseId, ExtensionStateCodeEnum.DECLINE, false, false);
            return job.getVariablesAsMap();
        });
    }

    /**
     * Check if interventions in the preauth requires doctor approval.
     *
     * @return
     */
    @JobWorker(type = PRE_AUTH_ASSIGNED_TO_DOC_START_EVENT)
    public Map<String, Object> preAuthAssignedToDocStartEvent(ActivatedJob job) {
        return AsyncUtils.withCamundaContext(job, () -> {
            log.info("preAuthAssignedToDocStartEvent : triggered for ClaimId :{}", job.getVariable(VAR_CLAIM_ID));
            String claimId = (String) job.getVariable(VAR_CLAIM_ID);
            String claimResponseId = (String) job.getVariable(VAR_CLAIM_RESPONSE_ID);
            Map<String, Object> variables = job.getVariablesAsMap();
            Claim claim = fhirClient.read().resource(Claim.class).withId(claimId).execute();


            // Scenario : Preauth was sent for clinical review but AI flagged for approval.
            // Transition preauth to APPROVED state with AI as user and AI score as remarks.
            String aiDecision = (String) variables.get(VAR_AI_DECISION);
            if (aiDecision != null && claim.getUse().equals(Claim.Use.PREAUTHORIZATION)) {
                Decision decision = Decision.fromDisplayName(aiDecision);
                // Only execute this for approved preauthorizations
                if (decision == Decision.APPROVE) {
                    Boolean isAutoApproved = (Boolean) variables.get(VAR_IS_AUTO_APPROVED);
                    Boolean isCivilServiceApproved = (Boolean) variables.get(VAR_IS_CIVIL_SERVICE_APPROVED);
                    Boolean isDoctorApprovalRequired = (Boolean) variables.get(VAR_IS_DOC_APPROVAL_REQ);

                    if (Boolean.TRUE.equals(isAutoApproved)
                            && Boolean.TRUE.equals(isCivilServiceApproved)
                            && Boolean.TRUE.equals(isDoctorApprovalRequired)) {
                        log.info("AI approved preauth routed to doctor review, auto-approving with AI user for claimId: {}",
                                claimId);
                        String aiScores = (String) variables.get("aiScores");
                        String aiRemarks = (String) variables.get("aiRemarks");
                        // Trigger preauth status change to Approved based on AI decision;
                        aiTriggerStatusChange(claimId, aiScores, aiRemarks, ExtensionStateCodeEnum.APPROVED, Claim.Use.PREAUTHORIZATION);
                        // Broadcast approved status update
                        return broadcastAIStatusChange(variables, ExtensionStateCodeEnum.APPROVED, claim, claimResponseId);
                    }
                }
            }

            // Scenario : Claim was sent for medical review but AI flagged for approval.
            // Transition claim through Medical Review → Approved → SENT_FOR_PAYMENT_PROCESSING
            // with AI as user and AI score as remarks.
            if (aiDecision != null && claim.getUse().equals(Claim.Use.CLAIM)) {
                Decision decision = Decision.fromDisplayName(aiDecision);
                Boolean isAiApprovedClaim = Boolean.TRUE.equals(variables.get("isAiApprovedClaim"));
                if (decision == Decision.APPROVE && isAiApprovedClaim) {
                    Boolean isAutoApproved = (Boolean) variables.get(VAR_IS_AUTO_APPROVED);
                    Boolean isCivilServiceApproved = (Boolean) variables.get(VAR_IS_CIVIL_SERVICE_APPROVED);
                    Boolean isDoctorApprovalRequired = (Boolean) variables.get(VAR_IS_DOC_APPROVAL_REQ);

                    if (Boolean.TRUE.equals(isAutoApproved)
                            && Boolean.TRUE.equals(isCivilServiceApproved)
                            && Boolean.TRUE.equals(isDoctorApprovalRequired)) {
                        log.info("AI approved claim routed to medical review, auto-approving with AI user for claimId: {}",
                                claimId);
                        String aiScores = (String) variables.get("aiScores");
                        String aiRemarks = (String) variables.get("aiRemarks");
                        // Trigger claim status change to Approved based on AI decision;
                        aiTriggerStatusChange(claimId, aiScores, aiRemarks, ExtensionStateCodeEnum.SENT_FOR_PAYMENT_PROCESSING, Claim.Use.CLAIM);
                        // Publish DoctorReviewAction to complete the catch event after the listener finishes.
                        // TTL ensures the message is buffered until the subscription activates.
                        zeebe.newPublishMessageCommand()
                                .messageName("DoctorReviewAction")
                                .correlationKey(claimId)
                                .variables(Map.of(VAR_CLAIM_ID, claimId, VAR_IS_APPROVED_BY_DOC, true))
                                .timeToLive(Duration.ofSeconds(30))
                                .send().join();
                        log.info("Published DoctorReviewAction with isApprovedByDoc=true for AI auto-approved claimId: {}", claimId);
                        // Broadcast approved status update
                        return broadcastAIStatusChange(variables, ExtensionStateCodeEnum.APPROVED, claim, claimResponseId);
                    }

                }
            }

            ExtensionStateCodeEnum status = null;
            // Mark as in-review so that Doc can take action.
            if (claim.getUse().equals(Claim.Use.PREAUTHORIZATION)) {
                status = ExtensionStateCodeEnum.CLINICAL_REVIEW;
            } else {
                status = ExtensionStateCodeEnum.MEDICAL_REVIEW;
            }
            log.info("Marking ClaimID {} and ClaimResponseId {} as {}", claimId, claimResponseId, status);
            updateClaimAndResponseStatus(claimId, claim, claimResponseId, status, false, false);
            return variables;
        });
    }

    /**
     * PreAuth Completion handler.
     * Approves the preauth and notifies providers.
     * After normal approval, handles AI CLINICAL_REVIEW transition:
     * - AI CLINICAL_REVIEW + auto-approved + no doctor required → approve normally,
     * then move to CLINICAL_REVIEW with AI user + AI score
     */
    @JobWorker(type = HANDLE_PRE_AUTH_APPROVED_EVENT)
    public Map<String, Object> handlePreAuthApprovedEvent(ActivatedJob job) {
        return AsyncUtils.withCamundaContext(job, () -> {
            String claimId = (String) job.getVariable(VAR_CLAIM_ID);
            String claimResponseId = (String) job.getVariable(VAR_CLAIM_RESPONSE_ID);
            Map<String, Object> variables = job.getVariablesAsMap();
            Claim claim = fhirClient.read().resource(Claim.class).withId(claimId).execute();

            log.info("PreAuth {} marked as approved. Sending information to Providers", claimId);
            // if AI marked camunda approved preauth for clinical review
            String aiDecision = (String) variables.get(VAR_AI_DECISION);
            if (aiDecision != null && claim.getUse().equals(Claim.Use.PREAUTHORIZATION)) {
                Boolean isDoctorApprovalRequired = (Boolean) variables.get(VAR_IS_DOC_APPROVAL_REQ);
                Boolean isAutoApproved = (Boolean) variables.get(VAR_IS_AUTO_APPROVED);
                Boolean isCivilServiceApproved = (Boolean) variables.get(VAR_IS_CIVIL_SERVICE_APPROVED);
                Boolean isEwaRequireDoctorReview = (Boolean) variables.get(VAR_IS_EFWA_REQUIRE_DOCTOR_REVIEW);
                String aiScores = (String) variables.get("aiScores");
                String aiRemarks = (String) variables.get("aiRemarks");
                Decision decision = Decision.fromDisplayName(aiDecision);

                // Scenario : Preauth was auto-approved but AI flagged for clinical review.
                // Transition to CLINICAL_REVIEW state with AI as user and AI score as remarks.
                if (decision == Decision.CLINICAL_REVIEW
                        && Boolean.TRUE.equals(isAutoApproved)
                        && Boolean.TRUE.equals(isCivilServiceApproved)
                        && Boolean.FALSE.equals(isDoctorApprovalRequired)
                        && Boolean.TRUE.equals(isEwaRequireDoctorReview)) {
                    log.info(
                            "AI flagged clinical review for auto-approved preauth, transitioning to CLINICAL_REVIEW with AI user for claimId: {}",
                            claimId);
                    // Trigger preauth status change to clinical review based on AI decision;
                    aiTriggerStatusChange(claimId, aiScores, aiRemarks, ExtensionStateCodeEnum.CLINICAL_REVIEW, Claim.Use.PREAUTHORIZATION);
                    // Broadcast clinical review status update
                    return broadcastAIStatusChange(variables, ExtensionStateCodeEnum.CLINICAL_REVIEW, claim, claimResponseId);
                }
            }

            updateClaimAndResponseStatus(claimId, claim, claimResponseId, ExtensionStateCodeEnum.APPROVED, true, true);
            ClaimResponse claimResponse = fhirClient.read().resource(ClaimResponse.class).withId(claimResponseId)
                    .execute();
            claimsHandlerUtility.callClaimResponseWebhooks(claimResponse, claimId, ExtensionStateCodeEnum.APPROVED);
            variables.put(VAR_STATUS, VAR_STATUS_APPROVED);

            return variables;
        });
    }

    /**
     * Handle Claim Approved event so that all callbacks can be executed.
     *
     * @return
     */
    @JobWorker(type = HANDLE_CLAIM_APPROVED_EVENT)
    public Map<String, Object> handleClaimApprovedEvent(ActivatedJob job) {
        return AsyncUtils.withCamundaContext(job, () -> {
            String claimId = (String) job.getVariable(VAR_CLAIM_ID);
            String claimResponseId = (String) job.getVariable(VAR_CLAIM_RESPONSE_ID);
            log.info("Claim {} marked as approved. Sending information to Providers", claimId);
            ClaimResponse claimResponse = fhirClient.read().resource(ClaimResponse.class).withId(claimResponseId).execute();

            // Note : ERP receiver is updating the Status as Payment completed/declined hence not doing here
            // Mark it as approved.
            // claimResponse.setOutcome(ClaimResponse.RemittanceOutcome.COMPLETE);
            // claim.setStatus(Claim.ClaimStatus.ACTIVE);
            // updateClaimAndResponseStatus(claimId, claim, claimResponse, ExtensionStateCodeEnum.APPROVED);

            // This method is only use for call back
            claimsHandlerUtility.callClaimResponseWebhooks(claimResponse, claimId, ExtensionStateCodeEnum.APPROVED);
            Map<String, Object> variableMap = job.getVariablesAsMap();
            variableMap.put(VAR_STATUS, VAR_STATUS_APPROVED);
            return job.getVariablesAsMap();
        });
    }

    /**
     * Check if interventions in the preauth requires doctor approval.
     *
     * @return
     */
    @JobWorker(type = HANDLE_PRE_AUTH_RETURNED_EVENT)
    public Map<String, Object> handlePreAuthReturnedEvent(ActivatedJob job) {
        return AsyncUtils.withCamundaContext(job, () -> {
            String claimId = (String) job.getVariable(VAR_CLAIM_ID);
            String claimResponseId = (String) job.getVariable(VAR_CLAIM_RESPONSE_ID);
            Map<String, Object> variables = job.getVariablesAsMap();

            String aiDecision = (String) variables.get(VAR_AI_DECISION);
            Claim claim = fhirClient.read().resource(Claim.class).withId(claimId).execute();

            //Log process notes if preauth sent back to provider
            if (aiDecision != null && claim.getUse().equals(Claim.Use.PREAUTHORIZATION)) {
                String aiScores = (String) variables.get("aiScores");
                String aiRemarks = (String) variables.get("aiRemarks");
                aiTriggerStatusChange(claimId, aiScores, aiRemarks, ExtensionStateCodeEnum.SENT_BACK, Claim.Use.PREAUTHORIZATION);
            }

            log.info("Claim {} marked as ReturnToSender. Sending information to Providers", claimId);
            updateClaimAndResponseStatus(claimId, claim, claimResponseId, ExtensionStateCodeEnum.SENT_BACK, true, true);
            ClaimResponse claimResponse = fhirClient.read().resource(ClaimResponse.class).withId(claimResponseId)
                    .execute();
            claimsHandlerUtility.callClaimResponseWebhooks(claimResponse, claimId, ExtensionStateCodeEnum.SENT_BACK);
            return job.getVariablesAsMap();
        });
    }

    /**
     * handle Claim Return to sender.
     *
     * @return
     */
    @JobWorker(type = HANDLE_CLAIM_RETURNED_EVENT)
    public Map<String, Object> handleClaimReturnedEvent(ActivatedJob job) {
        return AsyncUtils.withCamundaContext(job, () -> {
            String claimId = (String) job.getVariable(VAR_CLAIM_ID);
            String claimResponseId = (String) job.getVariable(VAR_CLAIM_RESPONSE_ID);

            log.info("Claim {} marked as ReturnToSender. informing Providers now", claimId);
            Claim claim = fhirClient.read().resource(Claim.class).withId(claimId).execute();
            updateClaimAndResponseStatus(claimId, claim, claimResponseId, ExtensionStateCodeEnum.SENT_BACK, true, true);
            ClaimResponse claimResponse = fhirClient.read().resource(ClaimResponse.class).withId(claimResponseId).execute();
            claimsHandlerUtility.callClaimResponseWebhooks(claimResponse, claimId, ExtensionStateCodeEnum.SENT_BACK);
            return job.getVariablesAsMap();
        });
    }

    /**
     * Check if interventions in the preauth requires doctor approval.
     *
     * @return
     */
    @JobWorker(type = AUTO_ADJUDICATION_HANDLER)
    public Map<String, Object> autoAdjudicationProcess(ActivatedJob job) {
        return AsyncUtils.withCamundaContext(job, () ->
        {
            String claimId = (String) job.getVariable(VAR_CLAIM_ID);
            // Pass the current process instance key from the job:
            taskListService.deleteDuplicateProcessWorkflows(claimId, job.getProcessInstanceKey());
            return handlerService.autoAdjudicate(job);
        });
    }

    /**
     * Marking the PHC Claim as approved.
     *
     * @return
     */
    @JobWorker(type = HANDLE_PHC_CLAIM_APPROVED_EVENT)
    public Map<String, Object> handlePHCClaimApprovedEvent(ActivatedJob job) {
        return AsyncUtils.withCamundaContext(job, () -> {
            String claimId = (String) job.getVariable(VAR_CLAIM_ID);
            String claimResponseId = (String) job.getVariable(VAR_CLAIM_RESPONSE_ID);

            log.info("PHC Claims : {} marked as approved. Sending information to Providers", claimId);
            Claim claim = fhirClient.read().resource(Claim.class).withId(claimId).execute();
            updateClaimAndResponseStatus(claimId, claim, claimResponseId, ExtensionStateCodeEnum.APPROVED, true, true);
            ClaimResponse claimResponse = fhirClient.read().resource(ClaimResponse.class).withId(claimResponseId).execute();
            claimsHandlerUtility.callClaimResponseWebhooks(claimResponse, claimId, ExtensionStateCodeEnum.APPROVED);
            Map<String, Object> variableMap = job.getVariablesAsMap();
            variableMap.put(VAR_STATUS, VAR_STATUS_APPROVED);
            return job.getVariablesAsMap();
        });
    }

    /**
     * Submitting claims to Livia.
     *
     * @param job
     * @return
     */
    @JobWorker(type = CIVIL_SERVICE_HANDLER)
    public Map<String, Object> civilServiceHandler(ActivatedJob job) {
        return AsyncUtils.withCamundaContext(job, () -> {
            String claimId = (String) job.getVariable(VAR_CLAIM_ID);
            log.info("Claim :  {} , [civilServiceHandler] marked as approved. informing Providers now", claimId);
            return Map.of(VAR_IS_CIVIL_SERVICE_APPROVED, job.getVariable(VAR_IS_CIVIL_SERVICE_APPROVED));
        });
    }

    /**
     * Marking claim to Approved before sending it to Payment Processing to ERP.
     * @return
     */
    @JobWorker(type = START_PAYMENT_TO_ERP)
    public Map<String, Object> startPaymentToERP(ActivatedJob job) {
        return AsyncUtils.withCamundaContext(job, () -> {
            log.info("Marking ClaimId: {} as Approved before ERP", job.getVariable(VAR_CLAIM_ID));
            // Mark as Approved.
            String claimId = (String) job.getVariable(VAR_CLAIM_ID);
            String claimResponseId = (String) job.getVariable(VAR_CLAIM_RESPONSE_ID);
            Claim claim = fhirClient.read().resource(Claim.class).withId(claimId).execute();
            updateClaimAndResponseStatus(claimId, claim, claimResponseId, ExtensionStateCodeEnum.APPROVED, false, false);
            ClaimResponse claimResponse = fhirClient.read().resource(ClaimResponse.class).withId(claimResponseId).execute();
            claimsHandlerUtility.callClaimResponseWebhooks(claimResponse, claimId, ExtensionStateCodeEnum.APPROVED);
            return job.getVariablesAsMap();
        });
    }



    @JobWorker(type = AI_NOTES_REQUEST)
    public Map<String, Object> handleAINote(ActivatedJob job) {
        User aiUser = aiUserService.getAiUser();
        HealthIdContextHolder.userId(aiUser.getUsername());
        return AsyncUtils.withCamundaContext(job, () -> {
            String claimId = (String) job.getVariable(VAR_CLAIM_ID);
            String bundleId = insuranceClaimsService.getBundleId(claimId);
            log.info("Initiating AI Engine Process Request for claimId: {}", claimId);
            Bundle bundle = fhirClient.read().resource(Bundle.class).withId(bundleId).execute();
            log.info("Processing AI Note analysis for ClaimID: {}", claimId);
            aiNotesService.sendClaimToAIEngine(bundle, UUID.fromString(claimId));
            return job.getVariablesAsMap();
        });
    }

    @JobWorker(type = SEND_PREAUTH_EFWA_REQUEST)
    public Map<String, Object> sendPreAuthEfwaRequest(ActivatedJob job) {
        User aiUser = aiUserService.getAiUser();
        HealthIdContextHolder.userId(aiUser.getUsername());
        return AsyncUtils.withCamundaContext(job, () -> {
            String claimId = (String) job.getVariable(VAR_CLAIM_ID);
            String bundleId = insuranceClaimsService.getBundleId(claimId);
            log.info("Initiating PreAuth AI Engine Process Request for claimId: {}", claimId);

            log.info("found bundle id {}:", bundleId);

            // Using FHIR Client to fetch bundle as typical in this worker class
            Bundle bundle = fhirClient.read().resource(Bundle.class).withId(bundleId).execute();

            log.info("Sending PreAuth Bundle to AI Engine for ClaimID: {}", claimId);
            preAuthAINotesService.sendPreAuthToAIEngine(bundle, UUID.fromString(claimId));

            return job.getVariablesAsMap();
        });
    }

    /**
     * Start Event of Claim assigned to Manual Review.
     * @return
     */
    @JobWorker(type = CLAIM_ASSIGNED_TO_MANUAL_REVIEW_START_EVENT)
    public Map<String, Object> claimAssignedToManualReviewStartEvent(ActivatedJob job) {
        return AsyncUtils.withCamundaContext(job, () -> {
            log.info("claimAssignedToManualReviewStartEvent : triggered for ClaimId :{}", job.getVariable(VAR_CLAIM_ID));
            String claimId = (String) job.getVariable(VAR_CLAIM_ID);
            String claimResponseId = (String) job.getVariable(VAR_CLAIM_RESPONSE_ID);
            Map<String, Object> variables = job.getVariablesAsMap();
            Claim claim = fhirClient.read().resource(Claim.class).withId(claimId).execute();

            // Scenario : Claim was sent for manual review but AI flagged for approval.
            // Transition claim through Manual Review → Approved → SENT_FOR_PAYMENT_PROCESSING
            // with AI as user and AI score as remarks
            String aiDecision = (String) variables.get(VAR_AI_DECISION);
            if (aiDecision != null && claim.getUse().equals(Claim.Use.CLAIM)) {
                Decision decision = Decision.fromDisplayName(aiDecision);
                Boolean isAiApprovedClaim = Boolean.TRUE.equals(variables.get("isAiApprovedClaim"));
                if (decision == Decision.APPROVE && isAiApprovedClaim) {
                    Boolean isAutoApproved = (Boolean) variables.get(VAR_IS_AUTO_APPROVED);
                    Boolean isCivilServiceApproved = (Boolean) variables.get(VAR_IS_CIVIL_SERVICE_APPROVED);

                    if (Boolean.TRUE.equals(isAutoApproved)
                            && Boolean.TRUE.equals(isCivilServiceApproved)) {
                        log.info("AI approved claim routed to manual review, auto-approving with AI user for claimId: {}",
                                claimId);
                        String aiScores = (String) variables.get("aiScores");
                        String aiRemarks = (String) variables.get("aiRemarks");
                        // Trigger claim status change to Approved based on AI decision;
                        aiTriggerStatusChange(claimId, aiScores, aiRemarks, ExtensionStateCodeEnum.SENT_FOR_PAYMENT_PROCESSING, Claim.Use.CLAIM);
                        // Publish ManualReviewAction to complete the catch event after the listener finishes.
                        // TTL ensures the message is buffered until the subscription activates.
                        zeebe.newPublishMessageCommand()
                                .messageName("ManualReviewAction")
                                .correlationKey(claimId)
                                .variables(Map.of(VAR_CLAIM_ID, claimId, VAR_IS_MANUAL_APPROVE, true))
                                .timeToLive(Duration.ofSeconds(30))
                                .send().join();
                        log.info("Published ManualReviewAction with isManualApproved=true for AI auto-approved claimId: {}", claimId);
                        // Broadcast approved status update
                        return broadcastAIStatusChange(variables, ExtensionStateCodeEnum.APPROVED, claim, claimResponseId);
                    }
                }
            }

            ExtensionStateCodeEnum status = ExtensionStateCodeEnum.MANUAL_REVIEW;
            log.info("Marking ClaimID {} and ClaimResponseId {} as {}", claimId, claimResponseId, status);
            updateClaimAndResponseStatus(claimId, claim, claimResponseId, status, false, false);

            return variables;
        });
    }

    @JobWorker(type = MISSING_DOC_CALL_BACK)
    public Map<String, Object> handleMissingDocCallBack(ActivatedJob job) {
        return AsyncUtils.withCamundaContext(job, () -> {
            String claimId = (String) job.getVariable(VAR_CLAIM_ID);
            String claimResponseId = (String) job.getVariable(VAR_CLAIM_RESPONSE_ID);
            log.info("Claim {} marked as MISSING_DOCUMENTS. Sending information to Providers", claimId);
            ClaimResponse claimResponse = fhirClient.read().resource(ClaimResponse.class).withId(claimResponseId).execute();
            claimsHandlerUtility.callClaimResponseWebhooks(claimResponse, claimId, ExtensionStateCodeEnum.MISSING_DOCUMENTS);
            return job.getVariablesAsMap();
        });
    }

    @JobWorker(type = REJECTED_CLAIM_CALL_BACK)
    public Map<String, Object> handleRejectedClaimCallBack(ActivatedJob job) {
        return AsyncUtils.withCamundaContext(job, () -> {
            String claimId = (String) job.getVariable(VAR_CLAIM_ID);
            String claimResponseId = (String) job.getVariable(VAR_CLAIM_RESPONSE_ID);
            log.info("Claim {} marked as REJECTED. Sending information to Providers", claimId);
            ClaimResponse claimResponse = fhirClient.read().resource(ClaimResponse.class).withId(claimResponseId).execute();
            claimsHandlerUtility.callClaimResponseWebhooks(claimResponse, claimId, ExtensionStateCodeEnum.REJECTED);
            return job.getVariablesAsMap();
        });
    }

    /**
     * Check EFWA eligibility for the claim. Sets isEfwaEnabled variable.
     */
    @JobWorker(type = CHECK_EFWA_ELIGIBILITY_HANDLER)
    public Map<String, Object> checkEfwaEligibility(ActivatedJob job) {
        return AsyncUtils.withCamundaContext(job, () -> {
            String claimId = (String) job.getVariable(VAR_CLAIM_ID);
            log.info("Checking EFWA eligibility for claimId: {}", claimId);
            Map<String, Object> variables = job.getVariablesAsMap();
            handlerService.checkEfwaEligibility(claimId, variables);
            return variables;
        });
    }

    /**
     * handle Claim Return to sender.
     *
     */
    @JobWorker(type = HANDLE_CLAIM_REJECTED_BY_RULE)
    public Map<String, Object> handleClaimRejectedRuleEvent(ActivatedJob job) {
        return getStringObjectMap(job);
    }

    private Map<String, Object> getStringObjectMap(ActivatedJob job) {
        return AsyncUtils.withCamundaContext(job, () -> {
            String claimId = (String) job.getVariable(VAR_CLAIM_ID);
            String claimResponseId = (String) job.getVariable(VAR_CLAIM_RESPONSE_ID);
            log.info("Claim {} marked as Rejected", claimId);
            Claim claim = fhirClient.read().resource(Claim.class).withId(claimId).execute();
            syncClaimAndResponseStatus(claimId, claim, claimResponseId, true, true);
            ClaimResponse claimResponse = fhirClient.read().resource(ClaimResponse.class).withId(claimResponseId).execute();
            claimsHandlerUtility.callClaimResponseWebhooks(claimResponse, claimId, ExtensionStateCodeEnum.REJECTED);
            return job.getVariablesAsMap();
        });
    }

    /**
     * Bypassing the stage of IN_REVIEW for PreAuth for both Doc Rejection and Rule Engine Rejection.
     * And the Bulk rejection of claims.
     * @param claim the claim object to process
     * @param variableMap a map of variables relevant to the claim processing workflow
     * @param processInstanceKey the unique key of the process instance being handled
     */
    private void processClaimTask(Claim claim, Map<String, Object> variableMap, long processInstanceKey) {
        String claimId = claim.getIdPart();
        Claim.Use use = claim.getUse();

        boolean isPreAuth = Claim.Use.PREAUTHORIZATION.equals(use);
        boolean isClaim = Claim.Use.CLAIM.equals(use);

        boolean rejectedByDoc = isRejectedByDoc(variableMap);
        boolean rejectedByRule = isRejectedByRuleEngine(variableMap);

        if (isPreAuth && rejectedByDoc) {
            log.info("Completing User Tasks After doctor rejection preAuth claimId :{} , processInstanceKey : {} ", claim.getIdPart(), processInstanceKey);
            String notes = (String) variableMap.get(VAR_NOTES);
            //EPORTAL-1445
            handleLiviaPreAuthCancellation(claim, claimId);
            claimResponseService.processPreAuth(claimId, AdjudicateRequestBody.builder()
                    .action(ExtensionStateCodeEnum.SENT_BACK)
                    .notes("Sent back with Clinical Review comments : " + notes)
                    .build(), true);
            return;
        }

        if (isClaim && rejectedByRule) {
            log.info("Completing User Tasks After Rejected By Rule Engine claimId : {}, processInstanceKey : {} ", claim.getIdPart(), processInstanceKey);
            claimResponseService.processClaim(claimId, AdjudicateRequestBody.builder()
                    .action(ExtensionStateCodeEnum.REJECTED)
                    .notes(findReason(claim))
                    .build(), true, false);
        }

    }

    /**
     * Finds and constructs a reason for given claim items by analyzing them using the rule engine.
     * @param claim the claim for which the reason needs to be determined
     * @return a concatenated string representing the reason for the claim
     */
    private String findReason(Claim claim) {
        StringBuilder reason = new StringBuilder();
        try {
            Optional<List<ClaimLineItem>> lineItems = lineItemRepository.findByClaimId(UUID.fromString(claim.getIdPart()));

            if (lineItems.isPresent()) {
                List<ClaimHistoryDTO> response = lineItems.get().stream()
                        .map(memberService::getHistoryResponse)
                        .toList();

                response.forEach(dto -> extractReasons(dto, reason));
            }
        } catch (Exception e) {
            log.warn("Error while fetching reason for claimId: {} - {}", claim.getIdPart(), e.getMessage());
        }
        return reason.toString();
    }

    /**
     * Extracts reasons from a provided ClaimHistoryDTO object and appends them to a StringBuilder.
     * Reasons are retrieved from either the "reason" field or from adjudication parameters.
     * If adjudication parameters are present, the method iterates through each, extracting
     * valid reasons and appends them to the provided StringBuilder.
     *
     * @param dto the ClaimHistoryDTO object containing claim details and possible reasons
     * @param reason the StringBuilder to which the extracted reasons will be appended
     */
    private void extractReasons(ClaimHistoryDTO dto, StringBuilder reason) {
        if (StringUtils.hasText(dto.getReason())) {
            appendReason(reason, dto.getReason());
        } else if (Objects.nonNull(dto.getAdjudicationParams())) {
            try {
                Map<String, RuleInputDTO> resultArray = (Map<String, RuleInputDTO>) dto.getAdjudicationParams();
                resultArray.values().stream()
                        .map(RuleInputDTO::getReason)
                        .filter(StringUtils::hasText)
                        .forEach(r -> appendReason(reason, r));
            } catch (Exception e) {
                log.warn("Error extracting adjudication reasons: {}", e.getMessage(), e);
            }
        }
    }

    private void appendReason(StringBuilder reason, String newReason) {
        if (!newReason.isBlank()) {
            if (reason.length() > 0) {
                reason.append(", ");
            }
            reason.append(newReason);
        }
    }

    /**
     * This method is checking for doctor rejection in CLINICAL_REVIEW state.
     *
     * @param variableMap
     * @return
     */
    private boolean isRejectedByDoc(Map<String, Object> variableMap) {
        if (variableMap.get(VAR_IS_APPROVED_BY_DOC) != null && !(boolean) variableMap.get(VAR_IS_APPROVED_BY_DOC)) {
            return true;
        }
        return false;
    }

    /**
     * This method is checking for rejection from Rule Engine IN_REVIEW state.
     *
     * @param variableMap
     * @return
     */
    private boolean isRejectedByRuleEngine(Map<String, Object> variableMap) {
        return variableMap.get(VAR_IS_AUTO_APPROVED) != null && !(boolean) variableMap.get(VAR_IS_AUTO_APPROVED);
    }

    /**
     * @return
     */
    @JobWorker(type = PRE_AUTH_CIVIL_HANDLER)
    public Map<String, Object> preAuthCivilHandler(ActivatedJob job) {
        return job.getVariablesAsMap();
    }

    /**
     * Update the Status of given Claim and Claim Response objects.
     * @param claimId
     * @param claim
     * @param claimResponseId
     * @param status
     */
    private void updateClaimAndResponseStatus(String claimId, Claim claim, String claimResponseId, ExtensionStateCodeEnum status, boolean claimActive, boolean claimResponseCompleted) {
        if (!StringUtils.hasText(claimId)) {
            log.warn("Claim ID is null or empty. Cannot proceed with update.");
            return;
        }

        if (claim == null) {
            log.warn("Claim is null for ClaimID {}. Nothing to update.", claimId);
            return;
        }

        log.info("Updating status for ClaimID: {}, ClaimResponseId: {}, New Status: {}", claimId, claimResponseId, status.getDisplayName());

            if (claimActive) {
                claim.setStatus(Claim.ClaimStatus.ACTIVE);
            }
            updateFhirResource(claim, claimId, status, "Claim", claimActive, claimResponseCompleted);
            ClaimResponse claimResponse = fhirClient.read().resource(ClaimResponse.class).withId(extractId(claimResponseId)).execute();
            if (claimResponse == null) {
                log.warn("ClaimResponse is null for claimResponseId {}. Nothing to update.", claimResponseId);
                return;
            }
            if (claimResponseCompleted) {
                claimResponse.setOutcome(ClaimResponse.RemittanceOutcome.COMPLETE);
            }
            updateFhirResource(claimResponse, claimResponseId, status, "ClaimResponse", claimActive, claimResponseCompleted);

            if (insuranceClaimsService.existsByExternalId(claimId)) {
                insuranceClaimsService.updateClaimsStatus(claimId, status);
                log.info("Database [claimsTb] successfully updated for ClaimID: {}", claimId);
            } else {
                log.warn("ClaimID {} not found in the database. Skipping DB update.", claimId);
            }

            statusUpdateService.triggerCallBackStatusUpdate(claimId, true, true, false);
    }

    private void updateFhirResource(DomainResource resource, String claimId, ExtensionStateCodeEnum status, String resourceType, boolean claimActive, boolean claimResponseCompleted) {
        if (resource == null) return;
        try {
            log.info("Updating ResourceType: {}, ID: '{}', Current Version: {}",
                    resourceType, claimId, resource.getMeta().getVersionId());

            extentionService.updateState(resource, status);
            MethodOutcome outcome = fhirClient.update().resource(resource).execute();

            log.info("{} update successful. ID: {}, New Version: {}",
                    resourceType, claimId, outcome.getId().getVersionIdPart());

        } catch (BaseServerResponseException e) {
            if (e.getStatusCode() == 409) {
                log.warn("{} version conflict for ID {}. Fetching latest version...", resourceType, claimId);

                DomainResource latestResource = fetchLatestResource(claimId, resourceType);

                if (latestResource != null) {
                    extentionService.updateState(latestResource, status);
                    MethodOutcome retryOutcome = null;
                    if (latestResource instanceof Claim claim) {
                        if (claimActive) {
                            claim.setStatus(Claim.ClaimStatus.ACTIVE);
                        }
                        retryOutcome = fhirClient.update().resource(claim).execute();
                    } else if (latestResource instanceof ClaimResponse claimResponse) {
                        if (claimResponseCompleted) {
                            claimResponse.setOutcome(ClaimResponse.RemittanceOutcome.COMPLETE);
                        }
                        retryOutcome = fhirClient.update().resource(claimResponse).execute();
                    }
                    log.info("Retry update successful for {}. ID: {}, New Version: {}",
                            resourceType, claimId, retryOutcome.getId().getVersionIdPart());
                }
            } else {
                log.error("Error updating {} for ID {}: status {}, {}", resourceType, claimId, status, e.getMessage(), e);
            }
        }
    }

    private DomainResource fetchLatestResource(String claimId, String resourceType) {
        try {
            return switch (resourceType) {
                case "Claim" -> fhirClient.read().resource(Claim.class).withId(claimId).execute();
                case "ClaimResponse" -> fhirClient.read().resource(ClaimResponse.class).withId(claimId).execute();
                default -> {
                    log.error("Unsupported resource type: {}", resourceType);
                    yield null;
                }
            };
        } catch (ResourceNotFoundException e) {
            log.error("Resource not found for type: {} and ID: {}", resourceType, claimId);
            throw new BadRequestException(e.getMessage());
        }
    }

    private String extractId(String value) {

        log.info("extractId() → Input value: {}", value);

        if (value == null || value.isBlank()) {
            log.warn("extractId() → Input is null or blank, returning null");
            return null;
        }

        // Case: Direct UUID (no slash)
        if (!value.contains("/")) {
            log.info("extractId() → Detected plain ID (no URL). Returning: {}", value);
            return value;
        }

        // Split by slash
        String[] parts = value.split("/");
        log.info("extractId() → Split parts: {}", Arrays.toString(parts));

        // Check for history
        for (int i = 0; i < parts.length; i++) {
            if ("_history".equals(parts[i]) && i > 0) {
                String historyId = parts[i - 1];
                log.info("extractId() → Found _history at index {}. Extracted ID: {}", i, historyId);
                return historyId;
            }
        }
        // No history, return last segment
        String lastPart = parts[parts.length - 1];
        log.info("extractId() → No _history found. Using last segment: {}", lastPart);

        return lastPart;
    }

    /**
     * Trigger AI-driven status change for both PreAuth and Claim workflows.
     * @param claimId
     * @param aiScores
     * @param aiRemarks
     * @param status
     * @param use Claim.Use.PREAUTHORIZATION or Claim.Use.CLAIM
     */
    private void aiTriggerStatusChange(String claimId, String aiScores, String aiRemarks, ExtensionStateCodeEnum status, Claim.Use use) {

        User aiUser = aiUserService.getAiUser();
        HealthIdContextHolder.userId(aiUser.getUsername());

        String notes = "Sent to " + status  + " by AI. "+ (aiScores != null ? "AI Score: " + aiScores : "")
                + (aiRemarks != null ? " Remarks: " + aiRemarks : "");
        AdjudicateRequestBody body = AdjudicateRequestBody.builder()
                .action(status)
                .notes(notes)
                .build();

        if (use == Claim.Use.PREAUTHORIZATION) {
            claimResponseService.processPreAuth(claimId, body, true, true);
        } else {
            claimResponseService.processClaim(claimId, body, true, true);
        }
    }

    /**
     * This method broadcasts AI status change
     * @param variables
     * @param claim
     * @param claimResponseId
     * @return variables
     */

    private Map<String, Object> broadcastAIStatusChange(Map<String, Object> variables, ExtensionStateCodeEnum status,
                                                      Claim claim,  String claimResponseId)
    {

        log.info("---broadcasting AI status change for claim id--- {}", claim.getIdPart());

        boolean fhirFlag = ExtensionStateCodeEnum.APPROVED == status;
        updateClaimAndResponseStatus(claim.getIdPart(), claim, claimResponseId, status, fhirFlag, fhirFlag);

        ClaimResponse claimResponse = fhirClient.read().resource(ClaimResponse.class)
                    .withId(claimResponseId).execute();
        claimsHandlerUtility.callClaimResponseWebhooks(claimResponse, claim.getIdPart(), status);

        variables.put(VAR_STATUS, status.getDisplayName());
        return variables;
    }


    /**
     * Handles Livia (PMF) pre-authorization cancellation.
     *
     * <p>This method performs the following checks before triggering the external
     * Livia cancellation API:
     *
     * <ul>
     *   <li>Verifies that the claim is associated with the PMF insurer.</li>
     *   <li>Ensures the claim contains at least one valid PMF line item with a positive amount.</li>
     * </ul>
     *
     * <p>If both conditions are satisfied, the method invokes the Livia integration
     * service to cancel the pre-authorization using the claim ID as the reference number.
     *
     * <p>This method is intentionally guarded with early returns to avoid unnecessary
     * database lookups or external API calls for non-PMF claims.
     *
     * @param claim   the FHIR claim object containing insurance information
     * @param claimId the internal claim identifier used as the external reference
     */
    public void handleLiviaPreAuthCancellation(Claim claim, String claimId) {
      log.info("Preauthorization cancellation detected for claimId: {}.", claimId);

        boolean isLivia = claim.hasInsurance() && claim.getInsurance().stream()
                        .anyMatch(insurance ->
                                insurance.getCoverage() != null &&
                                        insurance.getCoverage().getReference() != null &&
                                        insurance.getCoverage().getReference().toLowerCase().contains(PMF));

        if (!isLivia) {
            return;
        }

        List<ClaimLineItem> items = insuranceClaimsService.getClaimItems(claimId);

        boolean hasValidPmfItem = items
                .stream()
                .anyMatch(item -> item.isPmfItem() &&
                        item.getPmfAmount() != null &&
                        item.getPmfAmount().compareTo(BigDecimal.ZERO) > 0);

        if (!hasValidPmfItem) {
            return;
        }
        String cancellationReason = "Preauthorization rejected by SHA after clinical/medical review";
        liviaClaimService.cancelLiviaPreAuth(claimId, claimId, cancellationReason);
    }

    /**
     * Update the Status of given Claim and ClaimResponse resources.
     *
     * @param claimId           claim identifier
     * @param claim             Claim resource
     * @param claimResponseId   ClaimResponse identifier
     */
    private void syncClaimAndResponseStatus(String claimId,
                                            Claim claim,
                                            String claimResponseId,
                                            boolean isActiveClaim, boolean isClaimResponseCompleted) {

        final String CLAIM_RESOURCE = "Claim";
        final String CLAIM_RESPONSE_RESOURCE = "ClaimResponse";

        if (!StringUtils.hasText(claimId)) {
            log.warn("syncClaimAndResponseStatus skipped: claimId is null or empty");
            return;
        }

        if (claim == null) {
            log.warn("syncClaimAndResponseStatus skipped: Claim resource is null for claimId={}", claimId);
            return;
        }

        log.info("Syncing REJECTED status for claimId={}, claimResponseId={}", claimId, claimResponseId);

    /*
     -------------------------------------------------
     Update Claim Resource
     -------------------------------------------------
     */
        if (isActiveClaim) {
            claim.setStatus(Claim.ClaimStatus.ACTIVE);
        }
        updateFhirResource(claim, claimId, ExtensionStateCodeEnum.REJECTED, CLAIM_RESOURCE, true, true);

    /*
     -------------------------------------------------
     Load ClaimResponse
     -------------------------------------------------
     */
        ClaimResponse claimResponse = fhirClient.read()
                .resource(ClaimResponse.class)
                .withId(extractId(claimResponseId))
                .execute();


        if (claimResponse == null) {
            log.warn("ClaimResponse not found for claimResponseId={}", claimResponseId);
            return;
        }

    /*
     -------------------------------------------------
     Update ClaimResponse Resource
     -------------------------------------------------
     */
        if (isClaimResponseCompleted) {
            claimResponse.setOutcome(ClaimResponse.RemittanceOutcome.ERROR);
        }

        updateFhirResource(claimResponse, claimResponseId, ExtensionStateCodeEnum.REJECTED, CLAIM_RESPONSE_RESOURCE, true, true);

    /*
     -------------------------------------------------
     Update Database
     -------------------------------------------------
     */
        if (insuranceClaimsService.existsByExternalId(claimId)) {
            insuranceClaimsService.updateClaimsStatus(claimId, ExtensionStateCodeEnum.REJECTED);
            log.info("Claim status updated in database for claimId={}", claimId);
        } else {
            log.warn("Claim not found in database for claimId={}", claimId);
        }
    /*
     -------------------------------------------------
     Trigger Status Callback
     -------------------------------------------------
     */
        statusUpdateService.triggerCallBackStatusUpdate(claimId, true, true, false);
        log.info("Status synchronization completed for claimId={}", claimId);
    }
}
