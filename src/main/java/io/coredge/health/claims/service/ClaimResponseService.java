package io.coredge.health.claims.service;

import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import com.google.gson.Gson;
import io.coredge.health.claims.audit.ClaimAudit;
import io.coredge.health.claims.audit.ClaimAuditRepository;
import io.coredge.health.claims.audit.ClaimAuditReprocessService;
import io.coredge.health.claims.constants.Constants;
import io.coredge.health.claims.context.HealthIdContextHolder;
import io.coredge.health.claims.db.entity.ClaimLineItem;
import io.coredge.health.claims.db.entity.InsuranceClaim;
import io.coredge.health.claims.db.entity.ProcessNotes;
import io.coredge.health.claims.db.repositories.ClaimBulkSendBackRepository;
import io.coredge.health.claims.db.repositories.InsuranceClaimsRepository;
import io.coredge.health.claims.db.repositories.LineItemRepository;
import io.coredge.health.claims.db.repositories.ProcessNotesRepository;
import io.coredge.health.claims.dto.AuditUpdateRequest;
import io.coredge.health.claims.dto.ProcessNotesAuditInfo;
import io.coredge.health.claims.enums.BulkAction;
import io.coredge.health.claims.enums.ClaimType;
import io.coredge.health.claims.enums.ExtensionStateCodeEnum;
import io.coredge.health.claims.enums.SendbackStatus;
import io.coredge.health.claims.exception.app.BadRequestException;
import io.coredge.health.claims.exception.base.DataBadRequestException;
import io.coredge.health.claims.exception.models.ErrorCode;
import io.coredge.health.claims.models.ClaimBulkSendBackLogs;
import io.coredge.health.claims.models.ErpReprocessListClaimsRequest;
import io.coredge.health.claims.models.PreAuthCancelAuditLog;
import io.coredge.health.claims.models.claims.*;
import io.coredge.health.claims.utils.ClaimsHandlerUtility;
import io.coredge.health.claims.utils.DateUtils;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import static io.coredge.health.claims.constants.Constants.CLAIM_APPROVED_EXTENSION_STATES;
import static io.coredge.health.claims.constants.Constants.WorkflowParams.*;
import static io.coredge.health.claims.enums.ExtensionStateCodeEnum.CANCELED;
import static io.coredge.health.claims.enums.ExtensionStateCodeEnum.PARTIAL_APPROVAL;

@Service
@Slf4j
public class ClaimResponseService {

    @Autowired
    private IGenericClient fhirClient;

    @Autowired
    private TaskListService taskListService;

    @Autowired
    private PostOfficeService postOfficeService;

    @Autowired
    private ClaimExtensionService extentionService;

    @Autowired
    private UserAssignmentService userAssignmentService;

    @Autowired
    private ProcessNotesService processNotesService;

    @Autowired
    private InsuranceClaimsRepository claimsRepository;

    @Value("${claimRecallTimeLimit}")
    private Integer claimRecallTimeLimitHours;

    @Autowired
    private ClaimBulkSendBackRepository claimBulkSendBackRepository;

    @Autowired
    private StatusUpdateService statusUpdateService;

    @Autowired
    private LineItemRepository lineItemRepository;

    @Autowired
    private InsuranceClaimsService insuranceClaimsService;

    @Autowired
    private ClaimsHandlerUtility claimsHandlerUtility;

    @Autowired
    private PerAuthCancelAuditService perAuthCancelAuditService;

    @Autowired
    private ProcessNotesRepository processNotesRepository;

    @Autowired
    private SurveillanceService surveillanceService;

    @Autowired
    private ClaimAuditReprocessService claimAuditReprocessService;

    @Autowired
    private LiviaClaimService liviaClaimService;

    @Autowired
    private io.coredge.health.claims.config.properties.ApplicationProperties.Livia liviaProperties;

    @Autowired
    private ClaimAuditRepository claimAuditRepository;

    @Value("${surveillance.effective.date}")
    private String effectiveDate;

    private static final int BATCH_SIZE = 300;

    /**
     * This method will complete the PreAuth user tasks.
     *
     * @param claimId
     * @param requestBody
     * @return
     * @throws Exception
     */
    public List<ClaimResponse.NoteComponent> processPreAuth(String claimId, AdjudicateRequestBody requestBody,
                                                            boolean processUserTask) {
        return processPreAuth(claimId, requestBody, processUserTask, false);
    }

    public List<ClaimResponse.NoteComponent> processPreAuth(String claimId, AdjudicateRequestBody requestBody,
                                                            boolean processUserTask, boolean isAiTriggered) {
        // Fetch the ClaimResponse by claimId
        InsuranceClaim iClaim = claimsRepository.findByClaimId(UUID.fromString(claimId)).get().getFirst();
        ClaimResponse claimResponse = postOfficeService.getClaimResponseByClaimId(claimId, iClaim.getClaimResponseId());
        Claim claim = fhirClient.read().resource(Claim.class).withId(claimId).execute();
        if (claimResponse == null || claim == null) {
            throw new DataBadRequestException(ErrorCode.BAD_REQUEST);
        }
        return processClaimResponsePreAuth(claimId, claim, claimResponse, requestBody, processUserTask, isAiTriggered);
    }

    private List<ClaimResponse.NoteComponent> processClaimResponsePreAuth(String claimId, Claim claim,
                                                                          ClaimResponse claimResponse, AdjudicateRequestBody requestBody, boolean processUserTask) {
        return processClaimResponsePreAuth(claimId, claim, claimResponse, requestBody, processUserTask, false);
    }

