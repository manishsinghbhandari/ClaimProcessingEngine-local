package io.coredge.health.claims.db.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * PHC Processing Cursor Entity
 * Tracks background processing of PHC workflows (calculation, approval, ERP submission)
 * Enables resumable batch processing with checkpoints
 */
@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "phc_processing_cursor", schema = "public",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_phc_cursor_year_month_action", 
                         columnNames = {"year_month", "action"})
    }
)
public class PhcProcessingCursor extends AuditableBase<String> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "year_month", length = 10, nullable = false)
    private String yearMonth;  // Format: YYYYMM (e.g., "202511") or MMM yyyy (e.g., "Oct 2025")

    @Column(name = "batch_preparation_date", nullable = false)
    private LocalDate batchPreparationDate;  // 28th of the month

    @Enumerated(EnumType.STRING)
    @Column(name = "action", length = 20, nullable = false)
    private ProcessingAction action;

    @Column(name = "last_processed_batch_id")
    private Long lastProcessedBatchId;  // Checkpoint for batch payments

    @Column(name = "last_processed_payment_id")
    private Long lastProcessedPaymentId;  // Checkpoint for facility payments

    @Column(name = "total_records")
    @Builder.Default
    private Integer totalRecords = 0;

    @Column(name = "processed_records")
    @Builder.Default
    private Integer processedRecords = 0;

    @Column(name = "failed_records")
    @Builder.Default
    private Integer failedRecords = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private ProcessingStatus status;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "retry_count")
    @Builder.Default
    private Integer retryCount = 0;

    @Column(name = "max_retries")
    @Builder.Default
    private Integer maxRetries = 3;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    /**
     * Calculate progress percentage
     */
    public Double getProgressPercentage() {
        if (totalRecords == null || totalRecords == 0) {
            return 0.0;
        }
        return (processedRecords.doubleValue() / totalRecords.doubleValue()) * 100.0;
    }
    /**
     * Processing actions for PHC workflows
     * CALCULATION -> APPROVAL -> SEND_TO_ERP (sequential dependency)
     */
    public enum ProcessingAction {
        CALCULATION,
        APPROVAL,
        SEND_TO_ERP
    }

    /**
     * Processing status for cursor tracking
     */
    public enum ProcessingStatus {
        PENDING,
        IN_PROGRESS,
        COMPLETED,
        FAILED,
        PAUSED
    }

    /**
     * Check if processing can be retried
     */
    public boolean canRetry() {
        return status == ProcessingStatus.FAILED && retryCount < maxRetries;
    }

    /**
     * Check if all records are processed
     */
    public boolean isCompleted() {
        return totalRecords != null && processedRecords != null && 
               processedRecords.equals(totalRecords);
    }
}
