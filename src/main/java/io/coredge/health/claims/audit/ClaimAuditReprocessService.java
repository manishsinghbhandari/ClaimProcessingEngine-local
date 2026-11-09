package io.coredge.health.claims.audit;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.coredge.health.claims.constants.Constants;
import io.coredge.health.claims.context.HealthIdContextHolder;
import io.coredge.health.claims.db.entity.*;
import io.coredge.health.claims.db.repositories.BenefitsMasterRepository;
import io.coredge.health.claims.db.repositories.ErpClaimsRepository;
import io.coredge.health.claims.db.repositories.LineItemRepository;
import io.coredge.health.claims.db.repositories.SurveillanceLogRepository;
import io.coredge.health.claims.enums.ClaimType;
import io.coredge.health.claims.enums.ExtensionStateCodeEnum;
import io.coredge.health.claims.exception.app.BadRequestException;
import io.coredge.health.claims.exception.app.DatabaseException;
import io.coredge.health.claims.exception.base.SystemException;
import io.coredge.health.claims.helpers.ClaimPaymentHelper;
import io.coredge.health.claims.service.CamundaService;
import io.coredge.health.claims.service.ErpCallBackService;
import io.coredge.health.claims.service.ErpClaimsService;
import io.coredge.health.claims.service.InsuranceClaimsService;
import io.coredge.health.claims.service.ProcessNotesService;
import io.coredge.health.claims.service.StatusUpdateService;
import io.coredge.health.claims.service.TariffThresholdService;
import io.coredge.health.claims.utils.ClaimsHandlerUtility;
import io.coredge.health.claims.worker.SendClaimForPaymentHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.*;
import org.hl7.fhir.r4.model.codesystems.Adjudication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.DisposableBean;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
public class ClaimAuditReprocessService implements InitializingBean, DisposableBean {

    @Value("${audit.reprocess.scheduler.enable:true}")
    private Boolean enableScheduler;

    @Value("${audit.reprocess.batch.size:10}")
    private int batchSize;

    @Value("${audit.reprocess.max.retries:3}")
    private int maxRetries;

    @Value("${audit.reprocess.retry.delay.ms:5000}")
    private long retryDelayMs;

    private ScheduledExecutorService erpRetryExecutor;

    private final ClaimAuditRepository claimAuditRepository;
    private final ClaimPaymentHelper claimPaymentHelper;
    private final InsuranceClaimsService insuranceClaimsService;
    private final ClaimsHandlerUtility claimsHandlerUtility;
    private final StatusUpdateService statusUpdateService;
    private final LineItemRepository lineItemRepository;
    private final ProcessNotesService processNotesService;
    private final BenefitsMasterRepository benefitsMasterRepository;
    private final ErpCallBackService erpCallBackService;
    private final ErpClaimsService erpClaimsService;
    private final ErpClaimsRepository erpClaimsRepository;
    private final CamundaService camundaService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private Long lastProcessedId = 0L;

    @Autowired
    public ClaimAuditReprocessService(ClaimAuditRepository claimAuditRepository, ClaimPaymentHelper claimPaymentHelper,
            InsuranceClaimsService insuranceClaimsService, ClaimsHandlerUtility claimsHandlerUtility,
            StatusUpdateService statusUpdateService, LineItemRepository lineItemRepository,
            ProcessNotesService processNotesService, BenefitsMasterRepository benefitsMasterRepository,
            ErpCallBackService erpCallBackService, ErpClaimsService erpClaimsService,
            ErpClaimsRepository erpClaimsRepository, CamundaService camundaService) {
        this.claimAuditRepository = claimAuditRepository;
        this.claimPaymentHelper = claimPaymentHelper;
        this.insuranceClaimsService = insuranceClaimsService;
        this.claimsHandlerUtility = claimsHandlerUtility;
        this.statusUpdateService = statusUpdateService;
        this.lineItemRepository = lineItemRepository;
        this.processNotesService = processNotesService;
        this.benefitsMasterRepository = benefitsMasterRepository;
        this.erpCallBackService = erpCallBackService;
        this.erpClaimsService = erpClaimsService;
        this.erpClaimsRepository = erpClaimsRepository;
        this.camundaService = camundaService;
    }

    @Autowired
    private SurveillanceLogRepository surveillanceLogRepository;

    @Autowired
    private SendClaimForPaymentHandler sendClaimForPaymentHandler;

    @Autowired
    private TariffThresholdService tariffThresholdService;

    @Override
    public void afterPropertiesSet() {
        this.erpRetryExecutor = Executors.newScheduledThreadPool(batchSize);
    }

    @Override
    public void destroy() {
        if (this.erpRetryExecutor != null) {
            this.erpRetryExecutor.shutdown();
        }
    }

    /**
     * Scheduled task to process pending claims for reprocessing.
     * Runs every 2 minutes to check for unprocessed claims and reprocess them.
     */
    @Scheduled(fixedRateString = "${audit.reprocess.scheduler.delay:120000}")
    public void processPendingClaimsForReprocessing() {
        if (!enableScheduler) {
            log.debug("Audit reprocess scheduler is disabled.");
            return;
        }

        log.info("Checking for pending claims for reprocessing...");

        try {
            Pageable pageable = PageRequest.of(0, batchSize);
            List<ClaimAudit> pendingClaims = claimAuditRepository.fetchUnprocessedAfterId(lastProcessedId, pageable);

            if (pendingClaims.isEmpty()) {
                log.info("No pending claims found for reprocessing.");
                return;
            }

            log.info("Found {} pending claims for reprocessing. Starting batch processing...", pendingClaims.size());

            // Process the batch
            processBatch(pendingClaims, 1);

            // Update the last processed ID only for claims that were successfully processed
            // This allows REPROCESS failures to be retried on next scheduler run
            Long maxSuccessfullyProcessedId = pendingClaims.stream()
                    .filter(ClaimAudit::isProcessed)
                    .mapToLong(ClaimAudit::getId)
                    .max()
                    .orElse(0L);

            if (maxSuccessfullyProcessedId > lastProcessedId) {
                lastProcessedId = maxSuccessfullyProcessedId;
                log.info("Completed processing batch. Updated lastProcessedId to: {}", lastProcessedId);
            } else {
                log.info("Completed processing batch. lastProcessedId unchanged: {} (some claims pending retry)",
                        lastProcessedId);
            }

        } catch (Exception e) {
            log.error("Error occurred while processing pending claims for reprocessing: {}", e.getMessage(), e);
        }
    }

    // Each batch will be processed on its own virtual thread
    private void processBatch(List<ClaimAudit> batch, int batchNumber) {
        int processed = 0;
        for (ClaimAudit claimAudit : batch) {
            try {
                log.info("[Batch {}] Processing claimId={}, auditId={}", batchNumber, claimAudit.getClaimId(),
                        claimAudit.getId());
                reprocessAuditedClaim(claimAudit);
                log.info("[Batch {}] Successfully processed claimId={}, auditId={}", batchNumber,
                        claimAudit.getClaimId(), claimAudit.getId());
                processed++;
            } catch (Exception ex) {
                log.warn("[Batch {}] Failed to process claimId={}, auditId={}. Error: {}", batchNumber,
                        claimAudit.getClaimId(), claimAudit.getId(), ex.getMessage(), ex);
            }
        }
        log.info("[Batch {}] Completed. Total records processed: {}", batchNumber, processed);
    }

