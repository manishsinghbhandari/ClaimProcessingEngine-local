package io.coredge.health.claims.payments.service;

import io.coredge.health.claims.context.HealthIdContextHolder;
import io.coredge.health.claims.payments.entity.PaymentProcessing;
import io.coredge.health.claims.payments.enums.PaymentsEnum;
import io.coredge.health.claims.payments.repository.PaymentProcessingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentBatchService {

    private final PaymentProcessingRepository processingRepository;
    private final PaymentBatchProcessor batchProcessor;
    private final PaymentUpdateErpNotificationService paymentUpdateErpNotificationService;

    @Value("${payment.batch.scheduler.enabled:true}")
    private boolean schedulerEnabled;

    /**
     * Scheduled task to process approved payments every 2 hours (configurable).
     * Groups claims by provider and sends them in batches to the Gateway.
     */
    @Scheduled(cron = "${payment.batch.scheduler.cron:0 0 */2 * * *}")
    public void processPaymentBatches() {
        if (!schedulerEnabled) {
            log.debug("Payment batch scheduler is disabled. Skipping.");
            return;
        }
        log.info("Starting scheduled payment batch processing job...");
        processClaimsByStatus(PaymentsEnum.PaymentStatus.APPROVED_FOR_PAYMENT.name());
        log.info("Completed scheduled payment batch processing job.");
    }

    /**
     * Manually triggers processing of claims that need reparation.
     * Runs in the background to avoid blocking the caller.
     */
    @Async("taskExecutor")
    public void reprocessReparationClaims() {
        log.info("Starting manual reparation processing job...");
        processClaimsByStatus(PaymentsEnum.PaymentStatus.NEEDS_REPARATION.name());
        log.info("Completed manual reparation processing job.");
    }

    /**
     * Manually triggers processing of claims that need reparation.
     * Runs in the background to avoid blocking the caller.
     */
    @Async("taskExecutor")
    public void reprocessErpReparationClaims() {
        log.info("Starting manual erp reparation processing job...");
        String status = PaymentsEnum.PaymentStatus.ERP_PAYMENT_NEEDS_REPARATION.name();
        List<String> providerIds = processingRepository.findDistinctProviderIdsByStatus(status);

        if (providerIds.isEmpty()) {
            log.info("No providers found needing ERP reparation. Skipping.");
            return;
        }

        log.info("Found {} providers needing ERP reparation. Processing...", providerIds.size());

        providerIds.forEach(providerId -> {
            try {
                List<PaymentProcessing> claims = processingRepository.findByProviderIdAndPaymentStatus(providerId,
                        status);
                if (!claims.isEmpty()) {
                    processErpReparationClaims(claims);
                }
            } catch (Exception e) {
                log.error("ERP Reparation failed for provider {}: {}", providerId, e.getMessage());
            }
        });
        log.info("Completed manual erp reparation processing job.");
    }

    private void processClaimsByStatus(String status) {
        List<String> providerIds = processingRepository.findDistinctProviderIdsByStatus(status);
        if (providerIds.isEmpty()) {
            log.info("No providers found with claims in status {}. Skipping.", status);
            return;
        }

        log.info("Found {} providers with claims in status {}. Processing by provider...", providerIds.size(), status);

        for (String providerId : providerIds) {
            try {
                processClaimsForProvider(providerId, status);
            } catch (Exception e) {
                log.error("Failed to process claims for provider {}: {}", providerId, e.getMessage());
            }
        }
    }

    private void processClaimsForProvider(String providerId, String status) {
        List<PaymentProcessing> providerClaims = processingRepository.findByProviderIdAndPaymentStatus(providerId,
                status);
        if (providerClaims.isEmpty())
            return;

        // 1. Separate claims failing decryption from valid ones
        List<PaymentProcessing> validClaims = new ArrayList<>();
        List<PaymentProcessing> failedClaims = new ArrayList<>();

        for (PaymentProcessing claim : providerClaims) {
            if (isDecryptionFailed(claim)) {
                failedClaims.add(claim);
            } else {
                validClaims.add(claim);
            }
        }

        // 2. Isolate failures (Mark for REPARATION)
        if (!failedClaims.isEmpty()) {
            log.warn("Isolating {} claims for provider {} due to decryption failure.", failedClaims.size(), providerId);
            List<String> failedClaimIds = failedClaims.stream().map(PaymentProcessing::getClaimId)
                    .collect(Collectors.toList());
            bulkUpdateStatusRemarksAndBatchId(
                    PaymentsEnum.PaymentStatus.NEEDS_REPARATION.name(),
                    "Decryption Failed: Sensitive bank details could not be recovered for batching.",
                    null,
                    failedClaimIds);
        }

        // 3. Group valid claims by decrypted Bank Account and process
        Map<String, List<PaymentProcessing>> bankAccountGroups = validClaims.stream()
                .collect(Collectors.groupingBy(PaymentProcessing::getDebitBankAccount));

        bankAccountGroups.forEach((bankAccount, claims) -> {
            try {
                log.info("Submitting {} claims to gateway for provider {} and bank account {}", claims.size(),
                        providerId, bankAccount);
                batchProcessor.processBatchForProvider(providerId, claims);
            } catch (Exception e) {
                log.error("Batch submission failed for provider {} and bank {}: {}", providerId, bankAccount,
                        e.getMessage());
            }
        });
    }

    private boolean isDecryptionFailed(PaymentProcessing claim) {
        String account = claim.getDebitBankAccount();
        if (account == null || account.isBlank())
            return true; // Missing account is a failure
        // Heuristic: AES/GCM Base64 ciphertext with IV is typically > 35 chars.
        // Account numbers are typically < 25 digits.
        return account.length() > 30 || account.contains("/") || account.contains("+") || account.contains("=");
    }

    private void processErpReparationClaims(List<PaymentProcessing> claimsForProcessing) {
        log.info("Starting manual erp reparation processing job, No of records : {}", claimsForProcessing.size());
        claimsForProcessing.forEach(paymentProcessing -> {
            try {
                log.info("ERP Re-Processing claimId : {},  for erpType {}", paymentProcessing.getClaimId(),
                        paymentProcessing.getErpType());
                PaymentsEnum.PaymentStatus paymentStatus = paymentUpdateErpNotificationService
                        .updateStatusInErp(paymentProcessing);
                paymentProcessing.setPaymentStatus(paymentStatus.name());
                // Save individually within loop as each might have different status result from
                // ERP
                processingRepository.save(paymentProcessing);
            } catch (Exception e) {
                log.error("ERP Re-Processing failed, claimId : {},  for erpType {}", paymentProcessing.getClaimId(),
                        paymentProcessing.getErpType(), e);
            }
        });
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
}
