package io.coredge.health.claims.models.claims;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ClaimAINotesUserReviewRequest {
    private String claimId;
    private JsonNode userReview; // Changed to JsonNode for JSON structure
}
