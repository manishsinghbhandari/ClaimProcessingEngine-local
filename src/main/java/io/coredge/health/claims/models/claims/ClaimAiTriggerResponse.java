package io.coredge.health.claims.models.claims;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ClaimAiTriggerResponse {
    
    private Long id;
    private String packageCode;
    private String interventionCode;
    private LocalDate startDate;
    private LocalDate endDate;
    private String status;
    private Long lastProcessedClaimId;
    private Integer processedClaimsCount;
    private String aiUrl;
    private Boolean isWorkflow;
    private String createdBy;
    private LocalDateTime createdDate;
    private String lastModifiedBy;
    private LocalDateTime lastModifiedDate;
}
