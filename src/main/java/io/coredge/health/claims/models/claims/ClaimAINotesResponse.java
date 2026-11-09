package io.coredge.health.claims.models.claims;

import com.fasterxml.jackson.databind.JsonNode;
import io.coredge.health.claims.enums.ClaimAIProcessStatusEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ClaimAINotesResponse {
    private Long id;
    private String claimId;
    private JsonNode notes; // Changed to JsonNode for JSON structure
    private JsonNode userReview; // Changed to JsonNode for JSON structure
    private String overallNotes;
    private ClaimAIProcessStatusEnum status;
    private String createdBy;
    private LocalDateTime createdDate;
    private String lastModifiedBy;
    private LocalDateTime lastModifiedDate;
}
