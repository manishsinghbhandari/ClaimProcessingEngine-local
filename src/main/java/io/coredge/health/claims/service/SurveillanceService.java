package io.coredge.health.claims.service;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import io.coredge.health.claims.audit.ClaimAudit;
import io.coredge.health.claims.audit.ClaimAuditRepository;
import io.coredge.health.claims.audit.ClaimAuditReprocessService;
import io.coredge.health.claims.db.entity.InsuranceClaim;
import io.coredge.health.claims.db.entity.SurveillanceLog;
import io.coredge.health.claims.db.repositories.SurveillanceLogRepository;
import io.coredge.health.claims.dto.AuditUpdateRequest;
import io.coredge.health.claims.dto.InterventionDetails;
import io.coredge.health.claims.dto.SurveillanceFlagRequest;
import io.coredge.health.claims.dto.SurveillanceLogResponse;
import io.coredge.health.claims.enums.ClaimType;
import io.coredge.health.claims.enums.ExtensionStateCodeEnum;
import io.coredge.health.claims.exception.app.BadRequestException;
import io.coredge.health.claims.exception.app.DataNotFoundException;
import io.coredge.health.claims.exception.app.DataValidationException;
import io.coredge.health.claims.exception.base.ExternalServiceException;
import io.coredge.health.claims.helpers.ClaimPaymentHelper;
import io.coredge.health.claims.models.claims.AdjudicateRequestBody;
import io.coredge.health.claims.utils.ClaimsHandlerUtility;
import io.coredge.health.claims.worker.SendClaimForPaymentHandler;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.r4.model.Claim;
import org.hl7.fhir.r4.model.ClaimResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import io.coredge.health.claims.context.HealthIdContextHolder;

@Slf4j
@Service
public class SurveillanceService {

    @Autowired
    private IGenericClient fhirClient;

    @Autowired
    private FhirContext fhirContext;

    @Autowired
    private ClaimExtensionService extentionService;

    @Autowired
    private StatusUpdateService statusUpdateService;

    @Autowired
    private ClaimAuditRepository claimAuditRepository;

    @Autowired
    private ProcessNotesService processNotesService;

    @Autowired
    private SurveillanceLogRepository surveillanceLogRepository;

    private final ClaimsHandlerUtility claimsHandlerUtility;

    @Autowired
    private UserAssignmentService userAssignmentService;

    @Autowired
    public SurveillanceService(@Lazy ClaimsHandlerUtility claimsHandlerUtility) {
        this.claimsHandlerUtility = claimsHandlerUtility;
    }

    @Autowired
    private InsuranceClaimsService insuranceClaimsService;

    @Autowired
    private ClaimPaymentHelper claimPaymentHelper;

    @Autowired
    private ClaimAuditReprocessService claimAuditReprocessService;

    @Autowired
    private SendClaimForPaymentHandler sendClaimForPaymentHandler;
    /**
     * Surveillance
     * Updates the audit status of a claim based on the audit decision.
     * For approved claims, it simply removes the audit flag.
     * For rejected claims, it removes the audit flag, updates the claim status to REJECTED,
     * and synchronizes the status with FHIR Claim and ClaimResponse objects.
     * For partial payment claims, it updates the claim with the approved amount or LOS for each intervention.
     *
     * @param request The request containing claim ID, audit decision, and optional parameters
     * @return Success message
     */
    public String updateAuditStatus(AuditUpdateRequest request) {
        if (request == null) {
            throw new BadRequestException("Request cannot be null");
        }

        String claimId = request.getClaimId();
        ExtensionStateCodeEnum auditDecision = request.getAuditDecision();

        log.info("Processing surveillance for claim ID: {} with decision: {}", claimId, auditDecision);

        try {
            validateRequest(request, auditDecision);
            // For SENT_FOR_PAYMENT_PROCESSING, skip audit flag check since approved claims may not be under surveillance
            boolean checkAuditFlag = false;
            InsuranceClaim claim = validateAndGetClaim(claimId, checkAuditFlag);

            String result;
            switch (auditDecision) {
                case APPROVED:
                    result = handleApprovedClaim(claim, claimId,request);
                    break;
                case PARTIAL_APPROVAL:
                    result = handlePartialPaymentClaim(claim, claimId, request);
                    break;
                case SENT_BACK:
                    result = handleSentBackClaim(claim, claimId, request);
                    break;
                case REJECTED:
                    result = handleRejectedClaim(claim, claimId, request);
                    break;
                case UNDER_INVESTIGATION:
                    result = handleUnderInvestigationClaim(claim, request);
                    break;
                case UNDER_COMMITTEE_REVIEW:
                    result = handleCommitteeReviewClaim(claim, request);
                    break;
                case MISSING_DOCUMENTS:
                    result = handleMissingDocumentsClaim(claim, claimId, request);
                    break;
                case SENT_FOR_PAYMENT_PROCESSING:
                    result = handleSendForPaymentClaim(claim, claimId, request);
                    break;
                case SENT_TO_SURVEILLANCE:
                    result = handleSendToSurveillanceClaim(claim, claimId, request);
                    break;
                default:
                    throw new BadRequestException("Invalid audit decision: " + auditDecision);
            }

            return result;

        }catch (BadRequestException | DataNotFoundException | ExternalServiceException |FHIRException e) {
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error processing claim ID: {} , error :{} , {}", claimId, e , e.getMessage() , e);
            throw new BadRequestException("Unexpected error processing claim ID: " + claimId);
        }
    }