    private List<ClaimResponse.NoteComponent> processClaimResponsePreAuth(String claimId, Claim claim,
                                                                          ClaimResponse claimResponse, AdjudicateRequestBody requestBody, boolean processUserTask,
                                                                          boolean isAiTriggered) {
        boolean isClinicalReviewState = extentionService.verifyState(claimResponse, ExtensionStateCodeEnum.CLINICAL_REVIEW);
        if (processUserTask && !isClinicalReviewState && !isAiTriggered) {
            log.warn("Clinical-Review/In-Review state rules failed.");
            throw new DataBadRequestException(ErrorCode.BAD_REQUEST);
        }
        // Determine state and mapping values based on the conditions
        ExtensionStateCodeEnum status = requestBody.getAction();
        String variableKey = null;
        Boolean variableValue = null;
        String messageName = null;

        if (isClinicalReviewState && (status == ExtensionStateCodeEnum.APPROVED || status == ExtensionStateCodeEnum.SENT_BACK)) {
            variableKey = VAR_IS_APPROVED_BY_DOC;
            variableValue = (status == ExtensionStateCodeEnum.APPROVED);
            messageName = Constants.BPMN_USER_TASK_MESSAGE.DOCTOR_REVIEW;
        }

        // Add process notes and complete the user task
        List<ClaimResponse.NoteComponent> processNotes = addProcessNotes(claim, claimResponse, requestBody.getNotes(), status);
        if (processUserTask && (!isAiTriggered || isClinicalReviewState))
            completeUserTask(claimId, requestBody.getNotes(), variableKey,messageName, Boolean.TRUE.equals(variableValue), status);
        updateClaimsStatusAndBuckets(claim.getIdPart(), status, false);
        processNotesService.saveProcessNotes(claim.getIdPart(), status, requestBody.getNotes());
        return processNotes;
    }

    /**
     * This method will complete the Claims user tasks.
     *
     * @param claimId
     * @param requestBody
     * @return
     * @throws Exception
     */
    public List<ClaimResponse.NoteComponent> processClaim(String claimId, AdjudicateRequestBody requestBody,
            boolean processUserTask, boolean isAiTriggered) {
        // Fetch the ClaimResponse by claimId
        InsuranceClaim iClaim = claimsRepository.findByClaimId(UUID.fromString(claimId)).get().getFirst();
        ClaimResponse claimResponse = postOfficeService.getClaimResponseByClaimId(claimId, iClaim.getClaimResponseId());
        Claim claim = fhirClient.read().resource(Claim.class).withId(claimId).execute();
        if (claimResponse == null || claim == null) {
            throw new DataBadRequestException(ErrorCode.BAD_REQUEST);
        }
        return processClaimResponse(claimId, claim, claimResponse, requestBody, processUserTask, isAiTriggered);
    }

    private List<ClaimResponse.NoteComponent> processClaimResponse(String claimId, Claim claim, ClaimResponse claimResponse,
            AdjudicateRequestBody requestBody, boolean processUserTask, boolean isAiTriggered) {

        boolean isMedicalReview = extentionService.verifyState(claimResponse, ExtensionStateCodeEnum.MEDICAL_REVIEW);
        boolean isDeclined = extentionService.verifyState(claimResponse, ExtensionStateCodeEnum.DECLINE);

        if (processUserTask && !isMedicalReview && !isDeclined && !isAiTriggered) {
            log.warn("Failed to meet Medical-Review/Declined state requirements.");
            throw new DataBadRequestException(ErrorCode.BAD_REQUEST, "Failed to meet Medical-Review/Declined state requirements.");
        }

        // Determine state and mapping values based on the conditions
        ExtensionStateCodeEnum status = requestBody.getAction();
        String variableKey = null;
        Boolean variableValue = null;
        String messageName = null;

        if (isDeclined && (status == ExtensionStateCodeEnum.SENT_BACK || status == ExtensionStateCodeEnum.REJECTED)) {
            variableKey = VAR_IS_RETURN_TO_SENDER;
            variableValue = (status == ExtensionStateCodeEnum.SENT_BACK);
            messageName = Constants.BPMN_USER_TASK_MESSAGE.DECLINE_REVIEW;
        } else if (isMedicalReview && (status == ExtensionStateCodeEnum.SENT_FOR_PAYMENT_PROCESSING || status == ExtensionStateCodeEnum.DECLINE)) {
            variableKey = VAR_IS_APPROVED_BY_DOC;
            variableValue = (status == ExtensionStateCodeEnum.SENT_FOR_PAYMENT_PROCESSING);
            messageName = Constants.BPMN_USER_TASK_MESSAGE.DOCTOR_REVIEW;
        }
        if (status == ExtensionStateCodeEnum.SENT_FOR_PAYMENT_PROCESSING)
            status = ExtensionStateCodeEnum.APPROVED;

        // Add process notes and complete the user task
        List<ClaimResponse.NoteComponent> processNotes = addProcessNotes(claim, claimResponse, requestBody.getNotes(), status);
        if (processUserTask && (!isAiTriggered || isMedicalReview)) {
            completeUserTask(claimId, requestBody.getNotes(), variableKey,messageName, Boolean.TRUE.equals(variableValue), status);
        }
        updateClaimsStatusAndBuckets(claim.getIdPart(), status, false);
        processNotesService.saveProcessNotes(claim.getIdPart(), status, requestBody.getNotes());
        return processNotes;
    }

    private List<ClaimResponse.NoteComponent> addProcessNotes(Claim claim, ClaimResponse claimResponse, String notes, ExtensionStateCodeEnum state) {
        // Add each process note from the list to the ClaimResponse
        ClaimResponse.NoteComponent processNote = new ClaimResponse.NoteComponent();
        processNote.setNumber(claimResponse.getProcessNote().size() + 1);
        processNote.setType(Enumerations.NoteType.DISPLAY);
        processNote.setText(notes);
        claimResponse.addProcessNote(processNote);
        extentionService.updateState(claim, state);
        extentionService.updateState(claimResponse, state);
        // Update the ClaimResponse on the FHIR server
        fhirClient.update().resource(claim).execute();
        MethodOutcome outcome = fhirClient.update().resource(claimResponse).execute();
        log.info("ClaimResponse updated for id: {}", outcome.getId().getIdPart());
        return claimResponse.getProcessNote();
    }