    public void reprocessAuditedClaim(ClaimAudit claimAudit) {
        log.info("Starting claim audit processing at {},  ClaimId : {}", LocalDateTime.now(),
                claimAudit.getClaimId());
        try {
            String claimId = String.valueOf(claimAudit.getClaimId());
            InsuranceClaim claimDetailsDB = insuranceClaimsService.getByExternalID(claimId);
            Claim claim = claimPaymentHelper.getClaimObject(claimId);
            ClaimResponse claimResponse = getClaimResponse(claimDetailsDB);

            // Handle REPROCESS case separately - doesn't require intervention code/line
            // items
            if (claimAudit.getAuditStatus().equalsIgnoreCase(ExtensionStateCodeEnum.REPROCESS.name())) {
                asyncReprocessDeskReviewedClaim(claimAudit, claimId, claimDetailsDB.getClaimNumber(), claim,
                        claimResponse,
                        1);
                return;
            }

            // 1. Get the line items
            String interventionCode = claimAudit.getInterventionCode(); // form the audit object.
            var claimItems = insuranceClaimsService.getClaimItems(claimId)
                    .stream()
                    .filter(c -> interventionCode.equals(c.getCode()))
                    .filter(c -> claimAudit.getSequence() == null || claimAudit.getSequence().equals(c.getSequence()))
                    .findFirst().orElseThrow();
            if (claimAudit.getAuditStatus().equalsIgnoreCase(ExtensionStateCodeEnum.REJECTED.name())) {
                processRejectedClaims(claimItems, claimAudit, claimDetailsDB, claimId, claim, claimResponse);
            } else if (claimAudit.getAuditStatus().equalsIgnoreCase(ExtensionStateCodeEnum.PARTIAL_APPROVAL.name())) {
                processPartialApprovedClaims(claimItems, claimAudit, claimId, claim, claimResponse, true);
            } else if (claimAudit.getAuditStatus().equalsIgnoreCase(ExtensionStateCodeEnum.MISSING_DOCUMENTS.name())) {
                processMissingDocumentsClaims(claimItems, claimAudit, claimDetailsDB, claimId, claim, claimResponse);
            } else if (claimAudit.getAuditStatus()
                    .equalsIgnoreCase(ExtensionStateCodeEnum.SENT_TO_SURVEILLANCE.name())) {
                processSurveillanceClaims(claimItems, claimAudit, claimDetailsDB, claimId, claim, claimResponse);
            } else if (claimAudit.getAuditStatus().equalsIgnoreCase(ExtensionStateCodeEnum.SENT_BACK.name())) {
                processSentBackClaims(claimItems, claimAudit, claimDetailsDB, claimId, claim, claimResponse);
            } else if (claimAudit.getAuditStatus().equalsIgnoreCase(ExtensionStateCodeEnum.PAYMENT_COMPLETED.name())) {
                processPaymentCompletedClaims(claimItems, claimAudit, claimDetailsDB, claimId, claim, claimResponse);
            } else if (claimAudit.getAuditStatus().equalsIgnoreCase(ExtensionStateCodeEnum.APPROVED.name())) {
                // if the claim is approved. currently do nothing.
                log.info("Audit process, Claim is approved, [APPROVED] : {} ", claimAudit.getClaimId());
            } else {
                throw new BadRequestException("Audit process, Invalid audit status: " + claimAudit.getAuditStatus());
            }
            // Mark the data is processed
            markAsProcessed(claimAudit, true, "Success");
        } catch (Exception e) {
            log.warn("Audit process, Claims is failed : {} ", e.getMessage());
            // Check for PARTIAL_APPROVAL claims and flag them again for surveillance
            handleFailedPartialApprovalClaim(claimAudit);
            markAsProcessed(claimAudit, true, e.getMessage());
        } finally {
            log.info("Saving Audit : {}", claimAudit.getClaimId());
            createOrUpdate(claimAudit);
            // Trigger workflow after audit is saved and processed
            if (claimAudit.isProcessed()
                    && claimAudit.getAuditStatus().equalsIgnoreCase(ExtensionStateCodeEnum.PARTIAL_APPROVAL.name())
                    && claimAudit.getProcessRemarks().equalsIgnoreCase("Success")) {
                asyncTriggerWorkflowForPartialApproval(claimAudit.getClaimId().toString());
            }
        }
    }

    private void processRejectedClaims(ClaimLineItem claimItems, ClaimAudit claimAudit, InsuranceClaim claimDetailsDB,
            String claimId, Claim claim, ClaimResponse claimResponse) {
        log.warn("Audit process, Rejected claimId={}, due to business validation failure: {}",
                claimAudit.getClaimId(), claimAudit.getRemarks());

        // Also update the claim line items
        claimItems.setStatus(Constants.ClaimLineItemStatus.REJECTED);
        claimItems.setJustification("Rejected by SHA");
        claimItems.setRejectionReason(claimAudit.getRemarks());
        claimItems.setExplanation(claimAudit.getExtraRemarks());

        lineItemRepository.save(claimItems);

        // Add notes to claim response
        addNotesToClaimResponse(claimId, claimResponse, BigDecimal.ZERO, BigDecimal.ZERO,
                buildRemarks(claimAudit.getRemarks(), claimAudit.getExtraRemarks()), true,
                ExtensionStateCodeEnum.REJECTED.name());
        // Update the status to REJECTED in FHIR and Database.
        log.info("Audit process, Updating Claims status rejected in FHIR && DB : {} ", claimAudit.getClaimId());
        updateStatusFHIR(claimId, claim, claimResponse, ExtensionStateCodeEnum.REJECTED);
    }

    private void processMissingDocumentsClaims(ClaimLineItem claimItems, ClaimAudit claimAudit,
            InsuranceClaim claimDetailsDB, String claimId, Claim claim, ClaimResponse claimResponse) {
        log.warn("Audit process, Missing Document claimId={}, due to business validation failure: {}",
                claimAudit.getClaimId(), claimAudit.getRemarks());
        createSurveillanceLogEntry(UUID.fromString(claimId), ExtensionStateCodeEnum.MISSING_DOCUMENTS.name(),
                claimDetailsDB.getStatus(), buildRemarks(claimAudit.getRemarks(), claimAudit.getExtraRemarks()),
                HealthIdContextHolder.userId());
        claimDetailsDB.setAuditFlag(true);
        claimDetailsDB.setSurveillanceStatus(ExtensionStateCodeEnum.MISSING_DOCUMENTS.name());
        insuranceClaimsService.save(claimDetailsDB);
        // Add notes to claim response
        addNotesToClaimResponse(claimId, claimResponse, BigDecimal.ZERO, BigDecimal.ZERO,
                buildRemarks(claimAudit.getRemarks(), claimAudit.getExtraRemarks()), true,
                ExtensionStateCodeEnum.MISSING_DOCUMENTS.name());
        // Update the status to MISSING_DOCUMENTS in FHIR and Database.
        log.info("Audit process, Updating Claims status missing documents in FHIR && DB : {} ",
                claimAudit.getClaimId());
        updateStatusFHIR(claimId, claim, claimResponse, ExtensionStateCodeEnum.MISSING_DOCUMENTS);
    }

    private void processSurveillanceClaims(ClaimLineItem claimItems, ClaimAudit claimAudit,
            InsuranceClaim claimDetailsDB, String claimId, Claim claim, ClaimResponse claimResponse) {
        log.warn("Marking claimId={}, for surveillance due to business validation: {}",
                claimAudit.getClaimId(), claimAudit.getRemarks());
        createSurveillanceLogEntry(UUID.fromString(claimId), ExtensionStateCodeEnum.SENT_TO_SURVEILLANCE.name(),
                claimDetailsDB.getStatus(), buildRemarks(claimAudit.getRemarks(), claimAudit.getExtraRemarks()),
                HealthIdContextHolder.userId());
        claimDetailsDB.setAuditFlag(true);
        claimDetailsDB.setSurveillanceStatus(ExtensionStateCodeEnum.SENT_TO_SURVEILLANCE.name());
        insuranceClaimsService.save(claimDetailsDB);
        // Add notes to claim response
        addNotesToClaimResponse(claimId, claimResponse, BigDecimal.ZERO, BigDecimal.ZERO,
                buildRemarks(claimAudit.getRemarks(), claimAudit.getExtraRemarks()), true,
                ExtensionStateCodeEnum.SENT_TO_SURVEILLANCE.name());
        // Update the status to SENT_TO_SURVEILLANCE in FHIR and Database.
        log.info("Audit process, Updating Claims status sent to surveillance in FHIR && DB : {} ",
                claimAudit.getClaimId());
        updateStatusFHIR(claimId, claim, claimResponse, ExtensionStateCodeEnum.SENT_TO_SURVEILLANCE);
    }

    private void processSentBackClaims(ClaimLineItem claimItems, ClaimAudit claimAudit, InsuranceClaim claimDetailsDB,
            String claimId, Claim claim, ClaimResponse claimResponse) {
        log.warn("Marking claimId={}, as sent back due to business validation: {}",
                claimAudit.getClaimId(), claimAudit.getRemarks());
        claimDetailsDB.setAuditFlag(true);
        claimDetailsDB.setSurveillanceStatus(ExtensionStateCodeEnum.SENT_BACK.name());
        insuranceClaimsService.save(claimDetailsDB);
        // Add notes to claim response
        addNotesToClaimResponse(claimId, claimResponse, BigDecimal.ZERO, BigDecimal.ZERO,
                buildRemarks(claimAudit.getRemarks(), claimAudit.getExtraRemarks()), true,
                ExtensionStateCodeEnum.SENT_BACK.name());
        // Update the status to SENT_BACK in FHIR and Database.
        log.info("Audit process, Updating Claims status sent back in FHIR && DB : {} ", claimAudit.getClaimId());
        updateStatusFHIR(claimId, claim, claimResponse, ExtensionStateCodeEnum.SENT_BACK);
    }

    private void processPaymentCompletedClaims(ClaimLineItem claimItems, ClaimAudit claimAudit,
            InsuranceClaim claimDetailsDB, String claimId, Claim claim, ClaimResponse claimResponse) {
        log.info("Marking claimId={}, as payment completed: {}",
                claimAudit.getClaimId(), claimAudit.getRemarks());
        claimDetailsDB.setSurveillanceStatus(ExtensionStateCodeEnum.PAYMENT_COMPLETED.name());
        insuranceClaimsService.save(claimDetailsDB);
        // Add notes to claim response
        addNotesToClaimResponse(claimId, claimResponse, BigDecimal.ZERO, BigDecimal.ZERO,
                buildRemarks(claimAudit.getRemarks(), claimAudit.getExtraRemarks()), false,
                ExtensionStateCodeEnum.PAYMENT_COMPLETED.name());
        // Update the status to PAYMENT_COMPLETED in FHIR and Database.
        log.info("Audit process, Updating Claims status payment completed in FHIR && DB : {} ",
                claimAudit.getClaimId());
        updateStatusFHIR(claimId, claim, claimResponse, ExtensionStateCodeEnum.PAYMENT_COMPLETED);
    }