    private InsuranceClaim validateAndGetClaim(String claimId, boolean checkAuditFlag) {
        InsuranceClaim claim = insuranceClaimsService.findByExternalID(claimId);
        if (claim == null) {
            throw new DataNotFoundException("Claim not found with ID: " + claimId);
        }
        if (!claim.isAuditFlag() && checkAuditFlag) {
            throw new BadRequestException("Claim is not under surveillance");
        }
        // exclude preauths from surveillance actions
        if (claim.getUse().equalsIgnoreCase(ClaimType.PREAUTHORIZATION.name())) {
            throw new BadRequestException("Claim type is not supported");
        }
        return claim;
    }

    private final List<ExtensionStateCodeEnum> APPROVED_STATES = List.of(
            ExtensionStateCodeEnum.SENT_TO_SURVEILLANCE,
            ExtensionStateCodeEnum.MISSING_DOCUMENTS,
            ExtensionStateCodeEnum.RESUBMITTED_MISSING_DOCUMENTS);

    private void validateRequest(AuditUpdateRequest request, ExtensionStateCodeEnum auditDecision) {
        if ((ExtensionStateCodeEnum.REJECTED.equals(auditDecision) ||
                ExtensionStateCodeEnum.SENT_BACK.equals(auditDecision) ||
                ExtensionStateCodeEnum.MISSING_DOCUMENTS.equals(auditDecision) ||
                ExtensionStateCodeEnum.UNDER_INVESTIGATION.equals(auditDecision) ||
                ExtensionStateCodeEnum.UNDER_COMMITTEE_REVIEW.equals(auditDecision) ||
                ExtensionStateCodeEnum.SENT_FOR_PAYMENT_PROCESSING.equals(auditDecision)) &&
                (request.getReason() == null || request.getReason().isEmpty())) {
            throw new BadRequestException("Reason is required");
        }

        if (ExtensionStateCodeEnum.PARTIAL_APPROVAL.equals(auditDecision)) {
            if (request.getInterventions() == null || request.getInterventions().isEmpty()) {
                throw new BadRequestException("Interventions are required for partial payment");
            }
        }
    }

    private void validatePartialPaymentRequest(AuditUpdateRequest request) {
        if (request.getInterventions() == null || request.getInterventions().isEmpty()) {
            throw new BadRequestException("At least one intervention must be provided for partial payment");
        }

        // Validate each intervention
        for (InterventionDetails intervention : request.getInterventions()) {
            if (intervention.getApprovedLOS() == null && intervention.getApprovedAmount() == null) {
                throw new BadRequestException("Either Approved LOS or Approved Amount must be provided for each intervention");
            }
            if (intervention.getInterventionCode() == null || intervention.getInterventionCode().isEmpty()) {
                throw new BadRequestException("Intervention code is required for each intervention");
            }
            if (intervention.getSequence() == null) {
                throw new BadRequestException("Sequence is required for each intervention");
            }
            if (intervention.getApprovedLOS() != null && intervention.getApprovedLOS() <= 0) {
                throw new BadRequestException("Approved LOS must be greater than zero");
            }
            if (intervention.getApprovedAmount() != null && intervention.getApprovedAmount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BadRequestException("Approved Amount must be greater than zero");
            }

            // Get all claim items for this intervention code and sequence
            var claimItems = insuranceClaimsService.getClaimItems(request.getClaimId())
                    .stream()
                    .filter(c -> intervention.getInterventionCode().equals(c.getCode())
                            && intervention.getSequence().equals(c.getSequence()))
                    .findFirst()
                    .orElseThrow(() -> new BadRequestException(
                            String.format("No claim item found with intervention code '%s' and sequence '%d'",
                                    intervention.getInterventionCode(), intervention.getSequence())));

            // Perform validation using the matched claim item
            if(intervention.getApprovedLOS() != null && intervention.getApprovedLOS() > 0){
                claimAuditReprocessService.validateApprovedLos(claimItems, intervention.getApprovedLOS(), request.getClaimId());
            }
            if(intervention.getApprovedAmount() != null && intervention.getApprovedAmount().compareTo(BigDecimal.ZERO) > 0){
                claimAuditReprocessService.validateApprovedAmount(claimItems, intervention.getApprovedAmount(), request.getClaimId());
            }
        }
    }