    private void completeUserTask(String claimId, String notes, String mapKey,String messageName ,  boolean mapValue, ExtensionStateCodeEnum state) {
        Map<String, Object> variables = new HashMap<>();
        variables.put(VAR_CLAIM_ID, claimId);
        variables.put(VAR_USER_ID, HealthIdContextHolder.userId());
        variables.put(mapKey, mapValue);
        variables.put(VAR_STATE, state.getStateCode());
        variables.put(VAR_NOTES, notes);
        // This method will complete user task.
        taskListService.completeTask( messageName , variables);
    }

    public List<ProcessNotesAuditInfo> getProcessNotesWithAudit(String claimId) {
        List<ProcessNotesAuditInfo> enrichedNotes = new ArrayList<>();
        Optional<List<ProcessNotes>> auditListOptional = processNotesRepository.findByClaimId(claimId);

        if (auditListOptional.isEmpty() || auditListOptional.get().isEmpty()) {
            return enrichedNotes;
        }


        List<ProcessNotes> auditList = auditListOptional.get();
        for (ProcessNotes audit : auditList) {
            ClaimResponse.NoteComponent noteComponent = new ClaimResponse.NoteComponent();
            noteComponent.setNumber(audit.getSequenceNo());
            noteComponent.setType(Enumerations.NoteType.DISPLAY);
            noteComponent.setText(audit.getNotes());

            enrichedNotes.add(new ProcessNotesAuditInfo(
                    noteComponent,
                    audit.getLastModifiedBy(),
                    audit.getLastModifiedDate()
            ));
        }

        return enrichedNotes;
    }

    private void updateClaimsStatusAndBuckets(String id, ExtensionStateCodeEnum stateCode, boolean auditFlag) {
        userAssignmentService.updateUserBucketsStatus(id, stateCode, auditFlag, true);
        // Reject attached preAuths if the claim state is REJECTED.
        if (stateCode.equals(ExtensionStateCodeEnum.REJECTED)) {
            rejectAttachedPreAuths(id, stateCode);
        }
    }

    /**
     * This method will update the status of the preAuth claims attached to the claim.
     *
     * @param claimId
     */
    public void rejectAttachedPreAuths(String claimId, ExtensionStateCodeEnum stateCode) {
        List<UUID> attachedPreAuthIds = lineItemRepository.findPreAuthIdsByClaimId(UUID.fromString(claimId));
        if (!attachedPreAuthIds.isEmpty()) {
            Optional<List<InsuranceClaim>> response = claimsRepository.findByClaimIdIn(attachedPreAuthIds);
            if (response.isPresent() && !response.get().isEmpty()) {
                for (InsuranceClaim preAuthClaim : response.get()) {
                    preAuthClaim.setStatus(stateCode);
                    claimsRepository.save(preAuthClaim);
                }
            }
        }
    }


    /**
     * This method is for recall button action.
     *
     * @param claimId
     * @param requestBody
     * @throws Exception
     */
    public List<ClaimResponse.NoteComponent> recallPreAuth(String claimId, AdjudicateRequestBody requestBody) {
        // Validate action type is cancelled
        if (!requestBody.getAction().equals(CANCELED)) {
            throw new DataBadRequestException(ErrorCode.BAD_REQUEST);
        }
        // Fetch the ClaimResponse by claimId
        InsuranceClaim iClaim = claimsRepository.findByClaimId(UUID.fromString(claimId)).get().getFirst();
        ClaimResponse claimResponse = postOfficeService.getClaimResponseByClaimId(claimId, iClaim.getClaimResponseId());
        Claim claim = fhirClient.read().resource(Claim.class).withId(claimId).execute();
        if (claimResponse == null || claim == null) {
            throw new DataBadRequestException(ErrorCode.BAD_REQUEST);
        }
        return processRecall(claimId, claim, claimResponse, requestBody, iClaim);
    }

    /**
     * Recalls (cancels) a pre-authorization request submitted by a provider.
     * <p>
     * This method:
     * <ol>
     *     <li>Fetches the pre-authorization claim by its external ID.</li>
     *     <li>Validates that the claim is indeed a pre-authorization, not expired, and not already consumed.</li>
     *     <li>Fetches the associated FHIR Claim and ClaimResponse.</li>
     *     <li>Processes the recall/cancellation and returns updated claim response notes.</li>
     *     <li>Logs the entire request/response lifecycle for auditing purposes.</li>
     * </ol>
     *
     * @param request The request body containing the pre-authorization ID, requester, and cancellation reason.
     * @return List of {@link ClaimResponse.NoteComponent} containing updated notes after recall.
     * @throws BadRequestException     If the claim is not a pre-authorization.
     * @throws DataBadRequestException If the claim is expired or already consumed.
     * @throws Exception               For any other processing errors.
     */
    public List<ClaimResponse.NoteComponent> recallPreAuthByProvider(ProviderPreAuthCancelRequestBody request) {
        PreAuthCancelAuditLog.PreAuthCancelAuditLogBuilder logBuilder = PreAuthCancelAuditLog.builder()
                .requester(request.getRequester());
        try {
            InsuranceClaim iClaim = insuranceClaimsService.findByExternalID(request.getPreAuthId());
            if (iClaim == null) {
                throw new BadRequestException(String.format("Requested preauthId (%s) not found", request.getPreAuthId()));
            }
            logBuilder.requestBodyJson(new Gson().toJson(request));

            // Validate pre-authorization
            if (!Constants.Use.PREAUTHORIZATION.equalsIgnoreCase(iClaim.getUse()))
                throw new BadRequestException(String.format("Requested Id (%s) is not Preauthorization", request.getPreAuthId()));

            if (iClaim.getExpiryDate() != null && !iClaim.getExpiryDate().isAfter(LocalDate.now()))
                throw new DataBadRequestException(ErrorCode.BAD_REQUEST,
                        String.format("Requested preauthId (%s) is expired", request.getPreAuthId()));

            if (!lineItemRepository.findConsumedPreAuthIds(
                    List.of(iClaim.getClaimId()), Constants.Use.CLAIM, CLAIM_APPROVED_EXTENSION_STATES).isEmpty())
                throw new DataBadRequestException(ErrorCode.BAD_REQUEST,
                        String.format("The requested preAuthId (%s) has already been used and can't be cancelled.", request.getPreAuthId()));

            // Fetch claim and claim response
            Claim claim = fhirClient.read().resource(Claim.class).withId(iClaim.getExternalId()).execute();
            ClaimResponse claimResponse = claimsHandlerUtility.getClaimResponse(iClaim);

            // Process recall
            List<ClaimResponse.NoteComponent> notes = processRecall(
                    iClaim.getExternalId(),
                    claim,
                    claimResponse,
                    AdjudicateRequestBody.builder()
                            .notes(request.getCancellationReason())
                            .action(CANCELED)
                            .build(),
                    iClaim
            );

            logBuilder.response(new Gson().toJson(notes)).status(String.valueOf(HttpStatus.OK.value()));
            return notes;

        } catch (Exception ex) {
            logBuilder.errorMessage(ex.getMessage())
                    .status(String.valueOf(ex instanceof DataBadRequestException ? HttpStatus.BAD_REQUEST.value() : HttpStatus.UNPROCESSABLE_ENTITY.value()));
            throw ex;
        } finally {
            try {
                perAuthCancelAuditService.insertAuditLog(logBuilder.build());
            } catch (Exception ex) {
                log.warn("Audit logger exception: {}", ex.getMessage());
            }
        }
    }


