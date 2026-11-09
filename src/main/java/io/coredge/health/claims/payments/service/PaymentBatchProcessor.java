package io.coredge.health.claims.payments.service;

import io.coredge.health.claims.config.properties.ApplicationProperties;
import io.coredge.health.claims.context.HealthIdContextHolder;
import io.coredge.health.claims.db.entity.Provider;
import io.coredge.health.claims.db.repositories.ProviderRepository;
import io.coredge.health.claims.payments.clients.PaymentGatewayClient;
import io.coredge.health.claims.payments.dto.DisbursementRequest;
import io.coredge.health.claims.payments.entity.FundBankDetails;
import io.coredge.health.claims.payments.entity.PaymentBatch;
import io.coredge.health.claims.payments.entity.PaymentProcessing;
import io.coredge.health.claims.payments.enums.PaymentsEnum;
import io.coredge.health.claims.payments.repository.FundBankDetailsRepository;
import io.coredge.health.claims.payments.repository.PaymentBatchRepository;
import io.coredge.health.claims.payments.repository.PaymentProcessingRepository;
import io.coredge.health.claims.payments.exception.PaymentGateway400Exception;
import io.coredge.health.claims.payments.exception.PaymentValidationException;
import io.coredge.health.claims.payments.utils.PaymentIdGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class PaymentBatchProcessor {

    private final PaymentProcessingRepository processingRepository;
    private final PaymentBatchRepository batchRepository;
    private final ProviderRepository providerRepository;
    private final FundBankDetailsRepository fundBankDetailsRepository;
    private final PaymentGatewayClient gatewayClient;
    private final PaymentAuditService auditService;
    private final PaymentIdGenerator paymentIdGenerator;
    private final ObjectMapper objectMapper;

    @Value("${payment.gateway.call.back.link}")
    private String referenceLink;

    private record ValidationResult(
            Provider provider,
            String fundBankName,
            String fundBranchCode,
            String fundBankAccountNo) {
    }

    public PaymentBatchProcessor(PaymentProcessingRepository processingRepository,
            PaymentBatchRepository batchRepository,
            ProviderRepository providerRepository,
            FundBankDetailsRepository fundBankDetailsRepository,
            PaymentGatewayClient gatewayClient,
            PaymentAuditService auditService,
            PaymentIdGenerator paymentIdGenerator,
            ObjectMapper objectMapper) {
        this.processingRepository = processingRepository;
        this.batchRepository = batchRepository;
        this.providerRepository = providerRepository;
        this.fundBankDetailsRepository = fundBankDetailsRepository;
        this.gatewayClient = gatewayClient;
        this.auditService = auditService;
        this.paymentIdGenerator = paymentIdGenerator;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processBatchForProvider(String providerId, List<PaymentProcessing> claims) {
        // Generate formatted unique IDs
        String batchId = paymentIdGenerator.generateBatchId();
        String invoiceNumber = paymentIdGenerator.generateInvoiceNumber();
        String purchaseOrderNumber = paymentIdGenerator.generatePurchaseOrderNumber();

        // Calculate total amount and collect claim IDs
        BigDecimal totalAmount = claims.stream().map(PaymentProcessing::getAmount).reduce(BigDecimal.ZERO,
                BigDecimal::add);
        BigDecimal providerShareAmount = claims.stream()
                .map(c -> c.getProviderShareAmount() != null ? c.getProviderShareAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal systemUtilizationAmount = claims.stream()
                .map(c -> c.getSystemUtilizationAmount() != null ? c.getSystemUtilizationAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<String> claimIds = claims.stream().map(PaymentProcessing::getClaimId).collect(Collectors.toList());

        String providerName = claims.getFirst().getProviderName();

        // 1. Create and Save Batch with status CREATED
        PaymentBatch batch = new PaymentBatch();
        batch.setBatchId(batchId);
        batch.setProviderId(providerId);
        batch.setProviderName(providerName);
        batch.setAmount(totalAmount);
        batch.setProviderShareAmount(providerShareAmount);
        batch.setSystemUtilizationAmount(systemUtilizationAmount);
        batch.setClaimIds(claimIds);
        batch.setInvoiceNumber(invoiceNumber);
        batch.setPurchaseOrderNumber(purchaseOrderNumber);
        batch.setBatchStatus(PaymentsEnum.BatchStatus.CREATED);
        batchRepository.save(batch);

        // 2. Link Claims to Batch but KEEP status APPROVED_FOR_PAYMENT (Bulk Update
        // with Partitioning)
        bulkUpdateBatchId(batchId, claimIds);

        // 3. Pre-validation: Ensure all required data for the Gateway is present
        ValidationResult validationResult;
        try {
            validationResult = validateBatchData(claims, providerId);
        } catch (PaymentValidationException e) {
            String validationError = e.getErrorMessage();
            log.error("Pre-validation failed for batch {}: {}", batchId, validationError);

            // Log FAILED audit event
            auditService.logEvent(PaymentsEnum.AuditEventType.BATCH,
                    PaymentsEnum.SystemType.PAYER,
                    PaymentsEnum.SystemType.PAYMENT_GATEWAY,
                    PaymentsEnum.PaymentStatus.APPROVED_FOR_PAYMENT,
                    null, batchId, "Batch construction for: " + batchId, null, null,
                    PaymentsEnum.AuditStatus.FAILED.name(), validationError);

            // Update Batch status
            batch.setBatchStatus(PaymentsEnum.BatchStatus.FAILED);
            batchRepository.save(batch);

            // Update Claims with remarks, status and preserve batchId for traceability
            bulkUpdateStatusRemarksAndBatchId(
                    PaymentsEnum.PaymentStatus.NEEDS_REPARATION.name(),
                    validationError,
                    batchId,
                    claimIds);
            return;
        }

        // 4. Construct DisbursementRequest DTO (using pre-validated result)
        DisbursementRequest request = buildDisbursementRequest(batch, claims, validationResult.provider(),
                validationResult.fundBankName(), validationResult.fundBranchCode(),
                validationResult.fundBankAccountNo());

        // 5. Log BATCH event (PASSED Validation)
        auditService.logEvent(PaymentsEnum.AuditEventType.BATCH,
                PaymentsEnum.SystemType.PAYER,
                PaymentsEnum.SystemType.PAYMENT_GATEWAY,
                PaymentsEnum.PaymentStatus.APPROVED_FOR_PAYMENT,
                null, batchId, "Batch constructed: " + batchId, null, null,
                PaymentsEnum.AuditStatus.PASSED.name(), "Validation successful");

        // 6. Call Payment Gateway
        try {
            String gatewayRef = gatewayClient.sendBatchPayment(request);

            // 7. SUCCESS: Update Batch and Claims to SENT_FOR_PAYMENT
            batch.setBatchStatus(PaymentsEnum.BatchStatus.SENT_FOR_PAYMENT);
            batch.setPaymentGatewayReference(getTraceId(gatewayRef));
            batchRepository.save(batch);

            // 7. SUCCESS: Update Claims to SENT_FOR_PAYMENT (Bulk Update with Partitioning)
            bulkUpdateStatusRemarksAndBatchId(
                    PaymentsEnum.PaymentStatus.SENT_FOR_PAYMENT.name(),
                    null,
                    batchId,
                    claimIds);

            log.info("Batch {} successfully sent for payment. Gateway Ref: {}", batchId, gatewayRef);

            auditService.logEvent(PaymentsEnum.AuditEventType.BATCH,
                    PaymentsEnum.SystemType.PAYER,
                    PaymentsEnum.SystemType.PAYMENT_GATEWAY,
                    PaymentsEnum.PaymentStatus.SENT_FOR_PAYMENT,
                    null, batchId, request, gatewayRef, "200",
                    PaymentsEnum.AuditStatus.PASSED.name(), "Successfully sent to Gateway");
        } catch (PaymentGateway400Exception e) {
            String errorJson = e.getMessage();
            String extractedMessage = errorJson;
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> errorMap = objectMapper.readValue(errorJson, java.util.Map.class);
                if (errorMap.containsKey("message")) {
                    extractedMessage = (String) errorMap.get("message");
                }
            } catch (Exception parseEx) {
                log.warn("Failed to parse gateway error JSON: {}", errorJson);
            }

            log.error("Gateway rejected batch {} with 400 error: {}. Claims will be marked for REPARATION.",
                    batchId, extractedMessage);

            // FAILURE: Set Batch status to FAILED
            batch.setBatchStatus(PaymentsEnum.BatchStatus.FAILED);
            batchRepository.save(batch);

            // Flag claims so they are EXCLUDED from future runs until fixed
            final String remarkToSave = "Gateway Rejection: " + extractedMessage;
            log.info("Updating {} claims for batch {} with remark: {}", claims.size(), batchId, remarkToSave);

            // Flag claims with NEEDS_REPARATION, preserve batchId for traceability
            bulkUpdateStatusRemarksAndBatchId(
                    PaymentsEnum.PaymentStatus.NEEDS_REPARATION.name(),
                    remarkToSave,
                    batchId,
                    claimIds);

            auditService.logEvent(PaymentsEnum.AuditEventType.BATCH,
                    PaymentsEnum.SystemType.PAYER,
                    PaymentsEnum.SystemType.PAYMENT_GATEWAY,
                    PaymentsEnum.PaymentStatus.APPROVED_FOR_PAYMENT,
                    null, batchId, request, errorJson, "400",
                    PaymentsEnum.AuditStatus.FAILED.name(), "Gateway rejected request: " + extractedMessage);

        } catch (Exception e) {
            log.error("Gateway call failed for batch {}: {}. Claims will remain in APPROVED status for retry.",
                    batchId, e.getMessage());

            // PERSISTENT FAILURE: Set Batch status to FAILED
            batch.setBatchStatus(PaymentsEnum.BatchStatus.FAILED);
            batchRepository.save(batch);

            // PERSISTENT FAILURE: Update remarks (Bulk Update with Partitioning)
            bulkUpdateRemarksAndBatchId(e.getMessage(), batchId, claimIds);

            auditService.logEvent(PaymentsEnum.AuditEventType.BATCH,
                    PaymentsEnum.SystemType.PAYER,
                    PaymentsEnum.SystemType.PAYMENT_GATEWAY,
                    PaymentsEnum.PaymentStatus.APPROVED_FOR_PAYMENT,
                    null, batchId, request, e.getMessage(), "ERROR",
                    PaymentsEnum.AuditStatus.FAILED.name(),
                    "Gateway call failed: " + e.getMessage());
        }
    }

    /**
     * Utility method to extract traceId from gateway response JSON.
     * This is the best effort attempt and will return null if parsing fails or
     * traceId is not found.
     * 
     * @param gatewayRef
     * @return
     */
    private @Nullable String getTraceId(String gatewayRef) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> responseMap = objectMapper.readValue(gatewayRef, Map.class);
            Map<String, Object> dataMap = (Map<String, Object>) responseMap.get("data");

            return dataMap != null ? (String) dataMap.get("traceId") : null;
        } catch (Exception e) {
            log.warn("Failed to parse gateway response: {}", gatewayRef);
            return null;
        }
    }

    private ValidationResult validateBatchData(List<PaymentProcessing> claims, String providerId) {
        // 1. Check Payer Debit Details (from first claim)
        PaymentProcessing first = claims.getFirst();
        if (isEmpty(first.getDebitBankAccount()))
            throw new PaymentValidationException("Missing Payer Debit Bank Account");
        if (isEmpty(first.getDebitBankName()))
            throw new PaymentValidationException("Missing Payer Debit Bank Name");

        // 2. Check Provider Credit Details
        Provider provider = providerRepository.findByFidCode(providerId)
                .orElseThrow(() -> new PaymentValidationException(
                        "Provider details not found for providerId: " + providerId))
                .getFirst();

        if (isEmpty(provider.getAccountNumber()))
            throw new PaymentValidationException("Missing Provider Bank Account");
        if (isEmpty(provider.getBankName()))
            throw new PaymentValidationException("Missing Provider Bank Name");
        if (isEmpty(provider.getAccountName()))
            throw new PaymentValidationException("Missing Provider Account Name");
        if (isEmpty(provider.getBankCode()))
            throw new PaymentValidationException("Missing Provider Bank Code");
        if (isEmpty(provider.getBranch()))
            throw new PaymentValidationException("Missing Provider Branch");

        // 3. Check Debit Bank Details from fund_bank_details
        String fundBankName = null;
        String fundBranchCode = null;
        String fundBankAccountNo = first.getDebitBankAccount();

        List<FundBankDetails> fundBankDetails = fundBankDetailsRepository.findAll();
        if (fundBankDetails.isEmpty()) {
            throw new PaymentValidationException("Fund Bank Details are empty.");
        }

        for (FundBankDetails bankDetail : fundBankDetails) {
            if (fundBankAccountNo.equals(bankDetail.getFundBankAccountNumber())) {
                fundBankName = bankDetail.getFundBankName();
                fundBranchCode = bankDetail.getFundBranchCode();
                break;
            }
        }

        if (fundBankName == null || fundBranchCode == null) {
            throw new PaymentValidationException(
                    "No matching fund bank details found for accountNo. : " + fundBankAccountNo);
        }

        // 4. Check Claim Level Data
        for (PaymentProcessing c : claims) {
            if (c.getAmount() == null || c.getAmount().compareTo(BigDecimal.ZERO) <= 0)
                throw new PaymentValidationException("Invalid amount for claim: " + c.getClaimId());
            if (isEmpty(c.getPatientId()))
                throw new PaymentValidationException("Missing Patient ID for claim: " + c.getPatientId());
            if (c.getClaimCreationDate() == null)
                throw new PaymentValidationException("Missing Creation Date for claim: " + c.getClaimId());
        }

        return new ValidationResult(provider, fundBankName, fundBranchCode, fundBankAccountNo);
    }

    private boolean isEmpty(String s) {
        return s == null || s.isBlank();
    }

    private DisbursementRequest buildDisbursementRequest(PaymentBatch batch, List<PaymentProcessing> claims,
            Provider provider, String fundBankName, String fundBranchCode, String fundBankAccountNo) {
        List<DisbursementRequest.ClaimRequest> claimDtos = claims.stream()
                .map(c -> DisbursementRequest.ClaimRequest.builder()
                        .claimId(c.getClaimId())
                        .primaryContributor(c.getPatientId())
                        .beneficiary(c.getPatientId())
                        .amount(c.getAmount())
                        .currency("KES")
                        .dateRaised(c.getClaimCreationDate()
                                .format(java.time.format.DateTimeFormatter
                                        .ofPattern("yyyy-MM-dd HH:mm:ss")))
                        .fundDistribution(
                                createFundDistribution(c.getProviderShareAmount(), c.getSystemUtilizationAmount()))
                        .build())
                .collect(Collectors.toList());

        return DisbursementRequest.builder()
                .facility(DisbursementRequest.Facility.builder()
                        .identifier(batch.getProviderId())
                        .name(batch.getProviderName())
                        .build())
                .invoice(DisbursementRequest.Invoice.builder()
                        .number(batch.getInvoiceNumber())
                        .batchId(batch.getBatchId())
                        .purchaseOrderNumber(batch.getPurchaseOrderNumber())
                        .build())
                .claims(claimDtos)
                .totalAmount(batch.getAmount())
                .fundDistribution(
                        createFundDistribution(batch.getProviderShareAmount(), batch.getSystemUtilizationAmount()))
                .resultUrl(referenceLink)
                .debit(DisbursementRequest.Account.builder()
                        .bankName(fundBankName)
                        .accountNumber(fundBankAccountNo)
                        .sortCode(fundBranchCode)
                        .build())
                .credit(DisbursementRequest.Account.builder()
                        .bankName(provider.getBankName())
                        .accountNumber(provider.getAccountNumber())
                        .accountName(provider.getAccountName())
                        .bankCode(provider.getBankCode())
                        .branch(provider.getBranch())
                        .build())
                .build();
    }

    private io.coredge.health.claims.models.eclaims.Invoice.FundDistribution createFundDistribution(
            BigDecimal providerShare, BigDecimal systemUtilization) {
        return io.coredge.health.claims.models.eclaims.Invoice.FundDistribution.builder()
                .providerShare(io.coredge.health.claims.models.eclaims.Invoice.Amount.builder()
                        .amount(providerShare != null ? providerShare : BigDecimal.ZERO)
                        .build())
                .systemUtilization(io.coredge.health.claims.models.eclaims.Invoice.Amount.builder()
                        .amount(systemUtilization != null ? systemUtilization : BigDecimal.ZERO)
                        .build())
                .build();
    }

    private void bulkUpdateBatchId(String batchId, List<String> claimIds) {
        int partitionSize = 950;
        LocalDateTime now = LocalDateTime.now();
        String modifiedBy = HealthIdContextHolder.userId() != null
                ? HealthIdContextHolder.userId()
                : "system";
        for (int i = 0; i < claimIds.size(); i += partitionSize) {
            List<String> partition = claimIds.subList(i, Math.min(i + partitionSize, claimIds.size()));
            processingRepository.updateBatchIdByClaimIdIn(batchId, partition, now, modifiedBy);
        }
    }

    private void bulkUpdateStatusRemarksAndBatchId(String status, String remarks, String batchId,
            List<String> claimIds) {
        int partitionSize = 950;
        LocalDateTime now = LocalDateTime.now();
        String modifiedBy = HealthIdContextHolder.userId() != null
                ? HealthIdContextHolder.userId()
                : "system";
        for (int i = 0; i < claimIds.size(); i += partitionSize) {
            List<String> partition = claimIds.subList(i, Math.min(i + partitionSize, claimIds.size()));
            processingRepository.updateStatusRemarksAndBatchIdByClaimIdIn(status, remarks, batchId, partition, now,
                    modifiedBy);
        }
    }

    private void bulkUpdateRemarksAndBatchId(String remarks, String batchId, List<String> claimIds) {
        int partitionSize = 950;
        LocalDateTime now = LocalDateTime.now();
        String modifiedBy = HealthIdContextHolder.userId() != null
                ? HealthIdContextHolder.userId()
                : "system";
        for (int i = 0; i < claimIds.size(); i += partitionSize) {
            List<String> partition = claimIds.subList(i, Math.min(i + partitionSize, claimIds.size()));
            processingRepository.updateRemarksAndBatchIdByClaimIdIn(remarks, batchId, partition, now, modifiedBy);
        }
    }

}