    private String handleApprovedClaim(InsuranceClaim claim, String claimId, AuditUpdateRequest request) {
        surveillanceLog(claim,request);
        if (APPROVED_STATES.contains(claim.getStatus())) {
            updateFhirResources(claimId, request, claim);
            insuranceClaimsService.updateClaimsStatus(claimId, ExtensionStateCodeEnum.APPROVED);
            userAssignmentService.updateUserBucketsStatus(claimId ,ExtensionStateCodeEnum.APPROVED , false , false);
            String formattedNotes = processNotesService.formatNotes(request.getCode(), request.getDescription(), request.getReason());
            processNotesService.saveProcessNotesAsCodeAndDesc(claimId, claim.getStatus(), formattedNotes);
            ClaimResponse fhirClaimResponse = readFhirClaimResponse(claimId, claim);
            sendCallbacksAndWebhooks(claimId, fhirClaimResponse, true, true, ExtensionStateCodeEnum.APPROVED);
        }
        claim.setAuditFlag(false);
        claim.setSurveillanceStatus(null);
        insuranceClaimsService.save(claim);


        log.info("Claim ID: {} approved and removed from surveillance", claimId);
        return "Success";
    }

    private String handleRejectedClaim(InsuranceClaim claim, String claimId, AuditUpdateRequest request) {
        surveillanceLog(claim,request);
        updateFhirResources(claimId, request, claim);
        insuranceClaimsService.updateClaimsStatus(claimId, ExtensionStateCodeEnum.REJECTED);

        claim.setAuditFlag(false);
        claim.setRejectionReason(request.getReason());
        claim.setSurveillanceStatus(null);
        insuranceClaimsService.save(claim);

        userAssignmentService.updateUserBucketsStatus(claimId ,ExtensionStateCodeEnum.REJECTED , false , false);

        ClaimResponse fhirClaimResponse = readFhirClaimResponse(claimId, claim);
        sendCallbacksAndWebhooks(claimId, fhirClaimResponse, true, true, ExtensionStateCodeEnum.REJECTED);

        log.info("Claim ID: {} rejected with reason: {} and removed from audit", claimId, request.getReason());
        return "Success";
    }

    private String handleSentBackClaim(InsuranceClaim claim, String claimId, AuditUpdateRequest request) {
        surveillanceLog(claim,request);
        updateFhirResources(claimId, request, claim);
        insuranceClaimsService.updateClaimsStatus(claimId, ExtensionStateCodeEnum.SENT_BACK);

        claim.setAuditFlag(false);
        claim.setSurveillanceStatus(null);
        insuranceClaimsService.save(claim);

        userAssignmentService.updateUserBucketsStatus(claimId ,ExtensionStateCodeEnum.SENT_BACK , false , false);

        ClaimResponse fhirClaimResponse = readFhirClaimResponse(claimId, claim);
        sendCallbacksAndWebhooks(claimId, fhirClaimResponse, true, true, ExtensionStateCodeEnum.SENT_BACK);

        log.info("Claim ID: {} Sent Back with reason: {} and removed from audit", claimId, request.getReason());
        return "Success";
    }