    // Partial Approval
    public void processPartialApprovedClaims(ClaimLineItem claimItems, ClaimAudit claimAudit, String claimId,
            Claim claim,
            ClaimResponse claimResponse, boolean triggerCallBack) throws BadRequestException {
        log.info("Claim partially approved. claimId={}, approvedLOS={}, approvedAmount={}, remarks={}",
                claimAudit.getClaimId(), claimAudit.getApprovedAuditLos(), claimAudit.getApprovedAmount(),
                claimAudit.getRemarks());
        if (claim.getUse().name().equalsIgnoreCase(ClaimType.PREAUTHORIZATION.name())) {
            log.info("Claim type is not supported");
            throw new BadRequestException("Claim type is not supported");
        }
        // Check if record already exists or already processed
        boolean recordExists = claimAuditRepository.existsByClaimIdAndInterventionCodeAndStatusAndProcessRemarks(
                claimAudit.getClaimId(),
                claimAudit.getInterventionCode(),
                claimAudit.getAuditStatus(),
                "Success");

        if (recordExists) {
            log.info("Record already exists or already processed for claimId={}, interventionCode={}, status={}",
                    claimAudit.getClaimId(), claimAudit.getInterventionCode(), claimAudit.getAuditStatus());
            throw new BadRequestException("Record already exists or already processed"); // Skip processing
        }

        Integer approvedLos = claimAudit.getApprovedAuditLos() != null ? claimAudit.getApprovedAuditLos() : null;
        BigDecimal approvedAmount = claimAudit.getApprovedAmount();

        // Enhanced reprocessing logic based on what data is provided
        if (approvedLos != null && approvedAmount != null && approvedLos > 0
                && approvedAmount.compareTo(BigDecimal.ZERO) > 0) {
            // Both LOS and Approved Amount provided - Amount takes precedence but LOS fields are also updated
            validateApprovedLos(claimItems, approvedLos, claimId);
            validateApprovedAmount(claimItems, approvedAmount, claimId);
            processBothLosAndAmount(claimItems, claimAudit, claimId, claim, claimResponse, approvedLos, approvedAmount);
        } else if (approvedLos != null && approvedAmount == null && approvedLos > 0) {
            // Only LOS provided
            validateApprovedLos(claimItems, approvedLos, claimId);
            processLosOnly(claimItems, claimAudit, claimId, claim, claimResponse, approvedLos);
        } else if (approvedLos == null && approvedAmount != null && approvedAmount.compareTo(BigDecimal.ZERO) > 0) {
            // Only Approved Amount provided
            validateApprovedAmount(claimItems, approvedAmount, claimId);
            processApprovedAmountOnly(claimItems, claimAudit, claimId, claim, claimResponse, approvedAmount);
        } else {
            log.warn("Neither LOS nor Approved Amount provided or greater than zero for claim: {}", claimId);
            throw new BadRequestException("Either LOS or Approved Amount must be provided and be greater than zero");
        }

        // Update claim table approvedAmount as sum of all claim_items approvedAmount
        updateClaimApprovedTotal(claimId);

        // Update the FHIR
        log.info("Audit process, Updating Claims status partial approved in FHIR && DB : {} ",
                claimAudit.getClaimId());
        updateStatusFHIR(claimId, claim, claimResponse, ExtensionStateCodeEnum.APPROVED, triggerCallBack);
    }

    /**
     * Process claim when only LOS is provided
     * Derive approved amount using per diem rate and update both LOS and approved
     * amount
     */
    private void processLosOnly(ClaimLineItem claimItems, ClaimAudit claimAudit, String claimId,
            Claim claim, ClaimResponse claimResponse, Integer approvedLos) throws BadRequestException {
        log.info("Processing LOS-only scenario for claimId: {}, approvedLOS: {}", claimId, approvedLos);

        try {
            // Calculate approved amount using per diem rate from BenefitsMaster table
            BigDecimal calculatedApprovedAmount = calculateApprovedAmountFromLos(claimItems, approvedLos, true);

            // Update adjudication_params with both approvedLos and approvedAmount
            String updatedAdjudicationParams = updateAdjudicationParamsForLos(claimItems.getAdjudicationParams(),
                    approvedLos, calculatedApprovedAmount);

            // Update adjudication_rules with approvedLos and approvedAmount
            String updatedAdjudicationRules = updateAdjudicationRules(claimItems.getAdjudicationRule(), approvedLos,
                    calculatedApprovedAmount);
            claimItems.setAdjudicationRule(updatedAdjudicationRules);

            // Update LOS utilization - update service period dates for claim_items and claims
            updateLosUtilization(claimItems, claim, approvedLos);

            // Process claim approval with calculated amount
            processClaimApproval(claimItems, claimAudit, claimId, claim, claimResponse, calculatedApprovedAmount,
                    updatedAdjudicationParams);

        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error processing LOS-only scenario for claimId: {}", claimId, e);
            throw new BadRequestException("Failed to process LOS-only approval: " + e.getMessage());
        }
    }

    /**
     * Process claim when only approved amount is provided
     * Update only approvedAmount fields
     */
    private void processApprovedAmountOnly(ClaimLineItem claimItems, ClaimAudit claimAudit, String claimId,
            Claim claim, ClaimResponse claimResponse, BigDecimal approvedAmount) throws BadRequestException {
        log.info("Processing approved amount-only scenario for claimId: {}, approvedAmount: {}", claimId,
                approvedAmount);

        try {
            // Update adjudication_params with approvedAmount only
            String updatedAdjudicationParams = updateAdjudicationParamsForAmount(claimItems.getAdjudicationParams(),
                    approvedAmount);

            // Update adjudication_rules with approvedAmount only
            String updatedAdjudicationRules = updateAdjudicationRules(claimItems.getAdjudicationRule(), null,
                    approvedAmount);
            claimItems.setAdjudicationRule(updatedAdjudicationRules);

            // Process claim approval with provided amount
            processClaimApproval(claimItems, claimAudit, claimId, claim, claimResponse, approvedAmount,
                    updatedAdjudicationParams);

        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error processing approved amount-only scenario for claimId: {}", claimId, e);
            throw new BadRequestException("Failed to process approved amount-only approval: " + e.getMessage());
        }
    }

    /**
     * Process claim when both LOS and approved amount are provided
     * Amount takes precedence but LOS-related fields and dates are also updated
     */
    private void processBothLosAndAmount(ClaimLineItem claimItems, ClaimAudit claimAudit, String claimId,
            Claim claim, ClaimResponse claimResponse, Integer approvedLos,
            BigDecimal approvedAmount) throws BadRequestException {
        log.info("Processing both LOS and amount scenario for claimId: {}, approvedLOS: {}, approvedAmount: {}",
                claimId, approvedLos, approvedAmount);

        try {
            // calculate amount from LOS to validate against provided amount
            BigDecimal calculatedApprovedAmount = calculateApprovedAmountFromLos(claimItems, approvedLos, false);
            // use the lower of the two amounts
            if (approvedAmount.compareTo(calculatedApprovedAmount) > 0) {
                log.warn(
                        "Provided approved amount {} exceeds calculated amount {} from LOS for claimId: {}. Using calculated amount.",
                        approvedAmount, calculatedApprovedAmount, claimId);
                approvedAmount = calculatedApprovedAmount;
            }
            // Update adjudication_params with both approvedLos and approvedAmount
            String updatedAdjudicationParams = updateAdjudicationParamsForLos(claimItems.getAdjudicationParams(),
                    approvedLos, approvedAmount);

            // Update adjudication_rules with both approvedLos and approvedAmount
            String updatedAdjudicationRules = updateAdjudicationRules(claimItems.getAdjudicationRule(), approvedLos,
                    approvedAmount);
            claimItems.setAdjudicationRule(updatedAdjudicationRules);

            // Update LOS utilization - update service period dates for claim_items and claims
            updateLosUtilization(claimItems, claim, approvedLos);

            // Process claim approval with provided amount (amount takes precedence)
            processClaimApproval(claimItems, claimAudit, claimId, claim, claimResponse, approvedAmount,
                    updatedAdjudicationParams);

            log.info("Successfully processed both LOS and amount scenario for claimId: {}", claimId);

        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error processing both LOS and amount scenario for claimId: {}", claimId, e);
            throw new BadRequestException("Failed to process both LOS and amount approval: " + e.getMessage());
        }
    }

