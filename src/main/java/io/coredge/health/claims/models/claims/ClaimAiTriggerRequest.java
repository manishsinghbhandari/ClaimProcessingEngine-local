package io.coredge.health.claims.models.claims;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ClaimAiTriggerRequest {
    
    private String packageCode;
    private String interventionCode;
    private LocalDate startDate;
    private LocalDate endDate;
    private String aiUrl;
    private Boolean isWorkflow;
}
