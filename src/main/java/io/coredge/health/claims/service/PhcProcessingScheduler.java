package io.coredge.health.claims.service;

import io.coredge.health.claims.context.HealthIdContextHolder;
import io.coredge.health.claims.db.entity.PhcProcessingCursor;
import io.coredge.health.claims.db.entity.PhcProcessingCursor.ProcessingAction;
import io.coredge.health.claims.db.entity.PhcProcessingCursor.ProcessingStatus;
import io.coredge.health.claims.db.entity.PhcBatchPayment;
import io.coredge.health.claims.db.entity.PhcClaimPayment;
import io.coredge.health.claims.db.repositories.PhcProcessingCursorRepository;
import io.coredge.health.claims.db.repositories.PhcBatchPaymentRepository;
import io.coredge.health.claims.db.repositories.PhcClaimPaymentRepository;
import io.coredge.health.claims.enums.ExtensionStateCodeEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Background scheduler for processing PHC payment cursors.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PhcProcessingScheduler {

    private final PhcProcessingCursorRepository cursorRepository;
    private final PhcBatchPaymentRepository phcBatchPaymentRepository;
    private final PhcClaimPaymentRepository phcClaimPaymentRepository;
    private final AutomatedPhcPaymentService automatedPhcPaymentService;
    private final PhcPaymentApprovalService phcPaymentApprovalService;

    @Value("${phc.processing.enable}")
    private boolean processingEnabled;

    @Value("${phc.processing.batch-size:1}")
    private int batchSize;

    @Value("${phc.erp.facility.batch-size:5}")
    private int erpFacilityBatchSize;

    @Value("${phc.erp.facility.delay-ms:20000}")
    private long erpFacilityDelayMs;

    @Value("${phc.erp.healer.enable:false}")
    private boolean healerEnabled;

    @Value("${phc.processing.checkpoint-interval:100}")
    private int checkpointInterval;

    /**
     * Main scheduler method that processes pending PHC cursors.
     * Runs at configurable intervals (default: every 60 seconds after previous completes).
     */
    @Scheduled(fixedDelayString = "${phc.processing.delay:60000}")
    public void processPendingCursors() {
        log.info("PhcProcessingScheduler started");
        if (!processingEnabled) {
            log.debug("PHC background processing is disabled");
            return;
        }

        log.debug("Checking for pending and in-progress PHC cursors...");

        // Get all PENDING and IN_PROGRESS cursors ordered by creation date
        List<PhcProcessingCursor> pendingCursors = cursorRepository.findByStatusInOrderByCreationDateAsc(
                Arrays.asList(ProcessingStatus.PENDING, ProcessingStatus.IN_PROGRESS)
        );

        if (pendingCursors.isEmpty()) {
            log.debug("No pending or in-progress cursors found");
            return;
        }

        log.info("Found {} pending/in-progress cursor(s) to process", pendingCursors.size());

        for (PhcProcessingCursor cursor : pendingCursors) {
            try {
                processCursor(cursor);
            } catch (Exception e) {
                log.error("Error processing cursor {}: {}", cursor.getId(), e.getMessage(), e);
                handleCursorError(cursor, e);
            }
        }
    }

    /**
     * Process a single cursor based on its action type.
     * Marks cursor as IN_PROGRESS, delegates to appropriate handler, then marks as COMPLETED.
     * Sets user context from cursor's createdBy for auditing.
     */
    private void processCursor(PhcProcessingCursor cursor) {
        log.info("Processing cursor {} - Action: {}, YearMonth: {}", 
                cursor.getId(), cursor.getAction(), cursor.getYearMonth());

        String cursorCreator = cursor.getCreatedBy() != null ? cursor.getCreatedBy() : "system";
        HealthIdContextHolder.userId(cursorCreator);
        // Mark as IN_PROGRESS
        cursor.setStatus(ProcessingStatus.IN_PROGRESS);
        cursor.setStartedAt(LocalDateTime.now());
        cursor = cursorRepository.save(cursor);

        try {
            switch (cursor.getAction()) {
                case CALCULATION:
                    processCalculation(cursor);
                    break;
                case APPROVAL:
                    processApproval(cursor);
                    break;
                case SEND_TO_ERP:
                    processErpSubmission(cursor);
                    break;
                default:
                    throw new IllegalStateException("Unknown action: " + cursor.getAction());
            }

            // Mark as COMPLETED
            cursor.setStatus(ProcessingStatus.COMPLETED);
            cursor.setCompletedAt(LocalDateTime.now());
            cursor = cursorRepository.save(cursor);

            log.info("Successfully completed cursor {} - Action: {}", cursor.getId(), cursor.getAction());

        } catch (Exception e) {
            log.error("Failed to process cursor {}: {}", cursor.getId(), e.getMessage(), e);
            throw e;
        } finally {
            // Clear user context after processing
            HealthIdContextHolder.remove();
        }
    }

    /**
     * Process CALCULATION action - runs PHC payment calculation with batching and checkpoints.
     */
    private void processCalculation(PhcProcessingCursor cursor) {
        log.info("Running cursor-based PHC calculation for yearMonth: {}", cursor.getYearMonth());

        try {
            // Parse yearMonth string to YearMonth object
            YearMonth targetMonth = parseYearMonth(cursor.getYearMonth());
            
            // Convert YYYYMM format to 'MMM yyyy' format for Helper.getStartAndEndDateTime()
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM yyyy", java.util.Locale.ENGLISH);
            String monthNameForHelper = targetMonth.format(formatter);
            // Call new cursor-based calculation with batching and checkpoint support
            automatedPhcPaymentService.calculatePhcPaymentsWithCursor(cursor, targetMonth, monthNameForHelper);
            
            log.info("PHC calculation completed for yearMonth: {}", cursor.getYearMonth());

        } catch (Exception e) {
            log.error("Error in PHC calculation: {}", e.getMessage(), e);
            throw new RuntimeException("PHC calculation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Process APPROVAL action - auto-approves facility payments in batches.
     * Processes facility payments for a month and updates batch payment statuses.
     */
    private void processApproval(PhcProcessingCursor cursor) {
        log.info("Processing approval for yearMonth: {}", cursor.getYearMonth());

        try {
            // Get all facility payments for this month
            List<PhcClaimPayment> facilityPayments = phcClaimPaymentRepository.findByBatchPreparationDate(
                    cursor.getBatchPreparationDate());
            
            if (facilityPayments.isEmpty()) {
                log.warn("No facility payments found for yearMonth: {}", cursor.getYearMonth());
                cursor.setTotalRecords(0);
                cursor.setProcessedRecords(0);
                cursorRepository.save(cursor);
                return;
            }

            // Resume logic: Check if we're resuming from a checkpoint
            boolean isResuming = cursor.getLastProcessedPaymentId() != null;
            Long lastProcessedId = cursor.getLastProcessedPaymentId();
            
            if (isResuming) {
                log.info("Resuming approval from checkpoint - last processed payment ID: {}", lastProcessedId);
            }

            cursor.setTotalRecords(facilityPayments.size());
            cursorRepository.save(cursor);

            int processed = cursor.getProcessedRecords() != null ? cursor.getProcessedRecords() : 0;
            int approved = 0;
            int failed = cursor.getFailedRecords() != null ? cursor.getFailedRecords() : 0;
            int skipped = 0;

            // Process in batches for performance
            for (int i = 0; i < facilityPayments.size(); i += batchSize) {
                int end = Math.min(i + batchSize, facilityPayments.size());
                List<PhcClaimPayment> batch = facilityPayments.subList(i, end);

                for (PhcClaimPayment payment : batch) {
                    try {
                        // Skip if already processed (resuming scenario)
                        if (isResuming && lastProcessedId != null && payment.getId() <= lastProcessedId) {
                            skipped++;
                            if (skipped % 50 == 0) {
                                log.debug("Skipped {} already processed payments", skipped);
                            }
                            continue;
                        }

                        // Skip if already approved (duplicate prevention)
                        if (ExtensionStateCodeEnum.APPROVED.name().equals(payment.getStatus().trim())) {
                            log.debug("Facility payment ID: {} already approved. Skipping.", payment.getId());
                            processed++;
                            continue;
                        }

                        // Auto-approve if pending
                        if (ExtensionStateCodeEnum.PENDING.name().equals(payment.getStatus().trim())) {
                            payment.setStatus(ExtensionStateCodeEnum.APPROVED.name());
                            payment.setApprovedAmount(payment.getAmount());
                            payment.setApprovedBy(HealthIdContextHolder.userId());
                            payment.setApprovedAt(LocalDateTime.now());
                            phcClaimPaymentRepository.save(payment);
                            approved++;
                            processed++;
                            
                            log.debug("Auto-approved facility payment ID: {} with amount: {}", 
                                    payment.getId(), payment.getAmount());
                        } else {
                            // Count as processed even if not pending (rejected, etc.)
                            processed++;
                        }

                        // Update cursor checkpoint every N records (configurable, default 100)
                        if (processed % checkpointInterval == 0) {
                            cursor.setProcessedRecords(processed);
                            cursor.setLastProcessedPaymentId(payment.getId());
                            if (payment.getPhcBatchPayment() != null) {
                                cursor.setLastProcessedBatchId(payment.getPhcBatchPayment().getId());
                            }
                            cursorRepository.save(cursor);
                            log.info("Approval progress: {}/{} payments processed, {} newly approved, {} skipped", 
                                    processed, facilityPayments.size(), approved, skipped);
                        }

                    } catch (Exception e) {
                        log.error("Error approving facility payment {}: {}", payment.getId(), e.getMessage());
                        failed++;
                        cursor.setFailedRecords(failed);
                        cursorRepository.save(cursor);
                    }
                }
            }

            // Final update
            cursor.setProcessedRecords(processed);
            cursor.setFailedRecords(failed);
            cursorRepository.save(cursor);

            log.info("Approval processing completed for yearMonth: {} - Processed: {}, Approved: {}, Failed: {}", 
                    cursor.getYearMonth(), processed, approved, failed);

            // Update batch payment statuses
            updateBatchPaymentStatuses(cursor.getBatchPreparationDate());

        } catch (Exception e) {
            log.error("Error in approval processing: {}", e.getMessage(), e);
            throw new RuntimeException("Approval processing failed: " + e.getMessage(), e);
        }
    }

    /**
     * Process SEND_TO_ERP action - submits approved batch payments (counties) to ERP system.
     */
    private void processErpSubmission(PhcProcessingCursor cursor) {
        log.info("Processing ERP submission for yearMonth: {} (facility batch size: {}, delay: {}ms)", 
                cursor.getYearMonth(), erpFacilityBatchSize, erpFacilityDelayMs);

        try {
            // Get all approved batch payments (counties) for this month
            YearMonth ym = YearMonth.from(cursor.getBatchPreparationDate());
            LocalDate startDate = ym.atDay(1);
            LocalDate endDate = ym.atEndOfMonth();
            List<PhcBatchPayment> batchPayments = phcBatchPaymentRepository.findByDateBetween(startDate, endDate);
            
            // Filter only APPROVED county batches that haven't been sent to ERP yet
            List<PhcBatchPayment> approvedCounties = batchPayments.stream()
                    .filter(b -> b.getStatus() == PhcBatchPayment.Status.APPROVED)
                    .collect(Collectors.toList());

            if (approvedCounties.isEmpty()) {
                log.warn("No approved batch payments found for yearMonth: {}", cursor.getYearMonth());
                cursor.setTotalRecords(0);
                cursor.setProcessedRecords(0);
                cursorRepository.save(cursor);
                return;
            }

            // Calculate total facility payments using batchPreparationDate for performance
            List<PhcClaimPayment> approvedFacilities = phcClaimPaymentRepository
                    .findByBatchPreparationDateAndStatus(
                            cursor.getBatchPreparationDate(), 
                            ExtensionStateCodeEnum.APPROVED.name()
                    );
            int totalFacilityPayments = approvedFacilities.size();
            
            cursor.setTotalRecords(totalFacilityPayments);
            cursorRepository.save(cursor);
            
            log.info("Processing {} counties ({} facility payments) for ERP submission", 
                    approvedCounties.size(), totalFacilityPayments);

            // Resume logic: Check if we're resuming from a checkpoint
            boolean isResuming = cursor.getLastProcessedBatchId() != null;
            Long lastProcessedBatchId = cursor.getLastProcessedBatchId();
            
            if (isResuming) {
                log.info("Resuming ERP submission from checkpoint - last processed batch ID: {}", lastProcessedBatchId);
            }

            int processed = 0;
            int succeeded = 0;
            int failed = 0;
            int skipped = 0;

            // Process each county sequentially
            for (PhcBatchPayment countyBatch : approvedCounties) {
                try {
                    // Skip only if county is FULLY completed (ID < lastProcessedBatchId)
                    // If ID == lastProcessedBatchId, enter county to let facility-level resume handle it
                    if (isResuming && lastProcessedBatchId != null && countyBatch.getId() < lastProcessedBatchId) {
                        skipped++;
                        if (skipped % 5 == 0) {
                            log.debug("Skipped {} fully completed counties", skipped);
                        }
                        continue;
                    }

                    log.info("Processing county batch {} ({}/{}) for ERP submission", 
                            countyBatch.getId(), processed - skipped + 1, approvedCounties.size());
                    
                    // Send all facility payments for this county in batches
                    // Cursor tracking happens at facility level inside sendBatchPaymentToErpWithBatching
                    phcPaymentApprovalService.sendBatchPaymentToErpWithBatching(
                            countyBatch.getId(), 
                            erpFacilityBatchSize, 
                            erpFacilityDelayMs,
                            cursor);
                    
                    succeeded++;
                    processed++;

                    // Only update batch checkpoint (processedRecords tracked at facility level)
                    cursor.setLastProcessedBatchId(countyBatch.getId());
                    cursorRepository.save(cursor);
                    
                    log.info("Successfully completed county batch {} ({}/{})", 
                            countyBatch.getId(), processed - skipped, approvedCounties.size());

                } catch (Exception e) {
                    log.error("Error submitting county batch {} to ERP: {}", 
                            countyBatch.getId(), e.getMessage(), e);
                    failed++;
                    processed++;
                    // failedRecords tracked at facility level in sendBatchPaymentToErpWithBatching
                    cursor.setLastProcessedBatchId(countyBatch.getId());
                    cursorRepository.save(cursor);
                }
            }

            // Facility-level tracking is handled in sendBatchPaymentToErpWithBatching
            log.info("ERP submission completed for yearMonth: {} - Counties processed: {}, Succeeded: {}, Failed: {}, Skipped: {}", 
                    cursor.getYearMonth(), processed, succeeded, failed, skipped);

        } catch (Exception e) {
            log.error("Error in ERP submission: {}", e.getMessage(), e);
            throw new RuntimeException("ERP submission failed: " + e.getMessage(), e);
        }
    }

    /**
     * Handle cursor processing errors and update retry logic.
     * Failed cursors can retry up to maxRetries times (default: 3).
     */
    private void handleCursorError(PhcProcessingCursor cursor, Exception e) {
        try {
            cursor.setStatus(ProcessingStatus.FAILED);
            cursor.setErrorMessage(e.getMessage());
            cursor.setCompletedAt(LocalDateTime.now());
            
            if (cursor.canRetry()) {
                cursor.setRetryCount(cursor.getRetryCount() + 1);
                cursor.setStatus(ProcessingStatus.PENDING);  // Will retry on next run
                log.warn("Cursor {} failed, will retry (attempt {}/{})", 
                        cursor.getId(), cursor.getRetryCount(), cursor.getMaxRetries());
            } else {
                log.error("Cursor {} failed and cannot retry (max attempts reached)", cursor.getId());
            }
            
            cursorRepository.save(cursor);
        } catch (Exception saveException) {
            log.error("Error saving cursor error state: {}", saveException.getMessage(), saveException);
        }
    }

    /**
     * Parse yearMonth string to YearMonth object.
     * Supports two formats: YYYYMM (202510) or "MMM yyyy" (Oct 2025).
     */
    private YearMonth parseYearMonth(String yearMonth) {
        try {
            // Try YYYYMM format first
            if (yearMonth.matches("\\d{6}")) {
                int year = Integer.parseInt(yearMonth.substring(0, 4));
                int month = Integer.parseInt(yearMonth.substring(4, 6));
                return YearMonth.of(year, month);
            }
            // Try "MMM yyyy" format
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM yyyy", java.util.Locale.ENGLISH);
            return YearMonth.parse(yearMonth, formatter);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid yearMonth format: " + yearMonth, e);
        }
    }

    /**
     * Update batch payment statuses after facility payments approval.
     * Recalculates batch status based on facility payment approval counts.
     */
    private void updateBatchPaymentStatuses(LocalDate batchDate) {
        try {
            // Single query: fetch all facility payments for the month
            List<PhcClaimPayment> allFacilityPayments = phcClaimPaymentRepository.findByBatchPreparationDate(batchDate);
            
            if (allFacilityPayments.isEmpty()) {
                log.info("No facility payments found for batch date: {}", batchDate);
                return;
            }

            // Group facility payments by batch in memory (avoids N+1 queries)
            Map<PhcBatchPayment, List<PhcClaimPayment>> paymentsByBatch = allFacilityPayments.stream()
                    .filter(fp -> fp.getPhcBatchPayment() != null)
                    .collect(Collectors.groupingBy(PhcClaimPayment::getPhcBatchPayment));

            for (Map.Entry<PhcBatchPayment, List<PhcClaimPayment>> entry : paymentsByBatch.entrySet()) {
                PhcBatchPayment batch = entry.getKey();
                List<PhcClaimPayment> facilityPayments = entry.getValue();

                // Count statuses
                long approvedCount = facilityPayments.stream()
                        .filter(fp -> ExtensionStateCodeEnum.APPROVED.name().equals(fp.getStatus().trim()))
                        .count();
                long rejectedCount = facilityPayments.stream()
                        .filter(fp -> ExtensionStateCodeEnum.REJECTED.name().equals(fp.getStatus().trim()))
                        .count();
                long pendingCount = facilityPayments.stream()
                        .filter(fp -> ExtensionStateCodeEnum.PENDING.name().equals(fp.getStatus().trim()))
                        .count();

                // Determine batch status
                if (approvedCount == facilityPayments.size()) {
                    batch.setStatus(PhcBatchPayment.Status.APPROVED);
                    batch.setApprovedBy(HealthIdContextHolder.userId());
                    batch.setApprovedAt(LocalDateTime.now());
                } else if (rejectedCount == facilityPayments.size()) {
                    batch.setStatus(PhcBatchPayment.Status.REJECTED);
                } else if ((approvedCount > 0 || rejectedCount > 0) && pendingCount > 0) {
                    batch.setStatus(PhcBatchPayment.Status.PARTIAL_APPROVAL);
                } else if (approvedCount > 0 && rejectedCount > 0) {
                    batch.setStatus(PhcBatchPayment.Status.PARTIAL_APPROVAL);
                }

                phcBatchPaymentRepository.save(batch);
            }
            
            log.info("Updated {} batch payment statuses", paymentsByBatch.size());
        } catch (Exception e) {
            log.error("Error updating batch payment statuses: {}", e.getMessage(), e);
        }
    }

    /**
     * Post-completion healer scheduler to retry facility payments that failed during ERP submission.
     * Runs after SEND_TO_ERP cursors complete to catch any missed facility payments.
     * 
     * Strategy:
     * 1. Find all COMPLETED SEND_TO_ERP cursors
     * 2. For each cursor's batchPreparationDate, find facility payments still in APPROVED status
     * 3. Retry sending those to ERP (successful submissions change status to SENT_FOR_PAYMENT_PROCESSING)
     */
    @Scheduled(fixedDelayString = "${phc.processing.retry.delay:10000}")
    public void healFailedErpSubmissions() {
        if (!processingEnabled || !healerEnabled) {
            log.warn("PHC ERP healer is disabled");
            return;
        }

        try {
            // Find all COMPLETED SEND_TO_ERP cursors
            List<PhcProcessingCursor> completedErpCursors = cursorRepository.findByActionAndStatus(
                    ProcessingAction.SEND_TO_ERP,
                    ProcessingStatus.COMPLETED
            );

            if (completedErpCursors.isEmpty()) {
                return;
            }

            int totalHealed = 0;
            int totalFailed = 0;

            for (PhcProcessingCursor cursor : completedErpCursors) {
                try {
                    RetryResult result = retryMissedFacilityPayments(cursor);
                    totalHealed += result.healed();
                    totalFailed += result.failed();
                } catch (Exception e) {
                    log.error("Error retrying missed payments for cursor {}: {}", cursor.getId(), e.getMessage(), e);
                }
            }

            if (totalHealed > 0 || totalFailed > 0) {
                log.info("PHC ERP healer completed: {} facility payments healed, {} failed", totalHealed, totalFailed);
            }

        } catch (Exception e) {
            log.error("Error in PHC ERP healer scheduler: {}", e.getMessage(), e);
        }
    }

    /**
     * Retry missed facility payments for a specific cursor's period.
     * Finds APPROVED facility payments that should have been sent to ERP but weren't.
     */
    private RetryResult retryMissedFacilityPayments(PhcProcessingCursor cursor) {
        // Find facility payments for this cursor's period that are still APPROVED
        List<PhcClaimPayment> missedPayments = phcClaimPaymentRepository.findByBatchPreparationDateAndStatus(
                cursor.getBatchPreparationDate(),
                ExtensionStateCodeEnum.APPROVED.name()
        );

        if (missedPayments.isEmpty()) {
            log.debug("No missed facility payments found for cursor {} period {}",
                    cursor.getId(), cursor.getBatchPreparationDate());
            return new RetryResult(0, 0);
        }

        log.warn("Found {} facility payments in APPROVED status for cursor {} that should have been sent to ERP",
                missedPayments.size(), cursor.getId());

        int healed = 0;
        int failed = 0;

        // Process each missed payment
        for (PhcClaimPayment facilityPayment : missedPayments) {
            try {
                // Attempt to send to ERP (updates facility payment status)
                log.info("Send to erp facility payment {} - retrying ERP submission", facilityPayment.getId());
                String result = phcPaymentApprovalService.sendFacilityPaymentToErp(facilityPayment);
                healed++;

                // Add small delay to avoid overwhelming ERP
                Thread.sleep(erpFacilityDelayMs);

            } catch (Exception e) {
                log.error("Failed to heal facility payment {}: {}", facilityPayment.getId(), e.getMessage());
                failed++;
            }
        }

        // Update batch status after healing facility payments (matches normal flow)
        if (healed > 0) {
            PhcBatchPayment batchPayment = missedPayments.get(0).getPhcBatchPayment();
            if (batchPayment != null) {
                phcPaymentApprovalService.updateBatchStatusAfterErpSubmission(batchPayment);
                log.info("Updated batch payment {} status to SENT_FOR_PAYMENT_PROCESSING after healing {} facility payments",
                        batchPayment.getId(), healed);
            }
        }

        return new RetryResult(healed, failed);
    }

    /**
     * Result record for retry operations
     */
    private record RetryResult(int healed, int failed) {}
}