    /**
     * Common method to process claim approval
     * This reduces code duplication and improves maintainability
     */
    private void processClaimApproval(ClaimLineItem claimItems, ClaimAudit claimAudit, String claimId,
            Claim claim, ClaimResponse claimResponse, BigDecimal approvedAmount,
            String updatedAdjudicationParams) throws BadRequestException {
        try {
            // Update adjudication_params
            claimItems.setAdjudicationParams(updatedAdjudicationParams);

            // Get claim total once for reuse
            BigDecimal claimTotal = claim.getTotal().getValue();

            // Update claim_items table
            updateClaimItemsForApproval(claimItems, claimAudit, approvedAmount, claimTotal);

            // Update claim response
            updateApprovedPriceInClaimResponse(claim, claimResponse, claimAudit.getInterventionCode(),
                    claimAudit.getSequence(), approvedAmount);

            // Add notes
            String remarks = buildRemarks(claimAudit.getRemarks(), claimAudit.getExtraRemarks());
            addNotesToClaimResponse(claimId, claimResponse, claimTotal, approvedAmount, remarks, false,
                    ExtensionStateCodeEnum.APPROVED.name());

            log.info("Successfully processed claim approval for claimId: {}, approvedAmount: {}", claimId,
                    approvedAmount);

        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error processing claim approval for claimId: {}", claimId, e);
            throw new BadRequestException("Failed to process claim approval: " + e.getMessage());
        }
    }

    /**
     * Calculate approved amount from LOS using per diem rate from BenefitsMaster
     * table
     * Validates that calculated amount does not exceed claim amount
     */
    private BigDecimal calculateApprovedAmountFromLos(ClaimLineItem claimItems, Integer approvedLos,
            Boolean validateAmount) {
        try {
            // Check for tariff threshold logic (e.g., SHA-03 12-day rule)
            String interventionCode = claimItems.getCode();
            String providerLevel = claimItems.getClaim() != null && claimItems.getClaim().getProvider() != null
                    ? claimItems.getClaim().getProvider().getLevel() : null;
            Optional<BenefitsMaster> benefitsOpt = benefitsMasterRepository
                    .findByInterventionCodeAndProviderPaymentMechanism(interventionCode, "PER DIEM");
            if (benefitsOpt.isPresent()) {
                Optional<BigDecimal> thresholdAmount = tariffThresholdService
                        .calculateApprovedAmount(interventionCode, approvedLos, providerLevel, benefitsOpt.get());
                if (thresholdAmount.isPresent()) {
                    BigDecimal calculatedAmount = thresholdAmount.get();
                    log.info("Tariff threshold applied in reprocess: intervention={}, LOS={}, amount={}",
                            interventionCode, approvedLos, calculatedAmount);
                    if (validateAmount) {
                        validateApprovedAmount(claimItems, calculatedAmount,
                                String.valueOf(claimItems.getClaim().getClaimId()));
                    }
                    return calculatedAmount;
                }
            }

            // Default: flat per diem rate
            BigDecimal perDiemRate = extractPerDiemRate(claimItems, claimItems.getClaim());
            BigDecimal calculatedAmount = perDiemRate.multiply(BigDecimal.valueOf(approvedLos));
            log.info("Calculated approved amount: {} (LOS: {} * Per diem: {})", calculatedAmount, approvedLos,
                    perDiemRate);
            if (validateAmount) {
                // Validate that calculated amount does not exceed claim amount
                validateApprovedAmount(claimItems, calculatedAmount,
                        String.valueOf(claimItems.getClaim().getClaimId()));
            }
            return calculatedAmount;
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error calculating approved amount from LOS");
            throw new BadRequestException("Failed to calculate approved amount from LOS");
        }
    }

    /**
     * Extract per diem rate from BenefitsMaster table based on intervention code
     * and provider level
     */
    private BigDecimal extractPerDiemRate(ClaimLineItem claimItem, InsuranceClaim claim) {
        BigDecimal perDiemFromBenefits = getPerDiemRateFromBenefitsMaster(claimItem.getCode(), claim.getProvider());
        if (perDiemFromBenefits != null) {
            log.info("Per diem rate {} extracted from BenefitsMaster for intervention code: {} and provider level: {}",
                    perDiemFromBenefits, claimItem.getCode(),
                    claim.getProvider() != null ? claim.getProvider().getLevel() : "unknown");
            return perDiemFromBenefits;
        }
        log.error("Per diem rate not found in BenefitsMaster for intervention code: {} and provider level: {}",
                claimItem.getCode(), claim.getProvider() != null ? claim.getProvider().getLevel() : "unknown");
        throw new BadRequestException("Per diem rate not found");
    }

    /**
     * Get per diem rate from BenefitsMaster table based on intervention code and
     * provider level
     */
    private BigDecimal getPerDiemRateFromBenefitsMaster(String interventionCode, Provider provider) {
        if (interventionCode == null || provider == null) {
            log.warn("Missing intervention code or provider for BenefitsMaster lookup");
            return null;
        }

        try {
            // Find BenefitsMaster record for PER DIEM payment mechanism
            Optional<BenefitsMaster> benefitsOpt = benefitsMasterRepository
                    .findByInterventionCodeAndProviderPaymentMechanism(interventionCode, "PER DIEM");

            if (benefitsOpt.isEmpty()) {
                log.warn("No BenefitsMaster record found for intervention code: {} with PER DIEM payment mechanism",
                        interventionCode);
                return null;
            }

            BenefitsMaster benefits = benefitsOpt.get();

            // Extract provider level number from provider level string
            Integer providerLevelNumber = extractProviderLevelNumber(provider.getLevel());
            if (providerLevelNumber == null) {
                log.warn("Could not extract provider level number from: {}", provider.getLevel());
                return null;
            }

            // Get appropriate tariff based on provider level
            Integer tariffValue = getTariffByLevel(benefits, providerLevelNumber);
            if (tariffValue == null || tariffValue <= 0) {
                log.warn("No tariff found for provider level {} in BenefitsMaster", providerLevelNumber);
                return null;
            }

            // Convert tariff integer to BigDecimal
            return new BigDecimal(tariffValue);

        } catch (Exception e) {
            log.error("Error getting per diem rate");
            return null;
        }
    }

    /**
     * Extract numeric provider level from provider level string
     * Examples: "level 2" -> 2, "level-4" -> 4, "level 6a" -> 6, "level-3a" -> 3
     */
    private Integer extractProviderLevelNumber(String providerLevel) {
        if (providerLevel == null || providerLevel.trim().isEmpty()) {
            return null;
        }
        try {
            // Extract first number found
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(\\d+)");
            java.util.regex.Matcher matcher = pattern.matcher(providerLevel);
            if (matcher.find()) {
                int level = Integer.parseInt(matcher.group(1));
                // Validate level is within expected range (2-6)
                if (level >= 2 && level <= 6) {
                    return level;
                }
            }
            log.warn("Could not extract valid provider level number from: {}", providerLevel);
            return null;
        } catch (Exception e) {
            log.warn("Error extracting provider level number from '{}'", providerLevel);
            return null;
        }
    }

    /**
     * Get tariff value from BenefitsMaster based on provider level number
     */
    private Integer getTariffByLevel(BenefitsMaster benefits, Integer levelNumber) {
        return switch (levelNumber) {
            case 2 -> benefits.getLevel2Tariff();
            case 3 -> benefits.getLevel3Tariff();
            case 4 -> benefits.getLevel4Tariff();
            case 5 -> benefits.getLevel5Tariff();
            case 6 -> benefits.getLevel6Tariff();
            default -> {
                log.warn("Unsupported provider level: {}", levelNumber);
                yield null;
            }
        };
    }

    /**
     * Update adjudication_params for LOS scenario (both approvedLos and
     * approvedAmount)
     */
    private String updateAdjudicationParamsForLos(String adjudicationParams, Integer approvedLos,
            BigDecimal approvedAmount) {
        try {
            Map<String, Object> paramMap = objectMapper.readValue(adjudicationParams,
                    new TypeReference<Map<String, Object>>() {
                    });

            // Update both approvedLos and approvedAmount
            paramMap.put("approvedLos", approvedLos);
            paramMap.put("approvedAmount", approvedAmount);

            log.info("Updated adjudication_params with approvedLos: {} and approvedAmount: {}", approvedLos,
                    approvedAmount);
            return objectMapper.writeValueAsString(paramMap);
        } catch (Exception e) {
            log.error("Error updating adjudication_params for LOS scenario");
            throw new BadRequestException("Failed to update adjudication_params");
        }
    }

    /**
     * Update adjudication_params for approved amount scenario (approvedAmount only)
     */
    private String updateAdjudicationParamsForAmount(String adjudicationParams, BigDecimal approvedAmount) {
        try {
            Map<String, Object> paramMap = objectMapper.readValue(adjudicationParams,
                    new TypeReference<Map<String, Object>>() {
                    });

            // Update only approvedAmount
            paramMap.put("approvedAmount", approvedAmount);

            log.info("Updated adjudication_params with approvedAmount: {}", approvedAmount);
            return objectMapper.writeValueAsString(paramMap);
        } catch (Exception e) {
            log.error("Error updating adjudication_params for amount scenario: {}", e.getMessage(), e);
            throw new BadRequestException("Failed to update adjudication_params", e);
        }
    }