    public String handlePartialPaymentClaim(InsuranceClaim claim, String claimId, AuditUpdateRequest request) {
        validatePartialPaymentRequest(request);
        surveillanceLog(claim,request);
        // Process each intervention in the list
        for (InterventionDetails intervention : request.getInterventions()) {
            saveClaimAudit(claimId, intervention, request.getReason());
        }

        claim.setAuditFlag(false);
        claim.setSurveillanceStatus(ExtensionStateCodeEnum.PARTIAL_APPROVAL.name());
        String formattedNotes = processNotesService.formatNotes(request.getCode(), request.getDescription(), request.getReason());
        processNotesService.saveProcessNotesAsCodeAndDesc(claimId, claim.getStatus(), formattedNotes);
        userAssignmentService.updateUserBucketsStatus(claimId ,ExtensionStateCodeEnum.PARTIAL_APPROVAL , false , false);
        insuranceClaimsService.save(claim);

        return "Success";
    }

    private String handleSendForPaymentClaim(InsuranceClaim claim, String claimId, AuditUpdateRequest request) {
        // Validate that claim status is APPROVED before sending for payment processing
        if (!claim.getStatus().equals(ExtensionStateCodeEnum.APPROVED)) {
            throw new BadRequestException("Claim must be in APPROVED status to be sent for payment processing.");
        }

        try {
            surveillanceLog(claim,request);
            // Call handler directly so exceptions propagate properly
            sendClaimForPaymentHandler.handlePaymentErp(null, null, claimId, false, false);
            String formattedNotes = processNotesService.formatNotes(request.getCode(), request.getDescription(), request.getReason());
            processNotesService.saveProcessNotes(claimId, claim.getStatus(), formattedNotes);
            userAssignmentService.updateUserBucketsStatus(claim.getClaimId().toString() ,ExtensionStateCodeEnum.SENT_FOR_PAYMENT_PROCESSING , false , false);
            log.info("Claim ID: {} sent for payment processing and removed from audit", claimId);
            return "Success";
        } catch (BadRequestException | DataValidationException | ExternalServiceException ex){
            log.error("Failed to send claim {} for payment processing: {}", claimId, ex.getMessage());
            throw ex;
        } catch (Exception e){
            throw new BadRequestException("Failed to send claim for payment processing: " + e.getCause());
        }
    }

    private String handleSendToSurveillanceClaim(InsuranceClaim claim, String claimId, AuditUpdateRequest request) {
        SurveillanceFlagRequest surveillanceFlagRequest = new SurveillanceFlagRequest();
        surveillanceFlagRequest.setClaimId(claimId);
        surveillanceFlagRequest.setCode(request.getCode());
        surveillanceFlagRequest.setDescription(request.getDescription());
        surveillanceFlagRequest.setReason(request.getReason());
        surveillanceFlagRequest.setChangeStatus(true);

        userAssignmentService.updateUserBucketsStatus(claim.getClaimId().toString() ,ExtensionStateCodeEnum.SENT_TO_SURVEILLANCE , false , false);

        return flagClaimForSurveillance(surveillanceFlagRequest);
    }

    private void saveClaimAudit(String claimId, InterventionDetails intervention, String generalReason) {
        log.info("Saving claim audit for claim ID: {} with intervention code: {}", claimId, intervention.getInterventionCode());
        String interventionCode = intervention.getInterventionCode();

        try {
            // Check if record already exists or already processed
            boolean recordExists = claimAuditRepository.existsByClaimIdAndInterventionCodeAndStatusAndProcessRemarks(
                    UUID.fromString(claimId),
                    interventionCode,
                    ExtensionStateCodeEnum.PARTIAL_APPROVAL.name(),
                    "Success"
            );

            if (recordExists) {
                log.info("⚠️ Record already exists or already processed for claimId={}, interventionCode={}, status={}",
                        claimId, interventionCode, ExtensionStateCodeEnum.PARTIAL_APPROVAL.name());
                throw new BadRequestException("Record already exists or already processed for claim ID: " + claimId + " and intervention code: " + interventionCode);
            }

            // Create and save the new audit record
            ClaimAudit newClaimAudit = new ClaimAudit();
            newClaimAudit.setClaimId(UUID.fromString(claimId));
            newClaimAudit.setInterventionCode(interventionCode);
            newClaimAudit.setSequence(intervention.getSequence());
            newClaimAudit.setApprovedAuditLos(intervention.getApprovedLOS());
            newClaimAudit.setApprovedAmount(intervention.getApprovedAmount());

            // Use intervention-specific reason if available, otherwise use the general reason
            String reason = (intervention.getReason() != null && !intervention.getReason().isEmpty())
                    ? intervention.getReason()
                    : generalReason;
            newClaimAudit.setRemarks(reason);

            newClaimAudit.setAuditStatus(ExtensionStateCodeEnum.PARTIAL_APPROVAL.name());
            claimAuditRepository.save(newClaimAudit);
            log.info("Successfully saved claim audit for claim ID: {} with intervention code: {}", claimId, interventionCode);
        } catch(BadRequestException ex) {
            throw ex;
        } catch (Exception e) {
            log.error("Error saving claim audit: {}", e.getMessage());
            throw new BadRequestException("Error saving claim audit for claim ID: " + claimId + " and intervention code: " + interventionCode);
        }
    }

