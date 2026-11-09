package io.coredge.health.claims.dto.phc;

import io.coredge.health.claims.db.entity.PhcProcessingCursor.ProcessingAction;
import io.coredge.health.claims.db.entity.PhcProcessingCursor.ProcessingStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Detailed status DTO for PHC Processing Cursor
 * Used for monitoring and tracking progress
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PhcCursorStatusDto {
    
    private Long cursorId;
    private String yearMonth;
    private ProcessingAction action;
    private ProcessingStatus status;
    private Integer totalRecords;
    private Integer processedRecords;
    private Integer failedRecords;
    private Double progressPercentage;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private String errorMessage;
    private Integer retryCount;
    private Integer maxRetries;
    private Boolean canRetry;
    private String estimatedTimeRemaining;
    private Long durationSeconds;
}