    /**
     * Update claim_items table fields for approval with fund deduction logic
     */
    private void updateClaimItemsForApproval(ClaimLineItem claimItems, ClaimAudit claimAudit,
            BigDecimal approvedAmount, BigDecimal requestedAmount) {
        // Store original fund amounts with null safety
        BigDecimal originalPmsf = Objects.requireNonNullElse(claimItems.getPmfAmount(), BigDecimal.ZERO);
        BigDecimal originalEccif = Objects.requireNonNullElse(claimItems.getEccifAmount(), BigDecimal.ZERO);
        BigDecimal originalTotal = Objects.requireNonNullElse(claimItems.getNetValue(), requestedAmount);
        log.info("Original fund allocation - PMSF: {}, ECCIF: {}, Total: {}",
                originalPmsf, originalEccif, originalTotal);

        // Calculate new fund distribution
        var newFunds = calculateFundDistribution(originalPmsf, originalEccif, originalTotal, approvedAmount);

        // Update claim item fields in a single transaction-safe operation
        updateClaimItemFields(claimItems, claimAudit, approvedAmount, requestedAmount, newFunds);

        log.info("Successfully updated claim item with approved amount: {}", approvedAmount);
    }

    /**
     * Update claim item fields with calculated fund distribution
     */
    private void updateClaimItemFields(ClaimLineItem claimItems, ClaimAudit claimAudit,
            BigDecimal approvedAmount, BigDecimal requestedAmount,
            BigDecimal[] fundDistribution) {
        claimItems.setRequestedAmount(requestedAmount);
        claimItems.setApprovedPrice(approvedAmount);
        claimItems.setApprovedQuantity(1);
        claimItems.setJustification("Partial Approved by SHA");
        claimItems.setRejectionReason(claimAudit.getRemarks());
        claimItems.setExplanation(claimAudit.getExtraRemarks());

        // Update fund amounts based on calculated distribution
        claimItems.setPmfAmount(fundDistribution[0]); // PMSF
        claimItems.setEccifAmount(fundDistribution[1]); // ECCIF
        // SHIF amount = fundDistribution[2] (calculated but not stored directly)

        try {
            lineItemRepository.save(claimItems);
            log.info("Updated claim_items with fund distribution - PMSF: {}, ECCIF: {}, SHIF: {}",
                    fundDistribution[0], fundDistribution[1], fundDistribution[2]);
        } catch (Exception e) {
            log.error("Failed to save claim item updates", e);
            throw new RuntimeException("Failed to update claim item", e);
        }
    }

    /**
     * Calculate optimized fund distribution for partial approvals
     * Deduction happens in reverse allocation order: PMSF → ECCIF → SHIF
     *
     * @return BigDecimal array: [newPmsf, newEccif, newShif]
     */
    private BigDecimal[] calculateFundDistribution(BigDecimal originalPmsf, BigDecimal originalEccif,
            BigDecimal originalTotal, BigDecimal approvedAmount) {
        // Input validation
        if (originalTotal.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Original total must be positive");
        }
        if (approvedAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new BadRequestException("Approved amount cannot be negative");
        }
        BigDecimal originalShif = originalTotal.subtract(originalEccif).subtract(originalPmsf);
        // Validate fund consistency
        if (originalShif.compareTo(BigDecimal.ZERO) < 0) {
            log.warn("Inconsistent fund allocation detected: SHIF amount is negative ({})", originalShif);
            originalShif = BigDecimal.ZERO;
        }
        log.info("Original distribution - PMSF: {}, ECCIF: {}, SHIF: {}, Total: {}",
                originalPmsf, originalEccif, originalShif, originalTotal);
        BigDecimal reductionAmount = originalTotal.subtract(approvedAmount);
        // No reduction needed
        if (reductionAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return new BigDecimal[] { originalPmsf, originalEccif, originalShif };
        }
        log.info("Applying reduction amount: {}", reductionAmount);
        // Apply cascading reduction: PMSF → ECCIF → SHIF
        BigDecimal[] funds = { originalPmsf, originalEccif, originalShif };
        String[] fundNames = { "PMSF", "ECCIF", "SHIF" };
        BigDecimal remainingReduction = reductionAmount;

        for (int i = 0; i < funds.length && remainingReduction.compareTo(BigDecimal.ZERO) > 0; i++) {
            if (funds[i].compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal reduction = remainingReduction.min(funds[i]);
                funds[i] = funds[i].subtract(reduction);
                remainingReduction = remainingReduction.subtract(reduction);

                log.info("Reduced {} by: {}, new {}: {}, remaining reduction: {}",
                        fundNames[i], reduction, fundNames[i], funds[i], remainingReduction);
            }
        }

        // Final validation
        BigDecimal calculatedTotal = funds[0].add(funds[1]).add(funds[2]);
        if (calculatedTotal.compareTo(approvedAmount) != 0) {
            log.error("Fund distribution error: calculated total {} != approved amount {}",
                    calculatedTotal, approvedAmount);
            throw new BadRequestException("Fund distribution calculation failed");
        }

        return funds;
    }

    /**
     * Update claim table approvedAmount as sum of all claim_items approvedAmount
     */
    private void updateClaimApprovedTotal(String claimId) {
        try {
            // Get all claim items for this claim
            List<ClaimLineItem> claimItems = insuranceClaimsService.getClaimItems(claimId);

            // Calculate sum of approved amounts
            BigDecimal totalApprovedAmount = claimItems.stream()
                    .map(ClaimLineItem::getApprovedPrice)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            // Update the claim's approved total
            InsuranceClaim claim = insuranceClaimsService.getByExternalID(claimId);
            claim.setApprovedTotal(totalApprovedAmount);
            insuranceClaimsService.save(claim);

            log.info("Updated claim table approvedAmount to: {} for claimId: {}", totalApprovedAmount, claimId);
        } catch (Exception e) {
            log.error("Error updating claim approved total for claimId: {}", claimId);
            throw new BadRequestException("Failed to update claim approved total");
        }
    }

    public static String buildRemarks(String remarks, String extraRemarks) {
        return Stream.of(remarks, extraRemarks)
                .filter(Objects::nonNull)
                .collect(Collectors.joining(","));
    }

    private void updateApprovedPriceInClaimResponse(Claim claim, ClaimResponse claimResponse, String interventionCode,
            Integer sequence, BigDecimal approvedAmount) throws BadRequestException {
        log.info("Starting ClaimResponse update for interventionCode: '{}', approvedAmount: {}", interventionCode,
                approvedAmount);
        if (claim == null) {
            log.warn("No Claim resource found in the bundle.");
            return;
        }

        // Step 1: Find the sequence number for the given intervention code
        Integer matchingSequence = claim.getItem().stream()
                .filter(item -> item.getProductOrService().getCodingFirstRep().getCode().equals(interventionCode))
                .filter(item -> sequence == null || item.getSequence() == sequence) // If sequence is provided, match it
                .map(Claim.ItemComponent::getSequence)
                .findFirst()
                .orElse(null);

        if (matchingSequence == null) {
            log.warn("No matching Claim.item found for interventionCode: '{}'", interventionCode);
            throw new BadRequestException("Invalid interventionCode for the claimId : %s, interventionCode : %s"
                    .formatted(claim.getIdPart(), interventionCode));
        }

        log.info("Found matching Claim.item with claimId:{}, sequence: {}", claim.getIdPart(), matchingSequence);

        // Step 3: Find the matching item in the ClaimResponse
        ClaimResponse.ItemComponent responseItem = claimResponse.getItem().stream()
                .filter(item -> item.getItemSequence() == matchingSequence)
                .findFirst()
                .orElse(null);

        if (responseItem == null) {
            log.warn("No matching ClaimResponse.item found for claimId:{},  sequence: {}", claim.getIdPart(),
                    matchingSequence);
            return;
        }

        BigDecimal previousApprovedAdjAmount = BigDecimal.ZERO;
        boolean updated = false;
        for (ClaimResponse.AdjudicationComponent adjudication : responseItem.getAdjudication()) {
            String categoryCode = adjudication.getCategory().getText();
            if (Adjudication.BENEFIT.toCode().equalsIgnoreCase(categoryCode)) {
                previousApprovedAdjAmount = adjudication.getAmount().getValue();
                log.info("Found existing adjudication with category 'BENEFIT'. Updating amount and reason.");
                adjudication.setAmount(new Money().setValue(approvedAmount));
                adjudication.setReason(new CodeableConcept().setText("Partially Approved by SHA"));
                updated = true;

                // Also need to update the total Approved Price.
                BigDecimal approvedTotal = claimResponse.hasTotal()
                        ? claimResponse.getTotal().getFirst().getAmount().getValue()
                        : BigDecimal.ZERO;
                if (approvedTotal != null) {
                    // Add to new TOTAL APPROVED AMOUNT for Patrial Approved Claims in claim response.
                    log.info("Updating approvedTotal in claim response");
                    BigDecimal newTotalApprovedAmount = getNewTotalApprovedAmount(approvedTotal,
                            previousApprovedAdjAmount, approvedAmount);
                    log.info(
                            "Audit, Updating PreviousTotalApprovedAmount: [{}] TotalApprovedAmount : [{}],  in claimResponse: {}",
                            approvedTotal, newTotalApprovedAmount, claimResponse.getIdPart());
                    claimResponse.getTotal().getFirst().getAmount().setValue(newTotalApprovedAmount);
                }
                break;
            }
        }

        if (!updated) {
            log.info("No existing 'BENEFIT' adjudication found. Creating a new one.");
            ClaimResponse.AdjudicationComponent newAdjudication = new ClaimResponse.AdjudicationComponent();
            newAdjudication.setCategory(new CodeableConcept().addCoding(
                    new Coding().setCode(Adjudication.BENEFIT.toCode())));
            newAdjudication.setAmount(new Money().setValue(approvedAmount));
            newAdjudication.setReason(new CodeableConcept().setText("Partially Approved by SHA"));

            responseItem.addAdjudication(newAdjudication);

            // Also need to update the total Approved Price.
            BigDecimal approvedTotal = claimResponse.hasTotal()
                    ? claimResponse.getTotal().getFirst().getAmount().getValue()
                    : BigDecimal.ZERO;
            if (approvedTotal != null) {
                // Add to new TOTAL APPROVED AMOUNT for Patrial Approved Claims in claim response.
                BigDecimal newTotalApprovedAmount = getNewTotalApprovedAmount(approvedTotal, previousApprovedAdjAmount,
                        approvedAmount);
                log.info(
                        "Audit, Adding PreviousTotalApprovedAmount: [{}] TotalApprovedAmount : [{}],  in claimResponse: {}",
                        approvedTotal, newTotalApprovedAmount, claimResponse.getIdPart());
                if (claimResponse.hasTotal()) {
                    log.info("Updating newApprovedTotal in claim response");
                    claimResponse.getTotal().getFirst().getAmount().setValue(newTotalApprovedAmount);
                } else {
                    ClaimResponse.TotalComponent responseTotal = claimResponse.addTotal();
                    responseTotal.setAmount(
                            new Money().setValue(newTotalApprovedAmount).setCurrency(Constants.CURRENCY_SHILLING));
                }
            }

        }

        log.info("Approved price update completed successfully for sequence: {}", matchingSequence);
    }