    private List<ClaimResponse.NoteComponent> processRecall(String claimId, Claim claim, ClaimResponse claimResponse, AdjudicateRequestBody requestBody, InsuranceClaim iClaim) {
        boolean inInReview = extentionService.verifyState(claimResponse, ExtensionStateCodeEnum.IN_REVIEW);
        boolean isApproved = extentionService.verifyState(claimResponse, ExtensionStateCodeEnum.APPROVED);

        if (!inInReview && !isApproved) {
            log.warn("Action cancellation failed to meet In-Review/Approved state requirements.");
            throw new DataBadRequestException(ErrorCode.BAD_REQUEST, "Action cancellation failed to meet Approved state requirements.");
        }

        // Check if this is a civil servant preauth (POMSF) and notify Livia before cancellation
        boolean isCivilServantPreauth = isCivilServantPreauth(claim);
        if (isCivilServantPreauth && liviaProperties.isIntegrationEnable()) {
            List<ClaimLineItem> claimItems = insuranceClaimsService.getClaimItems(iClaim.getExternalId());
            boolean hasPmfComponent = claimItems.stream().anyMatch(item -> item.isPmfItem() && item.getPmfAmount() != null && item.getPmfAmount().doubleValue() > 0);
            if (hasPmfComponent) {
                log.info("Civil servant preauth detected for claimId: {}. Notifying Livia before cancellation.", claimId);

                // Use external ID as the preauth reference number for Livia
                String preAuthReferenceNo = iClaim.getExternalId();
                String cancellationReason = requestBody.getNotes() != null ? requestBody.getNotes() : "Preauth cancelled";

                try {
                    String liviaResponse = liviaClaimService.cancelLiviaPreAuth(claimId, preAuthReferenceNo, cancellationReason);
                    log.info("Successfully notified Livia about preauth cancellation for claimId: {}, response: {}", claimId, liviaResponse);
                } catch (DataBadRequestException e) {
                    log.error("Failed to notify Livia about preauth cancellation for claimId: {}. Blocking local cancellation. Error: {}",
                            claimId, e.getMessage());
                    throw new DataBadRequestException(ErrorCode.BAD_REQUEST, e.getMessage());
                }
            }
        }

        // Determine state and mapping values based on the conditions
        ExtensionStateCodeEnum status = null;

        if (isApproved) {
            status = CANCELED;
        }
        List<ClaimResponse.NoteComponent> processNotes = addProcessNotes(claim, claimResponse, requestBody.getNotes(), status);
        updateClaimsStatusAndBuckets(claim.getIdPart(), status, false);
        processNotesService.saveProcessNotes(claim.getIdPart(), status, requestBody.getNotes());
        statusUpdateService.triggerCallBackStatusUpdate(claim.getIdPart(), true, true, false);
        // Notify Savannah provider portal about the cancellation
        log.info("Calling savannah flow from here");
        claimsHandlerUtility.callClaimResponseWebhooks(claimResponse, claim.getIdPart(), status);
        log.info("Updated the status for claimId : {} to status : {} ", claimId, status);
        return processNotes;
    }

    /**
     * Checks if the claim is a civil servant preauth
     */
    private boolean isCivilServantPreauth(Claim claim) {
        if (claim == null || !claim.getUse().equals(Claim.Use.PREAUTHORIZATION)) {
            return false;
        }

        return claim.hasInsurance() && claim.getInsurance().stream()
                .anyMatch(insurance -> insurance.getCoverage().getReference().toLowerCase().contains("pmf"));
    }

// Bellow this is DEAD CODE after new WORK FLOW changes.