    public String handleUnderInvestigationClaim(InsuranceClaim claim, AuditUpdateRequest request) {
        surveillanceLog(claim, request);
        claim.setSurveillanceStatus(ExtensionStateCodeEnum.UNDER_INVESTIGATION.name());
        insuranceClaimsService.save(claim);
        String formattedNotes = processNotesService.formatNotes(request.getCode(), request.getDescription(), request.getReason());
        processNotesService.saveProcessNotes(claim.getClaimId().toString(), ExtensionStateCodeEnum.UNDER_INVESTIGATION, formattedNotes);
        userAssignmentService.updateUserBucketsStatus(claim.getClaimId().toString() ,ExtensionStateCodeEnum.UNDER_INVESTIGATION , false , false);

        return "Success";
    }

    public String handleCommitteeReviewClaim(InsuranceClaim claim, AuditUpdateRequest request) {
        if (!ExtensionStateCodeEnum.UNDER_INVESTIGATION.name().equals(claim.getSurveillanceStatus())) {
            throw new BadRequestException("Claim must be under investigation to be sent for committee review");
        }
        surveillanceLog(claim,request);
        claim.setSurveillanceStatus(ExtensionStateCodeEnum.UNDER_COMMITTEE_REVIEW.name());
        insuranceClaimsService.save(claim);
        String formattedNotes = processNotesService.formatNotes(request.getCode(), request.getDescription(), request.getReason());
        processNotesService.saveProcessNotes(claim.getClaimId().toString(), ExtensionStateCodeEnum.UNDER_COMMITTEE_REVIEW, formattedNotes);
        userAssignmentService.updateUserBucketsStatus(claim.getClaimId().toString() ,ExtensionStateCodeEnum.UNDER_COMMITTEE_REVIEW , false , false);

        return "Success";
    }

    private String handleMissingDocumentsClaim(InsuranceClaim claim, String claimId, AuditUpdateRequest request) {
//        if(claim.getStatus() != ExtensionStateCodeEnum.RESUBMITTED_MISSING_DOCUMENTS) {
//            throw new BadRequestException("Claim must be in RESUBMITTED_MISSING_DOCUMENTS state to be sent for MISSING_DOCUMENTS");
//        }
        surveillanceLog(claim,request);
        updateFhirResources(claimId, request, claim);
        insuranceClaimsService.updateClaimsStatus(claimId, ExtensionStateCodeEnum.MISSING_DOCUMENTS);
        claim.setSurveillanceStatus(ExtensionStateCodeEnum.MISSING_DOCUMENTS.name());
        insuranceClaimsService.save(claim);
        userAssignmentService.updateUserBucketsStatus(claim.getClaimId().toString() ,ExtensionStateCodeEnum.MISSING_DOCUMENTS , false , false);
        ClaimResponse fhirClaimResponse = readFhirClaimResponse(claimId, claim);
        sendCallbacksAndWebhooks(claimId, fhirClaimResponse, true, true, ExtensionStateCodeEnum.MISSING_DOCUMENTS);

        return "Success";
    }