    private BigDecimal getNewTotalApprovedAmount(BigDecimal approvedTotal, BigDecimal previousApprovedAdjAmount,
            BigDecimal newApprovedAmount) {
        // Check for null values first before calling any methods on them
        if (previousApprovedAdjAmount != null && newApprovedAmount != null &&
                previousApprovedAdjAmount.compareTo(BigDecimal.ZERO) > 0
                && newApprovedAmount.compareTo(BigDecimal.ZERO) > 0) {
            return approvedTotal.subtract(previousApprovedAdjAmount).add(newApprovedAmount);
        } else if (newApprovedAmount != null && newApprovedAmount.compareTo(BigDecimal.ZERO) > 0) {
            return approvedTotal.add(newApprovedAmount);
        }
        return approvedTotal;
    }

    private void addNotesToClaimResponse(String claimID, ClaimResponse claimResponse, BigDecimal reqAmount,
            BigDecimal approved, String auditNotes, Boolean isRejected, String status) {
        // Always add process notes to ensure callbacks succeed
        String notes = (auditNotes != null && !auditNotes.isEmpty())
                ? auditNotes
                : "Status updated";

        String rejectionNote;
        if (isRejected) {
            rejectionNote = String.format("Claim marked as %s by SHA, Notes: %s", status, notes);
        } else {
            rejectionNote = String.format(
                    "Audit claim Notes, Partial Amount Approved, RequestedAmount: %.2f KES, ApprovedAmount: %.2f KES, Notes: %s",
                    reqAmount, approved, notes);
        }

        ClaimResponse.NoteComponent paymentRejectionsNote = new ClaimResponse.NoteComponent();
        if (claimResponse == null) {
            throw new BadRequestException("Claim Response cannot be null");
        }
        paymentRejectionsNote.setNumber(claimResponse.getProcessNote().size() + 1);
        paymentRejectionsNote.setType(Enumerations.NoteType.DISPLAY);
        paymentRejectionsNote.setText(rejectionNote);
        claimResponse.addProcessNote(paymentRejectionsNote);
        processNotesService.saveProcessNotes(claimID, ExtensionStateCodeEnum.valueOf(status), rejectionNote);
    }

    private ClaimResponse getClaimResponse(InsuranceClaim iClaim) {
        // Claim Response
        ClaimResponse claimResponse = null;
        if (StringUtils.isNotEmpty(iClaim.getClaimResponseId())) {
            claimResponse = claimPaymentHelper.getClaimResponseById(iClaim.getClaimResponseId());
        }
        // Temp Fix for the Multiple Claim response object in FHIR
        if (claimResponse == null) {
            claimResponse = claimPaymentHelper.getClaimResponseObject(iClaim.getExternalId());
            if (claimResponse == null) {
                log.warn("No ClaimResponse found for external claimId: {}", iClaim.getExternalId());
                throw new RuntimeException(
                        "Error occurred, ClaimResponse is not found against claimId :" + iClaim.getExternalId());
            }
        }
        return claimResponse;
    }

    private void updateStatusFHIR(String claimID, Claim claim, ClaimResponse claimResponse,
            ExtensionStateCodeEnum status) {
        updateStatusFHIR(claimID, claim, claimResponse, status, true);
    }

    private void updateStatusFHIR(String claimID, Claim claim, ClaimResponse claimResponse,
            ExtensionStateCodeEnum status, boolean triggerCallBack) {
        try {
            log.info("Audit process: updating claim status. claimId={}, status={}", claimID, status);

            if (triggerCallBack) {
                // Update claim and response FHIR statuses
                claimsHandlerUtility.updateClaimAndResponseStatus(claimID, claim, claimResponse, status);
                log.info("Successfully updated claim and response status. claimId={}, status={}", claimID, status);

                // Trigger provider callback
                claimsHandlerUtility.callClaimResponseWebhooks(claimResponse, claimID, status);
                log.info("Callback webhook triggered for claimId={}", claimID);
            }

            // Status update to third-party
            boolean isAfyangu = true;
            boolean isCpp = true;
            boolean cppBackProcess = false;
            statusUpdateService.triggerCallBackStatusUpdate(claimID, isAfyangu, isCpp, cppBackProcess);
            log.info("External status update triggered for claimId={}", claimID);

        } catch (Exception e) {
            log.error("Audit process failed while updating claim status. claimId={}, status={}, error={}",
                    claimID, status, e.getMessage(), e);
        }
    }

    public void createOrUpdate(ClaimAudit audit) {
        claimAuditRepository.save(audit);
    }

    public void markAsProcessed(ClaimAudit audit, Boolean processed, String remarks) {
        if (remarks.length() > 100) {
            remarks = remarks.substring(0, 100);
        }
        audit.setProcessed(processed);
        audit.setProcessedAt(LocalDateTime.now());
        audit.setProcessRemarks(remarks);
    }

    private void updateLosUtilization(ClaimLineItem claimItems, Claim claim, Integer approvedLos) {
        try {
            log.info("Updating LOS utilization for claimId: {}, approvedLos: {} days",
                    claimItems.getClaim().getClaimId(), approvedLos);

            int losInDays = approvedLos;

            // Check serviced period status to determine update strategy
            LocalDate servicedPeriodStart = claimItems.getServicedPeriodStart();
            LocalDate servicedPeriodEnd = claimItems.getServicedPeriodEnd();

            if (servicedPeriodStart == null && servicedPeriodEnd == null) {
                // Case 1: serviced_period fields are null - update only claims
                log.info("Serviced period fields are null, updating only claims for claimId: {}",
                        claimItems.getClaim().getClaimId());
                updateClaimsDates(claimItems.getClaim(), losInDays);
            } else {
                // Case 2: serviced_period fields exist - update both claim_items and claims
                log.info("Serviced period fields exist, updating both claim_items and claims for claimId: {}",
                        claimItems.getClaim().getClaimId());
                updateClaimItemsServicePeriod(claimItems, losInDays);
                updateClaimsDates(claimItems.getClaim(), losInDays);
            }

            log.info("Successfully updated LOS utilization for claimId: {}",
                    claimItems.getClaim().getClaimId());

        } catch (Exception e) {
            log.error("Error updating LOS utilization for claimId: {}, error: {}",
                    claimItems.getClaim().getClaimId(), e.getMessage(), e);
            throw new BadRequestException("Failed to update LOS utilization: " + e.getMessage());
        }
    }

    private void updateClaimItemsServicePeriod(ClaimLineItem claimItems, int losInDays) {
        LocalDate servicedPeriodStart = claimItems.getServicedPeriodStart();
        LocalDate servicedPeriodEnd = claimItems.getServicedPeriodEnd();

        // Only update if both serviced_period_start and serviced_period_end are not null
        if (servicedPeriodStart != null && servicedPeriodEnd != null) {
            // Update serviced_period_end by adding LOS days to serviced_period_start
            LocalDate newServicedPeriodEnd = servicedPeriodStart.plusDays(losInDays);
            claimItems.setServicedPeriodEnd(newServicedPeriodEnd);

            log.info("Updated claim_items service period - claimId: {}, start: {}, end: {} (was: {})",
                    claimItems.getClaim().getClaimId(), servicedPeriodStart, newServicedPeriodEnd, servicedPeriodEnd);
        } else {
            log.info(
                    "Skipping claim_items service period update - serviced_period_start or serviced_period_end is null for claimId: {}",
                    claimItems.getClaim().getClaimId());
        }
    }