    /**
     * Processes bulk claim actions such as SEND_BACK or REJECT for the specified claim type.
     * <p>
     * This method retrieves the relevant claims from the database, validates their state,
     * applies the requested action, and logs the results. It returns a summary of
     * successfully processed claims, failed claims, and claims that were not found in the database.
     * </p>
     *
     * @param bulkAdjudicateRequest The request containing claim IDs and additional metadata for processing.
     * @param action                The bulk action to perform (SEND_BACK or REJECT).
     * @param claimType             The type of claim being processed (CLAIM or PREAUTHORIZATION).
     * @return BulkClaimResponse    A response object containing the number of successful, failed, and not-found claims.
     * @throws BadRequestException If the requested action is invalid or no matching claims are found.
     */
    public BulkClaimResponse processBulkClaimActions(BulkAdjudicateRequest bulkAdjudicateRequest, BulkAction action, ClaimType claimType) {
        log.info("Processing Bulk {} Request for {}: {}", action, claimType.getDisplayName(), bulkAdjudicateRequest.getClaims().size());

        long startTime = System.currentTimeMillis();

        ExtensionStateCodeEnum stateCode = ALLOWED_COMBINATIONS.getOrDefault(action, Map.of()).get(claimType);
        if (stateCode == null) {
            throw new BadRequestException("Invalid Action and ClaimType combination.");
        }

        List<UUID> claimsIds = extractClaimIds(bulkAdjudicateRequest);
        Map<UUID, String> claimIdToNotes = mapClaimIdToNotes(bulkAdjudicateRequest);
        Optional<List<InsuranceClaim>> insuranceClaims = Optional.empty();


        if (action.equals(BulkAction.SENT_BACK) && claimType.equals(ClaimType.CLAIM))
            insuranceClaims = claimsRepository.findAllByClaimId(claimsIds, ExtensionStateCodeEnum.CLINICAL_REVIEW, claimType.getDisplayName());
        if (action.equals(BulkAction.SENT_BACK) && claimType.equals(ClaimType.PREAUTHORIZATION))
            insuranceClaims = claimsRepository.findAllByClaimId(claimsIds, ExtensionStateCodeEnum.IN_REVIEW, claimType.getDisplayName());
        else if (action.equals(BulkAction.REJECT) && claimType.equals(ClaimType.CLAIM))
            insuranceClaims = claimsRepository.findAllByClaimId(claimsIds, ExtensionStateCodeEnum.CLINICAL_REVIEW, claimType.getDisplayName());//CLINICAL REVIEW

        if (insuranceClaims.isEmpty()) {
            log.warn("No Matching Claims Found In the Database.");
            throw new BadRequestException("No Matching Claims Found.");
        }

        List<InsuranceClaim> foundClaims = insuranceClaims.get();
        Set<UUID> foundClaimIds = foundClaims.stream()
                .map(InsuranceClaim::getClaimId)
                .collect(Collectors.toSet());

        List<UUID> notFoundClaimIds = claimsIds.stream()
                .filter(id -> !foundClaimIds.contains(id))
                .toList();

        Map<String, List<String>> claimResponseNotes = processInsuranceClaims(foundClaims, claimIdToNotes, action, claimType);

        List<String> successfulClaims = claimResponseNotes.getOrDefault("SuccessFullInReviewClaimsUpdated", Collections.emptyList());
        List<String> failedClaims = claimResponseNotes.getOrDefault("FailedInReviewClaims", Collections.emptyList());

        BulkClaimResponse response = new BulkClaimResponse(
                successfulClaims.size(),
                successfulClaims,
                failedClaims.size(),
                failedClaims,
                notFoundClaimIds.size(),
                notFoundClaimIds.stream().map(UUID::toString).collect(Collectors.toList())
        );

        saveClaimProcessingLog(response, claimIdToNotes, action);
        long endTime = System.currentTimeMillis();
        log.info("Time taken to process Bulk {} Request for Claims: {} ms", action, (endTime - startTime));

        return response;
    }

    /**
     * Processes a batch of insurance claims based on the specified bulk action and claim type.
     * This method handles actions like sending back or rejecting claims and categorizes them into
     * successfully processed and failed claims.
     *
     * @param insuranceClaims The list of insurance claims to be processed.
     * @param claimIdToNotes  A mapping of claim IDs to their corresponding notes.
     * @param action          The bulk action to be performed (e.g., SEND_BACK, REJECT).
     * @param claimType       The type of claim (e.g., CLAIM, PREAUTHORIZATION).
     * @return A map containing two lists:
     * - "SuccessFullInReviewClaimsUpdated": A list of successfully processed claim IDs.
     * - "FailedInReviewClaims": A list of failed claims with corresponding error messages.
     * @throws BadRequestException If an invalid bulk action is requested.
     */
    private Map<String, List<String>> processInsuranceClaims(
            List<InsuranceClaim> insuranceClaims, Map<UUID, String> claimIdToNotes,
            BulkAction action, ClaimType claimType) {

        Map<String, List<String>> result = new HashMap<>();
        List<String> successfulClaims = new ArrayList<>();
        Map<String, String> failedClaims = new HashMap<>();

        // Determine the action string dynamically
        String adjudicationAction = null;
        if (action.equals(BulkAction.SENT_BACK)) {
            adjudicationAction = claimType.equals(ClaimType.CLAIM) ? "Approve" : "Returned to Sender";
        } else if (action.equals(BulkAction.REJECT) && claimType.equals(ClaimType.CLAIM)) {
            adjudicationAction = "Decline";
        }

        for (InsuranceClaim insuranceClaim : insuranceClaims) {
            try {
                processSingleClaimAction(insuranceClaim, claimIdToNotes, claimType, adjudicationAction);
                successfulClaims.add(insuranceClaim.getClaimId().toString());
            } catch (Exception e) {
                log.warn("Error processing claim with ID: {}. Error: {}", insuranceClaim.getClaimId(), e.getMessage());
                failedClaims.put(insuranceClaim.getClaimId().toString(),
                        "Failed to process claim with ID: " + insuranceClaim.getClaimId() + ". Error: " + e.getMessage());
            }
        }

        result.put("SuccessFullInReviewClaimsUpdated", successfulClaims);
        result.put("FailedInReviewClaims", failedClaims.entrySet().stream()
                .map(entry -> entry.getKey() + ": " + entry.getValue())
                .collect(Collectors.toList()));

        return result;
    }

