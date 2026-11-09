package io.coredge.health.claims.service;

import io.coredge.health.claims.db.entity.PhcBatchPayment;
import io.coredge.health.claims.db.entity.PhcClaimPayment;
import io.coredge.health.claims.db.entity.PhcProcessingCursor;
import io.coredge.health.claims.db.entity.PhcProcessingCursor.ProcessingAction;
import io.coredge.health.claims.db.entity.PhcProcessingCursor.ProcessingStatus;
import io.coredge.health.claims.db.repositories.PhcBatchPaymentRepository;
import io.coredge.health.claims.db.repositories.PhcClaimPaymentRepository;
import io.coredge.health.claims.db.repositories.PhcProcessingCursorRepository;
import io.coredge.health.claims.dto.phc.PhcCursorResponseDto;
import io.coredge.health.claims.dto.phc.PhcCursorStatusDto;
import io.coredge.health.claims.exception.app.BadRequestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service for PHC background processing using cursor-based approach
 * Handles cursor initialization, validation, monitoring, and control
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PhcBackgroundProcessingService {

    private final PhcProcessingCursorRepository cursorRepository;
    private final PhcBatchPaymentRepository phcBatchPaymentRepository;
    private final PhcClaimPaymentRepository phcClaimPaymentRepository;

    private static final DateTimeFormatter MON_YYYY_FORMATTER = DateTimeFormatter.ofPattern("MMM yyyy", java.util.Locale.ENGLISH);
    private static final String YYYYMM_PATTERN = "\\d{6}";

    // ==================== CURSOR INITIALIZATION METHODS ====================

    /**
     * Initialize CALCULATION cursor
     * Creates a cursor for PHC payment calculation with no prerequisites
     */
    public PhcCursorResponseDto initializeCalculationCursor(String yearMonth) {
        log.info("Initializing CALCULATION cursor for yearMonth: {}", yearMonth);
        
        // Parse and normalize yearMonth to YYYYMM format to prevent duplicates
        YearMonth ym = parseYearMonthFromString(yearMonth);
        String normalizedYearMonth = formatYearMonthToYYYYMM(ym);
        LocalDate batchDate = ym.atDay(28);
        
        log.debug("Normalized yearMonth from '{}' to '{}'", yearMonth, normalizedYearMonth);
        
        // Check if cursor already exists (using normalized format, excluding FAILED)
        Optional<PhcProcessingCursor> existing = cursorRepository.findLatestNonFailedByYearMonthAndAction(
                normalizedYearMonth, ProcessingAction.CALCULATION, ProcessingStatus.FAILED);
        
        if (existing.isPresent()) {
            PhcProcessingCursor cursor = existing.get();
            if (cursor.getStatus() == ProcessingStatus.PENDING || 
                cursor.getStatus() == ProcessingStatus.IN_PROGRESS) {
                throw new BadRequestException(
                    "Calculation already in progress for " + yearMonth);
            }
            if (cursor.getStatus() == ProcessingStatus.COMPLETED) {
                throw new BadRequestException(
                    "Calculation already completed for " + yearMonth);
            }
        }
        
        // Create new cursor with normalized yearMonth
        PhcProcessingCursor cursor = PhcProcessingCursor.builder()
                .yearMonth(normalizedYearMonth)  // ✅ Store normalized format
                .batchPreparationDate(batchDate)
                .action(ProcessingAction.CALCULATION)
                .status(ProcessingStatus.PENDING)
                .totalRecords(0)  // Will be updated during processing
                .processedRecords(0)
                .failedRecords(0)
                .retryCount(0)
                .maxRetries(3)
                .build();
        
        cursor = cursorRepository.save(cursor);
        log.info("Created CALCULATION cursor with ID: {} for yearMonth: {}", cursor.getId(), normalizedYearMonth);
        
        return PhcCursorResponseDto.createInitialResponse(
                cursor.getId(), 
                yearMonth, 
                ProcessingAction.CALCULATION,
                "PHC calculation initiated. Processing will begin shortly."
        );
    }

    /**
     * Initialize APPROVAL cursor
     * Requires CALCULATION to be completed first
     */
    public PhcCursorResponseDto initializeApprovalCursor(String yearMonth) {
        log.info("Initializing APPROVAL cursor for yearMonth: {}", yearMonth);
        
        // Parse and normalize yearMonth to YYYYMM format
        YearMonth ym = parseYearMonthFromString(yearMonth);
        String normalizedYearMonth = formatYearMonthToYYYYMM(ym);
        LocalDate batchDate = ym.atDay(28);
        
        log.debug("Normalized yearMonth from '{}' to '{}'", yearMonth, normalizedYearMonth);
        
        // Validate CALCULATION is completed (using normalized format)
        validateCalculationCompleted(normalizedYearMonth);
        
        // Check if cursor already exists (using normalized format, excluding FAILED)
        Optional<PhcProcessingCursor> existing = cursorRepository.findLatestNonFailedByYearMonthAndAction(
                normalizedYearMonth, ProcessingAction.APPROVAL, ProcessingStatus.FAILED);
        
        if (existing.isPresent()) {
            PhcProcessingCursor cursor = existing.get();
            if (cursor.getStatus() == ProcessingStatus.PENDING || 
                cursor.getStatus() == ProcessingStatus.IN_PROGRESS) {
                throw new BadRequestException(
                    "Approval already in progress for " + yearMonth );
            }
            if (cursor.getStatus() == ProcessingStatus.COMPLETED) {
                throw new BadRequestException(
                    "Approval already completed for " + yearMonth + ". Use retry if needed.");
            }
        }
        
        // Count total facility payments to approve
        List<PhcClaimPayment> facilityPayments = phcClaimPaymentRepository.findByBatchPreparationDate(batchDate);
        
        // Create new cursor with normalized yearMonth
        PhcProcessingCursor cursor = PhcProcessingCursor.builder()
                .yearMonth(normalizedYearMonth)  // ✅ Store normalized format
                .batchPreparationDate(batchDate)
                .action(ProcessingAction.APPROVAL)
                .status(ProcessingStatus.PENDING)
                .totalRecords(facilityPayments.size())
                .processedRecords(0)
                .failedRecords(0)
                .retryCount(0)
                .maxRetries(3)
                .build();
        
        cursor = cursorRepository.save(cursor);
        log.info("Created APPROVAL cursor with ID: {} for yearMonth: {} with {} facilities", 
                cursor.getId(), normalizedYearMonth, facilityPayments.size());
        
        return PhcCursorResponseDto.createInitialResponse(
                cursor.getId(), 
                yearMonth, 
                ProcessingAction.APPROVAL,
                String.format("Approval processing initiated for %d facilities.", facilityPayments.size())
        );
    }

    /**
     * Initialize SEND_TO_ERP cursor
     * Requires APPROVAL to be completed first
     */
    public PhcCursorResponseDto initializeErpSubmissionCursor(String yearMonth) {
        log.info("Initializing SEND_TO_ERP cursor for yearMonth: {}", yearMonth);
        
        // Parse and normalize yearMonth to YYYYMM format
        YearMonth ym = parseYearMonthFromString(yearMonth);
        String normalizedYearMonth = formatYearMonthToYYYYMM(ym);
        LocalDate batchDate = ym.atDay(28);
        
        log.debug("Normalized yearMonth from '{}' to '{}'", yearMonth, normalizedYearMonth);
        
        // Validate APPROVAL is completed (using normalized format)
        validateApprovalCompleted(normalizedYearMonth);
        
        // Check if cursor already exists (using normalized format, excluding FAILED)
        Optional<PhcProcessingCursor> existing = cursorRepository.findLatestNonFailedByYearMonthAndAction(
                normalizedYearMonth, ProcessingAction.SEND_TO_ERP, ProcessingStatus.FAILED);
        
        if (existing.isPresent()) {
            PhcProcessingCursor cursor = existing.get();
            if (cursor.getStatus() == ProcessingStatus.PENDING || 
                cursor.getStatus() == ProcessingStatus.IN_PROGRESS) {
                throw new BadRequestException(
                    "ERP submission already in progress for " + yearMonth );
            }
            if (cursor.getStatus() == ProcessingStatus.COMPLETED) {
                throw new BadRequestException(
                    "ERP submission already completed for " + yearMonth);
            }
        }
        
        // Count approved batch payments for ERP submission
        LocalDate startDate = ym.atDay(1);
        LocalDate endDate = ym.atEndOfMonth();
        List<PhcBatchPayment> batchPayments = phcBatchPaymentRepository.findByDateBetween(startDate, endDate);
        List<PhcBatchPayment> approvedBatches = batchPayments.stream()
                .filter(b -> b.getStatus() == PhcBatchPayment.Status.APPROVED)
                .collect(Collectors.toList());
        
        // Create new cursor with normalized yearMonth
        PhcProcessingCursor cursor = PhcProcessingCursor.builder()
                .yearMonth(normalizedYearMonth)  // ✅ Store normalized format
                .batchPreparationDate(batchDate)
                .action(ProcessingAction.SEND_TO_ERP)
                .status(ProcessingStatus.PENDING)
                .totalRecords(approvedBatches.size())
                .processedRecords(0)
                .failedRecords(0)
                .retryCount(0)
                .maxRetries(3)
                .build();
        
        cursor = cursorRepository.save(cursor);
        log.info("Created SEND_TO_ERP cursor with ID: {} for yearMonth: {} with {} batches", 
                cursor.getId(), normalizedYearMonth, approvedBatches.size());
        
        return PhcCursorResponseDto.createInitialResponse(
                cursor.getId(), 
                yearMonth, 
                ProcessingAction.SEND_TO_ERP,
                String.format("ERP submission initiated for %d county batches.", approvedBatches.size())
        );
    }

    // ==================== DEPENDENCY VALIDATION METHODS ====================

    /**
     * Validate that CALCULATION is completed before APPROVAL
     */
    private void validateCalculationCompleted(String yearMonth) {
        boolean calculationCompleted = cursorRepository.existsByYearMonthAndActionAndStatus(
                yearMonth, ProcessingAction.CALCULATION, ProcessingStatus.COMPLETED);
        
        if (!calculationCompleted) {
            Optional<PhcProcessingCursor> calcCursor = cursorRepository.findLatestNonFailedByYearMonthAndAction(
                    yearMonth, ProcessingAction.CALCULATION, ProcessingStatus.FAILED);
            
            if (calcCursor.isEmpty()) {
                throw new BadRequestException(
                    "PHC calculation must be completed before approval. No calculation found for " + yearMonth);
            }
            
            PhcProcessingCursor cursor = calcCursor.get();
            throw new BadRequestException(
                String.format("PHC calculation must be completed before approval. Current status: %s (Cursor ID: %d)",
                    cursor.getStatus(), cursor.getId()));
        }
        
        log.info("Validation passed: CALCULATION completed for yearMonth: {}", yearMonth);
    }

    /**
     * Validate that APPROVAL is completed before SEND_TO_ERP
     */
    private void validateApprovalCompleted(String yearMonth) {
        boolean approvalCompleted = cursorRepository.existsByYearMonthAndActionAndStatus(
                yearMonth, ProcessingAction.APPROVAL, ProcessingStatus.COMPLETED);
        
        if (!approvalCompleted) {
            Optional<PhcProcessingCursor> approvalCursor = cursorRepository.findLatestNonFailedByYearMonthAndAction(
                    yearMonth, ProcessingAction.APPROVAL, ProcessingStatus.FAILED);
            
            if (approvalCursor.isEmpty()) {
                throw new BadRequestException(
                    "Approval must be completed before ERP submission. No approval found for " + yearMonth);
            }
            
            PhcProcessingCursor cursor = approvalCursor.get();
            throw new BadRequestException(
                String.format("Approval must be completed before ERP submission. Current status: %s (Cursor ID: %d)",
                    cursor.getStatus(), cursor.getId()));
        }
        
        log.info("Validation passed: APPROVAL completed for yearMonth: {}", yearMonth);
    }

    // ==================== CURSOR MANAGEMENT METHODS ====================

    /**
     * Get cursor status by ID
     */
    public PhcCursorStatusDto getCursorStatus(Long cursorId) {
        log.info("Fetching cursor status for ID: {}", cursorId);
        
        PhcProcessingCursor cursor = cursorRepository.findById(cursorId)
                .orElseThrow(() -> new BadRequestException("Cursor not found: " + cursorId));
        
        return convertCursorToStatusDto(cursor);
    }

    /**
     * List all cursors with optional filters
     */
    public Page<PhcCursorStatusDto> listCursors(String yearMonth, ProcessingAction action, 
                                                ProcessingStatus status, Pageable pageable) {
        log.info("Listing cursors with filters - yearMonth: {}, action: {}, status: {}", 
                yearMonth, action, status);
        
        List<PhcProcessingCursor> allCursors = cursorRepository.findAll();
        
        // Apply filters
        List<PhcProcessingCursor> filteredCursors = allCursors.stream()
                .filter(c -> yearMonth == null || c.getYearMonth().equals(yearMonth))
                .filter(c -> action == null || c.getAction() == action)
                .filter(c -> status == null || c.getStatus() == status)
                .collect(Collectors.toList());
        
        // Manual pagination
        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), filteredCursors.size());
        List<PhcProcessingCursor> pageContent = filteredCursors.subList(start, end);
        
        List<PhcCursorStatusDto> dtos = pageContent.stream()
                .map(this::convertCursorToStatusDto)
                .collect(Collectors.toList());
        
        return new PageImpl<>(dtos, pageable, filteredCursors.size());
    }

    /**
     * Retry a failed cursor
     */
    public String retryCursor(Long cursorId) {
        log.info("Retrying cursor: {}", cursorId);
        
        PhcProcessingCursor cursor = cursorRepository.findById(cursorId)
                .orElseThrow(() -> new BadRequestException("Cursor not found: " + cursorId));
        
        // Validate cursor can be retried
        if (!cursor.canRetry()) {
            if (cursor.getStatus() != ProcessingStatus.FAILED) {
                throw new BadRequestException("Only FAILED cursors can be retried. Current status: " + cursor.getStatus());
            }
            if (cursor.getRetryCount() >= cursor.getMaxRetries()) {
                throw new BadRequestException("Maximum retry attempts reached: " + cursor.getRetryCount() + "/" + cursor.getMaxRetries());
            }
        }
        
        // Reset cursor for retry
        cursor.setStatus(ProcessingStatus.PENDING);
        cursor.setErrorMessage(null);
        cursor.setRetryCount(cursor.getRetryCount() + 1);
        cursor.setLastModifiedDate(LocalDateTime.now());
        
        cursorRepository.save(cursor);
        
        String message = String.format("Cursor %d reset for retry (attempt %d/%d)", 
                cursorId, cursor.getRetryCount(), cursor.getMaxRetries());
        log.info(message);
        
        return message;
    }

    /**
     * Cancel a pending or in-progress cursor
     */
    public String cancelCursor(Long cursorId) {
        log.info("Cancelling cursor: {}", cursorId);
        
        PhcProcessingCursor cursor = cursorRepository.findById(cursorId)
                .orElseThrow(() -> new BadRequestException("Cursor not found: " + cursorId));
        
        // Only allow cancelling PENDING or IN_PROGRESS cursors
        if (cursor.getStatus() != ProcessingStatus.PENDING && cursor.getStatus() != ProcessingStatus.IN_PROGRESS) {
            throw new BadRequestException("Only PENDING or IN_PROGRESS cursors can be cancelled. Current status: " + cursor.getStatus());
        }
        
        // Mark as paused (can be resumed later if needed)
        cursor.setStatus(ProcessingStatus.PAUSED);
        cursor.setLastModifiedDate(LocalDateTime.now());
        
        cursorRepository.save(cursor);
        
        String message = String.format("Cursor %d has been paused", cursorId);
        log.info(message);
        
        return message;
    }

    // ==================== HELPER METHODS ====================

    /**
     * Parse yearMonth string to YearMonth object
     * Handles both YYYYMM and "Mon YYYY" formats
     */
    private YearMonth parseYearMonthFromString(String yearMonth) {
        String decoded = yearMonth.replace("%20", " ");
        
        // Try YYYYMM format first
        if (decoded.matches(YYYYMM_PATTERN)) {
            return parseYYYYMM(decoded);
        }
        
        // Try Mon YYYY format
        return parseMonYYYY(decoded);
    }

    private YearMonth parseYYYYMM(String yyyymm) {
        int year = Integer.parseInt(yyyymm.substring(0, 4));
        int month = Integer.parseInt(yyyymm.substring(4, 6));
        return YearMonth.of(year, month);
    }

    private YearMonth parseMonYYYY(String monYyyy) {
        return YearMonth.parse(monYyyy, MON_YYYY_FORMATTER);
    }

    /**
     * Format YearMonth to YYYYMM string for consistent database storage
     * Prevents duplicates like "202510" and "Oct 2025"
     */
    private String formatYearMonthToYYYYMM(YearMonth ym) {
        return String.format("%04d%02d", ym.getYear(), ym.getMonthValue());
    }

    /**
     * Convert cursor entity to status DTO
     */
    private PhcCursorStatusDto convertCursorToStatusDto(PhcProcessingCursor cursor) {
        PhcCursorStatusDto dto = PhcCursorStatusDto.builder()
                .cursorId(cursor.getId())
                .yearMonth(cursor.getYearMonth())
                .action(cursor.getAction())
                .status(cursor.getStatus())
                .totalRecords(cursor.getTotalRecords())
                .processedRecords(cursor.getProcessedRecords())
                .failedRecords(cursor.getFailedRecords())
                .progressPercentage(cursor.getProgressPercentage())
                .startedAt(cursor.getStartedAt())
                .completedAt(cursor.getCompletedAt())
                .errorMessage(cursor.getErrorMessage())
                .retryCount(cursor.getRetryCount())
                .maxRetries(cursor.getMaxRetries())
                .canRetry(cursor.canRetry())
                .build();

        return dto;
    }
}