    private void updateClaimsDates(InsuranceClaim insuranceClaim, int losInDays) {
        LocalDateTime dateFrom = insuranceClaim.getDateFrom();
        LocalDateTime dateTo = insuranceClaim.getDateTo();

        try {
            // Get claim items using repository to check serviced period status
            Optional<List<ClaimLineItem>> claimItemsOpt = lineItemRepository.findByClaimId(insuranceClaim.getClaimId());

            if (claimItemsOpt.isPresent() && !claimItemsOpt.get().isEmpty()) {
                List<ClaimLineItem> claimItems = claimItemsOpt.get();

                // Update claims date_to based on number of claim items (regardless of serviced period status)
                if (dateFrom != null) {
                    LocalDateTime newDateTo;

                    if (claimItems.size() == 1) {
                        // Single claim item: use date_from + losInDays
                        newDateTo = dateFrom.plusDays(losInDays);
                        log.info("Single claim item found, setting date_to to date_from + {} days", losInDays);
                    } else {
                        // Multiple claim items: find the latest serviced_period_end
                        LocalDate latestServicedPeriodEnd = claimItems.stream()
                                .map(ClaimLineItem::getServicedPeriodEnd)
                                .filter(Objects::nonNull)
                                .max(LocalDate::compareTo)
                                .orElse(null);

                        if (latestServicedPeriodEnd != null) {
                            // Convert LocalDate to LocalDateTime (using time from dateFrom)
                            newDateTo = latestServicedPeriodEnd.atTime(dateFrom.toLocalTime());
                            log.info(
                                    "Multiple claim items found ({}), setting date_to to latest serviced_period_end: {}",
                                    claimItems.size(), latestServicedPeriodEnd);
                        } else {
                            // Fallback to date_from + losInDays if no serviced_period_end found
                            newDateTo = dateFrom.plusDays(losInDays);
                            log.info(
                                    "Multiple claim items found but no serviced_period_end, fallback to date_from + {} days",
                                    losInDays);
                        }
                    }

                    insuranceClaim.setDateTo(newDateTo);
                    insuranceClaimsService.save(insuranceClaim);
                    log.info("Updated claims dates - claimId: {}, date_from: {}, date_to: {} (was: {})",
                            insuranceClaim.getClaimId(), dateFrom, newDateTo, dateTo);
                } else {
                    log.warn("Cannot update claims date_to - date_from is null for claimId: {}",
                            insuranceClaim.getClaimId());
                }

            } else {
                log.warn("No claim items found for claimId: {}, cannot determine serviced period status",
                        insuranceClaim.getClaimId());
            }
        } catch (Exception e) {
            log.error("Error updating claims dates for claimId: {}, error: {}",
                    insuranceClaim.getClaimId(), e.getMessage(), e);
            throw new BadRequestException("Failed to update claims dates: " + e.getMessage());
        }
    }

    private String updateAdjudicationRules(String adjudicationRules, Integer approvedLos, BigDecimal approvedAmount) {
        try {
            Map<String, Object> rulesMap = objectMapper.readValue(adjudicationRules,
                    new TypeReference<Map<String, Object>>() {
                    });

            // Add/Update approvedLos parameter
            if (approvedLos != null && approvedLos > 0) {
                String approvedLosRule = "<= " + approvedLos;
                rulesMap.put("approvedLos", approvedLosRule);
                log.info("Added/Updated approvedLos rule to: {}", approvedLosRule);
            }

            // Add/Update approvedAmount parameter using same existing claimAmount rule
            if (rulesMap.containsKey("claimAmount")) {
                String claimAmountRule = (String) rulesMap.get("claimAmount");
                if (claimAmountRule != null && !claimAmountRule.trim().isEmpty()) {
                    rulesMap.put("approvedAmount", claimAmountRule);
                    log.info("Added/Updated approvedAmount rule to: {} (copied from claimAmount)", claimAmountRule);
                }
            }

            String updatedRules = objectMapper.writeValueAsString(rulesMap);
            log.info("Successfully updated adjudication_rules");
            return updatedRules;
        } catch (Exception e) {
            log.error("Error updating adjudication_rules: {}", e.getMessage(), e);
            throw new BadRequestException("Failed to update adjudication_rules", e);
        }
    }

    public void validateApprovedLos(ClaimLineItem claimItems, Integer approvedLos, String claimId)
            throws BadRequestException {
        try {
            log.info("Validating approved LOS: {} for claimId: {}", approvedLos, claimId);
            // Parse adjudication_params to get existing LOS
            Map<String, Object> paramsMap = objectMapper.readValue(claimItems.getAdjudicationParams(),
                    new TypeReference<Map<String, Object>>() {
                    });
            Object losObj = paramsMap.get("los");
            if (losObj == null) {
                log.warn("No existing LOS found in adjudication_params for claimId: {}", claimId);
                throw new BadRequestException("No existing LOS found");
            }
            Integer existingLos;
            if (losObj instanceof Number) {
                existingLos = ((Number) losObj).intValue();
            } else if (losObj instanceof String) {
                try {
                    existingLos = Integer.parseInt((String) losObj);
                } catch (NumberFormatException e) {
                    log.error("Invalid LOS format in adjudication_params for claimId: {}, los: {}", claimId, losObj);
                    throw new BadRequestException("Invalid LOS format");
                }
            } else {
                log.error("Unexpected LOS data type in adjudication_params for claimId: {}, type: {}", claimId,
                        losObj.getClass());
                throw new BadRequestException("Unexpected LOS data type");
            }
            // Validate: approvedLos <= existingLos
            if (approvedLos > existingLos) {
                log.error("Approved LOS validation failed for claimId: {}. Approved LOS ({}) exceeds existing LOS ({})",
                        claimId, approvedLos, existingLos);
                throw new BadRequestException("Approved LOS cannot exceed recorded LOS");
            }
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error validating approved LOS for claimId: {}", claimId, e);
            throw new BadRequestException("Failed to validate approved LOS: " + e.getMessage());
        }
    }

    public void validateApprovedAmount(ClaimLineItem claimItems, BigDecimal approvedAmount, String claimId)
            throws BadRequestException {
        try {
            log.info("Validating approved amount: {} for claimId: {}", approvedAmount, claimId);
            // Get claim total amount
            BigDecimal claimAmount = claimItems.getNetValue();
            if (claimAmount == null) {
                log.error("No claim amount found for claimId: {}", claimId);
                throw new BadRequestException("No claim amount found to validate against");
            }
            // Validate: approvedAmount <= claimAmount
            if (approvedAmount.compareTo(claimAmount) > 0) {
                log.error(
                        "Approved amount validation failed for claimId: {}. Approved amount ({}) exceeds claim amount ({})",
                        claimId, approvedAmount, claimAmount);
                throw new BadRequestException("Approved amount cannot exceed claimed amount");
            }
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error validating approved amount for claimId: {}", claimId, e);
            throw new BadRequestException("Failed to validate approved amount: " + e.getMessage());
        }
    }

    private void createSurveillanceLogEntry(UUID claimId, String status, ExtensionStateCodeEnum previousStatus,
            String reason, String createdBy) {
        SurveillanceLog surveillanceLog = new SurveillanceLog();
        surveillanceLog.setClaimId(claimId);
        surveillanceLog.setStatus(status);
        surveillanceLog.setPreviousStatus(previousStatus.name());
        surveillanceLog.setReason(reason);
        surveillanceLog.setCreatedBy(createdBy);
        surveillanceLogRepository.save(surveillanceLog);
        log.info("Surveillance log entry created for claim: {} with status: {} by user: {}", claimId, status,
                createdBy);
    }

    /**
     * Asynchronously trigger ERP reprocessing for partial approval claims
     * This prevents ERP failures from affecting the main claim audit reprocessing
     * flow
     */
    @Async("virtualThreadExecutor")
    public void asyncTriggerWorkflowForPartialApproval(String claimID) {
        try {
            UUID claimUuid = UUID.fromString(claimID);
            // Check if claim has any pending unprocessed audit items
            boolean hasPendingAudits = claimAuditRepository.hasUnprocessedAuditItems(claimUuid);
            if (hasPendingAudits) {
                log.info(
                        "Skipping workflow trigger for claimId={} - has pending unprocessed audit items for partially approved claim",
                        claimID);
                return;
            }

            // Find all ERP claims that share the same base claim ID and update them
            List<ErpClaims> relatedClaims = erpClaimsRepository.findByClaimNoStartingWith(claimID);
            if (!relatedClaims.isEmpty()) {
                // Update all related claims to be unique to avoid conflicts when workflow recreates claims
                updateAllRelatedClaimsToUnique(relatedClaims);
                log.info("Successfully updated {} ERP claims for base claimId={}", relatedClaims.size(), claimID);
            } else {
                log.info("No ERP claim records found for claimId={} - workflow will create new record", claimID);
            }

            // Trigger Camunda workflow for payment processing with claim ID
            erpCallBackService.reProcessErpRequest(claimID, false);
            log.info("Claim reprocessing workflow triggered for claimId={}", claimID);

        } catch (DatabaseException ex) {
            throw ex;
        } catch (Exception e) {
            log.error("Failed to trigger workflow for claimId={}", claimID, e);
            throw new SystemException("While processing asyncTriggerWorkflowForPartialApproval: " + e.getMessage(), e);
        }
    }