    /**
     * Processes a single insurance claim based on the specified claim type and action.
     * This method retrieves the claim and claim response, validates them, and applies
     * the adjudication action accordingly.
     *
     * @param insuranceClaim The insurance claim to be processed.
     * @param claimIdToNotes A mapping of claim IDs to their corresponding notes.
     * @param claimType      The type of claim (e.g., CLAIM, PREAUTHORIZATION).
     * @param action         The adjudication action to be performed (e.g., "Approve",
     *                       "Returned to Sender", "Decline").
     * @return A list of {@link ClaimResponse.NoteComponent} containing the processed claim notes.
     * @throws Exception If the claim or claim response is not found, or if an error occurs during processing.
     */
    private List<ClaimResponse.NoteComponent> processSingleClaimAction(
            InsuranceClaim insuranceClaim, Map<UUID, String> claimIdToNotes,
            ClaimType claimType, String action) throws Exception {

        UUID claimId = insuranceClaim.getClaimId();
        ClaimResponse claimResponse = postOfficeService.getClaimResponseByClaimId(
                claimId.toString(), insuranceClaim.getClaimResponseId());
        Claim claim = fhirClient.read(Claim.class, claimId.toString());

        if (claimResponse == null || claim == null) {
            throw new DataBadRequestException(ErrorCode.BAD_REQUEST);
        }

        AdjudicateRequestBody requestBody = AdjudicateRequestBody.builder()
                .action(ExtensionStateCodeEnum.DECLINE) // Dynamic action (Approve, Returned to Sender, Decline)
                .notes(claimIdToNotes.get(claimId))
                .build();

        return (claimType.equals(ClaimType.CLAIM))
                ? processClaimResponse(claimId.toString(), claim, claimResponse, requestBody, true, false)
                : processClaimResponsePreAuth(claimId.toString(), claim, claimResponse, requestBody, true);
    }


    /**
     * Extracts claim IDs from the bulk adjudication request.
     *
     * @param bulkAdjudicateRequest the bulk adjudication request
     * @return a list of UUIDs representing the claim IDs
     */
    private List<UUID> extractClaimIds(BulkAdjudicateRequest bulkAdjudicateRequest) {
        return bulkAdjudicateRequest.getClaims().stream()
                .map(claimsPayLoad::getClaimId)
                .collect(Collectors.toList());
    }

    /**
     * Maps claim IDs to their corresponding notes from the bulk adjudication request.
     *
     * @param bulkAdjudicateRequest the bulk adjudication request
     * @return a map of claim IDs to notes
     */
    private Map<UUID, String> mapClaimIdToNotes(BulkAdjudicateRequest bulkAdjudicateRequest) {
        return bulkAdjudicateRequest.getClaims().stream()
                .collect(Collectors.toMap(claimsPayLoad::getClaimId, claimsPayLoad::getMessage));
    }

    /**
     * Saves claim processing logs based on the provided BulkClaimResponse.
     * <p>
     * This method processes successful claims, failed claims with reasons, and claims not found in the database.
     * Each claim is logged with its status, timestamp, action performed, and any associated messages from the UI.
     * </p>
     *
     * @param bulkClaimResponse The response object containing details of successfully processed, failed, and not-found claims.
     * @param claimIdToNotes    A map containing claim IDs and their corresponding messages from the UI.
     * @param action            The bulk action performed (SEND_BACK or REJECT).
     */
    public void saveClaimProcessingLog(BulkClaimResponse bulkClaimResponse, Map<UUID, String> claimIdToNotes, BulkAction action) {
        List<ClaimBulkSendBackLogs> claimBulkSendBackLogs = new ArrayList<>();
        LocalDateTime currentTimestamp = LocalDateTime.now();

        // Process successful claims
        for (String claimId : bulkClaimResponse.getSuccessfulClaims()) {
            UUID claimUUID = UUID.fromString(claimId);
            ClaimBulkSendBackLogs logEntry = new ClaimBulkSendBackLogs();
            logEntry.setClaimId(claimId);
            logEntry.setStatus(SendbackStatus.SUCCESSFUL);
            logEntry.setTimestamp(currentTimestamp);
            logEntry.setMessageFromUI(claimIdToNotes.getOrDefault(claimUUID, "No message provided"));
            logEntry.setAction(action);
            claimBulkSendBackLogs.add(logEntry);
        }

        // Process failed claims with reasons
        for (String failedClaimReason : bulkClaimResponse.getFailedClaimsReasons()) {
            String[] parts = failedClaimReason.split(":", 2);
            String claimId = parts[0];
            String errorMessage = parts.length > 1 ? parts[1] : "Unknown error";

            UUID claimUUID = UUID.fromString(claimId);
            ClaimBulkSendBackLogs logEntry = new ClaimBulkSendBackLogs();
            logEntry.setClaimId(claimId);
            logEntry.setStatus(SendbackStatus.FAILED);
            logEntry.setTimestamp(currentTimestamp);
            logEntry.setMessageFromUI(claimIdToNotes.getOrDefault(claimUUID, "No message provided"));
            logEntry.setErrorMessage(errorMessage);
            logEntry.setAction(action);
            claimBulkSendBackLogs.add(logEntry);
        }

        // Process claims that were not found in the DB
        for (String claimId : bulkClaimResponse.getFailedClaimNotInDbClaims()) {
            UUID claimUUID = UUID.fromString(claimId);
            ClaimBulkSendBackLogs logEntry = new ClaimBulkSendBackLogs();
            logEntry.setClaimId(claimId);
            logEntry.setStatus(SendbackStatus.NOT_IN_DB);
            logEntry.setTimestamp(currentTimestamp);
            logEntry.setMessageFromUI(claimIdToNotes.getOrDefault(claimUUID, "No message provided"));
            logEntry.setErrorMessage("Claim not found in the database.");
            logEntry.setAction(action);
            claimBulkSendBackLogs.add(logEntry);
        }

        claimBulkSendBackRepository.saveAll(claimBulkSendBackLogs);
    }

