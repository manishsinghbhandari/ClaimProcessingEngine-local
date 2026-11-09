package io.coredge.health.claims.payments.service;

import io.coredge.health.claims.context.HealthIdContextHolder;
import io.coredge.health.claims.payments.dto.ErpApprovalRequest;
import io.coredge.health.claims.payments.dto.PaymentGatewayCallbackRequest;
import io.coredge.health.claims.payments.entity.PaymentBatch;
import io.coredge.health.claims.payments.entity.PaymentProcessing;
import io.coredge.health.claims.payments.enums.PaymentsEnum;
import io.coredge.health.claims.payments.exception.*;
import io.coredge.health.claims.payments.repository.FundBankDetailsRepository;
import io.coredge.health.claims.payments.repository.PaymentBatchRepository;
import io.coredge.health.claims.payments.repository.PaymentProcessingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentProcessingService {

    private final PaymentProcessingRepository processingRepository;
    private final PaymentBatchRepository batchRepository;
    private final FundBankDetailsRepository fundBankDetailsRepository;
    private final PaymentAuditService auditService;
    private final PaymentUpdateErpNotificationService paymentUpdateErpNotificationService;

    // =========================================================================
    // ERP Approval Callback
    // =========================================================================

    /**
     * Called when ERP approves a claim for payment.
     * Validates amount, patient ID, fund scheme, status, and ERP type before
     * updating.
     */
    @Transactional
    public void processErpApproval(ErpApprovalRequest request) {
        // Log initial request in an independent transaction
        auditService.logInitialRequest(PaymentsEnum.AuditEventType.CLAIM,
                PaymentsEnum.SystemType.ERP,
                request.getClaimId(), null, PaymentsEnum.PaymentStatus.SENT_FOR_PAYMENT, request);

        try {
            // 1. Fetch existing payment record
            PaymentProcessing processing = processingRepository.findByClaimId(request.getClaimId())
                    .orElseThrow(() -> new PaymentRecordNotFoundException(request.getClaimId()));

            // 2. Validate ERP approval fields against stored record (amount, patient, fund,
            // ERP type)
            validateErpApproval(processing, request);

            // 3. Log successful validation audit event
            auditService.logEvent(PaymentsEnum.AuditEventType.CLAIM,
                    PaymentsEnum.SystemType.ERP, PaymentsEnum.SystemType.PAYER,
                    PaymentsEnum.PaymentStatus.APPROVED_FOR_PAYMENT,
                    request.getClaimId(), null,
                    request, "ERP approval processed successfully", "SUCCESS",
                    PaymentsEnum.AuditStatus.PASSED.name(), "ERP callback validated and processed");

            // 4. Apply ERP approval — update fields and persist
            processing.setPaymentStatus(PaymentsEnum.PaymentStatus.APPROVED_FOR_PAYMENT.name());
            processing.setCeoApprovalDate(request.getCeoApprovalDate());
            processing.setDebitBankAccount(request.getCompanyBankAccount());
            processing.setDebitBankName(request.getCompanyBankName());
            processing.setRemarks(null); // Clear any previous error remarks
            processingRepository.save(processing);

            log.info("ERP approval completed for claim: {}", request.getClaimId());

        } catch (Exception e) {
            auditService.logEvent(PaymentsEnum.AuditEventType.CLAIM,
                    PaymentsEnum.SystemType.ERP, PaymentsEnum.SystemType.PAYER,
                    PaymentsEnum.PaymentStatus.PAYMENT_NEEDS_REPARATION,
                    request.getClaimId(), null,
                    request, "ERP approval processing failed", "Failed",
                    PaymentsEnum.AuditStatus.FAILED.name(), "Error: " + e.getMessage());

            log.error("ERP approval failed for claim: {}, Error: {}", request.getClaimId(), e.getMessage());

            // Re-throw business exceptions directly so GlobalExceptionHandler returns
            // correct HTTP status
            if (e instanceof RuntimeException re)
                throw re;
            throw new RuntimeException(e);
        }
    }

    // =========================================================================
    // Gateway Callback
    // =========================================================================

    /**
     * Called when the Payment Gateway confirms a batch payment outcome.
     * Validates batch integrity, maps status, updates batch + all claims,
     * then applies per-claim error remarks for VALIDATION_FAILED responses.
     */
    @Transactional
    public void processGatewayCallback(PaymentGatewayCallbackRequest request) {
        // Log initial request in an independent transaction
        auditService.logInitialRequest(PaymentsEnum.AuditEventType.BATCH,
                PaymentsEnum.SystemType.PAYMENT_GATEWAY,
                null, request.getBatchId(), PaymentsEnum.PaymentStatus.PAYMENT_COMPLETED, request);

        try {
            log.info("Gateway callback received for batch: {}", request.getBatchId());

            // 1. Fetch batch and validate mandatory fields / data integrity
            PaymentBatch batch = batchRepository.findById(request.getBatchId())
                    .orElseThrow(() -> new PaymentBatchRecordNotFoundException(request.getBatchId()));
            validateGatewayCallback(batch, request);

            // 2. Map gateway status to internal statuses
            PaymentsEnum.BatchStatus batchStatus;
            PaymentsEnum.PaymentStatus claimStatus;
            if ("FULLY_COMPLETED".equalsIgnoreCase(request.getStatus())
                    || "PAYMENT_COMPLETED".equalsIgnoreCase(request.getStatus())) {
                batchStatus = PaymentsEnum.BatchStatus.PAYMENT_COMPLETED;
                claimStatus = PaymentsEnum.PaymentStatus.PAYMENT_COMPLETED;
            } else if ("FAILED".equalsIgnoreCase(request.getStatus())
                    || "VALIDATION_FAILED".equalsIgnoreCase(request.getStatus())) {
                batchStatus = PaymentsEnum.BatchStatus.PAYMENT_REJECTED;
                claimStatus = PaymentsEnum.PaymentStatus.PAYMENT_REJECTED;
            } else {
                throw new PaymentValidationException("Unknown gateway status: " + request.getStatus());
            }

            // 3. Log successful validation audit event
            auditService.logEvent(PaymentsEnum.AuditEventType.BATCH,
                    PaymentsEnum.SystemType.PAYMENT_GATEWAY, PaymentsEnum.SystemType.PAYER,
                    claimStatus, null, request.getBatchId(),
                    request, "Gateway callback processed", "SUCCESS",
                    PaymentsEnum.AuditStatus.PASSED.name(), "Processed status: " + request.getStatus());

            log.info("Gateway callback validated for batch: {}", request.getBatchId());

            // 4. Resolve effective remarks — use actual error details when claimId = "N/A"
            // (batch-level errors), otherwise fall back to the generic processingRemarks
            String effectiveRemarks = resolveEffectiveRemarks(request);

            // 5. Persist batch record with updated status and remarks
            batch.setBankReferenceNumber(request.getExternalReference());
            batch.setPaymentCompletionTime(request.getTimestamp());
            batch.setBatchStatus(batchStatus);
            batch.setRemarks(effectiveRemarks);
            batchRepository.save(batch);
            log.info("Batch {} updated — status: {}", request.getBatchId(), batchStatus);

            // 6. Bulk-update all claims in the batch (status + remarks + audit fields)
            String auditor = HealthIdContextHolder.userId() != null ? HealthIdContextHolder.userId() : "system";
            processingRepository.updateBatchClaimsStatus(
                    request.getBatchId(), claimStatus.name(),
                    request.getExternalReference(), request.getTimestamp(),
                    effectiveRemarks, LocalDateTime.now(), auditor);

            // 7. Overwrite per-claim remarks for individual VALIDATION_FAILED entries
            // (grouped bulk update — one SQL UPDATE per unique remark)
            if ("VALIDATION_FAILED".equalsIgnoreCase(request.getStatus()) && request.getFailedClaims() != null) {
                handleFailedClaims(request);
            }

            // 8. Notify ERP only on full payment completion
            if (batchStatus == PaymentsEnum.BatchStatus.PAYMENT_COMPLETED) {
                log.info("Notifying ERP for batch completion: {}", request.getBatchId());
                paymentUpdateErpNotificationService.updateStatusInLocalAndErp(request);
            }

        } catch (Exception e) {
            auditService.logEvent(PaymentsEnum.AuditEventType.BATCH,
                    PaymentsEnum.SystemType.PAYMENT_GATEWAY, PaymentsEnum.SystemType.PAYER,
                    PaymentsEnum.PaymentStatus.PAYMENT_NEEDS_REPARATION,
                    null, request.getBatchId(),
                    request, "Batch callback processing failed", "Failed",
                    PaymentsEnum.AuditStatus.FAILED.name(), "Error: " + e.getMessage());

            log.error("Gateway callback failed for batch: {}, Error: {}", request.getBatchId(), e.getMessage());

            // Re-throw business exceptions directly so GlobalExceptionHandler returns
            // correct HTTP status
            if (e instanceof RuntimeException re)
                throw re;
            throw new RuntimeException(e);
        }
    }

    // =========================================================================
    // Validation — ERP Approval
    // =========================================================================

    /**
     * Validates an ERP approval request against the stored payment record.
     * Checks: mandatory fields, amount (2dp), patient ID, fund scheme, status, ERP
     * type, bank account mapping.
     */
    private void validateErpApproval(PaymentProcessing processing, ErpApprovalRequest request) {
        // Mandatory field presence
        if (request.getCeoApprovalDate() == null)
            throw new PaymentMissingDataException("ceoApprovalDate is missing for claim: " + request.getClaimId());
        if (request.getCompanyBankAccount() == null || request.getCompanyBankAccount().isBlank())
            throw new PaymentMissingDataException("companyBankAccount is missing for claim: " + request.getClaimId());
        if (request.getCompanyBankName() == null || request.getCompanyBankName().isBlank())
            throw new PaymentMissingDataException("companyBankName is missing for claim: " + request.getClaimId());

        // Amount match (tolerant to trailing zeros / minor precision differences)
        if (processing.getAmount() == null || request.getClaimAmount() == null ||
                processing.getAmount().setScale(2, RoundingMode.HALF_UP)
                        .compareTo(request.getClaimAmount().setScale(2, RoundingMode.HALF_UP)) != 0)
            throw new PaymentDataMismatchException(request.getClaimId(), "amount",
                    processing.getAmount().stripTrailingZeros().toPlainString(),
                    request.getClaimAmount().stripTrailingZeros().toPlainString());

        // Patient ID match
        if (processing.getPatientId() == null || !processing.getPatientId().equals(request.getBeneficiaryShaId()))
            throw new PaymentDataMismatchException(request.getClaimId(), "patientId",
                    processing.getPatientId(), request.getBeneficiaryShaId());

        // Fund scheme match
        if (processing.getFundScheme() == null || !processing.getFundScheme().equals(request.getFundScheme()))
            throw new PaymentDataMismatchException(request.getClaimId(), "fundScheme",
                    processing.getFundScheme(), request.getFundScheme());

        // Claim must be in SENT_TO_ERP status to be eligible for ERP approval
        if (!processing.getPaymentStatus().equals(PaymentsEnum.PaymentStatus.SENT_TO_ERP.toString()))
            throw new PaymentInvalidStatusException(request.getClaimId(), processing.getPaymentStatus(),
                    PaymentsEnum.PaymentStatus.SENT_TO_ERP.toString());

        // Company bank account must map to a known fund bank account
        boolean validBankAccount = fundBankDetailsRepository.findAll().stream()
                .anyMatch(m -> request.getCompanyBankAccount().equals(m.getFundBankAccountNumber()));
        if (!validBankAccount)
            throw new PaymentDataMismatchException(request.getClaimId(), "companyBankAccount",
                    "No matching fund_bank_details entry found.", request.getCompanyBankAccount());

        // ERP type match — null ERP in request is only valid if stored type is IFS
        if (request.getErp() == null) {
            if (!PaymentsEnum.ErpType.IFS.toString().equals(processing.getErpType()))
                throw new PaymentDataMismatchException(request.getClaimId(), "erpType",
                        processing.getErpType(), null);
        } else {
            if (processing.getErpType() == null || !processing.getErpType().equalsIgnoreCase(request.getErp()))
                throw new PaymentDataMismatchException(request.getClaimId(), "erpType",
                        processing.getErpType(), request.getErp());
        }
    }

    // =========================================================================
    // Validation — Gateway Callback
    // =========================================================================

    /**
     * Validates a gateway callback request against the stored batch record.
     * Checks: mandatory fields, externalReference (success only), amount, invoice,
     * PO, provider, batch status.
     */
    private void validateGatewayCallback(PaymentBatch batch, PaymentGatewayCallbackRequest request) {
        // Mandatory scalar fields
        if (request.getStatus() == null || request.getStatus().isBlank())
            throw new PaymentMissingDataException("status is missing for batch: " + request.getBatchId());
        if (request.getTimestamp() == null)
            throw new PaymentMissingDataException("timestamp is missing for batch: " + request.getBatchId());

        // externalReference is mandatory only for successful statuses
        boolean isSuccessStatus = "FULLY_COMPLETED".equalsIgnoreCase(request.getStatus())
                || "PAYMENT_COMPLETED".equalsIgnoreCase(request.getStatus());
        if (isSuccessStatus && (request.getExternalReference() == null || request.getExternalReference().isBlank()))
            throw new PaymentMissingDataException("externalReference is missing for batch: " + request.getBatchId());

        // Amount match (2dp tolerance)
        if (batch.getAmount() == null || request.getAmount() == null ||
                batch.getAmount().setScale(2, RoundingMode.HALF_UP)
                        .compareTo(request.getAmount().setScale(2, RoundingMode.HALF_UP)) != 0)
            throw new PaymentBatchMismatchException(request.getBatchId(), "amount",
                    batch.getAmount().stripTrailingZeros().toPlainString(),
                    request.getAmount().stripTrailingZeros().toPlainString());

        // Invoice number match
        if (batch.getInvoiceNumber() == null || !batch.getInvoiceNumber().equals(request.getInvoiceNumber()))
            throw new PaymentBatchMismatchException(request.getBatchId(), "invoiceNumber",
                    batch.getInvoiceNumber(), request.getInvoiceNumber());

        // Purchase order number match
        if (batch.getPurchaseOrderNumber() == null
                || !batch.getPurchaseOrderNumber().equals(request.getPurchaseOrderNumber()))
            throw new PaymentBatchMismatchException(request.getBatchId(), "purchaseOrderNumber",
                    batch.getPurchaseOrderNumber(), request.getPurchaseOrderNumber());

        // Facility identifier (provider ID) match
        if (batch.getProviderId() == null || !batch.getProviderId().equals(request.getFacilityIdentifier()))
            throw new PaymentBatchMismatchException(request.getBatchId(), "facilityIdentifier",
                    batch.getProviderId(), request.getFacilityIdentifier());

        // Batch must be in SENT_FOR_PAYMENT status to accept a callback
        if (!batch.getBatchStatus().equals(PaymentsEnum.BatchStatus.SENT_FOR_PAYMENT))
            throw new PaymentBatchInvalidStatusException(request.getBatchId(),
                    batch.getBatchStatus().toString(), PaymentsEnum.BatchStatus.SENT_FOR_PAYMENT.toString());
    }

    // =========================================================================
    // Remarks Resolution
    // =========================================================================

    /**
     * Resolves the effective remarks for a batch callback.
     * For VALIDATION_FAILED with batch-level errors (claimId = "N/A"), extracts the
     * actual errorDetails from those entries instead of using the generic
     * processingRemarks.
     */
    private String resolveEffectiveRemarks(PaymentGatewayCallbackRequest request) {
        if (!"VALIDATION_FAILED".equalsIgnoreCase(request.getStatus()) || request.getFailedClaims() == null) {
            return request.getProcessingRemarks();
        }

        List<String> batchLevelErrors = new ArrayList<>();
        for (PaymentGatewayCallbackRequest.FailedClaim fc : request.getFailedClaims()) {
            // claimId = null or "N/A" means this is a batch-level error, not a per-claim
            // error
            if (fc.getClaimId() == null || "N/A".equalsIgnoreCase(fc.getClaimId())) {
                String detail = fc.getErrorDetails() != null ? fc.getErrorDetails()
                        : (fc.getErrorReason() != null ? fc.getErrorReason() : "Unknown error");
                batchLevelErrors.add(detail);
            }
        }

        return batchLevelErrors.isEmpty() ? request.getProcessingRemarks() : String.join(", ", batchLevelErrors);
    }

    // =========================================================================
    // Per-Claim Failed Claim Handling
    // =========================================================================

    /**
     * Applies individual error remarks to specific failed claims within a
     * VALIDATION_FAILED batch.
     *
     * Strategy: group claim IDs by their final remark text, then issue one bulk
     * UPDATE per unique
     * remark — avoiding the N+1 SELECT+UPDATE pattern that would occur per-claim.
     *
     * Note: claimId = "N/A" entries are batch-level errors already handled via
     * resolveEffectiveRemarks,
     * so they are skipped here.
     */
    private void handleFailedClaims(PaymentGatewayCallbackRequest request) {
        // Step A — Build claimId → set of error parts (skipping N/A batch-level
        // entries)
        Map<String, Set<String>> claimErrors = new LinkedHashMap<>();
        for (PaymentGatewayCallbackRequest.FailedClaim fc : request.getFailedClaims()) {
            if (fc.getClaimId() == null || "N/A".equalsIgnoreCase(fc.getClaimId()))
                continue;

            String reason = fc.getErrorReason();
            String details = fc.getErrorDetails();

            // DUPLICATE_CLAIM collapses to a single keyword; everything else is "REASON:
            // details"
            String remarkPart = "DUPLICATE_CLAIM".equalsIgnoreCase(reason)
                    ? "DUPLICATE"
                    : (reason != null ? reason : "Unknown Error") + ": "
                            + (details != null ? details : "No details provided");

            claimErrors.computeIfAbsent(fc.getClaimId(), k -> new LinkedHashSet<>()).add(remarkPart);
        }

        // Step B — Invert: remark text → list of claim IDs (DUPLICATE wins if mixed
        // with other errors)
        Map<String, List<String>> remarkToClaimIds = new HashMap<>();
        claimErrors.forEach((claimId, errors) -> {
            String finalRemark = errors.contains("DUPLICATE") ? "DUPLICATE" : String.join(", ", errors);
            remarkToClaimIds.computeIfAbsent(finalRemark, k -> new ArrayList<>()).add(claimId);
        });

        // Step C — One bulk UPDATE per unique remark (audit fields included)
        LocalDateTime now = LocalDateTime.now();
        String auditor = HealthIdContextHolder.userId() != null ? HealthIdContextHolder.userId() : "system";
        remarkToClaimIds.forEach((remark, claimIds) -> processingRepository.updateRemarksByClaimIdsAndBatchId(remark,
                claimIds, request.getBatchId(), now, auditor));
    }
}