    /**
     * Update all related claims to be unique by appending their respective IDs
     * to avoid conflicts when the workflow recreates the claim records.
     * 
     * @param relatedClaims List of ERP claims that share the same base claim ID
     * @throws RuntimeException if update fails
     */
    private void updateAllRelatedClaimsToUnique(List<ErpClaims> relatedClaims) {
        try {
            log.info("Updating {} ERP claims to have unique IDs", relatedClaims.size());

            // Update all related claims by appending their respective IDs
            for (ErpClaims relatedClaim : relatedClaims) {
                String originalClaimNo = relatedClaim.getClaimNo();
                String uniqueClaimId = originalClaimNo + "-" + relatedClaim.getId();
                relatedClaim.setClaimNo(uniqueClaimId);
                relatedClaim.setPaymentStatus(ExtensionStateCodeEnum.CANCELED.name());
                erpClaimsRepository.save(relatedClaim);

                log.info("Updated ERP claim ID from {} to {} (ID: {})", originalClaimNo, uniqueClaimId,
                        relatedClaim.getId());
            }

        } catch (Exception e) {
            log.error("Failed to update related claims to unique IDs", e);
            throw new DatabaseException("Failed to update related claims to unique IDs", e);
        }
    }

    /**
     * Handle failed PARTIAL_APPROVAL claims by flagging them again for surveillance
     * if the surveillance_status is PARTIAL_APPROVAL
     */
    private void handleFailedPartialApprovalClaim(ClaimAudit claimAudit) {
        try {
            // Only handle PARTIAL_APPROVAL claims
            if (!ExtensionStateCodeEnum.PARTIAL_APPROVAL.name().equalsIgnoreCase(claimAudit.getAuditStatus())) {
                return;
            }
            String claimId = String.valueOf(claimAudit.getClaimId());
            InsuranceClaim claim = insuranceClaimsService.findByExternalID(claimId);
            // Check if surveillance_status is PARTIAL_APPROVAL
            if (ExtensionStateCodeEnum.PARTIAL_APPROVAL.name().equals(claim.getSurveillanceStatus())) {
                // Flag the claim again for surveillance
                claim.setAuditFlag(true);
                insuranceClaimsService.save(claim);
                log.warn(
                        "Failed PARTIAL_APPROVAL claim flagged again for surveillance: claimId={}, interventionCode={}",
                        claimId, claimAudit.getInterventionCode());
            } else {
                log.info("Failed PARTIAL_APPROVAL claim not flagged - surveillance_status is: {}",
                        claim.getSurveillanceStatus());
            }

        } catch (Exception e) {
            log.error("Error handling failed partial approval claim: claimId={}, error={}",
                    claimAudit.getClaimId(), e.getMessage(), e);
        }
    }

    /**
     * This method handles claims that need to be pushed to CPP before going to ERP.
     * 
     * Flow:
     * 1. Capture original status and update remarks for audit trail
     * 2. Push initial status to CPP (creates QUEUED record if not exists) - uses cppBackProcess=true
     * 3. Transition claim to APPROVED
     * 4. Push APPROVED status to CPP
     * 5. Send to ERP for payment processing
     * 
     */
    private void asyncReprocessDeskReviewedClaim(ClaimAudit claimAudit, String claimId,
            Long internalClaimId, Claim fhirClaim, ClaimResponse claimResponse, int attempt) {

        InsuranceClaim claimDetailsDB = insuranceClaimsService.findById(internalClaimId).orElse(null);
        if (claimDetailsDB == null) {
            log.error("Claim not found for internal ID: {}", internalClaimId);
            return;
        }
        if (claimDetailsDB.getUse().equalsIgnoreCase(ClaimType.PREAUTHORIZATION.name())) {
            log.error("Claim type is not supported");
            throw new BadRequestException("Claim type is not supported");
        }

        // Capture original status and update remarks for audit trail (only first time)
        if (attempt == 1
                && (claimAudit.getRemarks() == null || !claimAudit.getRemarks().startsWith("initial status:"))) {
            String originalStatus = claimDetailsDB.getStatus().name();
            String timestamp = LocalDateTime.now().toString();
            String newRemark = "initial status: " + originalStatus;

            String existingRemarks = claimAudit.getRemarks();
            if (StringUtils.isBlank(existingRemarks)) {
                claimAudit.setRemarks("[" + timestamp + "] " + newRemark);
            } else {
                claimAudit.setRemarks(existingRemarks + " | [" + timestamp + "] " + newRemark);
            }
        }

        log.info("Starting reprocess for claimId: {}, current status: {}, attempt: {}", claimId,
                claimDetailsDB.getStatus(), attempt);

        try {
            // ERP Repush: if already SENT_FOR_PAYMENT_PROCESSING, skip steps and repush directly to ERP
            if (claimDetailsDB.getStatus() == ExtensionStateCodeEnum.SENT_FOR_PAYMENT_PROCESSING) {
                log.info("ERP Repush: claimId={} is SENT_FOR_PAYMENT_PROCESSING, triggering ERP repush", claimId);
                sendClaimForPaymentHandler.handlePaymentErp(null, null, claimId, false, false, true);
                log.info("ERP Repush completed successfully for claimId: {}", claimId);
                markAsProcessed(claimAudit, true, "Success: ERP Repush");
                createOrUpdate(claimAudit);
                return;
            }

            boolean isAfyangu = false;
            boolean isCpp = true;
            boolean isAlreadyApproved = claimDetailsDB.getStatus() == ExtensionStateCodeEnum.APPROVED;

            // STEP 1: Initial push to CPP to create QUEUED record
            // For APPROVED claims: use cppBackProcess=true (CppService will temporarily set status to QUEUED)
            // For other statuses (e.g., MANUAL_REVIEW | CLINICAL_REVIEW e.t.c): use cppBackProcess=false (push current status first)
            if (isAlreadyApproved) {
                log.info("Step 1: Claim is APPROVED - pushing to CPP with cppBackProcess=true to create QUEUED record for claimId: {}", claimId);
                try {
                    statusUpdateService.triggerCallBackStatusUpdate(claimId, isAfyangu, isCpp, true);
                } catch (Exception e) {
                    log.error("Failed Step 1 CPP callback for claimId={}", claimId, e);
                }
            } else {
                log.info("Step 1: Pushing current status ({}) to CPP for claimId: {}", claimDetailsDB.getStatus(), claimId);
                try {
                    statusUpdateService.triggerCallBackStatusUpdate(claimId, isAfyangu, isCpp, false);
                } catch (Exception e) {
                    log.error("Failed Step 1 CPP callback for claimId={}", claimId, e);
                }
            }

            // STEP 2: Transition to APPROVED (skip only if already APPROVED)
            if (!isAlreadyApproved) {
                log.info("Step 2: Transitioning claimId={} to APPROVED", claimId);
                claimsHandlerUtility.updateClaimAndResponseStatus(claimId, fhirClaim, claimResponse,
                        ExtensionStateCodeEnum.APPROVED);
            } else {
                log.info("Step 2: Skipping status transition - claimId={} already APPROVED", claimId);
            }

            // STEP 3: Push APPROVED status to CPP
            log.info("Step 3: Pushing APPROVED status to CPP for claimId: {}", claimId);
            try {
                statusUpdateService.triggerCallBackStatusUpdate(claimId, isAfyangu, isCpp, false);
            } catch (Exception e) {
                log.error("Failed Step 3 CPP callback for claimId={}", claimId, e);
                // Continue - CPP update is best effort, ERP is the critical step
            }

            // STEP 4: Trigger ERP payment processing
            log.info("Step 4: Sending claimId={} to ERP for payment processing. Attempt {}", claimId, attempt);
            sendClaimForPaymentHandler.handlePaymentErp(null, null, claimId, false, false);

            // If successful
            log.info("Reprocess completed successfully for claimId: {}", claimId);
            markAsProcessed(claimAudit, true, "Success");
            createOrUpdate(claimAudit);

        } catch (Exception e) {
            log.warn("ERP attempt {}/{} failed for claimId={}: {}", attempt, maxRetries, claimId, e.getMessage());

            if (attempt < maxRetries) {
                log.info("Scheduling retry {} for claimId={} in {} ms", attempt + 1, claimId, retryDelayMs);
                erpRetryExecutor.schedule(() -> {
                    asyncReprocessDeskReviewedClaim(claimAudit, claimId, internalClaimId, fhirClaim,
                            claimResponse, attempt + 1);
                }, retryDelayMs, TimeUnit.MILLISECONDS);
            } else {
                log.error("ERP payment failed after {} attempts for claimId={}", maxRetries, claimId, e);
                markAsProcessed(claimAudit, true, "Failed after max retries: " + e.getMessage());
                createOrUpdate(claimAudit);
            }
        }
    }
}