    /**
     * Defines allowed combinations of BulkAction and ClaimType with their respective state codes.
     */
    private static final Map<BulkAction, Map<ClaimType, ExtensionStateCodeEnum>> ALLOWED_COMBINATIONS = Map.of(
            BulkAction.SENT_BACK, Map.of(
                    ClaimType.CLAIM, ExtensionStateCodeEnum.CLINICAL_REVIEW,
                    ClaimType.PREAUTHORIZATION, ExtensionStateCodeEnum.IN_REVIEW
            ),
            BulkAction.REJECT, Map.of(
                    ClaimType.CLAIM, ExtensionStateCodeEnum.CLINICAL_REVIEW
            )
    );

    /**
     * This method will complete the Claims user tasks for new flow of Manual-Review and Surveillance stages.
     *
     * @param claimId
     * @param requestBody
     * @return List of ClaimResponse.NoteComponent
     */

    public List<ClaimResponse.NoteComponent> processClaimNewStages(String claimId, AdjudicateRequestBody requestBody, boolean isManualReview) {
        // Fetch the ClaimResponse by claimId
        InsuranceClaim iClaim = claimsRepository.findByClaimId(UUID.fromString(claimId)).get().getFirst();
        ClaimResponse claimResponse = postOfficeService.getClaimResponseByClaimId(claimId, iClaim.getClaimResponseId());
        Claim claim = fhirClient.read().resource(Claim.class).withId(claimId).execute();
        if (claimResponse == null || claim == null) {
            throw new DataBadRequestException(ErrorCode.BAD_REQUEST);
        }
        return processClaimResponseNewStages(claimId, claim, claimResponse, requestBody, iClaim, isManualReview);
    }

    private List<ClaimResponse.NoteComponent> processClaimResponseNewStages(String claimId, Claim claim, ClaimResponse claimResponse,
                                                                            AdjudicateRequestBody requestBody, InsuranceClaim iClaim, Boolean onlyManualReview)
    {

        boolean isManualReview;
        boolean isSurveillance = false;
        boolean inMedicalReview = false;
        if(onlyManualReview){
            isManualReview = extentionService.verifyState(claimResponse, ExtensionStateCodeEnum.MANUAL_REVIEW);
        }else{
             isManualReview = extentionService.verifyState(claimResponse, ExtensionStateCodeEnum.MANUAL_REVIEW);
             isSurveillance = extentionService.verifyState(claimResponse, ExtensionStateCodeEnum.SENT_TO_SURVEILLANCE);
             inMedicalReview = extentionService.verifyState(claimResponse, ExtensionStateCodeEnum.MEDICAL_REVIEW);
        }


        if (!isManualReview && !isSurveillance && !inMedicalReview) {
            log.warn("Failed to meet Manual-Review/Sent-To-Surveillance state requirements.");
            throw new DataBadRequestException(ErrorCode.BAD_REQUEST, "Failed to meet Manual-Review/Sent-To-Surveillance state requirements.");
        }

        // Determine state and mapping values based on the conditions
        StateMappingResult result = determineNextStateAndVariables(isManualReview, isSurveillance, inMedicalReview, requestBody.getAction(),
                iClaim, requestBody, claimId, surveillanceService, claim, claimResponse);

        ExtensionStateCodeEnum nextState = result.nextState();
        String variableKey = result.variableKey();
        Boolean variableValue = result.variableValue();
        String messageName = result.messageName();
        boolean auditFlag = result.auditFlag();
        boolean processIntermediateStage = result.processIntermediateStage();

        List<ClaimResponse.NoteComponent> processNotes = new ArrayList<>();
        if (!processIntermediateStage) {
            // Add process notes and complete the user task
            String formattedNotes = processNotesService.formatNotes(requestBody.getCode(), requestBody.getDescription(), requestBody.getNotes());
            String finalNotes = processNotesService.saveProcessNotesAsCodeAndDesc(claim.getIdPart(), nextState, formattedNotes);
            updateClaimsStatusAndBuckets(claim.getIdPart(), nextState, auditFlag);
            processNotes = addProcessNotes(claim, claimResponse, finalNotes, nextState);
            completeUserTask(claimId, finalNotes, variableKey, messageName, Boolean.TRUE.equals(variableValue), nextState);
        }
        // This is the fix: trigger the callback regardless of the intermediate stage flag.
        statusUpdateService.triggerCallBackStatusUpdate(claim.getIdPart(), true, false, false);
        return processNotes;
    }

