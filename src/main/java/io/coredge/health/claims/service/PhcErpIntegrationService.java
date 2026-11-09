package io.coredge.health.claims.service;

import io.coredge.health.claims.db.entity.InsuranceClaim;
import io.coredge.health.claims.db.entity.PhcBatchPayment;
import io.coredge.health.claims.db.entity.PhcClaimPayment;
import io.coredge.health.claims.db.repositories.InsuranceClaimsRepository;
import io.coredge.health.claims.db.repositories.PhcBatchPaymentRepository;
import io.coredge.health.claims.db.repositories.PhcClaimPaymentRepository;
import io.coredge.health.claims.enums.ExtensionStateCodeEnum;
import io.coredge.health.claims.exception.app.BadRequestException;
import io.coredge.health.claims.exception.app.DataValidationException;
import io.coredge.health.claims.worker.SendClaimForPaymentHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * PHC ERP Integration Service
 * 
 * Handles integration with ERP/CPP systems for PHC payment processing:
 * - Submits approved payments to ERP
 * - Processes ERP callbacks and responses
 * - Updates payment status based on ERP feedback
 * - Manages payment completion workflow
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PhcErpIntegrationService {

    // Constants
    private static final String ERP_REFERENCE_PREFIX = "PHC-BATCH-";
    private static final String CLAIM_SUCCESS_MESSAGE = "Claim %s: SUCCESS";
    private static final String FACILITY_SUBMISSION_MESSAGE = "Facility %s: %d claims submitted";
    private static final String FACILITY_FAILURE_MESSAGE = "Facility %s: FAILED - %s";
    private static final String NO_CLAIMS_MESSAGE = "No claims found";

    private final PhcBatchPaymentRepository phcBatchPaymentRepository;
    private final PhcClaimPaymentRepository phcClaimPaymentRepository;
    private final InsuranceClaimsRepository insuranceClaimsRepository;
    private final SendClaimForPaymentHandler sendClaimForPaymentHandler;

    /**
     * Submit batch payment to ERP for processing
     * 
     * @param batchPaymentId The ID of the batch payment to submit
     * @throws DataValidationException if batch payment not found or invalid
     * @throws BadRequestException if batch payment not approved or submission fails
     */
    public void submitPaymentBatch(Long batchPaymentId) {
        log.info("Starting ERP submission for batch payment: {}", batchPaymentId);
        
        PhcBatchPayment batchPayment = findAndValidateBatchPayment(batchPaymentId);
        List<PhcClaimPayment> approvedFacilityPayments = getApprovedFacilityPayments(batchPayment, batchPaymentId);
        
        String erpReference = generateErpReference(batchPayment);
        
        try {
            submitFacilityPaymentsToErp(approvedFacilityPayments, erpReference);
            updateBatchPaymentStatus(batchPayment, PhcBatchPayment.Status.SENT_FOR_PAYMENT_PROCESSING);
            
            log.info("Successfully submitted batch payment {} to ERP with reference: {} ({} facility payments)", 
                    batchPaymentId, erpReference, approvedFacilityPayments.size());
                    
        } catch (Exception e) {
            handleBatchSubmissionFailure(batchPayment, batchPaymentId, e);
        }
    }
    
    /**
     * Find and validate batch payment for ERP submission
     */
    private PhcBatchPayment findAndValidateBatchPayment(Long batchPaymentId) {
        PhcBatchPayment batchPayment = phcBatchPaymentRepository.findById(batchPaymentId)
                .orElseThrow(() -> new DataValidationException("Batch payment not found: " + batchPaymentId));

        if (batchPayment.getStatus() != PhcBatchPayment.Status.APPROVED) {
            throw new BadRequestException("Batch payment must be approved before ERP submission. Current status: " + batchPayment.getStatus());
        }
        
        return batchPayment;
    }
    
    /**
     * Get approved facility payments for the batch
     */
    private List<PhcClaimPayment> getApprovedFacilityPayments(PhcBatchPayment batchPayment, Long batchPaymentId) {
        List<PhcClaimPayment> facilityPayments = phcClaimPaymentRepository.findByPhcBatchPayment(batchPayment)
                .stream()
                .filter(fp -> ExtensionStateCodeEnum.APPROVED.name().equals(fp.getStatus().trim()))
                .collect(Collectors.toList());

        if (CollectionUtils.isEmpty(facilityPayments)) {
            throw new DataValidationException("No approved facility payments found for batch: " + batchPaymentId);
        }
        
        return facilityPayments;
    }
    
    /**
     * Submit all facility payments to ERP
     */
    private void submitFacilityPaymentsToErp(List<PhcClaimPayment> facilityPayments, String erpReference) {
        for (PhcClaimPayment facilityPayment : facilityPayments) {
            try {
                submitFacilityPaymentToErp(facilityPayment, erpReference);
            } catch (Exception e) {
                log.error("Failed to submit facility payment {} to ERP: {}", facilityPayment.getId(), e.getMessage());
                // Continue with other payments even if one fails
            }
        }
    }
    
    /**
     * Update batch payment status
     */
    private void updateBatchPaymentStatus(PhcBatchPayment batchPayment, PhcBatchPayment.Status status) {
        batchPayment.setStatus(status);
        phcBatchPaymentRepository.save(batchPayment);
    }
    
    /**
     * Handle batch submission failure
     */
    private void handleBatchSubmissionFailure(PhcBatchPayment batchPayment, Long batchPaymentId, Exception e) {
        updateBatchPaymentStatus(batchPayment, PhcBatchPayment.Status.FAILED);
        log.error("Failed to submit batch payment {} to ERP: {}", batchPaymentId, e.getMessage(), e);
        throw new BadRequestException("ERP submission failed");
    }

    /**
     * Submit individual facility payment to ERP
     *
     * @param facilityPayment   The facility payment to submit
     * @param batchErpReference The batch ERP reference
     */
    private void submitFacilityPaymentToErp(PhcClaimPayment facilityPayment, String batchErpReference) {
        Long facilityPaymentId = facilityPayment.getId();
        Long providerId = facilityPayment.getProviderId();
        
        try {
            List<InsuranceClaim> claims = getClaimsForFacilityPayment(facilityPaymentId);
            
            if (CollectionUtils.isEmpty(claims)) {
                log.warn("No claims found for facility payment: {}", facilityPaymentId);
                throw new DataValidationException(NO_CLAIMS_MESSAGE);
            }

            submitClaimsToErp(claims);
            updateFacilityPaymentStatus(facilityPayment, ExtensionStateCodeEnum.SENT_FOR_PAYMENT_PROCESSING.name());

            log.info("Successfully submitted facility payment {} to ERP with {} claims", 
                    facilityPaymentId, claims.size());

        } catch(DataValidationException | BadRequestException ex){
            throw ex;
        } catch (Exception e) {
            handleFacilityPaymentFailure(facilityPayment, facilityPaymentId, providerId, e);
            throw new BadRequestException("Facility payment submission failed");
        }
    }
    
    /**
     * Get claims associated with facility payment
     */
    private List<InsuranceClaim> getClaimsForFacilityPayment(Long facilityPaymentId) {
        return insuranceClaimsRepository.findByPhcClaimPaymentsId(facilityPaymentId)
                .orElse(Collections.emptyList());
    }
    
    /**
     * Submit claims to ERP using existing handler
     */
    private void submitClaimsToErp(List<InsuranceClaim> claims) {
        for (InsuranceClaim claim : claims) {
            try {
                sendClaimForPaymentHandler.handlePaymentErp(null, null, claim.getExternalId(), false, false);
                log.debug(CLAIM_SUCCESS_MESSAGE, claim.getClaimId());
            } catch (Exception e) {
                log.error("Failed to submit claim {} to ERP: {}", claim.getClaimId(), e.getMessage());
                // Continue with other claims even if one fails
            }
        }
    }
    
    /**
     * Update facility payment status
     */
    private void updateFacilityPaymentStatus(PhcClaimPayment facilityPayment, String status) {
        facilityPayment.setStatus(status);
        phcClaimPaymentRepository.save(facilityPayment);
    }
    
    /**
     * Handle facility payment submission failure
     */
    private void handleFacilityPaymentFailure(PhcClaimPayment facilityPayment, Long facilityPaymentId, 
                                            Long providerId, Exception e) {
        updateFacilityPaymentStatus(facilityPayment, "FAILED");
        log.error("Failed to submit facility payment {} (provider: {}) to ERP: {}", 
                facilityPaymentId, providerId, e.getMessage(), e);
    }

    /**
     * Generate unique ERP reference for batch payment
     * 
     * @param batchPayment The batch payment to generate reference for
     * @return Unique ERP reference string
     */
    private String generateErpReference(PhcBatchPayment batchPayment) {
        String dateString = batchPayment.getDate().toString().replace("-", "");
        return ERP_REFERENCE_PREFIX + batchPayment.getId() + "-" + dateString;
    }

    /**
     * Get ERP submission status summary
     * 
     * @return Map containing batch and facility payment statistics
     */
    public Map<String, Object> getErpSubmissionSummary() {
        log.debug("Generating ERP submission status summary");
        
        Map<String, Object> summary = new HashMap<>();
        
        // Add batch payment statistics
        addBatchPaymentStatistics(summary);
        
        // Add facility payment statistics
        addFacilityPaymentStatistics(summary);
        
        log.debug("Generated ERP submission summary with {} entries", summary.size());
        return summary;
    }
    
    /**
     * Add batch payment statistics to summary
     */
    private void addBatchPaymentStatistics(Map<String, Object> summary) {
        summary.put("totalBatchPayments", phcBatchPaymentRepository.count());
        summary.put("pendingBatchPayments", phcBatchPaymentRepository.countByStatus(PhcBatchPayment.Status.PENDING));
        summary.put("approvedBatchPayments", phcBatchPaymentRepository.countByStatus(PhcBatchPayment.Status.APPROVED));
        summary.put("sentToErpBatchPayments", phcBatchPaymentRepository.countByStatus(PhcBatchPayment.Status.SENT_FOR_PAYMENT_PROCESSING));
        summary.put("completedBatchPayments", phcBatchPaymentRepository.countByStatus(PhcBatchPayment.Status.PAYMENT_COMPLETED));
        summary.put("failedBatchPayments", phcBatchPaymentRepository.countByStatus(PhcBatchPayment.Status.FAILED));
    }
    
    /**
     * Add facility payment statistics to summary
     */
    private void addFacilityPaymentStatistics(Map<String, Object> summary) {
        summary.put("totalFacilityPayments", phcClaimPaymentRepository.count());
        summary.put("pendingFacilityPayments", phcClaimPaymentRepository.countByStatus(ExtensionStateCodeEnum.PENDING.name()));
        summary.put("approvedFacilityPayments", phcClaimPaymentRepository.countByStatus(ExtensionStateCodeEnum.APPROVED.name()));
        summary.put("sentToErpFacilityPayments", phcClaimPaymentRepository.countByStatus(ExtensionStateCodeEnum.SENT_FOR_PAYMENT_PROCESSING.name()));
        summary.put("completedFacilityPayments", phcClaimPaymentRepository.countByStatus(ExtensionStateCodeEnum.PAYMENT_COMPLETED.name()));
        summary.put("failedFacilityPayments", phcClaimPaymentRepository.countByStatus("FAILED"));
    }
}