    public void handleResubmittedMissingDocumentsClaim(InsuranceClaim claim, String claimId) {
        if(claim.getStatus() != ExtensionStateCodeEnum.MISSING_DOCUMENTS) {
            throw new BadRequestException("Claim must be in MISSING_DOCUMENTS state to be sent for RESUBMITTED_MISSING_DOCUMENTS");
        }
        // Create AuditUpdateRequest to reuse existing updateFhirResources method
        AuditUpdateRequest auditRequest = new AuditUpdateRequest();
        auditRequest.setClaimId(claimId);
        auditRequest.setAuditDecision(ExtensionStateCodeEnum.RESUBMITTED_MISSING_DOCUMENTS);
        auditRequest.setReason("Resubmitted missing documents");
        surveillanceLog(claim,auditRequest);
        updateFhirResources(claimId, auditRequest, claim);
        insuranceClaimsService.updateClaimsStatus(claimId, ExtensionStateCodeEnum.RESUBMITTED_MISSING_DOCUMENTS);
        claim.setSurveillanceStatus(ExtensionStateCodeEnum.RESUBMITTED_MISSING_DOCUMENTS.name());
        claim.setAuditFlag(true);
        insuranceClaimsService.save(claim);
        ClaimResponse fhirClaimResponse = readFhirClaimResponse(claimId, claim);
        sendCallbacksAndWebhooks(claimId, fhirClaimResponse, true, true, ExtensionStateCodeEnum.RESUBMITTED_MISSING_DOCUMENTS);
    }

    private Claim readFhirClaim(String claimId) {
        return fhirClient.read().resource(Claim.class).withId(claimId).execute();
    }

    private ClaimResponse readFhirClaimResponse(String claimId, InsuranceClaim claim) {
        try {
            String responseId = claim.getClaimResponseId() != null ? claim.getClaimResponseId() : claimId;
            return fhirClient.read().resource(ClaimResponse.class).withId(responseId).execute();
        } catch (Exception e) {
            throw new FHIRException("Failed to read FHIR ClaimResponse for claim ID: " + claimId);
        }
    }
    private void updateFhirResources(String claimId, AuditUpdateRequest request, InsuranceClaim claim) {
        Claim fhirClaim = readFhirClaim(claimId);
        ClaimResponse fhirClaimResponse = readFhirClaimResponse(claimId, claim);

        if (fhirClaim != null) {
            updateFhirClaim(fhirClaim, claimId,request.getAuditDecision());
        }

        if (fhirClaimResponse != null) {
            updateFhirClaimResponse(fhirClaimResponse, claimId, request);
        }
//        send callbacks and webhooks
    }

    private void updateFhirClaim(Claim fhirClaim, String claimId, ExtensionStateCodeEnum decision) {
        try {
            extentionService.updateState(fhirClaim, decision);
            fhirClient.update().resource(fhirClaim).execute();
            log.info("Successfully updated FHIR Claim status for ID: {}", claimId);
        } catch (Exception e) {
            throw new FHIRException("Failed to update FHIR Claim for ID: " + claimId);
        }
    }

    private void updateFhirClaimResponse(ClaimResponse fhirClaimResponse, String claimId, AuditUpdateRequest request) {
        try {
            extentionService.updateState(fhirClaimResponse, request.getAuditDecision());

            // I have commented this for against EPORTAL-1521
            // Always add process notes for terminal statuses to ensure callbacks succeed
//            String notes = (request.getReason() != null && !request.getReason().isEmpty())
//                ? "Reason: " + request.getReason()
//                : "Status updated to " + request.getAuditDecision().getDisplayName();

            String formattedNotes = processNotesService.formatNotes(request.getCode(), request.getDescription(), request.getReason());
            ClaimResponse.NoteComponent note = new ClaimResponse.NoteComponent();
            note.setText(formattedNotes);
            fhirClaimResponse.addProcessNote(note);

            processNotesService.saveProcessNotes(claimId, request.getAuditDecision(), formattedNotes);
            
            fhirClient.update().resource(fhirClaimResponse).execute();
            log.info("Successfully updated FHIR ClaimResponse status for claim ID: {}", claimId);
        } catch (Exception e) {
            throw new FHIRException("Failed to update FHIR ClaimResponse for claim ID: " + claimId);
        }
    }
    public void sendCallbacksAndWebhooks(String claimId, ClaimResponse fhirClaimResponse , boolean isClaimUpdated, boolean isClaimResponseUpdated, ExtensionStateCodeEnum stateCode) {
        statusUpdateService.triggerCallBackStatusUpdate(claimId, isClaimUpdated, isClaimResponseUpdated, false);
        claimsHandlerUtility.callClaimResponseWebhooks(fhirClaimResponse, claimId, stateCode);
    }

