package io.coredge.health.claims.dto.phc;

import io.coredge.health.claims.db.entity.PhcProcessingCursor.ProcessingAction;
import io.coredge.health.claims.db.entity.PhcProcessingCursor.ProcessingStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Response DTO for PHC Processing Cursor
 * Returned when initiating background processing
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PhcCursorResponseDto {
    
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
    private String message;
    private String errorMessage;
    private Integer retryCount;
    private Integer maxRetries;
    private Boolean canRetry;
    
    /**
     * Create initial response when cursor is created
     */
    public static PhcCursorResponseDto createInitialResponse(Long cursorId, String yearMonth, 
                                                             ProcessingAction action, String message) {
        return PhcCursorResponseDto.builder()
                .cursorId(cursorId)
                .yearMonth(yearMonth)
                .action(action)
                .status(ProcessingStatus.PENDING)
                .totalRecords(0)
                .processedRecords(0)
                .failedRecords(0)
                .progressPercentage(0.0)
                .message(message)
                .retryCount(0)
                .canRetry(false)
                .build();
    }
}