    private StateMappingResult determineNextStateAndVariables(boolean isManualReview, boolean isSurveillance, boolean inMedicalReview, ExtensionStateCodeEnum action,
                                                              InsuranceClaim iClaim, AdjudicateRequestBody requestBody, String claimId,
                                                              SurveillanceService surveillanceService, Claim claim, ClaimResponse claimResponse)
    {
        ExtensionStateCodeEnum nextState = action; // default same as nextAction
        String variableKey = null;
        Boolean variableValue = null;
        boolean auditFlag = false;
        boolean processIntermediateStage = false;
        String messageName = null;
        LocalDateTime oldSurveillanceDate = LocalDate.parse(effectiveDate).atStartOfDay();


        if (isManualReview) {
            messageName = Constants.BPMN_USER_TASK_MESSAGE.MANUAL_REVIEW;
            switch (nextState) {
                case SENT_TO_SURVEILLANCE -> {
                    variableKey = VAR_SURVEILLANCE_REQ;
                    variableValue = true;
                    auditFlag = true;
                }
                case APPROVED -> {
                    variableKey = VAR_IS_MANUAL_APPROVE;
                    variableValue = true;
                }
                case MISSING_DOCUMENTS -> {
                    variableKey = VAR_MISSING_DOC;
                    variableValue = true;
                }
                case REJECTED -> {
                    variableKey = VAR_IS_RETURN_TO_SENDER;
                    variableValue = false;
                }
                case SENT_BACK -> {
                    variableKey = VAR_IS_RETURN_TO_SENDER;
                    variableValue = true;
                }
                default -> log.warn("Unhandled Manual-Review action: {}", nextState);
            }
        } else if (isSurveillance) {
            messageName = Constants.BPMN_USER_TASK_MESSAGE.SURVEILLANCE_REVIEW;
            if (iClaim.getSurveillanceStatus() != null && iClaim.getCreationDate().isBefore(oldSurveillanceDate)) {
                log.info("Processing old Flow Surveillance claim for claimId: {}", claimId);
                processIntermediateStage = true;
                surveillanceService.updateAuditStatus(getAuditUpdateRequest(claimId, requestBody));
                if(requestBody.getAction().equals(PARTIAL_APPROVAL)){
                    ClaimAudit claimAudit = claimAuditRepository.findByClaimId(UUID.fromString(claimId))
                            .orElseThrow(() -> new BadRequestException("Invalid Request, No Data found for ClaimID: " + claimId));
                    claimAuditReprocessService.reprocessAuditedClaim(claimAudit);
                }
            } else {
                switch (nextState) {
                    case APPROVED -> {
                        variableKey = VAR_IS_SURVEILLANCE_APPROVE;
                        variableValue = true;
                    }
                    case PARTIAL_APPROVAL -> {
                        nextState = ExtensionStateCodeEnum.APPROVED; // override
                        variableKey = VAR_IS_SURVEILLANCE_APPROVE;
                        variableValue = true;
                        partialPaymentProcessing(requestBody, iClaim, claimId, claim, claimResponse);
                    }
                    case UNDER_INVESTIGATION -> {
                        processIntermediateStage = true;
                        surveillanceService.handleUnderInvestigationClaim(iClaim, getAuditUpdateRequest(claimId, requestBody));
                    }
                    case UNDER_COMMITTEE_REVIEW -> {
                        processIntermediateStage = true;
                        surveillanceService.handleCommitteeReviewClaim(iClaim, getAuditUpdateRequest(claimId, requestBody));
                    }
                    case MISSING_DOCUMENTS -> {
                        variableKey = VAR_MISSING_DOC;
                        variableValue = true;
                    }
                    case REJECTED -> {
                        variableKey = VAR_IS_RETURN_TO_SENDER;
                        variableValue = false;
                    }
                    default -> log.warn("Unhandled Sent-To-Surveillance action: {}", nextState);
                }
            }
        } else if (inMedicalReview){
            messageName= Constants.BPMN_USER_TASK_MESSAGE.DOCTOR_REVIEW;
            switch (nextState){
                case SENT_TO_SURVEILLANCE -> {
                    variableKey = VAR_SURVEILLANCE_REQ;
                    variableValue = true;
                    auditFlag = true;
                }
                default -> log.warn("Unhandled Medical-Review action: {}", nextState);
            }
        }

        return new StateMappingResult(nextState, variableKey, variableValue, auditFlag, processIntermediateStage , messageName);
    }

    private void partialPaymentProcessing(AdjudicateRequestBody requestBody, InsuranceClaim iClaim, String claimId,
                                          Claim claim, ClaimResponse claimResponse) {
        AuditUpdateRequest auditUpdateRequest = getAuditUpdateRequest(claimId, requestBody);
        surveillanceService.handlePartialPaymentClaim(iClaim, claimId, auditUpdateRequest);

        boolean triggerCallBack = false;
        ClaimAudit claimAudit = claimAuditRepository.findByClaimId(UUID.fromString(claimId))
                .orElseThrow(() -> new BadRequestException("Invalid Request, No Data found for ClaimID: " + claimId));

        // Get the line items
        String interventionCode = claimAudit.getInterventionCode(); // form the audit object.
        var claimItems = insuranceClaimsService.getClaimItems(claimId)
                .stream()
                .filter(c -> interventionCode.equals(c.getCode()))
                .filter(c -> claimAudit.getSequence() == null || claimAudit.getSequence().equals(c.getSequence()))
                .findFirst().orElseThrow();

        claimAuditReprocessService.processPartialApprovedClaims(claimItems, claimAudit, claimId, claim, claimResponse, triggerCallBack);
    }

    @Async("virtualThreadExecutor")
    public void reProcessErpRequestClaimIds(ErpReprocessListClaimsRequest request) {
        List<String> claimIds = request.getClaimIDs();
        log.info("Received Request For Reprocess manual review Status claims, Size : {}, ", claimIds.size());
        // Create a virtual thread per batch
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            int totalClaims = claimIds.size();
            for (int i = 0; i < totalClaims; i += BATCH_SIZE) {
                int finalI = i;
                executor.submit(() -> processClaimsIdsBatch(claimIds.subList(finalI, Math.min(finalI + BATCH_SIZE, totalClaims)), request.getForce()));
            }

        }
    }

    private void processClaimsIdsBatch(List<String> batch, Boolean isManualReview) {
        // Add your processing logic here

        AdjudicateRequestBody requestBody = null;
        for (String claimId : batch) {
            requestBody = new AdjudicateRequestBody();
            requestBody.setNotes("Approve for payment by system");
            requestBody.setAction(ExtensionStateCodeEnum.APPROVED);
            processClaimNewStages(claimId, requestBody, isManualReview);
        }
    }

    private AuditUpdateRequest getAuditUpdateRequest(String claimId, AdjudicateRequestBody requestBody) {
        var interventions = Optional.ofNullable(requestBody.getInterventions())
                .filter(list -> !list.isEmpty())
                .orElseGet(ArrayList::new);

        return AuditUpdateRequest.builder()
                .claimId(claimId)
                .reason(requestBody.getNotes())
                .auditDecision(requestBody.getAction())
                .interventions(interventions)
                .build();
    }


    private record StateMappingResult(ExtensionStateCodeEnum nextState, String variableKey, Boolean variableValue,
                                     boolean auditFlag, boolean processIntermediateStage , String messageName) {}

}