    private void surveillanceLog(InsuranceClaim claim, AuditUpdateRequest request) {
        log.info("Surveillance log");
        String status = request.getAuditDecision().name();
        ExtensionStateCodeEnum previousStatus = claim.getStatus();
        // Use the reusable createSurveillanceLogEntry method
        createSurveillanceLogEntry(claim.getClaimId(), status, previousStatus, request.getReason(), HealthIdContextHolder.userId());
    }

    @Transactional
    public String flagClaimForSurveillance(SurveillanceFlagRequest request) {
        String userId = HealthIdContextHolder.userId();
        String claimId = String.valueOf(request.getClaimId());
        InsuranceClaim claim = validateAndGetClaim(request.getClaimId(), false);
        log.info("Attempting to flag claim {} for surveillance by user: {}", request.getClaimId(), userId);
        if(request.getReason() == null || request.getReason().isEmpty()) {
            throw new BadRequestException("Reason is required");
        }
        //Rejected and Paid claims cannot be flagged for surveillance
        if (claim.getStatus() == ExtensionStateCodeEnum.REJECTED || claim.getStatus() == ExtensionStateCodeEnum.PAYMENT_COMPLETED) {
            log.warn("Claim {} is in status {} and cannot be flagged for surveillance", request.getClaimId(), claim.getStatus());
            throw new BadRequestException("Claims in REJECTED or PAYMENT_COMPLETED status cannot be flagged for surveillance");
        }
        // Check if claim is already flagged for surveillance
        if (claim.isAuditFlag() && ExtensionStateCodeEnum.SENT_TO_SURVEILLANCE.name().equals(claim.getSurveillanceStatus())) {
            log.warn("Claim {} is already flagged for surveillance", request.getClaimId());
            throw new BadRequestException("Claim is already flagged for surveillance");
        }
        // Create AuditUpdateRequest to reuse existing updateFhirResources method
        AuditUpdateRequest auditRequest = new AuditUpdateRequest();
        auditRequest.setClaimId(claimId);
        auditRequest.setAuditDecision(ExtensionStateCodeEnum.SENT_TO_SURVEILLANCE);
        auditRequest.setCode(request.getCode());
        auditRequest.setDescription(request.getDescription());
        auditRequest.setReason(request.getReason());
        // Create surveillance log entry
        if(request.getChangeStatus() != null && request.getChangeStatus()) {
            updateFhirResources(claimId, auditRequest, claim);
            insuranceClaimsService.updateClaimsStatus(claimId, ExtensionStateCodeEnum.SENT_TO_SURVEILLANCE);
        }
        surveillanceLog(claim,auditRequest);
        claim.setAuditFlag(true);
        claim.setSurveillanceStatus(ExtensionStateCodeEnum.SENT_TO_SURVEILLANCE.name());
        insuranceClaimsService.save(claim);
        if(request.getChangeStatus() != null && request.getChangeStatus()) {
            ClaimResponse fhirClaimResponse = readFhirClaimResponse(claimId, claim);
            sendCallbacksAndWebhooks(claimId, fhirClaimResponse, true, true, ExtensionStateCodeEnum.SENT_TO_SURVEILLANCE);
        }

        log.info("Successfully flagged claim {} for surveillance by user: {}", request.getClaimId(), userId);
        return "Success";
    }

    private void createSurveillanceLogEntry(UUID claimId, String status, ExtensionStateCodeEnum previousStatus, String reason, String createdBy) {
        SurveillanceLog surveillanceLog = new SurveillanceLog();
        surveillanceLog.setClaimId(claimId);
        surveillanceLog.setStatus(status);
        surveillanceLog.setPreviousStatus(previousStatus.name());
        surveillanceLog.setReason(reason);
        surveillanceLog.setCreatedBy(createdBy);
        surveillanceLogRepository.save(surveillanceLog);
        log.info("Surveillance log entry created for claim: {} with status: {} by user: {}", claimId, status, createdBy);
    }

    public List<SurveillanceLogResponse> findSurveillanceLogByClaimId(String claimId) {
        List<SurveillanceLog> logs = surveillanceLogRepository.findByClaimIdOrderByCreationDateDesc(UUID.fromString(claimId));

        return logs.stream()
                .map(log -> SurveillanceLogResponse.builder()
                        .status(log.getStatus())
                        .previousStatus(log.getPreviousStatus())
                        .reason(log.getReason())
                        .creationDate(log.getCreationDate())
                        .lastModifiedDate(log.getLastModifiedDate())
                        .build())
                .collect(Collectors.toList());
    }
}